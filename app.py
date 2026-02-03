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
from threading import Lock
from datetime import datetime
from flask import Flask, render_template, request, redirect, session, send_file, url_for, flash, make_response
from downloader import download_content
from limiteur import get_user_data, spend_credit, add_credits, save_data, DATA_FILE
import telebot
import config
import auth

# === Configuration Flask ===
app = Flask(__name__, static_folder="static", template_folder="templates")
app.secret_key = os.getenv("FLASK_SECRET", "genius_ultra_secret_key")

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

def get_telegram_id(username):
    """Get telegram_id for a username from auth data"""
    try:
        user = auth.get_user(username)
        if user and user.get('telegram_id'):
            return user.get('telegram_id')
    except (KeyError, TypeError, AttributeError):
        pass
    return None

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
        if not username or not password:
            flash("Nom d'utilisateur et mot de passe requis.", "danger")
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
        if not username or not password:
            flash("Champs manquants.", "danger")
            return redirect(url_for('login'))

        with auth_lock:
            ok, reason = auth.authenticate_user(username, password)
            # record attempt for IP based login-throttling regardless of ok
            record_login_attempt(ip)
            if ok:
                session['user_id'] = username
                # Set admin flag if username is 'admin' or in admin list
                session['is_admin'] = username.lower() == 'admin'
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
        if not url:
            msg = "URL requise"
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
        pack = request.form.get('pack')
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
        try:
            bot_admin.send_message(config.ADMIN_ID, admin_text, reply_markup=markup, parse_mode="Markdown")
        except Exception:
            app.logger.exception("Erreur envoi notification admin Telegram")
            # On ne bloque pas l'utilisateur si Telegram échoue
        flash("Demande envoyée à l'administrateur (via Telegram).", "success")
        return redirect(url_for('shop'))

    return render_template("shop.html")

# === Page Crédits/À propos ===
@app.route('/credits')
def credits_page():
    return render_template("credits.html")

