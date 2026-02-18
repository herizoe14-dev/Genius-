"""
app.py — version simplifiée sans dépendances flask_talisman / flask_limiter.

Remarques :
- Nécessite les modules locaux : auth.py, downloader.py, limiteur.py et config.py (tes fichiers existants).
- Envoie les notifications d'achat uniquement au bot Telegram (bot_admin).
- Protection simple anti-brute-force et en-têtes de sécurité appliqués globalement.
- Utiliser Python 3.8+ ; installe Flask et pyTelegramBotAPI (voir instructions en bas).
- Système d'authentification par ID unique (pas de mot de passe).
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
from web_notifications import get_user_web_notifications, clear_user_web_notifications, delete_single_notification
from flask import jsonify

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
def sanitize_unique_id(unique_id):
    """Valide l'ID unique pour éviter les injections."""
    if not unique_id or not isinstance(unique_id, str):
        return None
    unique_id = unique_id.strip().upper()
    # L'ID unique est de 12 caractères hexadécimaux
    if not re.match(r'^[A-F0-9]{12}$', unique_id):
        return None
    return unique_id

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
    # CSP - Updated to support PWA Service Worker
    response.headers['Content-Security-Policy'] = "default-src 'self'; img-src 'self' data: https:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; worker-src 'self'; manifest-src 'self'"
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

def check_session_valid():
    """
    Vérifie que la session utilisateur est toujours valide.
    Retourne (True, user_id) si valide, (False, None) sinon.
    Invalide la session si une autre session a été créée ailleurs.
    """
    if 'user_id' not in session:
        return False, None
    
    user_id = session['user_id']
    session_token = session.get('session_token')
    
    if not auth.validate_session(user_id, session_token):
        session.pop('user_id', None)
        session.pop('session_token', None)
        return False, None
    
    return True, user_id

# === Routes d'auth (inscription / connexion / logout) ===
@app.route('/register', methods=['GET', 'POST'])
def register():
    ip = get_client_ip()
    # protection simple
    if too_many_requests(ip):
        flash("Trop de requêtes depuis ton IP, réessaie plus tard.", "danger")
        return redirect(url_for('login'))

    if request.method == 'POST':
        telegram_id = request.form.get('telegram_id', '').strip()
        
        # Validation Telegram ID si fourni
        if telegram_id:
            telegram_id = sanitize_telegram_id(telegram_id)
            if not telegram_id:
                flash("ID Telegram invalide.", "danger")
                return redirect(url_for('register'))
        
        with auth_lock:
            ok, result = auth.create_user(ip, telegram_id or None)
            if not ok:
                flash(result, "danger")
                return redirect(url_for('register'))
            
            unique_id = result
            # initialise données user si nécessaire
            get_user_data(unique_id)
        
        # Afficher l'ID unique à l'utilisateur
        flash(f"Compte créé avec succès ! Votre ID unique est : {unique_id}", "success")
        flash("⚠️ IMPORTANT : Notez bien cet ID, c'est votre seul moyen de connexion !", "warning")
        # On connecte directement l'utilisateur
        ok, session_token = auth.authenticate_user(unique_id, ip)
        if ok:
            session['user_id'] = unique_id
            session['session_token'] = session_token
        return redirect(url_for('home'))
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

        unique_id = request.form.get('unique_id', '').strip()
        
        # SÉCURITÉ : Validation des entrées
        unique_id = sanitize_unique_id(unique_id)
        if not unique_id:
            flash("ID invalide. L'ID doit être composé de 12 caractères.", "danger")
            return redirect(url_for('login'))

        with auth_lock:
            ok, result = auth.authenticate_user(unique_id, ip)
            # record attempt for IP based login-throttling regardless of ok
            record_login_attempt(ip)
            if ok:
                session['user_id'] = unique_id
                session['session_token'] = result
                flash("Connecté avec succès.", "success")
                return redirect(url_for('home'))
            else:
                auth.record_failed_attempt(unique_id, ip)
                flash(result, "danger")
                return redirect(url_for('login'))
    return render_template("login.html")

@app.route('/logout')
def logout():
    user_id = session.get('user_id')
    if user_id:
        auth.invalidate_session(user_id)
    session.pop('user_id', None)
    session.pop('session_token', None)
    return redirect(url_for('login'))

# === Dashboard ===
@app.route('/')
def home():
    if 'user_id' not in session:
        return redirect(url_for('login'))
    
    user_id = session['user_id']
    session_token = session.get('session_token')
    
    # Vérifier que la session est toujours valide (une seule session active)
    if not auth.validate_session(user_id, session_token):
        session.pop('user_id', None)
        session.pop('session_token', None)
        flash("Votre session a été invalidée (connexion depuis un autre appareil).", "warning")
        return redirect(url_for('login'))
    
    user_info = get_user_data(user_id)
    return render_template("dashboard.html", user_id=user_id, user=user_info)

# === À propos ===
@app.route('/about')
def about():
    return render_template("about.html")

# === PWA Service Worker ===
@app.route('/sw.js')
def service_worker():
    """Serve Service Worker from root for proper scope."""
    response = make_response(send_file('static/sw.js', mimetype='application/javascript'))
    response.headers['Cache-Control'] = 'no-cache'
    response.headers['Service-Worker-Allowed'] = '/'
    return response

