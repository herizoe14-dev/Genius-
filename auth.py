import json
import os
import time
import re
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime, timedelta

AUTH_FILE = "auth_data.json"
# Politique de sécurité
MAX_FAILED = 5            # nombre de tentatives avant lock
LOCK_SECONDS = 5 * 60     # durée du lock en secondes (ici 5 minutes)

# Nouveau : Logging des tentatives suspectes
SUSPICIOUS_LOG = "suspicious_activity.log"

def log_suspicious_activity(event_type, username, ip, details=""):
    """Enregistre les activités suspectes pour audit."""
    try:
        with open(SUSPICIOUS_LOG, "a", encoding="utf-8") as f:
            log_entry = {
                "timestamp": datetime.utcnow().isoformat() + "Z",
                "type": event_type,
                "username": username,
                "ip": ip,
                "details": details
            }
            f.write(json.dumps(log_entry) + "\n")
    except Exception:
        pass  # Ne pas bloquer sur erreur de log

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

def create_user(username, password, ip, telegram_id=None, email=None):
    """
    Crée un utilisateur avec mot de passe hashé et map ip->username.
    Retourne (True, "") si ok, (False, "raison") si erreur.
    """
    username = str(username)
    data = load_auth_data()
    if username in data.get("users", {}):
        log_suspicious_activity("duplicate_username", username, ip, "Tentative de création avec nom existant")
        return False, "Nom d'utilisateur déjà utilisé."
    
    # Check if email already exists
    if email:
        for user, info in data.get("users", {}).items():
            if info.get("email") == email:
                log_suspicious_activity("duplicate_email", username, ip, "Tentative de création avec email existant")
                return False, "Cette adresse email est déjà utilisée."
    
    existing = data.get("ip_map", {}).get(ip)
    if existing:
        log_suspicious_activity("multi_account", username, ip, f"IP déjà utilisée par {existing}")
        return False, "Un compte existe déjà depuis cette IP."
    pwd_hash = generate_password_hash(password)
    now = datetime.utcnow().isoformat() + "Z"
    data.setdefault("users", {})[username] = {
        "password_hash": pwd_hash,
        "created_at": now,
        "telegram_id": telegram_id or "",
        "email": email or "",
        "email_verified": False if email else True,  # Require verification if email provided
        "failed_attempts": 0,
        "locked_until": 0,
        "last_ip": ip
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
        log_suspicious_activity("unknown_user", username, "unknown", "Tentative avec utilisateur inexistant")
        return False, "Utilisateur inconnu."
    now = current_timestamp()
    locked_until = int(user.get("locked_until", 0) or 0)
    if locked_until > now:
        remaining = locked_until - now
        log_suspicious_activity("locked_attempt", username, "unknown", f"Tentative sur compte verrouillé, {remaining}s restant")
        return False, f"Compte verrouillé. Réessaye dans {remaining // 60} min."

    # Check if email verification is required
    if user.get("email") and not user.get("email_verified", True):
        return False, "Veuillez d'abord vérifier votre adresse email."

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
            log_suspicious_activity("account_locked", username, "unknown", f"Compte verrouillé après {MAX_FAILED} tentatives")
        else:
            log_suspicious_activity("failed_login", username, "unknown", f"Tentative {user['failed_attempts']}/{MAX_FAILED}")
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

def verify_user_email(username):
    """Mark user's email as verified."""
    data = load_auth_data()
    if username not in data.get("users", {}):
        return False
    data["users"][username]["email_verified"] = True
    save_auth_data(data)
    return True

def get_user_email(username):
    """Get user's email address."""
    user = get_user(username)
    if user:
        return user.get("email")
    return None