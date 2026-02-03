# --- CONFIGURATION CENTRALISÉE ---
import os

# Charger les variables d'environnement depuis .env si disponible
try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass  # python-dotenv n'est pas installé, on utilise les variables d'environnement système

# SÉCURITÉ : Utilisation de variables d'environnement pour les tokens sensibles
# Variables attendue par ytt.py
API_TOKEN = os.getenv("API_TOKEN", "8371092102:AAEejzC1RrSCuv0knFRsTtKTDnWWp86AcWo")

# Variables utilisées par admin.py et boutique.py
TOKEN_BOT_USER = os.getenv("TOKEN_BOT_USER", "8371092102:AAEejzC1RrSCuv0knFRsTtKTDnWWp86AcWo")
TOKEN_BOT_ADMIN = os.getenv("TOKEN_BOT_ADMIN", "8268078828:AAHRNYrWexFCkBfIU2OMrXbbysPYDEaMF54")

# Ton ID personnel pour recevoir les alertes sur le Bot 2
ADMIN_ID = int(os.getenv("ADMIN_ID", "5732047363"))

MAIL_SERVER = os.getenv("MAIL_SERVER", 'smtp.gmail.com')
MAIL_PORT = int(os.getenv("MAIL_PORT", "587"))  # Correction : 587 est le port SMTP correct avec TLS
MAIL_USERNAME = os.getenv("MAIL_USERNAME", 'ton-email@gmail.com')
MAIL_PASSWORD = os.getenv("MAIL_PASSWORD", 'votre-mot-de-passe-d-application')  # Pas ton mot de passe normal !