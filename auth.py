import json
import os
import time
import re
import secrets
from werkzeug.security import generate_password_hash, check_password_hash
from datetime import datetime, timedelta

AUTH_FILE = "auth_data.json"
# Politique de sécurité
MAX_FAILED = 5            # nombre de tentatives avant lock
LOCK_SECONDS = 5 * 60     # durée du lock en secondes (ici 5 minutes)
OTP_EXPIRY_SECONDS = 10 * 60  # OTP valide pendant 10 minutes

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

def generate_otp():
    """Génère un code OTP à 6 chiffres."""
    return ''.join([str(secrets.randbelow(10)) for _ in range(6)])

def create_user(username, password, ip, telegram_id=None):
    """
    Crée un utilisateur avec mot de passe hashé et map ip->username.
    Génère un OTP pour la vérification via Telegram.
    Retourne (True, otp_code) si ok, (False, "raison") si erreur.
    """
    username = str(username)
    data = load_auth_data()
    if username in data.get("users", {}):
        log_suspicious_activity("duplicate_username", username, ip, "Tentative de création avec nom existant")
        return False, "Nom d'utilisateur déjà utilisé."
    existing = data.get("ip_map", {}).get(ip)
    if existing:
        log_suspicious_activity("multi_account", username, ip, f"IP déjà utilisée par {existing}")
        return False, "Un compte existe déjà depuis cette IP."
    pwd_hash = generate_password_hash(password)
    now = datetime.utcnow().isoformat() + "Z"
    otp_code = generate_otp()
    otp_expires = current_timestamp() + OTP_EXPIRY_SECONDS
    data.setdefault("users", {})[username] = {
        "password_hash": pwd_hash,
        "created_at": now,
        "telegram_id": telegram_id or "",
        "failed_attempts": 0,
        "locked_until": 0,
        "last_ip": ip,
        "verified": False,
        "otp_code": otp_code,
        "otp_expires": otp_expires
    }
    data.setdefault("ip_map", {})[ip] = username
    save_auth_data(data)
    return True, otp_code

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

def verify_otp(username, otp_code):
    """
    Vérifie le code OTP pour un utilisateur.
    Retourne (True, "") si succès, (False, "raison") si erreur.
    """
    data = load_auth_data()
    user = data.get("users", {}).get(username)
    if not user:
        return False, "Utilisateur inconnu."
    
    if user.get("verified", False):
        return False, "Compte déjà vérifié."
    
    stored_otp = user.get("otp_code", "")
    otp_expires = int(user.get("otp_expires", 0))
    now = current_timestamp()
    
    if now > otp_expires:
        return False, "Code OTP expiré. Veuillez vous réinscrire."
    
    if otp_code != stored_otp:
        return False, "Code OTP incorrect."
    
    # OTP valide, marquer comme vérifié
    user["verified"] = True
    user["otp_code"] = ""  # Effacer le code utilisé
    user["otp_expires"] = 0
    save_auth_data(data)
    return True, ""

def is_user_verified(username):
    """Vérifie si un utilisateur a validé son compte avec OTP."""
    data = load_auth_data()
    user = data.get("users", {}).get(username)
    if not user:
        return False
    return user.get("verified", False)

def regenerate_otp(username):
    """
    Régénère un nouveau code OTP pour un utilisateur non vérifié.
    Retourne (True, otp_code) si succès, (False, "raison") si erreur.
    """
    data = load_auth_data()
    user = data.get("users", {}).get(username)
    if not user:
        return False, "Utilisateur inconnu."
    
    if user.get("verified", False):
        return False, "Compte déjà vérifié."
    
    otp_code = generate_otp()
    user["otp_code"] = otp_code
    user["otp_expires"] = current_timestamp() + OTP_EXPIRY_SECONDS
    save_auth_data(data)
    return True, otp_code