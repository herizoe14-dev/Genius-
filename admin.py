import json
import os
import re
import threading

import telebot

import auth
import config
from telebot import types 
from limiteur import add_credits
from web_notifications import add_web_notification

bot_admin = telebot.TeleBot(config.TOKEN_BOT_ADMIN)
bot_user = telebot.TeleBot(config.TOKEN_BOT_USER)

# --- Helpers ---

# SÉCURITÉ : Log des actions admin pour audit
ADMIN_LOG = "admin_actions.log"
MIN_USERNAME_LENGTH = 3
MAX_USERNAME_LENGTH = 30
TELEGRAM_ID_PATTERN = re.compile(r'^[0-9]+$')  # Telegram numeric IDs
WEB_USERNAME_PATTERN = re.compile(
    r'^[a-zA-Z0-9_-]{%d,%d}$' % (MIN_USERNAME_LENGTH, MAX_USERNAME_LENGTH)
)  # Web usernames

def log_admin_action(action, user_id, details=""):
    """Enregistre toutes les actions admin pour audit."""
    from datetime import datetime
    try:
        with open(ADMIN_LOG, "a", encoding="utf-8") as f:
            log_entry = {
                "timestamp": datetime.utcnow().isoformat() + "Z",
                "action": action,
                "target_user": str(user_id),
                "details": details
            }
            f.write(json.dumps(log_entry) + "\n")
    except Exception:
        pass

def send_telegram_message(bot, target_id, text, log_context=None, log_func=None, **kwargs):
    """Send a Telegram message and handle delivery errors."""
    if not target_id:
        return False
    try:
        bot.send_message(target_id, text, **kwargs)
        return True
    except Exception as exc:
        if log_func:
            log_func(f"Erreur envoi {log_context or 'Telegram'}")
        else:
            context = log_context or "telegram_send"
            log_admin_action("telegram_send_error", target_id or "unknown", f"{context}: {exc}")
        return False

def normalize_user_id(user_id):
    """Normalize a user identifier (str + trim, empty if None)."""
    return str(user_id).strip() if user_id is not None else ""

def is_valid_telegram_id(telegram_id):
    """Validate a Telegram identifier."""
    telegram_id = normalize_user_id(telegram_id)
    return bool(TELEGRAM_ID_PATTERN.match(telegram_id))

def get_valid_telegram_id(telegram_id):
    """Return the normalized Telegram ID if valid."""
    telegram_id = normalize_user_id(telegram_id)
    return telegram_id if is_valid_telegram_id(telegram_id) else None

def is_valid_user_identifier(user_id):
    """Validate a user identifier (Telegram ID or web username)."""
    user_id = normalize_user_id(user_id)
    if not user_id:
        return False
    return TELEGRAM_ID_PATTERN.match(user_id) or WEB_USERNAME_PATTERN.match(user_id)

def resolve_telegram_id(user_id):
    """Resolve Telegram ID from a web username or direct Telegram ID."""
    user_id = normalize_user_id(user_id)
    if not user_id:
        return None
    try:
        data = auth.load_auth_data()
    except (FileNotFoundError, json.JSONDecodeError, OSError) as exc:
        log_admin_action("resolve_telegram_id_error", user_id or "unknown", str(exc))
        data = {}
    users = data.get("users", {})
    if user_id in users:
        return get_valid_telegram_id(users[user_id].get("telegram_id"))
    return get_valid_telegram_id(user_id)

# --- FONCTION POUR LIRE LE JSON ---
def get_maintenance_config():
    file_path = "config_messages.json"
    if not os.path.exists(file_path):
        # Création par défaut si le fichier n'existe pas
        default = {
            "maintenance_text": "🚨 **MAINTENANCE**\nLe système est en pause.",
            "contact_url": "https://t.me/+V0JSweR8CEY0MGU8"
        }
        with open(file_path, "w") as f:
            json.dump(default, f, indent=4)
        return default
    
    with open(file_path, "r", encoding="utf-8") as f:
        return json.load(f)

