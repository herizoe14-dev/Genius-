import telebot
import config # Indispensable pour utiliser tes tokens centralisés
from telebot import types
import notifications

# On initialise le Bot 2 (Admin) ici pour envoyer les alertes
bot_admin = telebot.TeleBot(config.TOKEN_BOT_ADMIN)

def show_shop_menu(bot, chat_id):
    """Affiche le menu d'achat sur le Bot 1."""
    markup = types.InlineKeyboardMarkup(row_width=1)
    markup.add(
        types.InlineKeyboardButton("🥉 Pack Bronze : 10 Crédits", callback_data="buy_10"),
        types.InlineKeyboardButton("🥈 Pack Argent : 50 Crédits", callback_data="buy_50"),
        types.InlineKeyboardButton("🥇 Pack Or : 100 Crédits", callback_data="buy_100"),
        types.InlineKeyboardButton("👑 ACCÈS PREMIUM ILLIMITÉ", callback_data="buy_premium")
    )
    
    text = (
        "🛒 **BOUTIQUE GENIUS BOT**\n"
        "━━━━━━━━━━━━━━━━━━━━\n"
        "Boostez votre compte pour télécharger sans limites !\n\n"
        "💡 **Choisissez un pack ci-dessous :**"
    )
    bot.send_message(chat_id, text, reply_markup=markup, parse_mode="Markdown")

def register_boutique_handlers(bot):
    """Gère le clic sur un pack et envoie l'alerte au Bot 2."""
    @bot.callback_query_handler(func=lambda call: call.data.startswith("buy_"))
    def handle_purchase(call):
        user = call.from_user
        pack_name = call.data.replace("buy_", "").upper()
        
        # 1. Message d'attente pour l'utilisateur sur le Bot 1
        bot.answer_callback_query(call.id)
        bot.edit_message_text(
            chat_id=call.message.chat.id,
            message_id=call.message.message_id,
            text=f"⏳ **Demande pour le Pack {pack_name} envoyée.**\n\n_Veuillez patienter, un administrateur vérifie votre compte..._",
            parse_mode="Markdown"
        )

        # 2. Boutons de validation pour l'Admin (Bot 2)
        markup_admin = types.InlineKeyboardMarkup()
        btn_ok = types.InlineKeyboardButton("✅ ACCEPTER", callback_data=f"admin_ok|{user.id}|{pack_name}")
        btn_no = types.InlineKeyboardButton("❌ REFUSER", callback_data=f"admin_no|{user.id}")
        markup_admin.add(btn_ok, btn_no)

        # 3. Message clair pour l'Admin sur le Bot 2
        admin_text = (
            "🔔 **NOUVELLE DEMANDE D'ACHAT**\n"
            "━━━━━━━━━━━━━━━━━━━━\n"
            f"👤 **Utilisateur** : {user.first_name}\n"
            f"🆔 **ID** : `{user.id}`\n"
            f"📦 **Pack choisi** : {pack_name}\n"
            "━━━━━━━━━━━━━━━━━━━━\n"
            "Action requise :"
        )
        
        try:
            # On envoie à TON ID personnel via le Bot 2
            bot_admin.send_message(config.ADMIN_ID, admin_text, reply_markup=markup_admin, parse_mode="Markdown")
        except Exception as e:
            print(f"⚠️ Erreur d'envoi au Bot 2 : {e}")

        notifications.add_notification(user.id, f"⏳ Demande envoyée pour le Pack {pack_name}.")
