from telegram import InlineKeyboardButton, InlineKeyboardMarkup

def main_menu():
    kb = [
        [InlineKeyboardButton("👤 Mon Profil", callback_data="profile")],
        [InlineKeyboardButton("🛒 Boutique", callback_data="shop"), 
         InlineKeyboardButton("🛠 Admin", callback_data="admin_panel")]
    ]
    return InlineKeyboardMarkup(kb)

def shop_menu():
    kb = [
        [InlineKeyboardButton("💎 10 Crédits", callback_data="buy|10")],
        [InlineKeyboardButton("💎 50 Crédits", callback_data="buy|50")],
        [InlineKeyboardButton("💎 100 Crédits", callback_data="buy|100")],
        [InlineKeyboardButton("🔙 Retour", callback_data="home")]
    ]
    return InlineKeyboardMarkup(kb)

def admin_validation(user_id, amount):
    kb = [
        [InlineKeyboardButton("✅ Confirmer", callback_data=f"acc|{user_id}|{amount}"),
         InlineKeyboardButton("❌ Refuser", callback_data=f"ref|{user_id}")]
    ]
    return InlineKeyboardMarkup(kb)