# --- COMMANDE /ADMIN (INCHANGÉE) ---
@bot_admin.message_handler(commands=['admin'])
def admin_stats(message):
    # SÉCURITÉ : Vérification stricte de l'admin
    if message.from_user.id != config.ADMIN_ID:
        log_admin_action("unauthorized_access", message.from_user.id, "Tentative d'accès non autorisée à /admin")
        bot_admin.reply_to(message, "⛔ Accès refusé.")
        return
    DATA_FILE = "users_data.json"
    if not os.path.exists(DATA_FILE):
        bot_admin.reply_to(message, "⚠️ Aucune donnée utilisateur trouvée.")
        return
    with open(DATA_FILE, "r") as f:
        data = json.load(f)
    total_users = len(data)
    total_credits = sum(u.get('credits', 0) for u in data.values())
    user_list = "📊 **DÉTAILS CRÉDITS (Top 10)**\n"
    for count, (u_id, u_info) in enumerate(data.items()):
        if count >= 10: break
        user_list += f"• {u_id} : {u_info.get('credits', 0)} 💰\n"

    stats_msg = (f"👑 **TABLEAU DE BORD ADMIN**\n━━━━━━━━━━━━━━━━━━\n"
                 f"👥 Utilisateurs totaux : {total_users}\n"
                 f"💎 Crédits en circulation : {total_credits}\n"
                 f"━━━━━━━━━━━━━━━━━━\n{user_list}")

    markup = types.InlineKeyboardMarkup()
    btn_maintenance = types.InlineKeyboardButton("📢 Diffuser Maintenance", callback_data="broadcast_off")
    markup.add(btn_maintenance)
    bot_admin.send_message(message.chat.id, stats_msg, reply_markup=markup, parse_mode="Markdown")

