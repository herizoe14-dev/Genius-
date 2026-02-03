import time, os, uuid
from telebot import types
from limiteur import get_user_data, spend_credit
from downloader import download_content, split_file 
from queue_manager import add_to_queue, get_queue_position, remove_from_queue

# Stockage temporaire des liens pour éviter l'erreur BUTTON_DATA_INVALID
url_storage = {}

def register_handlers(bot):

    @bot.message_handler(commands=['start', 'menu'])
    def send_welcome(message):
        markup = types.ReplyKeyboardMarkup(resize_keyboard=True)
        markup.add("💰 Mes Crédits", "🛒 Boutique")
        bot.send_message(message.chat.id, "👋 **Bienvenue sur Genius Bot !**", reply_markup=markup)

    @bot.message_handler(func=lambda m: "youtu" in m.text)
    def handle_youtube_link(message):
        user_id = message.from_user.id
        if get_user_data(user_id)['credits'] > 0:
            # On génère un ID court unique pour ce lien
            link_id = str(uuid.uuid4())[:8]
            url_storage[link_id] = message.text
            
            markup = types.InlineKeyboardMarkup()
            # On envoie seulement l'ID court dans le callback_data
            markup.row(
                types.InlineKeyboardButton("🎵 MP3", callback_data=f"dl_mp3|{link_id}"),
                types.InlineKeyboardButton("🎥 MP4", callback_data=f"dl_mp4|{link_id}")
            )
            bot.reply_to(message, "✅ **Lien détecté !** Choisissez le format :", reply_markup=markup)
        else:
            bot.reply_to(message, "🚫 **Crédits insuffisants.**")

    @bot.callback_query_handler(func=lambda call: call.data.startswith("dl_"))
    def process_selection(call):
        action, link_id = call.data.split("|")
        
        # On récupère le vrai lien via l'ID
        url = url_storage.get(link_id)
        if not url:
            bot.answer_callback_query(call.id, "❌ Lien expiré, veuillez renvoyer le lien.")
            return

        mode = "mp3" if "mp3" in action else "mp4"
        user_id = call.from_user.id

        if get_user_data(user_id)['credits'] > 0:
            status_msg = bot.send_message(call.message.chat.id, "📡 **Analyse...**")
            add_to_queue(user_id, url, mode, status_msg.message_id, bot, call.message.chat.id)
            
            while get_queue_position(user_id, url) > 1:
                time.sleep(1)
            
            file_path = None
            try:
                file_path = download_content(url, mode, bot, call.message.chat.id, status_msg.message_id)
                file_size = os.path.getsize(file_path)

                if file_size > 45 * 1024 * 1024:
                    bot.edit_message_text("📦 **Gros fichier.** Découpage en cours...", call.message.chat.id, status_msg.message_id)
                    parts = split_file(file_path)
                    bot.send_message(call.message.chat.id, "💡 **Note :** Ouvrez la partie **.001** avec ZArchiver pour tout extraire.")
                    for p in parts:
                        with open(p, 'rb') as f:
                            bot.send_document(call.message.chat.id, f, timeout=300)
                        if os.path.exists(p): os.remove(p)
                else:
                    with open(file_path, 'rb') as f:
                        if mode == "mp4":
                            bot.send_video(call.message.chat.id, f, caption="🎥 Vidéo prête !", timeout=300)
                        else:
                            bot.send_audio(call.message.chat.id, f, caption="🎵 Audio prêt !", timeout=300)

                spend_credit(user_id)
                bot.delete_message(call.message.chat.id, status_msg.message_id)

            except Exception as e:
                bot.send_message(call.message.chat.id, f"❌ Erreur : {str(e)}")
            
            finally:
                if file_path and os.path.exists(file_path): os.remove(file_path)
                remove_from_queue(user_id, url)
                # Nettoyage de la mémoire
                if link_id in url_storage: del url_storage[link_id]

    @bot.message_handler(func=lambda m: m.text == "💰 Mes Crédits")
    def profile(m):
        user_id = m.from_user.id
        d = get_user_data(user_id)
        exp = d.get('expiration', '01/03/2026')
        message_profil = (f"👤 **PROFIL**\n🆔 ID : `{user_id}`\n💰 Crédits : **{d['credits']}**\n⏳ Expire le : {exp}")
        bot.send_message(m.chat.id, message_profil, parse_mode="Markdown")

    @bot.message_handler(func=lambda m: m.text == "🛒 Boutique")
    def shop(m):
        from boutique import show_shop_menu
        show_shop_menu(bot, m.chat.id)