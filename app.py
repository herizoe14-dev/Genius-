"""
app.py — version simplifiée sans dépendances flask_talisman / flask_limiter.

Remarques :
- Nécessite les modules locaux : auth.py, downloader.py, limiteur.py et config.py (tes fichiers existants).
- Envoie les notifications d'achat uniquement au bot Telegram (bot_admin).
- Protection simple anti-brute-force et en-têtes de sécurité appliqués globalement.
- Utiliser Python 3.8+ ; installe Flask et pyTelegramBotAPI (voir instructions en bas).
"""
import os
import json
import time
import re
from threading import Lock
from flask import Flask, render_template, request, redirect, session, send_file, url_for, flash, make_response
from downloader import download_content
from limiteur import get_user_data, spend_credit, add_credits, save_data, DATA_FILE
import telebot
import config
import auth
from admin import resolve_telegram_id, send_telegram_message
from web_notifications import get_user_web_notifications, delete_web_notification

# === Configuration Flask ===
app = Flask(__name__, static_folder="static", template_folder="templates")
# SÉCURITÉ : Générer une clé secrète forte si non fournie
app.secret_key = os.getenv("FLASK_SECRET") or os.urandom(32).hex()

# Cookies sécurisés (bien que pour être utiles il faut HTTPS en prod)
app.config.update(
    SESSION_COOKIE_SECURE=True,
    SESSION_COOKIE_HTTPONLY=True,
    SESSION_COOKIE_SAMESITE='Lax'
)

# Thread-safety pour auth et autres écritures
auth_lock = Lock()
pending_lock = Lock()

# Bots Telegram (Bot admin reçoit les demandes)
bot_admin = telebot.TeleBot(config.TOKEN_BOT_ADMIN)
bot_user = telebot.TeleBot(config.TOKEN_BOT_USER)

# Fichier de log / trace des achats (côté site, lecture locale uniquement)
PENDING_LOG = "pending_purchases.log"

# === Input validation and sanitization ===
def sanitize_username(username):
    """Valide le nom d'utilisateur pour éviter les injections."""
    if not username or not isinstance(username, str):
        return None
    # Vérifier que le username ne contient que des caractères valides
    if not re.match(r'^[a-zA-Z0-9_-]{3,30}$', username):
        return None
    return username

def sanitize_url(url):
    """Valide que l'URL est sécurisée."""
    if not url or not isinstance(url, str):
        return None
    # Vérifier que c'est une URL YouTube/vidéo valide
    url = url.strip()
    if len(url) > 500:  # URLs trop longues sont suspectes
        return None
    # Pattern de base pour URLs YouTube
    youtube_pattern = r'^https?://(www\.)?(youtube\.com|youtu\.be)/'
    if not re.match(youtube_pattern, url):
        return None
    return url

def sanitize_telegram_id(telegram_id):
    """Valide l'ID Telegram."""
    if not telegram_id:
        return None
    telegram_id = str(telegram_id).strip()
    if not telegram_id.isdigit() or len(telegram_id) > 15:
        return None
    return telegram_id

# === Protection basique — rate limiting en mémoire ===
# Note : c'est un système simple et volatile (ne persiste pas au redémarrage).
RATE_WINDOW_SECONDS = 60
MAX_REQUESTS_PER_WINDOW = 60      # global per IP
LOGIN_WINDOW_SECONDS = 60
MAX_LOGIN_ATTEMPTS_PER_WINDOW = 10
FAILED_LOGIN_LOCK_SECONDS = 300   # 5 minutes lock handled in auth.py

_requests_by_ip = {}   # ip -> [timestamps]
_login_attempts = {}   # ip -> [timestamps]
_requests_lock = Lock()

def _cleanup_timestamps(lst, window):
    now = int(time.time())
    return [t for t in lst if now - t < window]

def record_request(ip):
    with _requests_lock:
        lst = _requests_by_ip.get(ip, [])
        lst = _cleanup_timestamps(lst, RATE_WINDOW_SECONDS)
        lst.append(int(time.time()))
        _requests_by_ip[ip] = lst
        return len(lst)

