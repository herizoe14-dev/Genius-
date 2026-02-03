# Genius Bot - Téléchargeur YouTube Sécurisé

Application web Flask et bot Telegram pour télécharger du contenu YouTube avec système de crédits.

## 🔒 Améliorations de Sécurité

### Version actuelle inclut :

#### Sécurité du Site Web
- ✅ Clés secrètes sécurisées via variables d'environnement
- ✅ Validation et sanitisation de toutes les entrées utilisateur
- ✅ Protection CSRF sur les formulaires
- ✅ En-têtes de sécurité (CSP, HSTS, X-Frame-Options)
- ✅ Rate limiting pour prévenir les attaques par force brute
- ✅ Cookies sécurisés (HttpOnly, Secure, SameSite)

#### Sécurité des Comptes
- ✅ Mots de passe hashés avec Werkzeug (bcrypt)
- ✅ Politique de mots de passe forts (min 8 caractères, majuscule, minuscule, chiffre)
- ✅ Verrouillage automatique après 5 tentatives échouées
- ✅ Une seule inscription par IP pour éviter les abus
- ✅ Logging des tentatives suspectes

#### Système Anti-Triche
- ✅ Audit complet des transactions de crédits
- ✅ Logging de toutes les actions admin
- ✅ Détection des tentatives de multi-comptes
- ✅ Rate limiting sur les téléchargements (5 secondes minimum)

#### Sécurité du Bot Telegram
- ✅ Validation stricte des callback data
- ✅ Authentification admin renforcée
- ✅ Validation des URLs YouTube
- ✅ Rate limiting par utilisateur
- ✅ Protection contre les injections dans les callbacks

## 📋 Installation

### Prérequis
- Python 3.8+
- pip
- yt-dlp
- ffmpeg (pour la conversion audio)

### Installation des dépendances

```bash
pip install Flask werkzeug pyTelegramBotAPI python-dotenv
```

### Configuration

1. Copiez le fichier `.env.example` en `.env` :
```bash
cp .env.example .env
```

2. Éditez `.env` et remplissez vos valeurs :
```
FLASK_SECRET=votre_cle_secrete_generee_aleatoirement
TOKEN_BOT_USER=votre_token_bot_telegram
TOKEN_BOT_ADMIN=votre_token_bot_admin_telegram
ADMIN_ID=votre_id_telegram
```

⚠️ **IMPORTANT** : Ne commitez JAMAIS le fichier `.env` dans Git !

### Génération d'une clé secrète Flask

```bash
python -c "import os; print(os.urandom(32).hex())"
```

## 🚀 Démarrage

### Lancer le site web
```bash
python app.py
```

### Lancer le bot Telegram
```bash
python ytt.py
```

## 📊 Logs et Audit

L'application génère plusieurs fichiers de logs pour la sécurité et l'audit :

- `suspicious_activity.log` - Tentatives de connexion suspectes
- `credit_transactions.log` - Historique de toutes les transactions de crédits
- `admin_actions.log` - Toutes les actions administratives
- `pending_purchases.log` - Demandes d'achat en attente

Ces fichiers sont automatiquement exclus du versioning Git.

## 🔐 Bonnes Pratiques de Sécurité

1. **Mots de passe** : Utilisez des mots de passe forts avec au moins 8 caractères incluant majuscules, minuscules et chiffres
2. **HTTPS** : En production, utilisez toujours HTTPS avec un certificat SSL valide
3. **Backup** : Sauvegardez régulièrement les fichiers JSON de données
4. **Monitoring** : Surveillez les fichiers de logs pour détecter les activités suspectes
5. **Updates** : Maintenez Python et les dépendances à jour

## 📝 Structure des Fichiers

```
.
├── app.py              # Application Flask principale
├── auth.py             # Système d'authentification sécurisé
├── limiteur.py         # Gestion des crédits avec audit
├── handlers.py         # Handlers Telegram sécurisés
├── admin.py            # Panel admin avec logging
├── boutique.py         # Système de boutique
├── downloader.py       # Module de téléchargement
├── config.py           # Configuration centralisée
├── .env                # Variables d'environnement (NON versionné)
├── .env.example        # Template de configuration
└── .gitignore          # Fichiers exclus du versioning
```

## 🐛 Corrections de Bugs

- ✅ Correction du port SMTP (584 → 587)
- ✅ Ajout de validation stricte pour tous les inputs
- ✅ Amélioration de la gestion des erreurs
- ✅ Protection contre les race conditions
- ✅ Nettoyage automatique des fichiers temporaires

## 📖 API Telegram

### Commandes Utilisateur
- `/start` - Démarrer le bot
- `/menu` - Afficher le menu principal

### Commandes Admin
- `/admin` - Accéder au panneau d'administration (réservé à l'admin)

## ⚠️ Avertissements

- Les tokens Telegram dans `config.py` sont des exemples. **Remplacez-les par vos propres tokens** via `.env`
- Les tokens actuellement dans le code sont compromis et doivent être régénérés
- Ne partagez jamais vos tokens ou clés secrètes

## 🤝 Contribution

Pour contribuer à ce projet :
1. Toujours valider et sanitiser les entrées utilisateur
2. Ajouter des logs pour les opérations sensibles
3. Tester la sécurité avant de commit
4. Suivre les bonnes pratiques Python (PEP 8)

## 📄 Licence

Ce projet est à usage personnel. Assurez-vous de respecter les conditions d'utilisation de YouTube.
