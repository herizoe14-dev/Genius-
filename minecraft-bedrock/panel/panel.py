#!/usr/bin/env python3
"""Panneau web de gestion du serveur Minecraft Bedrock.

Aucune dépendance externe (bibliothèque standard Python uniquement).
Tourne en tant qu'utilisateur `minecraft` ; les actions qui demandent root
(démarrer/arrêter le service, mise à jour) passent par des règles sudo précises.
"""
import hashlib
import hmac
import json
import os
import re
import secrets
import ssl
import subprocess
import tarfile
import threading
import time
from http import cookies
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

CONFIG_PATH = Path(os.environ.get("PANEL_CONFIG", "/etc/bedrock-panel/config.json"))
CONFIG = json.loads(CONFIG_PATH.read_text())

MC_DIR = Path(CONFIG.get("mc_dir", "/opt/bedrock"))
SERVICE = CONFIG.get("service", "bedrock")
PORT = int(CONFIG.get("port", 8443))
CERT = CONFIG.get("cert")
KEY = CONFIG.get("key")
STATIC = Path(__file__).resolve().parent / "index.html"
CONSOLE_LOG = MC_DIR / "console.log"
BACKUP_DIR = MC_DIR / "backups"
UPDATE_LOG = MC_DIR / "update-panel.log"
SUDO = ["sudo", "-n"]

SESSION_TTL = 7 * 24 * 3600
MAX_BODY = 64 * 1024
sessions: dict[str, float] = {}
failed: dict[str, list[float]] = {}
lock = threading.Lock()

# Réglages modifiables depuis le panneau, avec leur validation.
EDITABLE = {
    "server-name": r"[^\n\r;]{1,60}",
    "gamemode": r"survival|creative|adventure",
    "difficulty": r"peaceful|easy|normal|hard",
    "max-players": r"[1-9][0-9]?",
    "allow-cheats": r"true|false",
    "allow-list": r"true|false",
    "online-mode": r"true|false",
    "view-distance": r"([5-9]|[1-2][0-9]|3[0-2])",
    "tick-distance": r"([4-9]|1[0-2])",
    "player-idle-timeout": r"[0-9]{1,4}",
    "default-player-permission-level": r"visitor|member|operator",
    "texturepack-required": r"true|false",
    "level-name": r"[A-Za-z0-9 _\-À-ÿ]{1,40}",
    "level-seed": r"-?[0-9]{0,20}",
}


# --------------------------------------------------------------------------- utils
def run(cmd, timeout=30):
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return p.returncode, (p.stdout + p.stderr).strip()
    except subprocess.TimeoutExpired:
        return 124, "délai dépassé"


def check_password(pw: str) -> bool:
    salt = bytes.fromhex(CONFIG["salt"])
    digest = hashlib.scrypt(pw.encode(), salt=salt, n=2**14, r=8, p=1).hex()
    return hmac.compare_digest(digest, CONFIG["hash"])


def is_running() -> bool:
    return run(["systemctl", "is-active", "--quiet", SERVICE])[0] == 0


def server_pid():
    code, out = run(["pgrep", "-x", "bedrock_server"])
    return int(out.split()[0]) if code == 0 and out else None


def send_console(cmd: str) -> bool:
    # `stuff` interprète \ et ^ : on les échappe.
    safe = cmd.replace("\\", "\\\\").replace("^", "\\^")
    code, _ = run(["screen", "-S", SERVICE, "-p", "0", "-X", "stuff", safe + "\r"])
    return code == 0


def read_log(lines=200) -> list[str]:
    if not CONSOLE_LOG.exists():
        return []
    with CONSOLE_LOG.open("rb") as f:
        f.seek(0, 2)
        size = f.tell()
        f.seek(max(0, size - 256 * 1024))
        data = f.read().decode("utf-8", "replace")
    data = re.sub(r"\x1b\[[0-9;?]*[A-Za-z]", "", data).replace("\r", "")
    return [l for l in data.split("\n") if l.strip()][-lines:]


def online_players() -> list[str]:
    players: list[str] = []
    for line in read_log(5000):
        m = re.search(r"Player connected: ([^,]+),", line)
        if m and m.group(1) not in players:
            players.append(m.group(1))
        m = re.search(r"Player disconnected: ([^,]+),", line)
        if m and m.group(1) in players:
            players.remove(m.group(1))
    return players


