import json
import os
from datetime import datetime, timedelta

DATA_FILE = "users_data.json"

def get_user_data(user_id):
    """Récupère les données et initialise les nouveaux avec 50 crédits et 30j."""
    if os.path.exists(DATA_FILE):
        with open(DATA_FILE, "r") as f:
            data = json.load(f)
    else:
        data = {}

    user_str = str(user_id)
    
    if user_str not in data:
        # Création auto avec expiration à +30 jours
        date_exp = (datetime.now() + timedelta(days=30)).strftime("%d/%m/%Y")
        data[user_str] = {
            "credits": 50,
            "expiration": date_exp,
            "statut": "Nouveau",
            "purchase_status": "",
            "purchase_status_read": True
        }
        save_data(data)

    updated = False
    if "purchase_status" not in data[user_str]:
        data[user_str]["purchase_status"] = ""
        updated = True
    if "purchase_status_read" not in data[user_str]:
        data[user_str]["purchase_status_read"] = True
        updated = True
    if updated:
        save_data(data)

    return data[user_str]

def save_data(data):
    """Sauvegarde sécurisée dans le JSON."""
    with open(DATA_FILE, "w") as f:
        json.dump(data, f, indent=4)

def spend_credit(user_id):
    """Débite 1 crédit lors d'un téléchargement."""
    if os.path.exists(DATA_FILE):
        with open(DATA_FILE, "r") as f:
            data = json.load(f)
        
        user_str = str(user_id)
        if user_str in data and data[user_str]['credits'] > 0:
            data[user_str]['credits'] -= 1
            save_data(data)
            return True
    return False

# --- LA FONCTION QUI MANQUAIT POUR L'ADMIN ---
def add_credits(user_id, amount):
    """Permet à admin.py d'ajouter des crédits sans erreur."""
    if os.path.exists(DATA_FILE):
        with open(DATA_FILE, "r") as f:
            data = json.load(f)
    else:
        data = {}

    user_str = str(user_id)
    if user_str in data:
        data[user_str]['credits'] += int(amount)
        save_data(data)
        return True
    return False

def set_purchase_status(user_id, message):
    """Met à jour le statut d'achat et le marque comme non lu."""
    if os.path.exists(DATA_FILE):
        with open(DATA_FILE, "r") as f:
            data = json.load(f)
    else:
        data = {}
    user_str = str(user_id)
    if user_str not in data:
        date_exp = (datetime.now() + timedelta(days=30)).strftime("%d/%m/%Y")
        data[user_str] = {
            "credits": 50,
            "expiration": date_exp,
            "statut": "Nouveau",
            "purchase_status": "",
            "purchase_status_read": True
        }
    data[user_str]["purchase_status"] = str(message)
    data[user_str]["purchase_status_read"] = False
    save_data(data)
    return True

def mark_purchase_status_read(user_id):
    """Marque le statut d'achat comme lu."""
    if not os.path.exists(DATA_FILE):
        return False
    with open(DATA_FILE, "r") as f:
        data = json.load(f)
    user_str = str(user_id)
    if user_str in data:
        data[user_str]["purchase_status_read"] = True
        save_data(data)
        return True
    return False
