# Genius Bot - Application Web de Téléchargement

Application web Flask permettant le téléchargement de contenus YouTube avec système de crédits et gestion d'utilisateurs.

## 🚀 Fonctionnalités

- **Authentification sécurisée** : Inscription et connexion avec hashage des mots de passe
- **Téléchargement de médias** : Téléchargement de vidéos YouTube en MP3 ou MP4
- **Système de crédits** : Chaque téléchargement coûte 1 crédit
- **Boutique intégrée** : Achat de packs de crédits (10, 50, 100)
- **Panel d'administration** : Gestion des utilisateurs et validation des achats
- **Protection anti-brute-force** : Limitation du taux de requêtes par IP
- **Notifications Telegram** : Alertes pour les nouveaux achats

## 📋 Prérequis

- Python 3.8+
- pip
- Compte Telegram Bot (2 bots : un pour les utilisateurs, un pour l'admin)

## 🔧 Installation

1. **Cloner le dépôt**
```bash
git clone <url-du-repo>
cd Genius-
```

2. **Installer les dépendances**
```bash
pip install -r requirements.txt
```

3. **Configurer les variables d'environnement**

Modifier le fichier `config.py` avec vos tokens :
```python
TOKEN_BOT_USER = "votre_token_bot_utilisateur"
TOKEN_BOT_ADMIN = "votre_token_bot_admin"
ADMIN_ID = votre_id_telegram
```

4. **Variables d'environnement optionnelles**
```bash
export FLASK_SECRET="votre_clé_secrète_flask"
```

## ▶️ Démarrage

### Mode développement
```bash
python app.py
```

### Mode production
```bash
# Avec Gunicorn
pip install gunicorn
gunicorn -w 4 -b 0.0.0.0:5000 app:app

# Ou avec waitress
pip install waitress
waitress-serve --host=0.0.0.0 --port=5000 app:app
```

L'application sera accessible sur : `http://0.0.0.0:5000`

## 👥 Utilisation

### Pour les utilisateurs

1. **Inscription** : Créez un compte (1 compte par IP)
   - Vous recevez 50 crédits gratuits valables 30 jours

2. **Téléchargement** : 
   - Collez l'URL YouTube
   - Choisissez le format (MP3 ou MP4)
   - Cliquez sur "Télécharger" (coûte 1 crédit)

3. **Acheter des crédits** :
   - Accédez à la boutique
   - Sélectionnez un pack
   - La demande est envoyée à l'administrateur

### Pour les administrateurs

1. **Accès au panel admin** : 
   - Connectez-vous avec le compte "admin"
   - Le lien "👑 Admin" apparaît dans le menu

2. **Gestion des utilisateurs** :
   - Voir les statistiques globales
   - Ajouter des crédits manuellement
   - Gérer les comptes

3. **Validation des achats** :
   - Approuver ou refuser les demandes d'achat
   - Les utilisateurs sont notifiés via Telegram

4. **Diffusion de messages** :
   - Envoyer un message à tous les utilisateurs via Telegram

## 📁 Structure du projet

```
Genius-/
├── app.py                  # Application Flask principale
├── auth.py                 # Gestion de l'authentification
├── downloader.py           # Téléchargement YouTube
├── limiteur.py             # Gestion des crédits
├── config.py               # Configuration
├── admin.py                # Bot Telegram admin
├── boutique.py             # Bot Telegram boutique
├── requirements.txt        # Dépendances Python
├── .gitignore             # Fichiers à ignorer
├── templates/             # Templates HTML
│   ├── base.html
│   ├── login.html
│   ├── register.html
│   ├── dashboard.html
│   ├── download.html
│   ├── shop.html
│   ├── credits.html
│   └── admin.html
├── static/                # Fichiers statiques
│   ├── css/
│   │   └── style.css
│   └── js/
│       └── script.js
└── downloads/             # Dossier de téléchargements temporaires
```

## 🔐 Sécurité

- Mots de passe hashés avec Werkzeug (bcrypt)
- Protection anti-brute-force (5 tentatives max, lock 5 min)
- Limitation de taux par IP (60 req/min)
- En-têtes de sécurité HTTP (HSTS, CSP, X-Frame-Options, etc.)
- Sessions sécurisées avec cookies HTTPOnly
- 1 compte par IP pour éviter les abus

## 🔑 Compte administrateur

Pour créer un compte administrateur :

1. Inscrivez-vous avec le nom d'utilisateur "admin"
2. Le système détectera automatiquement les droits admin
3. Vous pourrez accéder au panel d'administration

## 🐛 Résolution des problèmes

### Le serveur ne démarre pas
- Vérifiez que le port 5000 n'est pas déjà utilisé
- Vérifiez les tokens Telegram dans `config.py`

### Les téléchargements échouent
- Vérifiez votre connexion internet
- Assurez-vous que yt-dlp est à jour : `pip install -U yt-dlp`

### Les notifications Telegram ne fonctionnent pas
- Vérifiez vos tokens dans `config.py`
- Assurez-vous que les bots sont démarrés

## 📝 Licence

Ce projet est fourni "tel quel" sans garantie.

## 🤝 Support

Pour toute question ou problème, contactez l'administrateur via Telegram.
