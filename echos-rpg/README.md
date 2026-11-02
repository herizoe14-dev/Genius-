# Échos d'Aube — RPG Android hors ligne + multijoueur Wi-Fi local

RPG en vue de dessus (pixel art) fait avec **libGDX**. Il fonctionne **sans Internet** :
en solo, ou à plusieurs sur **le même Wi-Fi** (ou le partage de connexion d'un des téléphones).
Tous les graphismes sont générés par le code ; la police est DejaVu Sans (licence libre).

## Installer

1. Copier `release/EchosDAube.apk` sur le téléphone (Android 8.0 ou plus).
2. L'ouvrir et autoriser « Installer des applications inconnues » si Android le demande.

## Jouer à plusieurs (même Wi-Fi)

1. Tout le monde se connecte au même Wi-Fi, **ou** un téléphone active son *partage de connexion* et les autres s'y connectent (aucune donnée mobile n'est utilisée par le jeu).
2. Un joueur touche **Héberger (Wi-Fi)**. Son IP s'affiche en haut de l'écran.
3. Les autres touchent **Rejoindre** : la partie apparaît automatiquement. Sinon, **Entrer une IP** et saisir l'IP affichée chez l'hôte.

Jusqu'à 8 joueurs. L'hôte fait tourner le monde : s'il quitte, la partie se termine pour tous.
Ports utilisés : TCP 47500 (jeu) et UDP 47501 (découverte).

## Le jeu

- 3 classes : **Guerrier** (corps à corps, robuste), **Archère** (tir à distance), **Mage** (boule de feu avec dégâts de zone).
- Monstres par zones autour du village : gelées → loups → squelettes, et le **Golem ancien** dans les ruines au sud-est.
- XP et or **partagés** entre les joueurs proches lors d'une victoire, niveaux 1 à 50.
- **Fontaine du village** : soins rapides et achat de potions (15 or).
- Chat de groupe, mini-carte, sauvegarde automatique (une progression par classe).

Commandes tactiles : glisser sur la moitié gauche de l'écran pour bouger, maintenir **ATTAQUE**, **✚** pour boire une potion.
Sur PC : ZQSD/WASD ou flèches, Espace pour attaquer, Q potion, B acheter, Entrée pour le chat, Échap pour quitter.

## Compiler

Prérequis : JDK 17+ et le SDK Android (créer `local.properties` avec `sdk.dir=/chemin/vers/android-sdk`).

```bash
./gradlew :android:assembleRelease    # APK -> android/build/outputs/apk/release/
./gradlew :desktop:run                # version PC (peut jouer avec les téléphones du même réseau)
./gradlew :core:test                  # tests réseau : 2 joueurs, combat, découverte UDP...
```

## Architecture

- `core/…/net/GameServer` : serveur qui fait autorité (monstres, combats, XP), lancé dans l'appli de l'hôte (ou en local pour le solo, écoute alors seulement sur 127.0.0.1).
- `core/…/net/GameClient`, `ClientState` : client et état du monde reçu.
- `core/…/net/LanDiscovery` : découverte des parties par diffusion UDP.
- `core/…/world/WorldMap` : carte 128×128 générée à partir d'une graine (identique chez tous les joueurs).
- `android/…/AndroidLauncher` : verrou multicast Wi-Fi pour recevoir la diffusion UDP.