def read_props() -> dict:
    props = {}
    path = MC_DIR / "server.properties"
    if path.exists():
        for line in path.read_text(errors="replace").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()
    return props


def write_props(changes: dict):
    path = MC_DIR / "server.properties"
    lines = path.read_text(errors="replace").splitlines()
    done = set()
    for i, line in enumerate(lines):
        if "=" in line and not line.lstrip().startswith("#"):
            k = line.split("=", 1)[0].strip()
            if k in changes:
                lines[i] = f"{k}={changes[k]}"
                done.add(k)
    for k, v in changes.items():
        if k not in done:
            lines.append(f"{k}={v}")
    tmp = path.with_suffix(".tmp")
    tmp.write_text("\n".join(lines) + "\n")
    tmp.replace(path)


def status() -> dict:
    running = is_running()
    pid = server_pid()
    mem_mb = None
    if pid:
        try:
            for line in Path(f"/proc/{pid}/status").read_text().splitlines():
                if line.startswith("VmRSS:"):
                    mem_mb = round(int(line.split()[1]) / 1024)
        except OSError:
            pass
    since = run(["systemctl", "show", SERVICE, "-p", "ActiveEnterTimestampMonotonic", "--value"])[1]
    uptime = None
    try:
        if running and since and int(since) > 0:
            uptime = int(time.clock_gettime(time.CLOCK_MONOTONIC) - int(since) / 1e6)
    except ValueError:
        pass
    meminfo = {}
    for line in Path("/proc/meminfo").read_text().splitlines():
        k, v = line.split(":", 1)
        meminfo[k] = int(v.split()[0]) // 1024
    st = os.statvfs(str(MC_DIR))
    props = read_props()
    return {
        "running": running,
        "version": (MC_DIR / ".version").read_text().strip() if (MC_DIR / ".version").exists() else "?",
        "uptime": uptime,
        "server_mem_mb": mem_mb,
        "mem_total_mb": meminfo.get("MemTotal"),
        "mem_avail_mb": meminfo.get("MemAvailable"),
        "load": os.getloadavg()[0],
        "disk_free_gb": round(st.f_bavail * st.f_frsize / 1e9, 1),
        "players": online_players() if running else [],
        "max_players": props.get("max-players"),
        "server_name": props.get("server-name"),
        "seed": props.get("level-seed"),
        "port": props.get("server-port", "19132"),
        "updating": updating.is_set(),
    }


# --------------------------------------------------------------------------- tâches longues
updating = threading.Event()


def do_update():
    if updating.is_set():
        return
    updating.set()

    def job():
        try:
            with UPDATE_LOG.open("a") as f:
                f.write(f"\n=== {time.strftime('%F %T')} mise à jour demandée depuis le panneau ===\n")
                f.flush()
                subprocess.run(SUDO + ["/usr/local/bin/bedrock-update"], stdout=f, stderr=f, timeout=1800)
        finally:
            updating.clear()

    threading.Thread(target=job, daemon=True).start()


def make_backup() -> str:
    BACKUP_DIR.mkdir(exist_ok=True)
    name = f"worlds-{time.strftime('%F-%H%M%S')}.tar.gz"
    live = is_running()
    if live:
        send_console("save hold")
        time.sleep(4)
    try:
        with tarfile.open(BACKUP_DIR / name, "w:gz") as tar:
            tar.add(MC_DIR / "worlds", arcname="worlds")
    finally:
        if live:
            send_console("save resume")
    return name


def list_backups() -> list[dict]:
    if not BACKUP_DIR.exists():
        return []
    out = []
    for p in sorted(BACKUP_DIR.glob("worlds-*.tar.gz"), reverse=True):
        st = p.stat()
        out.append({"name": p.name, "size_mb": round(st.st_size / 1e6, 1), "date": time.strftime("%d/%m/%Y %H:%M", time.localtime(st.st_mtime))})
    return out


def backup_path(name: str):
    if not re.fullmatch(r"worlds-[0-9\-]+\.tar\.gz", name or ""):
        return None
    p = BACKUP_DIR / name
    return p if p.exists() else None


