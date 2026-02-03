# --- CONFIGURATION CENTRALISÉE ---
import os
import sys

# Charger les variables d'environnement depuis .env si disponible
try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass  # python-dotenv n'est pas installé, on utilise les variables d'environnement système

# SÉCURITÉ : Variables d'environnement obligatoires
# Les tokens par défaut sont pour le développement UNIQUEMENT et doivent être changés
def get_required_env(var_name, dev_default=None):
    """Récupère une variable d'environnement requise."""
    value = os.getenv(var_name)
    if not value:
        if dev_default and os.getenv("FLASK_ENV") == "development":
            print(f"⚠️  AVERTISSEMENT: Utilisation de la valeur par défaut pour {var_name}")
            print("⚠️  Configurez .env en production!")
            return dev_default
        else:
            print(f"❌ ERREUR: Variable d'environnement {var_name} manquante!")
            print("")
            print("💡 Pour configurer le bot, suivez ces étapes:")
            print("")
            print("   1. Copiez le fichier .env.example en .env :")
            print("      cp .env.example .env")
            print("")
            print("   2. Éditez le fichier .env avec vos tokens:")
            print("      nano .env   (ou utilisez votre éditeur préféré)")
            print("")
            print("   3. Obtenez votre token bot Telegram auprès de @BotFather")
            print("")
            print("   💡 Mode développement: définissez FLASK_ENV=development")
            print("      pour utiliser les tokens de test temporairement:")
            print("      export FLASK_ENV=development && python ytt.py")
            print("")
            sys.exit(1)
    return value

# Variables attendue par ytt.py
API_TOKEN = get_required_env("API_TOKEN", "8371092102:AAEejzC1RrSCuv0knFRsTtKTDnWWp86AcWo")

# Variables utilisées par admin.py et boutique.py
TOKEN_BOT_USER = get_required_env("TOKEN_BOT_USER", "8371092102:AAEejzC1RrSCuv0knFRsTtKTDnWWp86AcWo")
TOKEN_BOT_ADMIN = get_required_env("TOKEN_BOT_ADMIN", "8268078828:AAHRNYrWexFCkBfIU2OMrXbbysPYDEaMF54")

# Ton ID personnel pour recevoir les alertes sur le Bot 2
ADMIN_ID = int(get_required_env("ADMIN_ID", "5732047363"))

# Configuration mail (optionnelle)
MAIL_SERVER = os.getenv("MAIL_SERVER", 'smtp.gmail.com')
MAIL_PORT = int(os.getenv("MAIL_PORT", "587"))  # Correction : 587 est le port SMTP correct avec TLS
MAIL_USERNAME = os.getenv("MAIL_USERNAME")  # None si non configuré
MAIL_PASSWORD = os.getenv("MAIL_PASSWORD")  # None si non configuré