def too_many_requests(ip):
    n = record_request(ip)
    return n > MAX_REQUESTS_PER_WINDOW

def record_login_attempt(ip):
    with _requests_lock:
        lst = _login_attempts.get(ip, [])
        lst = _cleanup_timestamps(lst, LOGIN_WINDOW_SECONDS)
        lst.append(int(time.time()))
        _login_attempts[ip] = lst
        return len(lst)

def too_many_login_attempts(ip):
    n = record_login_attempt(ip)
    return n > MAX_LOGIN_ATTEMPTS_PER_WINDOW

# === Security headers applied to every response ===
def apply_security_headers(response):
    # HSTS : utile seulement en HTTPS (mais harmless en dev)
    response.headers['Strict-Transport-Security'] = 'max-age=63072000; includeSubDomains; preload'
    response.headers['X-Content-Type-Options'] = 'nosniff'
    response.headers['X-Frame-Options'] = 'DENY'
    response.headers['Referrer-Policy'] = 'no-referrer-when-downgrade'
    response.headers['Permissions-Policy'] = 'geolocation=()'
    # CSP minimal (adjust if you load external scripts/styles)
    response.headers['Content-Security-Policy'] = "default-src 'self'; img-src 'self' data: https:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'"
    return response

app.after_request(apply_security_headers)

# === Helpers ===
def get_client_ip():
    # Si derrière proxy, ajuste ou utilises ProxyFix en prod.
    xff = request.headers.get("X-Forwarded-For")
    if xff:
        return xff.split(",")[0].strip()
    return request.remote_addr or "unknown"

def ensure_pending_log():
    if not os.path.exists(PENDING_LOG):
        with open(PENDING_LOG, "w", encoding="utf-8") as f:
            f.write("")

# === Routes d'auth (inscription / connexion / logout) ===
@app.route('/register', methods=['GET', 'POST'])
def register():
    ip = get_client_ip()
    # protection simple
    if too_many_requests(ip):
        flash("Trop de requêtes depuis ton IP, réessaie plus tard.", "danger")
        return redirect(url_for('login'))

    if request.method == 'POST':
        username = request.form.get('username', '').strip()
        password = request.form.get('password', '')
        telegram_id = request.form.get('telegram_id', '').strip()
        
        # SÉCURITÉ : Validation et sanitisation des entrées
        username = sanitize_username(username)
        if not username:
            flash("Nom d'utilisateur invalide. Utilisez 3-30 caractères alphanumériques.", "danger")
            return redirect(url_for('register'))
        
        if not password or len(password) < 8:
            flash("Mot de passe requis (minimum 8 caractères).", "danger")
            return redirect(url_for('register'))
        
        # Validation force du mot de passe
        if not re.search(r'[A-Z]', password):
            flash("Mot de passe doit contenir au moins une majuscule.", "danger")
            return redirect(url_for('register'))
        if not re.search(r'[a-z]', password):
            flash("Mot de passe doit contenir au moins une minuscule.", "danger")
            return redirect(url_for('register'))
        if not re.search(r'[0-9]', password):
            flash("Mot de passe doit contenir au moins un chiffre.", "danger")
            return redirect(url_for('register'))
        
        # Validation Telegram ID si fourni
        if telegram_id:
            telegram_id = sanitize_telegram_id(telegram_id)
            if not telegram_id:
                flash("ID Telegram invalide.", "danger")
                return redirect(url_for('register'))
        
        with auth_lock:
            ok, reason = auth.create_user(username, password, ip, telegram_id or None)
            if not ok:
                flash(reason, "danger")
                return redirect(url_for('register'))
            # initialise données user si nécessaire
            get_user_data(username)
        flash("Compte créé. Connecte-toi.", "success")
        return redirect(url_for('login'))
    return render_template("register.html")