def restore_backup(p: Path):
    was = is_running()
    if was:
        run(SUDO + ["systemctl", "stop", SERVICE], timeout=90)
    make_safety = BACKUP_DIR / f"worlds-{time.strftime('%F-%H%M%S')}-avant-restauration.tar.gz"
    if (MC_DIR / "worlds").exists():
        with tarfile.open(make_safety, "w:gz") as tar:
            tar.add(MC_DIR / "worlds", arcname="worlds")
        subprocess.run(["rm", "-rf", str(MC_DIR / "worlds")])
    with tarfile.open(p) as tar:
        members = [m for m in tar.getmembers() if m.name.startswith("worlds") and not m.issym() and not m.islnk()]
        tar.extractall(MC_DIR, members=members, filter="data")
    if was:
        run(SUDO + ["systemctl", "start", SERVICE])


# --------------------------------------------------------------------------- HTTP
class Handler(BaseHTTPRequestHandler):
    server_version = "BedrockPanel"

    def log_message(self, fmt, *args):
        pass

    # -- helpers
    def client_ip(self):
        return self.client_address[0]

    def session_ok(self) -> bool:
        c = cookies.SimpleCookie(self.headers.get("Cookie", ""))
        tok = c["sid"].value if "sid" in c else None
        with lock:
            exp = sessions.get(tok) if tok else None
            if exp and exp > time.time():
                return True
            if tok:
                sessions.pop(tok, None)
        return False

    def send_json(self, data, code=200, extra_headers=None):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (extra_headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def body(self) -> dict:
        n = int(self.headers.get("Content-Length") or 0)
        if n > MAX_BODY:
            raise ValueError("trop gros")
        raw = self.rfile.read(n) if n else b"{}"
        return json.loads(raw or b"{}")

    def security_headers(self):
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")

    # -- GET
    def do_GET(self):
        url = urlparse(self.path)
        if url.path in ("/", "/index.html"):
            body = STATIC.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.security_headers()
            self.end_headers()
            self.wfile.write(body)
            return
        if not url.path.startswith("/api/"):
            return self.send_json({"error": "introuvable"}, 404)
        if url.path == "/api/me":
            return self.send_json({"logged": self.session_ok()})
        if not self.session_ok():
            return self.send_json({"error": "non connecté"}, 401)
        q = parse_qs(url.query)
        if url.path == "/api/status":
            return self.send_json(status())
        if url.path == "/api/logs":
            n = min(int(q.get("n", ["200"])[0]), 1000)
            return self.send_json({"lines": read_log(n)})
        if url.path == "/api/properties":
            props = read_props()
            return self.send_json({"editable": {k: props.get(k, "") for k in EDITABLE}})
        if url.path == "/api/backups":
            return self.send_json({"backups": list_backups()})
        if url.path == "/api/backups/download":
            p = backup_path(q.get("name", [""])[0])
            if not p:
                return self.send_json({"error": "introuvable"}, 404)
            self.send_response(200)
            self.send_header("Content-Type", "application/gzip")
            self.send_header("Content-Disposition", f'attachment; filename="{p.name}"')
            self.send_header("Content-Length", str(p.stat().st_size))
            self.end_headers()
            with p.open("rb") as f:
                while chunk := f.read(65536):
                    self.wfile.write(chunk)
            return
        if url.path == "/api/update-log":
            txt = UPDATE_LOG.read_text(errors="replace")[-8000:] if UPDATE_LOG.exists() else ""
            return self.send_json({"log": txt, "updating": updating.is_set()})
        return self.send_json({"error": "introuvable"}, 404)

    # -- POST
    def do_POST(self):
        url = urlparse(self.path)
        # Protection CSRF : en-tête personnalisé obligatoire (impossible à envoyer
        # depuis un autre site sans CORS) + cookie SameSite=Strict.
        if self.headers.get("X-Panel") != "1":
            return self.send_json({"error": "requête refusée"}, 403)
        try:
            data = self.body()
        except (ValueError, json.JSONDecodeError):
            return self.send_json({"error": "requête invalide"}, 400)

        if url.path == "/api/login":
            ip = self.client_ip()
            now = time.time()
            with lock:
                recent = [t for t in failed.get(ip, []) if now - t < 600]
                failed[ip] = recent
            if len(recent) >= 5:
                return self.send_json({"error": "Trop d'essais. Réessaie dans 10 minutes."}, 429)
            if not check_password(str(data.get("password", ""))):
                with lock:
                    failed[ip].append(now)
                time.sleep(1)
                return self.send_json({"error": "Mot de passe incorrect"}, 401)
            tok = secrets.token_urlsafe(32)
            with lock:
                sessions[tok] = now + SESSION_TTL
                failed.pop(ip, None)
            cookie = f"sid={tok}; Path=/; Max-Age={SESSION_TTL}; HttpOnly; SameSite=Strict"
            if CERT:
                cookie += "; Secure"
            return self.send_json({"ok": True}, extra_headers={"Set-Cookie": cookie})

        if not self.session_ok():
            return self.send_json({"error": "non connecté"}, 401)

        if url.path == "/api/logout":
            c = cookies.SimpleCookie(self.headers.get("Cookie", ""))
            with lock:
                sessions.pop(c["sid"].value if "sid" in c else "", None)
            return self.send_json({"ok": True}, extra_headers={"Set-Cookie": "sid=; Path=/; Max-Age=0"})

        if url.path == "/api/action":
            action = data.get("action")
            if action in ("start", "stop", "restart"):
                code, out = run(SUDO + ["systemctl", action, SERVICE], timeout=90)
                return self.send_json({"ok": code == 0, "message": out})
            if action == "update":
                do_update()
                return self.send_json({"ok": True})
            return self.send_json({"error": "action inconnue"}, 400)

        if url.path == "/api/command":
            cmd = str(data.get("command", "")).strip().lstrip("/")
            if not cmd or len(cmd) > 300 or re.search(r"[\x00-\x1f\x7f]", cmd):
                return self.send_json({"error": "commande invalide"}, 400)
            if not is_running():
                return self.send_json({"error": "Le serveur est arrêté"}, 409)
            return self.send_json({"ok": send_console(cmd)})

        if url.path == "/api/player":
            name = str(data.get("name", "")).strip()
            action = data.get("action")
            if not re.fullmatch(r"[A-Za-z0-9 _]{1,32}", name):
                return self.send_json({"error": "pseudo invalide"}, 400)
            cmds = {
                "op": f'op "{name}"', "deop": f'deop "{name}"',
                "kick": f'kick "{name}"',
                "allow": f'allowlist add "{name}"', "disallow": f'allowlist remove "{name}"',
            }
            if action not in cmds:
                return self.send_json({"error": "action inconnue"}, 400)
            if not is_running():
                return self.send_json({"error": "Le serveur est arrêté"}, 409)
            return self.send_json({"ok": send_console(cmds[action])})

        if url.path == "/api/properties":
            changes = {}
            for k, v in (data.get("changes") or {}).items():
                v = str(v).strip()
                if k not in EDITABLE or not re.fullmatch(EDITABLE[k], v):
                    return self.send_json({"error": f"valeur invalide pour {k}"}, 400)
                changes[k] = v
            if changes:
                write_props(changes)
            return self.send_json({"ok": True, "restart_needed": bool(changes)})

        if url.path == "/api/backups":
            try:
                return self.send_json({"ok": True, "name": make_backup()})
            except Exception as e:  # noqa: BLE001
                return self.send_json({"error": f"échec : {e}"}, 500)

        if url.path in ("/api/backups/restore", "/api/backups/delete"):
            p = backup_path(str(data.get("name", "")))
            if not p:
                return self.send_json({"error": "sauvegarde introuvable"}, 404)
            if url.path.endswith("delete"):
                p.unlink()
                return self.send_json({"ok": True})
            try:
                restore_backup(p)
                return self.send_json({"ok": True})
            except Exception as e:  # noqa: BLE001
                return self.send_json({"error": f"échec : {e}"}, 500)

        return self.send_json({"error": "introuvable"}, 404)


def main():
    httpd = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    if CERT and KEY:
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.minimum_version = ssl.TLSVersion.TLSv1_2
        ctx.load_cert_chain(CERT, KEY)
        httpd.socket = ctx.wrap_socket(httpd.socket, server_side=True)
    print(f"Panneau Bedrock sur le port {PORT}", flush=True)
    httpd.serve_forever()


if __name__ == "__main__":
    main()