# --- GESTION DES ACTIONS ---
@bot_admin.callback_query_handler(func=lambda call: call.data.startswith(("admin_", "broadcast_")))
def process_admin_actions(call):
    # SÉCURITÉ : Vérification que seul l'admin peut utiliser ces boutons
    if call.from_user.id != config.ADMIN_ID:
        bot_admin.answer_callback_query(call.id, "⛔ Non autorisé")
        log_admin_action("unauthorized_callback", call.from_user.id, f"Tentative d'utilisation de {call.data}")
        return
    
    # On récupère le texte et le lien depuis le JSON à chaque clic
    config_data = get_maintenance_config()
    msg_text = config_data["maintenance_text"]
    url_link = config_data["contact_url"]

    if call.data == "broadcast_off":
        DATA_FILE = "users_data.json"
        with open(DATA_FILE, "r") as f:
            data = json.load(f)
        
        count = 0
        for u_id in data.keys():
            try:
                target_id = resolve_telegram_id(u_id)
                if target_id:
                    # User has Telegram - send via Telegram
                    markup = types.InlineKeyboardMarkup()
                    markup.add(types.InlineKeyboardButton("💬 REJOINDRE LA DISCUSSION", url=url_link))
                    send_telegram_message(
                        bot_user,
                        target_id,
                        msg_text,
                        log_context="broadcast_maintenance",
                        reply_markup=markup,
                        parse_mode="Markdown"
                    )
                else:
                    # User doesn't have Telegram - send via web notification
                    add_web_notification(u_id, "🚨 Maintenance en cours. Le service est temporairement indisponible.", "admin_message")
                count += 1
            except Exception:
                continue
        bot_admin.answer_callback_query(call.id, f"✅ Envoyé à {count} personnes")
        log_admin_action("broadcast", "all", f"{count} utilisateurs notifiés")

    else:
        # SÉCURITÉ : Validation du format de callback_data
        parts = call.data.split("|")
        if len(parts) < 2:
            bot_admin.answer_callback_query(call.id, "❌ Format invalide")
            return
        
        action, u_id = parts[0], parts[1]
        
        # Validation de l'user_id
        if not is_valid_user_identifier(u_id):
            bot_admin.answer_callback_query(call.id, "❌ ID utilisateur invalide")
            return
        
        if action == "admin_ok":
            if len(parts) < 3:
                bot_admin.answer_callback_query(call.id, "❌ Pack manquant")
                return
            pack = parts[2]
            amount = 10 if "10" in pack else 50 if "50" in pack else 100
            add_credits(u_id, amount)
            bot_admin.edit_message_text(f"✅ Validé (+{amount}) pour {u_id}", call.message.chat.id, call.message.message_id)
            target_id = resolve_telegram_id(u_id)
            telegram_sent = send_telegram_message(
                bot_user,
                target_id,
                f"🎉 **Achat validé !** +{amount} crédits ajoutés.",
                log_context="purchase_approved",
                parse_mode="Markdown"
            )
            # Send web notification if Telegram message failed (user without Telegram)
            if not telegram_sent:
                add_web_notification(u_id, f"🎉 Achat validé ! +{amount} crédits ont été ajoutés à votre compte.", "admin_message")
            log_admin_action("approve_purchase", u_id, f"+{amount} crédits")
        
        elif action == "admin_off":
            markup = types.InlineKeyboardMarkup()
            markup.add(types.InlineKeyboardButton("💬 REJOINDRE LA DISCUSSION", url=url_link))
            bot_admin.edit_message_text(f"🚫 Info maintenance envoyée à {u_id}", call.message.chat.id, call.message.message_id)
            target_id = resolve_telegram_id(u_id)
            telegram_sent = send_telegram_message(
                bot_user,
                target_id,
                msg_text,
                log_context="maintenance_notice",
                reply_markup=markup,
                parse_mode="Markdown"
            )
            # Send web notification if Telegram message failed (user without Telegram)
            if not telegram_sent:
                add_web_notification(u_id, "🚨 Maintenance en cours. Le service est temporairement indisponible.", "admin_message")
            log_admin_action("send_maintenance", u_id, "Notification de maintenance")
        
        elif action == "admin_no":
            bot_admin.edit_message_text(f"❌ Refusé pour {u_id}", call.message.chat.id, call.message.message_id)
            target_id = resolve_telegram_id(u_id)
            telegram_sent = send_telegram_message(
                bot_user,
                target_id,
                "❌ Votre demande d'achat a été refusée.",
                log_context="purchase_rejected"
            )
            # Send web notification if Telegram message failed (user without Telegram)
            if not telegram_sent:
                add_web_notification(u_id, "❌ Votre demande d'achat a été refusée.", "admin_message")
            log_admin_action("reject_purchase", u_id, "Achat refusé")

# --- NOTIFICATIONS (INCHANGÉES) ---
def notify_new_purchase(user_id, username, pack_name):
    markup = types.InlineKeyboardMarkup()
    btn_ok = types.InlineKeyboardButton("✅ ACCEPTER", callback_data=f"admin_ok|{user_id}|{pack_name}")
    btn_no = types.InlineKeyboardButton("❌ REFUSER", callback_data=f"admin_no|{user_id}")
    btn_off = types.InlineKeyboardButton("🚫 INDISPONIBLE", callback_data=f"admin_off|{user_id}")
    markup.row(btn_ok, btn_no)
    markup.add(btn_off)
    msg = (f"🔔 **NOUVELLE DEMANDE D'ACHAT**\n━━━━━━━━━━━━━━━━━━\n👤 : {username}\n🆔 : `{user_id}`\n📦 : {pack_name}")
    bot_admin.send_message(config.ADMIN_ID, msg, reply_markup=markup, parse_mode="Markdown")

def notify_new_user(user):
    msg = (f"🆕 **NOUVEAU MEMBRE**\n👤: {user.first_name}\n🆔: `{user.id}`\n🎁: +50 Crédits offerts")
    bot_admin.send_message(config.ADMIN_ID, msg, parse_mode="Markdown")

def start_admin_bot_thread():
    threading.Thread(target=bot_admin.infinity_polling, daemon=True).start()