@app.route('/login', methods=['GET', 'POST'])
def login():
    ip = get_client_ip()
    if too_many_requests(ip):
        flash("Trop de requêtes depuis ton IP, réessaie plus tard.", "danger")
        return redirect(url_for('login'))

    if request.method == 'POST':
        if too_many_login_attempts(ip):
            flash("Trop de tentatives de connexion. Réessaie dans un moment.", "danger")
            return redirect(url_for('login'))

        username = request.form.get('username', '').strip()
        password = request.form.get('password', '')
        
        # SÉCURITÉ : Validation des entrées
        username = sanitize_username(username)
        if not username or not password:
            flash("Champs invalides.", "danger")
            return redirect(url_for('login'))

        with auth_lock:
            ok, reason = auth.authenticate_user(username, password)
            # record attempt for IP based login-throttling regardless of ok
            record_login_attempt(ip)
            if ok:
                session['user_id'] = username
                flash("Connecté avec succès.", "success")
                return redirect(url_for('home'))
            else:
                flash(reason, "danger")
                return redirect(url_for('login'))
    return render_template("login.html")

@app.route('/logout')
def logout():
    session.pop('user_id', None)
    return redirect(url_for('login'))

# === Dashboard ===
@app.route('/')
def home():
    if 'user_id' not in session:
        return redirect(url_for('login'))
    user_id = session['user_id']
    user_info = get_user_data(user_id)
    return render_template("dashboard.html", user_id=user_id, user=user_info)

# === À propos ===
@app.route('/about')
def about():
    return render_template("about.html")

