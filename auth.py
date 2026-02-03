import json
import os
import time
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime, timedelta

AUTH_FILE = "auth_data.json"
# Politique de sécurité
MAX_FAILED = 5            # nombre de tentatives avant lock
LOCK_SECONDS = 5 * 60     # durée du lock en secondes (ici 5 minutes)

def load_auth_data():
    if not os.path.exists(AUTH_FILE):
        return {"users": {}, "ip_map": {}}
    with open(AUTH_FILE, "r", encoding="utf-8") as f:
        return json.load(f)

def save_auth_data(data):
    with open(AUTH_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=4, ensure_ascii=False)

def user_exists(username):
    data = load_auth_data()
    return username in data.get("users", {})

def get_user(username):
    data = load_auth_data()
    return data.get("users", {}).get(username)

def get_user_by_ip(ip):
    data = load_auth_data()
    return data.get("ip_map", {}).get(ip)

def current_timestamp():
    return int(time.time())

def create_user(username, password, ip, telegram_id=None):
    """
    Crée un utilisateur avec mot de passe hashé et map ip->username.
    Retourne (True, "") si ok, (False, "raison") si erreur.
    """
    username = str(username)
    data = load_auth_data()
    if username in data.get("users", {}):
        return False, "Nom d'utilisateur déjà utilisé."
    existing = data.get("ip_map", {}).get(ip)
    if existing:
        return False, "Un compte existe déjà depuis cette IP."
    pwd_hash = generate_password_hash(password)
    now = datetime.utcnow().isoformat() + "Z"
    data.setdefault("users", {})[username] = {
        "password_hash": pwd_hash,
        "created_at": now,
        "telegram_id": telegram_id or "",
        "failed_attempts": 0,
        "locked_until": 0
    }
    data.setdefault("ip_map", {})[ip] = username
    save_auth_data(data)
    return True, ""

def authenticate_user(username, password):
    """
    Tentative d'authentification.
    Retourne (True, "") ou (False, "raison").
    Gère le comptage d'échecs et le verrouillage temporaire.
    """
    data = load_auth_data()
    user = data.get("users", {}).get(username)
    if not user:
        return False, "Utilisateur inconnu."
    now = current_timestamp()
    locked_until = int(user.get("locked_until", 0) or 0)
    if locked_until > now:
        remaining = locked_until - now
        return False, f"Compte verrouillé. Réessaye dans {remaining // 60} min."

    if check_password_hash(user.get("password_hash", ""), password):
        # succès : reset counters
        user["failed_attempts"] = 0
        user["locked_until"] = 0
        save_auth_data(data)
        return True, ""
    else:
        # échec : incrémenter et éventuellement verrouiller
        user["failed_attempts"] = int(user.get("failed_attempts", 0)) + 1
        if user["failed_attempts"] >= MAX_FAILED:
            user["locked_until"] = now + LOCK_SECONDS
            user["failed_attempts"] = 0  # reset after lock
        save_auth_data(data)
        attempts_left = max(0, MAX_FAILED - user.get("failed_attempts", 0))
        return False, f"Mot de passe incorrect. Tentatives restantes avant lock : {attempts_left}"

def link_telegram_id(username, telegram_id):
    data = load_auth_data()
    if username not in data.get("users", {}):
        return False
    data["users"][username]["telegram_id"] = str(telegram_id)
    save_auth_data(data)
    return True

def unregister_ip(ip):
    data = load_auth_data()
    if ip in data.get("ip_map", {}):
        del data["ip_map"][ip]
        save_auth_data(data)
        return True
    return False