#!/usr/bin/env python3
"""
Setup script for Genius Bot environment variables.
Creates a .env file based on .env.example with user-provided values.
"""

import os
import sys
import shutil

def main():
    """Main setup function."""
    print("\n" + "=" * 60)
    print("🔧 Configuration de Genius Bot")
    print("=" * 60 + "\n")
    
    env_file = ".env"
    env_example = ".env.example"
    
    # Check if .env.example exists
    if not os.path.exists(env_example):
        print(f"❌ ERREUR: Le fichier {env_example} n'existe pas!")
        print("Assurez-vous d'être dans le répertoire du projet.")
        sys.exit(1)
    
    # Check if .env already exists
    if os.path.exists(env_file):
        print(f"⚠️  Le fichier {env_file} existe déjà.")
        response = input("Voulez-vous le remplacer? (o/N): ").strip().lower()
        if response != 'o' and response != 'oui':
            print("Configuration annulée.")
            sys.exit(0)
        # Backup existing .env
        backup_file = ".env.backup"
        shutil.copy(env_file, backup_file)
        print(f"📋 Sauvegarde créée: {backup_file}")
    
    print("\n📝 Configuration des variables d'environnement")
    print("-" * 50)
    print("Appuyez sur Entrée pour utiliser la valeur par défaut (entre crochets)")
    print()
    
    config = {}
    
    # Flask Configuration
    print("=== Configuration Flask ===")
    default_secret = os.urandom(32).hex()
    config['FLASK_SECRET'] = input(f"FLASK_SECRET [{default_secret[:16]}...]: ").strip() or default_secret
    config['FLASK_ENV'] = input("FLASK_ENV [production]: ").strip() or "production"
    
    print()
    
    # Telegram Bot Tokens
    print("=== Tokens Telegram Bot ===")
    print("Obtenez vos tokens via @BotFather sur Telegram")
    config['API_TOKEN'] = input("API_TOKEN (token bot principal): ").strip()
    while not config['API_TOKEN']:
        print("⚠️  API_TOKEN est obligatoire!")
        config['API_TOKEN'] = input("API_TOKEN (token bot principal): ").strip()
    
    config['TOKEN_BOT_USER'] = input(f"TOKEN_BOT_USER [{config['API_TOKEN'][:20]}...]: ").strip() or config['API_TOKEN']
    config['TOKEN_BOT_ADMIN'] = input("TOKEN_BOT_ADMIN (token bot admin): ").strip()
    while not config['TOKEN_BOT_ADMIN']:
        print("⚠️  TOKEN_BOT_ADMIN est obligatoire!")
        config['TOKEN_BOT_ADMIN'] = input("TOKEN_BOT_ADMIN (token bot admin): ").strip()
    
    print()
    
    # Admin Configuration
    print("=== Configuration Admin ===")
    print("Utilisez @userinfobot sur Telegram pour obtenir votre ID")
    config['ADMIN_ID'] = input("ADMIN_ID (votre ID Telegram): ").strip()
    while not config['ADMIN_ID'] or not config['ADMIN_ID'].isdigit():
        print("⚠️  ADMIN_ID est obligatoire et doit être un nombre!")
        config['ADMIN_ID'] = input("ADMIN_ID (votre ID Telegram): ").strip()
    
    print()
    
    # Optional Mail Configuration
    print("=== Configuration Mail (optionnel) ===")
    print("Appuyez sur Entrée pour ignorer la configuration mail")
    config['MAIL_SERVER'] = input("MAIL_SERVER [smtp.gmail.com]: ").strip() or "smtp.gmail.com"
    config['MAIL_PORT'] = input("MAIL_PORT [587]: ").strip() or "587"
    config['MAIL_USERNAME'] = input("MAIL_USERNAME: ").strip()
    config['MAIL_PASSWORD'] = input("MAIL_PASSWORD: ").strip()
    
    # Write .env file
    print("\n" + "-" * 50)
    print("📝 Création du fichier .env...")
    
    with open(env_file, 'w') as f:
        f.write("# Configuration de l'application Genius Bot\n")
        f.write("# Généré automatiquement par setup_env.py\n\n")
        
        f.write("# Flask Configuration\n")
        f.write(f"FLASK_SECRET={config['FLASK_SECRET']}\n")
        f.write(f"FLASK_ENV={config['FLASK_ENV']}\n\n")
        
        f.write("# Telegram Bot Tokens\n")
        f.write(f"API_TOKEN={config['API_TOKEN']}\n")
        f.write(f"TOKEN_BOT_USER={config['TOKEN_BOT_USER']}\n")
        f.write(f"TOKEN_BOT_ADMIN={config['TOKEN_BOT_ADMIN']}\n\n")
        
        f.write("# Admin Configuration\n")
        f.write(f"ADMIN_ID={config['ADMIN_ID']}\n\n")
        
        f.write("# Mail Configuration (optionnel)\n")
        f.write(f"MAIL_SERVER={config['MAIL_SERVER']}\n")
        f.write(f"MAIL_PORT={config['MAIL_PORT']}\n")
        if config['MAIL_USERNAME']:
            f.write(f"MAIL_USERNAME={config['MAIL_USERNAME']}\n")
        else:
            f.write("# MAIL_USERNAME=\n")
        if config['MAIL_PASSWORD']:
            f.write(f"MAIL_PASSWORD={config['MAIL_PASSWORD']}\n")
        else:
            f.write("# MAIL_PASSWORD=\n")
    
    print("\n" + "=" * 60)
    print("✅ Configuration terminée!")
    print("=" * 60)
    print(f"\n📁 Fichier créé: {env_file}")
    print("\n🚀 Vous pouvez maintenant démarrer le bot:")
    print("   python ytt.py")
    print()

if __name__ == "__main__":
    main()
