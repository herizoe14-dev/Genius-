#!/usr/bin/env python3
"""
Script de configuration pour Genius Bot.
Aide les utilisateurs à créer leur fichier .env.
"""
import os
import shutil

ENV_FILE = ".env"
ENV_EXAMPLE = ".env.example"

def main():
    print("=" * 50)
    print("🔧 Configuration de Genius Bot")
    print("=" * 50)
    print()
    
    # Vérifier si .env.example existe
    if not os.path.exists(ENV_EXAMPLE):
        print(f"❌ Fichier {ENV_EXAMPLE} introuvable!")
        print("   Veuillez vérifier que vous êtes dans le bon répertoire.")
        return 1
    
    # Vérifier si .env existe déjà
    if os.path.exists(ENV_FILE):
        print(f"⚠️  Le fichier {ENV_FILE} existe déjà.")
        response = input("   Voulez-vous le remplacer? (o/N): ").strip().lower()
        if response not in ['o', 'oui', 'y', 'yes']:
            print("   Configuration annulée.")
            return 0
    
    # Copier .env.example vers .env
    shutil.copy(ENV_EXAMPLE, ENV_FILE)
    print(f"✅ Fichier {ENV_FILE} créé à partir de {ENV_EXAMPLE}")
    print()
    
    print("📝 Prochaines étapes:")
    print(f"   1. Ouvrez {ENV_FILE} dans votre éditeur de texte")
    print("   2. Remplacez les valeurs par défaut par vos vraies valeurs:")
    print()
    print("      API_TOKEN        - Token de votre bot Telegram principal")
    print("      TOKEN_BOT_USER   - Token du bot utilisateur")
    print("      TOKEN_BOT_ADMIN  - Token du bot admin")
    print("      ADMIN_ID         - Votre ID Telegram (numéro)")
    print("      FLASK_SECRET     - Clé secrète Flask (générée aléatoirement)")
    print()
    print("   💡 Pour obtenir un token Telegram:")
    print("      - Contactez @BotFather sur Telegram")
    print("      - Créez un nouveau bot avec /newbot")
    print("      - Copiez le token fourni")
    print()
    print("   💡 Pour trouver votre ADMIN_ID:")
    print("      - Contactez @userinfobot sur Telegram")
    print("      - Il vous donnera votre ID numérique")
    print()
    print("   💡 Pour générer une clé secrète Flask:")
    print('      python -c "import os; print(os.urandom(32).hex())"')
    print()
    print("=" * 50)
    print("🚀 Une fois configuré, lancez:")
    print("   python ytt.py     (Bot Telegram)")
    print("   python app.py     (Application Web)")
    print("=" * 50)
    
    return 0


if __name__ == "__main__":
    exit(main())