# === PWA Offline Page ===
@app.route('/offline')
def offline():
    """Offline fallback page for PWA."""
    return render_template("offline.html")

# === Notifications API ===
@app.route('/api/notifications')
def get_notifications():
    """API endpoint pour récupérer le nombre de notifications."""
    valid, user_id = check_session_valid()
    if not valid:
        return jsonify({"count": 0, "notifications": []}), 200
    
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
                                    "timestamp": entry.get("ts", 0)
                                })
                        except json.JSONDecodeError:
                            continue
    except Exception:
        app.logger.exception("Erreur lors de la lecture des notifications depuis le fichier pending_purchases.log")
    
    # Lire les notifications web (messages de l'admin)
    try:
        web_notifs = get_user_web_notifications(user_id)
        for notif in web_notifs:
            notifications.append({
                "type": notif.get("type", "admin_message"),
                "message": notif.get("message", ""),
                "timestamp": notif.get("timestamp", 0)
            })
    except Exception:
        app.logger.exception("Erreur lors de la lecture des notifications web")
    
    # Trier par timestamp (plus récent en premier)
    notifications.sort(key=lambda x: x.get("timestamp", 0), reverse=True)
    
    return jsonify({"count": len(notifications), "notifications": notifications}), 200

# === Clear Notifications API ===
@app.route('/api/notifications/clear', methods=['POST'])
def clear_notifications():
    """API endpoint pour supprimer toutes les notifications."""
    valid, user_id = check_session_valid()
    if not valid:
        return jsonify({"success": False, "message": "Non authentifié"}), 401
    
    # Clear web notifications
    clear_user_web_notifications(user_id)
    
    # Clear pending purchases for this user from the log
    ensure_pending_log()
    try:
        with pending_lock:
            remaining_lines = []
            with open(PENDING_LOG, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line:
                        try:
                            entry = json.loads(line)
                            if entry.get("user") != user_id:
                                remaining_lines.append(line)
                        except json.JSONDecodeError:
                            remaining_lines.append(line)
            
            with open(PENDING_LOG, "w", encoding="utf-8") as f:
                for line in remaining_lines:
                    f.write(line + "\n")
    except Exception:
        app.logger.exception("Erreur lors de la suppression des notifications")
        return jsonify({"success": False, "message": "Erreur serveur"}), 500
    
    return jsonify({"success": True, "message": "Notifications supprimées"}), 200

# === Settings Page ===
@app.route('/settings')
def settings():
    """User settings page."""
    valid, user_id = check_session_valid()
    if not valid:
        flash("Votre session a été invalidée (connexion depuis un autre appareil).", "warning")
        return redirect(url_for('login'))
    
    user_info = get_user_data(user_id)
    
    return render_template("settings.html", user_id=user_id, user=user_info)

# === Update Settings API ===
@app.route('/api/settings/theme', methods=['POST'])
def update_theme():
    """API endpoint pour sauvegarder les préférences de thème."""
    valid, user_id = check_session_valid()
    if not valid:
        return jsonify({"success": False, "message": "Non authentifié"}), 401
    
    # Store theme preference in session (could be stored in user data later)
    theme = request.json.get('theme', 'dark')
    if theme not in ['dark', 'light', 'auto']:
        return jsonify({"success": False, "message": "Thème invalide"}), 400
    
    session['theme'] = theme
    return jsonify({"success": True, "message": "Thème mis à jour"}), 200

# === Téléchargement ===
@app.route('/download', methods=['GET', 'POST'])
def download_page():
    valid, user_id = check_session_valid()
    if not valid:
        flash("Votre session a été invalidée (connexion depuis un autre appareil).", "warning")
        return redirect(url_for('login'))

    msg = None
    download_info = None
    if request.method == 'POST':
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
                    file_path, info = download_content(url, mode)
                    
                    # Format download info for display
                    duration = info.get('duration', 0)
                    duration_str = f"{duration // 60}:{duration % 60:02d}" if duration else "N/A"
                    views = info.get('view_count', 0)
                    views_str = f"{views:,}".replace(',', ' ') if views else "N/A"
                    filesize = info.get('filesize', 0)
                    if filesize:
                        if filesize > 1024 * 1024:
                            filesize_str = f"{filesize / (1024 * 1024):.1f} MB"
                        else:
                            filesize_str = f"{filesize / 1024:.1f} KB"
                    else:
                        filesize_str = None
                    
                    download_info = {
                        'title': info.get('title', 'Unknown'),
                        'uploader': info.get('uploader', 'Unknown'),
                        'duration_str': duration_str,
                        'views_str': views_str,
                        'resolution': info.get('resolution', None),
                        'filesize_str': filesize_str,
                    }
                    
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
    return render_template("download.html", msg=msg, download_info=download_info)

# === Boutique web ===
@app.route('/shop', methods=['GET', 'POST'])
def shop():
    valid, user_id = check_session_valid()
    if not valid:
        flash("Votre session a été invalidée (connexion depuis un autre appareil).", "warning")
        return redirect(url_for('login'))

    if request.method == 'POST':
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
