import telebot, config, threading, json, os
from telebot import types 
from limiteur import add_credits
from data_store import find_latest_pending, update_status_for_entry, mark_all_pending_as_off

bot_admin = telebot.TeleBot(config.TOKEN_BOT_ADMIN)
bot_user = telebot.TeleBot(config.TOKEN_BOT_USER)

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
    btn_purchase_off = types.InlineKeyboardButton("🚫 Indisponible (achats → TOUS)", callback_data="broadcast_purchase_off")
    markup.add(btn_maintenance)
    markup.add(btn_purchase_off)
    bot_admin.send_message(message.chat.id, stats_msg, reply_markup=markup, parse_mode="Markdown")

# --- GESTION DES ACTIONS ---
@bot_admin.callback_query_handler(func=lambda call: call.data.startswith(("admin_", "broadcast_")))
def process_admin_actions(call):
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
                markup = types.InlineKeyboardMarkup()
                markup.add(types.InlineKeyboardButton("💬 REJOINDRE LA DISCUSSION", url=url_link))
                bot_user.send_message(u_id, msg_text, reply_markup=markup, parse_mode="Markdown")
                count += 1
            except: continue
        bot_admin.answer_callback_query(call.id, f"✅ Envoyé à {count} personnes")

    elif call.data == "broadcast_purchase_off":
        # New handler: broadcast purchase unavailable to all users and mark all pending purchases as off
        DATA_FILE = "users_data.json"
        purchase_msg = "🚫 **ACHAT INDISPONIBLE**\n\nLes achats sont temporairement indisponibles. Veuillez réessayer plus tard."
        
        with open(DATA_FILE, "r") as f:
            data = json.load(f)
        
        count = 0
        for u_id in data.keys():
            try:
                bot_user.send_message(u_id, purchase_msg, parse_mode="Markdown")
                count += 1
            except: continue
        
        # Mark all pending purchases as off in purchases.json
        num_marked = mark_all_pending_as_off("Achat indisponible")
        
        bot_admin.answer_callback_query(
            call.id, 
            f"✅ Message envoyé à {count} utilisateurs. {num_marked} demandes marquées comme indisponibles."
        )

    else:
        parts = call.data.split("|")
        action, u_id = parts[0], parts[1]
        
        if action == "admin_ok":
            pack = parts[2]
            amount = 10 if "10" in pack else 50 if "50" in pack else 100
            
            # Find and update the purchase in purchases.json
            entry = find_latest_pending(u_id, pack)
            if entry:
                update_status_for_entry(entry, "accepted", f"Achat validé : +{amount} crédits")
            
            # Add credits as before
            add_credits(u_id, amount)
            bot_admin.edit_message_text(f"✅ Validé (+{amount}) pour {u_id}", call.message.chat.id, call.message.message_id)
            bot_user.send_message(u_id, f"🎉 **Achat validé !** +{amount} crédits ajoutés.")
        
        elif action == "admin_off":
            # Find and update the purchase in purchases.json
            entry = find_latest_pending(u_id)
            if entry:
                update_status_for_entry(entry, "off", msg_text)
            
            markup = types.InlineKeyboardMarkup()
            markup.add(types.InlineKeyboardButton("💬 REJOINDRE LA DISCUSSION", url=url_link))
            bot_admin.edit_message_text(f"🚫 Info maintenance envoyée à {u_id}", call.message.chat.id, call.message.message_id)
            bot_user.send_message(u_id, msg_text, reply_markup=markup, parse_mode="Markdown")
        
        elif action == "admin_no":
            # Find and update the purchase in purchases.json
            entry = find_latest_pending(u_id)
            if entry:
                update_status_for_entry(entry, "refused", "Demande d'achat refusée")
            
            bot_admin.edit_message_text(f"❌ Refusé pour {u_id}", call.message.chat.id, call.message.message_id)
            bot_user.send_message(u_id, "❌ Votre demande d'achat a été refusée.")

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