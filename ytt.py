import telebot
from config import API_TOKEN
import handlers, admin, boutique, os

# Utilisation de la RAM de 12Go pour gérer plusieurs tâches
bot = telebot.TeleBot(API_TOKEN, threaded=True, num_threads=5)

# Enregistrement des commandes
handlers.register_handlers(bot)
boutique.register_boutique_handlers(bot)

if __name__ == "__main__":
    if not os.path.exists("downloads"): os.makedirs("downloads")
    admin.start_admin_bot_thread()
    
    print("🚀 Genius Bot V4 : Connecté (RAM 12GB Boost)")
    
    # Correction : On ne passe pas non_stop=True ici
    bot.infinity_polling(timeout=120, long_polling_timeout=120)