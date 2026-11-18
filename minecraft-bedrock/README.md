# Serveur Minecraft Bedrock (Hetzner, Ubuntu)

- Toujours la **dernière version officielle** de Bedrock Dedicated Server, donc compatible avec Minecraft **1.26.44.x et les versions suivantes**
- Seed : `4208396003150133985`
- Mise à jour automatique chaque nuit (05:00), avec une sauvegarde des mondes avant chaque mise à jour (les 7 dernières sont gardées)
- Swap de 2 Go (nécessaire avec 1 Go de RAM), pare-feu UFW, service systemd

## Installation (une seule fois)

1. Connecte-toi au serveur (depuis un téléphone Android, avec **Termux** ou **JuiceSSH**) :
   ```
   ssh root@204.168.194.233
   ```
   Au premier login, Hetzner te demande de changer le mot de passe : choisis-en un nouveau.

2. Lance l'installation :
   ```
   apt-get update && apt-get install -y git
   git clone -b claude/minecraft-bedrock-server-dv3s7l https://github.com/herizoe14-dev/Genius-.git
   bash Genius-/minecraft-bedrock/install.sh
   ```
   (Si le dépôt est privé, GitHub te demande ton identifiant et un *token* en guise de mot de passe.)

3. Si tu as activé un **Firewall dans la console Hetzner Cloud**, ajoute-y une règle entrante **UDP 19132** (et 19133).

## Se connecter depuis Minecraft

Jouer → Serveurs → Ajouter un serveur
- Adresse : `204.168.194.233`
- Port : `19132`

## Commandes utiles (sur le serveur)

| Commande | Rôle |
|---|---|
| `systemctl status bedrock` | état du serveur |
| `systemctl restart bedrock` | redémarrer |
| `bedrock-console` | console du jeu (`op <pseudo>` pour te donner les droits admin) ; **Ctrl+A puis D** pour quitter sans arrêter |
| `bedrock-update` | forcer la recherche d'une mise à jour |
| `tail /var/log/bedrock-update.log` | historique des mises à jour |

## Modifier les réglages

Les réglages sont dans `/opt/bedrock/server.properties` (mode de jeu, difficulté, nombre de joueurs…), puis `systemctl restart bedrock`.
Le seed ne s'applique qu'à la **création** du monde. Pour régénérer : `systemctl stop bedrock && rm -rf "/opt/bedrock/worlds/Monde Genius" && systemctl start bedrock`.

## À savoir

Avec Bedrock, le serveur et le téléphone doivent être sur la **même version**. Le serveur se met à jour seul chaque nuit : garde Minecraft à jour sur le téléphone (Play Store). Si le serveur se met à jour avant ton téléphone, mets ton téléphone à jour ; si tu veux qu'il se mette à jour tout de suite, lance `bedrock-update`.
