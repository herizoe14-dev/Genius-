import telebot, config, threading, json, os, auth
from telebot import types 
from limiteur import add_credits

bot_admin = telebot.TeleBot(config.TOKEN_BOT_ADMIN)
bot_user = telebot.TeleBot(config.TOKEN_BOT_USER)

def resolve_telegram_id(user_id):
    user_str = str(user_id).strip()
    if user_str.isdigit():
        return int(user_str)
    try:
        auth_data = auth.load_auth_data()
    except Exception:
        return None
    telegram_id = auth_data.get("users", {}).get(user_str, {}).get("telegram_id", "")
    if str(telegram_id).isdigit():
        return int(telegram_id)
    return None

def iter_maintenance_recipients():
    recipients = set()
    data_file = "users_data.json"
    if os.path.exists(data_file):
        with open(data_file, "r", encoding="utf-8") as f:
            data = json.load(f)
        for u_id in data.keys():
            resolved = resolve_telegram_id(u_id)
            if resolved:
                recipients.add(resolved)
    try:
        auth_data = auth.load_auth_data()
        for info in auth_data.get("users", {}).values():
            telegram_id = str(info.get("telegram_id", "")).strip()
            if telegram_id.isdigit():
                recipients.add(int(telegram_id))
    except Exception:
        pass
    return recipients

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
    if message.from_user.id != config.ADMIN_ID:
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
    # On récupère le texte et le lien depuis le JSON à chaque clic
    config_data = get_maintenance_config()
    msg_text = config_data["maintenance_text"]
    url_link = config_data["contact_url"]

    if call.data == "broadcast_off":
        recipients = iter_maintenance_recipients()
        if not recipients:
            bot_admin.answer_callback_query(call.id, "⚠️ Aucun utilisateur Telegram lié.")
            return
        count = 0
        for chat_id in recipients:
            try:
                markup = types.InlineKeyboardMarkup()
                markup.add(types.InlineKeyboardButton("💬 REJOINDRE LA DISCUSSION", url=url_link))
                bot_user.send_message(chat_id, msg_text, reply_markup=markup, parse_mode="Markdown")
                count += 1
            except: continue
        bot_admin.answer_callback_query(call.id, f"✅ Envoyé à {count} personnes")

    else:
        parts = call.data.split("|")
        action, u_id = parts[0], parts[1]
        
        if action == "admin_ok":
            pack = parts[2]
            amount = int(pack) if str(pack).isdigit() else (10 if "10" in pack else 50 if "50" in pack else 100)
            add_credits(u_id, amount)
            chat_id = resolve_telegram_id(u_id)
            note = ""
            if chat_id:
                try:
                    bot_user.send_message(chat_id, f"🎉 **Achat validé !** +{amount} crédits ajoutés.", parse_mode="Markdown")
                except Exception:
                    note = " ⚠️ Notification Telegram échouée."
            else:
                note = " ⚠️ Telegram ID manquant."
            bot_admin.edit_message_text(f"✅ Validé (+{amount}) pour {u_id}{note}", call.message.chat.id, call.message.message_id)
        
        elif action == "admin_off":
            markup = types.InlineKeyboardMarkup()
            markup.add(types.InlineKeyboardButton("💬 REJOINDRE LA DISCUSSION", url=url_link))
            chat_id = resolve_telegram_id(u_id)
            note = ""
            if chat_id:
                try:
                    bot_user.send_message(chat_id, msg_text, reply_markup=markup, parse_mode="Markdown")
                except Exception:
                    note = " ⚠️ Notification Telegram échouée."
            else:
                note = " ⚠️ Telegram ID manquant."
            bot_admin.edit_message_text(f"🚫 Info maintenance envoyée à {u_id}{note}", call.message.chat.id, call.message.message_id)
        
        elif action == "admin_no":
            chat_id = resolve_telegram_id(u_id)
            note = ""
            if chat_id:
                try:
                    bot_user.send_message(chat_id, "❌ Votre demande d'achat a été refusée.")
                except Exception:
                    note = " ⚠️ Notification Telegram échouée."
            else:
                note = " ⚠️ Telegram ID manquant."
            bot_admin.edit_message_text(f"❌ Refusé pour {u_id}{note}", call.message.chat.id, call.message.message_id)

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
