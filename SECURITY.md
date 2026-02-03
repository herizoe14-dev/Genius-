# Politique de Sécurité

## 🔒 Versions Supportées

| Version | Supportée          |
| ------- | ------------------ |
| Latest  | :white_check_mark: |

## 🛡️ Signaler une Vulnérabilité

Si vous découvrez une vulnérabilité de sécurité, **NE PAS** créer une issue publique.

Contactez directement l'administrateur via Telegram (ID configuré dans ADMIN_ID).

Nous nous engageons à :
- Répondre dans les 48 heures
- Fournir une mise à jour régulière sur le statut
- Créditer le découvreur (si souhaité)

## 🔐 Mesures de Sécurité Implémentées

### Authentification et Comptes
- Hachage des mots de passe avec bcrypt via Werkzeug
- Politique de mots de passe forts obligatoire
- Verrouillage automatique après tentatives échouées
- Limitation d'un compte par IP
- Logging des tentatives suspectes

### Protection du Site Web
- Validation et sanitisation de toutes les entrées
- Protection CSRF sur les formulaires
- En-têtes de sécurité (CSP, HSTS, X-Frame-Options, etc.)
- Rate limiting global et par IP
- Cookies sécurisés (HttpOnly, Secure, SameSite)
- Clés secrètes via variables d'environnement

### Protection du Bot Telegram
- Validation stricte des callback data
- Authentification admin vérifiée
- Rate limiting par utilisateur
- Validation des URLs
- Protection contre les injections

### Audit et Monitoring
- Log des transactions de crédits
- Log des actions administratives
- Log des activités suspectes
- Traçabilité complète

## 🚨 Vulnérabilités Connues

### CRITIQUE - Tokens Exposés
**Status** : ✅ CORRIGÉ

Les tokens Telegram étaient hardcodés dans `config.py`. 

**Solution** : Utilisation de variables d'environnement via `.env`

**Action requise** : 
1. Régénérer tous les tokens Telegram
2. Configurer le fichier `.env`
3. Ne jamais commiter `.env`

### CRITIQUE - Clé Secrète Faible
**Status** : ✅ CORRIGÉ

La clé secrète Flask était faible et hardcodée.

**Solution** : 
- Génération automatique si non fournie
- Recommandation d'utiliser une clé forte via `.env`

## 📋 Checklist de Sécurité pour le Déploiement

- [ ] Régénérer tous les tokens Telegram
- [ ] Générer une clé secrète Flask forte
- [ ] Configurer `.env` avec les bonnes valeurs
- [ ] Vérifier que `.env` est dans `.gitignore`
- [ ] Activer HTTPS en production
- [ ] Configurer un proxy reverse (nginx/Apache)
- [ ] Limiter les permissions des fichiers de données
- [ ] Mettre en place des backups réguliers
- [ ] Surveiller les logs de sécurité
- [ ] Mettre à jour les dépendances régulièrement

## 🔄 Mises à Jour de Sécurité

Consultez régulièrement ce fichier pour les mises à jour de sécurité.

### 2026-02-03
- ✅ Implémentation de la validation des entrées
- ✅ Ajout du système de logging d'audit
- ✅ Protection des tokens via variables d'environnement
- ✅ Amélioration de la politique de mots de passe
- ✅ Rate limiting sur bot et site web
- ✅ Protection CSRF et en-têtes de sécurité

## 📚 Ressources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Flask Security Best Practices](https://flask.palletsprojects.com/en/latest/security/)
- [Telegram Bot Security](https://core.telegram.org/bots/security)

## ⚠️ Avertissement

Cette application gère des données sensibles (comptes utilisateurs, crédits). 
Assurez-vous de :
- Déployer en HTTPS uniquement
- Sauvegarder régulièrement les données
- Surveiller les logs de sécurité
- Maintenir le système à jour