# === Panel d'Administration ===
@app.route('/admin', methods=['GET', 'POST'])
def admin_panel():
    if 'user_id' not in session:
        return redirect(url_for('login'))
    
    # Check if user is admin (you can customize this logic)
    # For now, we'll check if the user_id matches a specific admin username
    user_id = session['user_id']
    is_admin = user_id == 'admin' or session.get('is_admin', False)
    
    if not is_admin:
        flash("Accès refusé. Vous devez être administrateur.", "danger")
        return render_template("admin.html", is_admin=False)
    
    # Handle POST requests for admin actions
    if request.method == 'POST':
        action = request.form.get('action')
        
        if action == 'add_credits':
            username = request.form.get('username')
            amount = int(request.form.get('amount', 0))
            if username and amount > 0:
                if add_credits(username, amount):
                    flash(f"✅ {amount} crédits ajoutés à {username}", "success")
                else:
                    flash(f"❌ Erreur lors de l'ajout de crédits pour {username}", "danger")
        
        elif action == 'approve_purchase':
            username = request.form.get('username')
            pack = request.form.get('pack')
            amount = int(pack) if pack else 0
            if username and amount > 0:
                if add_credits(username, amount):
                    # Remove from pending log
                    try:
                        with pending_lock:
                            if os.path.exists(PENDING_LOG):
                                lines = []
                                with open(PENDING_LOG, 'r', encoding='utf-8') as f:
                                    lines = f.readlines()
                                with open(PENDING_LOG, 'w', encoding='utf-8') as f:
                                    for line in lines:
                                        try:
                                            entry = json.loads(line.strip())
                                            if entry.get('user') != username or entry.get('pack') != pack:
                                                f.write(line)
                                        except:
                                            f.write(line)
                    except Exception as e:
                        app.logger.exception("Erreur lors de la mise à jour du log")
                    
                    # Notify user via Telegram bot if they have a telegram_id
                    try:
                        telegram_id = get_telegram_id(username)
                        if telegram_id:
                            bot_user.send_message(telegram_id, f"🎉 **Achat validé !** +{amount} crédits ajoutés.", parse_mode="Markdown")
                    except Exception as e:
                        app.logger.debug(f"Failed to send Telegram notification: {e}")
                    
                    flash(f"✅ Achat approuvé pour {username} (+{amount} crédits)", "success")
                else:
                    flash(f"❌ Erreur lors de l'approbation de l'achat", "danger")
        
        elif action == 'reject_purchase':
            username = request.form.get('username')
            # Remove from pending log
            try:
                with pending_lock:
                    if os.path.exists(PENDING_LOG):
                        lines = []
                        with open(PENDING_LOG, 'r', encoding='utf-8') as f:
                            lines = f.readlines()
                        with open(PENDING_LOG, 'w', encoding='utf-8') as f:
                            for line in lines:
                                try:
                                    entry = json.loads(line.strip())
                                    if entry.get('user') != username:
                                        f.write(line)
                                except:
                                    f.write(line)
            except Exception as e:
                app.logger.exception("Erreur lors de la mise à jour du log")
            
            # Notify user via Telegram bot if they have a telegram_id
            try:
                telegram_id = get_telegram_id(username)
                if telegram_id:
                    bot_user.send_message(telegram_id, "❌ Votre demande d'achat a été refusée.", parse_mode="Markdown")
            except Exception as e:
                app.logger.debug(f"Failed to send Telegram notification: {e}")
            
            flash(f"❌ Achat refusé pour {username}", "success")
        
        elif action == 'broadcast':
            message = request.form.get('message', '').strip()
            if message:
                # Load all users
                data = {}
                if os.path.exists(DATA_FILE):
                    with open(DATA_FILE, "r", encoding="utf-8") as f:
                        data = json.load(f)
                
                count = 0
                for username in data.keys():
                    try:
                        telegram_id = get_telegram_id(username)
                        if telegram_id:
                            bot_user.send_message(telegram_id, f"📢 **Message de l'administrateur**\n\n{message}", parse_mode="Markdown")
                            count += 1
                    except Exception as e:
                        app.logger.debug(f"Failed to send broadcast to {username}: {e}")
                        continue
                
                flash(f"📤 Message envoyé à {count} utilisateurs", "success")
        
        return redirect(url_for('admin_panel'))
    
    # Load statistics and data for GET request
    stats = {
        'total_users': 0,
        'total_credits': 0,
        'pending_purchases': 0
    }
    
    users_list = []
    pending_purchases = []
    
    # Load users data
    if os.path.exists(DATA_FILE):
        with open(DATA_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
            stats['total_users'] = len(data)
            stats['total_credits'] = sum(u.get('credits', 0) for u in data.values())
            
            for username, user_info in data.items():
                users_list.append({
                    'username': username,
                    'credits': user_info.get('credits', 0),
                    'expiration': user_info.get('expiration', 'N/A'),
                    'statut': user_info.get('statut', 'Actif')
                })
    
    # Load pending purchases
    if os.path.exists(PENDING_LOG):
        with pending_lock:
            with open(PENDING_LOG, 'r', encoding='utf-8') as f:
                for line in f:
                    try:
                        entry = json.loads(line.strip())
                        ts = entry.get('ts', 0)
                        timestamp_str = datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M:%S') if ts else 'N/A'
                        pending_purchases.append({
                            'user': entry.get('user', 'Unknown'),
                            'pack': entry.get('pack', 'Unknown'),
                            'timestamp': timestamp_str
                        })
                    except (json.JSONDecodeError, ValueError, OSError):
                        continue
    
    stats['pending_purchases'] = len(pending_purchases)
    
    return render_template("admin.html", 
                         is_admin=True, 
                         stats=stats, 
                         users=users_list, 
                         pending=pending_purchases)

# === Run ===
if __name__ == '__main__':
    # assure dossiers
    if not os.path.exists("downloads"):
        os.makedirs("downloads")
    # mode debug False en production
    app.run(host='0.0.0.0', port=5000, debug=False)