# === Notifications API ===
@app.route('/api/notifications')
def get_notifications():
    """API endpoint pour récupérer le nombre de notifications."""
    if 'user_id' not in session:
        return {"count": 0, "notifications": []}, 200
    
    user_id = session['user_id']
    notifications = []
    
    # Lire les notifications des achats en attente pour cet utilisateur
    ensure_pending_log()
    try:
        with pending_lock:
            with open(PENDING_LOG, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line:
                        try:
                            entry = json.loads(line)
                            if entry.get("user") == user_id:
                                notifications.append({
                                    "type": "pending_purchase",
                                    "message": f"Achat de {entry.get('pack')} crédits en attente",
                                    "timestamp": entry.get("ts", 0),
                                    "deletable": False  # Les achats en attente ne peuvent pas être supprimés
                                })
                        except json.JSONDecodeError:
                            continue
    except Exception:
        app.logger.exception("Erreur lors de la lecture des notifications depuis le fichier pending_purchases.log")
    
    # Lire les notifications web (messages de l'admin)
    try:
        web_notifs = get_user_web_notifications(user_id)
        for idx, notif in enumerate(web_notifs):
            notifications.append({
                "type": notif.get("type", "admin_message"),
                "message": notif.get("message", ""),
                "timestamp": notif.get("timestamp", 0),
                "deletable": True,  # Les notifications web peuvent être supprimées
                "index": idx  # Index pour suppression
            })
    except Exception:
        app.logger.exception("Erreur lors de la lecture des notifications web")
    
    # Trier par timestamp (plus récent en premier)
    notifications.sort(key=lambda x: x.get("timestamp", 0), reverse=True)
    
    return {"count": len(notifications), "notifications": notifications}, 200


@app.route('/api/notifications/delete', methods=['POST'])
def delete_notification():
    """API endpoint pour supprimer une notification."""
    if 'user_id' not in session:
        return {"success": False, "error": "Non authentifié"}, 401
    
    user_id = session['user_id']
    
    try:
        data = request.get_json()
        if not data or 'index' not in data:
            return {"success": False, "error": "Index manquant"}, 400
        
        index = data['index']
        if not isinstance(index, int) or index < 0:
            return {"success": False, "error": "Index invalide"}, 400
        
        success = delete_web_notification(user_id, index)
        if success:
            return {"success": True}, 200
        else:
            return {"success": False, "error": "Notification non trouvée"}, 404
    except Exception:
        app.logger.exception("Erreur lors de la suppression de la notification")
        return {"success": False, "error": "Erreur serveur"}, 500

# === Téléchargement ===
@app.route('/download', methods=['GET', 'POST'])
def download_page():
    if 'user_id' not in session:
        return redirect(url_for('login'))

    msg = None
    if request.method == 'POST':
        user_id = session['user_id']
        url = request.form.get('url', '').strip()
        mode = request.form.get('mode', 'mp3')
        
        # SÉCURITÉ : Validation de l'URL
        url = sanitize_url(url)
        if not url:
            msg = "URL YouTube invalide ou manquante"
        elif mode not in ['mp3', 'mp4']:
            msg = "Mode de téléchargement invalide"
        else:
            if not spend_credit(user_id):
                msg = "🔒 Crédits insuffisants. Achetez-en dans la boutique."
            else:
                try:
                    file_path = download_content(url, mode)
                    # send_file will stream the file to client
                    response = make_response(send_file(file_path, as_attachment=True))
                    # cleanup local file after sending (attempt)
                    try:
                        if os.path.exists(file_path):
                            os.remove(file_path)
                    except Exception:
                        app.logger.exception("Erreur suppression fichier local après envoi")
                    return response
                except Exception as e:
                    # rollback crédit en cas d'erreur
                    try:
                        data = {}
                        if os.path.exists(DATA_FILE):
                            with open(DATA_FILE, "r", encoding="utf-8") as f:
                                data = json.load(f)
                        uid = str(user_id)
                        if uid in data:
                            data[uid]['credits'] = data[uid].get('credits', 0) + 1
                            save_data(data)
                    except Exception:
                        app.logger.exception("Erreur rollback crédit")
                    msg = f"Erreur téléchargement : {e}"
    return render_template("download.html", msg=msg)

# === Boutique web ===
@app.route('/shop', methods=['GET', 'POST'])
def shop():
    if 'user_id' not in session:
        return redirect(url_for('login'))

    if request.method == 'POST':
        user_id = session['user_id']
        pack = request.form.get('pack', '').strip()
        
        # SÉCURITÉ : Validation stricte du pack
        if pack not in ("10", "50", "100"):
            flash("Pack invalide", "danger")
            return redirect(url_for('shop'))

        # Journal local pour suivi (non exposé en interface admin)
        ensure_pending_log()
        with pending_lock:
            with open(PENDING_LOG, "a", encoding="utf-8") as f:
                f.write(json.dumps({"user": user_id, "pack": pack, "ts": int(time.time())}) + "\n")

        # Notifier exclusivement le Bot Admin (Telegram) — l'admin traitera sur Telegram
        markup = telebot.types.InlineKeyboardMarkup()
        btn_ok = telebot.types.InlineKeyboardButton("✅ ACCEPTER", callback_data=f"admin_ok|{user_id}|{pack}")
        btn_no = telebot.types.InlineKeyboardButton("❌ REFUSER", callback_data=f"admin_no|{user_id}")
        markup.row(btn_ok, btn_no)
        admin_text = (
            "🔔 **NOUVELLE DEMANDE D'ACHAT (WEB)**\n"
            "━━━━━━━━━━━━━━━━━━━━\n"
            f"👤 Utilisateur : {user_id}\n"
            f"📦 Pack : {pack}\n"
            "━━━━━━━━━━━━━━━━━━━━\n"
            "Action requise :"
        )
        send_telegram_message(
            bot_admin,
            config.ADMIN_ID,
            admin_text,
            log_context="notification admin Telegram",
            log_func=app.logger.exception,
            reply_markup=markup,
            parse_mode="Markdown"
        )
        telegram_target = resolve_telegram_id(user_id)
        send_telegram_message(
            bot_user,
            telegram_target,
            "🧾 **Commande reçue !**\nVotre demande d'achat est en attente de validation.",
            log_context="notification utilisateur Telegram",
            log_func=app.logger.exception,
            parse_mode="Markdown"
        )
        flash("Demande envoyée à l'administrateur (via Telegram).", "success")
        return redirect(url_for('shop'))

    return render_template("shop.html")

# === Run ===
if __name__ == '__main__':
    # assure dossiers
    if not os.path.exists("downloads"):
        os.makedirs("downloads")
    # mode debug False en production
    app.run(host='0.0.0.0', port=5000, debug=False)
