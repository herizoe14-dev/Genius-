import json
import os
import time
import secrets
from datetime import datetime, timedelta

AUTH_FILE = "auth_data.json"
# Politique de sécurité
MAX_FAILED = 5            # nombre de tentatives avant lock
LOCK_SECONDS = 5 * 60     # durée du lock en secondes (ici 5 minutes)

# Nouveau : Logging des tentatives suspectes
SUSPICIOUS_LOG = "suspicious_activity.log"

def generate_unique_id():
    """Génère un ID unique de 12 caractères hexadécimaux (6 bytes = 12 hex chars)."""
    return secrets.token_hex(6).upper()  # 6 bytes = 12 caractères hex

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
        return {"users": {}, "ip_map": {}, "active_sessions": {}}
    with open(AUTH_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)
        # Ensure active_sessions exists for backward compatibility
        if "active_sessions" not in data:
            data["active_sessions"] = {}
        return data

def save_auth_data(data):
    with open(AUTH_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=4, ensure_ascii=False)

def user_exists(unique_id):
    """Vérifie si un utilisateur avec cet ID unique existe."""
    data = load_auth_data()
    return unique_id in data.get("users", {})

def get_user(unique_id):
    """Récupère les données d'un utilisateur par son ID unique."""
    data = load_auth_data()
    return data.get("users", {}).get(unique_id)

def get_user_by_ip(ip):
    """Récupère l'ID utilisateur associé à une IP."""
    data = load_auth_data()
    return data.get("ip_map", {}).get(ip)

def current_timestamp():
    return int(time.time())

def create_user(ip, telegram_id=None):
    """
    Crée un utilisateur avec un ID unique et enregistre son IP.
    Retourne (True, unique_id) si ok, (False, "raison") si erreur.
    """
    data = load_auth_data()
    
    # Vérifier si cette IP a déjà un compte
    existing = data.get("ip_map", {}).get(ip)
    if existing:
        log_suspicious_activity("multi_account", existing, ip, f"IP déjà utilisée par {existing}")
        return False, "Un compte existe déjà depuis cette IP."
    
    # Générer un ID unique
    unique_id = generate_unique_id()
    
    # S'assurer que l'ID est vraiment unique (très improbable mais on vérifie)
    max_attempts = 10
    attempts = 0
    while unique_id in data.get("users", {}):
        unique_id = generate_unique_id()
        attempts += 1
        if attempts >= max_attempts:
            return False, "Erreur serveur: impossible de générer un ID unique."
    
    now = datetime.utcnow().isoformat() + "Z"
    data.setdefault("users", {})[unique_id] = {
        "created_at": now,
        "telegram_id": telegram_id or "",
        "creation_ip": ip,
        "last_ip": ip,
        "failed_attempts": 0,
        "locked_until": 0
    }
    data.setdefault("ip_map", {})[ip] = unique_id
    save_auth_data(data)
    return True, unique_id

def authenticate_user(unique_id, ip, session_token=None):
    """
    Authentifie un utilisateur par son ID unique.
    Vérifie qu'une seule session est active à la fois.
    Retourne (True, new_session_token) ou (False, "raison").
    """
    data = load_auth_data()
    user = data.get("users", {}).get(unique_id)
    if not user:
        log_suspicious_activity("unknown_user", unique_id, ip, "Tentative avec ID inexistant")
        return False, "ID utilisateur inconnu."
    
    now = current_timestamp()
    locked_until = int(user.get("locked_until", 0) or 0)
    if locked_until > now:
        remaining = locked_until - now
        log_suspicious_activity("locked_attempt", unique_id, ip, f"Tentative sur compte verrouillé, {remaining}s restant")
        return False, f"Compte verrouillé. Réessaye dans {remaining // 60} min."
    
    # Générer un nouveau token de session
    new_session_token = secrets.token_hex(16)
    
    # Enregistrer cette session comme la session active (invalide les autres)
    data.setdefault("active_sessions", {})[unique_id] = {
        "token": new_session_token,
        "ip": ip,
        "created_at": now
    }
    
    # Mettre à jour la dernière IP utilisée
    user["last_ip"] = ip
    user["failed_attempts"] = 0
    user["locked_until"] = 0
    
    save_auth_data(data)
    return True, new_session_token

def validate_session(unique_id, session_token):
    """
    Vérifie si le token de session est valide pour cet utilisateur.
    Retourne True si la session est active, False sinon.
    """
    data = load_auth_data()
    active_session = data.get("active_sessions", {}).get(unique_id)
    
    if not active_session:
        return False
    
    return active_session.get("token") == session_token

def invalidate_session(unique_id):
    """Invalide la session active d'un utilisateur."""
    data = load_auth_data()
    if unique_id in data.get("active_sessions", {}):
        del data["active_sessions"][unique_id]
        save_auth_data(data)
        return True
    return False

def record_failed_attempt(unique_id, ip):
    """Enregistre une tentative de connexion échouée."""
    data = load_auth_data()
    user = data.get("users", {}).get(unique_id)
    if user:
        now = current_timestamp()
        user["failed_attempts"] = int(user.get("failed_attempts", 0)) + 1
        if user["failed_attempts"] >= MAX_FAILED:
            user["locked_until"] = now + LOCK_SECONDS
            user["failed_attempts"] = 0
            log_suspicious_activity("account_locked", unique_id, ip, f"Compte verrouillé après {MAX_FAILED} tentatives")
        else:
            log_suspicious_activity("failed_login", unique_id, ip, f"Tentative {user['failed_attempts']}/{MAX_FAILED}")
        save_auth_data(data)

def link_telegram_id(unique_id, telegram_id):
    """Lie un ID Telegram à un utilisateur."""
    data = load_auth_data()
    if unique_id not in data.get("users", {}):
        return False
    # Valider le format telegram_id (doit être numérique)
    telegram_id = str(telegram_id).strip()
    if not telegram_id or not telegram_id.isdigit():
        return False
    data["users"][unique_id]["telegram_id"] = telegram_id
    save_auth_data(data)
    return True

def unregister_ip(ip):
    """Désenregistre une IP de la map."""
    data = load_auth_data()
    if ip in data.get("ip_map", {}):
        del data["ip_map"][ip]
        save_auth_data(data)
        return True
    return False

def get_user_telegram_id(unique_id):
    """Récupère l'ID Telegram d'un utilisateur."""
    user = get_user(unique_id)
    if user:
        return user.get("telegram_id")
    return None