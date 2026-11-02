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

- **Avatar** personnalisable (peau, coiffure, couleur des cheveux) ; la tenue change selon la classe et l'armure portée, l'arme selon son palier.
- 3 classes, chacune avec une **compétence** qui coûte des PM : **Guerrier** (Tourbillon autour de soi), **Archère** (Volée sur 4 cibles), **Mage** (Météore en zone).
- **Village d'Aube = zone de sécurité** : palissade avec 4 portes, aucun monstre ne peut y entrer ni y poursuivre un joueur.
- 4 bâtiments avec boutique (s'approcher de la porte → bouton **Entrer**) :
  - **Apothicaire** (Lila) : potions de vie et de mana, petites et grandes ;
  - **Auberge** (Bram) : nourriture qui régénère PV/PM pendant un moment ;
  - **Forge** (Hugo) : armes et armures **de ta classe**, 5 paliers (niveaux 1, 5, 10, 18, 28) ;
  - **Dépôt** (Nora) : 48 places pour ranger tes objets.
  On peut revendre ses objets à l'apothicaire, à l'auberge ou à la forge.
- Sac de 24 places, équipement arme + armure (ATQ, DÉF, PV bonus).
- Monstres par zones autour du village : gelées → loups → squelettes, et le **Golem ancien** dans les ruines (butin garanti).
- XP et or **partagés** entre les joueurs proches, niveaux 1 à 50. Fontaine du village : soins rapides PV et PM.
- Chat de groupe, mini-carte, sauvegarde automatique (une progression par classe).

Graphismes : tout est dessiné par le code (tuiles 32 px avec transitions irrégulières entre herbe, terre, sable,
pavés et dalles ; arbres, sapins, maisons à colombages, fontaine, étals, palissade, personnages en couches).

Commandes tactiles : glisser sur la moitié gauche pour bouger, maintenir **ATTAQUE**, bouton étoile = compétence,
potions rouge/bleue = boire, sac = inventaire.
Sur PC : ZQSD/WASD ou flèches, Espace attaque, K compétence, Q/E potions vie/mana, I sac, F entrer dans une boutique,
Entrée pour le chat, Échap pour fermer/quitter.

## Compiler

Prérequis : JDK 17+ et le SDK Android (créer `local.properties` avec `sdk.dir=/chemin/vers/android-sdk`).

```bash
./gradlew :android:assembleRelease    # APK -> android/build/outputs/apk/release/
./gradlew :desktop:run                # version PC (peut jouer avec les téléphones du même réseau)
./gradlew :core:test                  # tests : 2 joueurs, combat, boutiques, dépôt, zone sûre, découverte UDP...
```

## Architecture

- `core/…/net/GameServer` : serveur qui fait autorité (monstres, combats, XP), lancé dans l'appli de l'hôte (ou en local pour le solo, écoute alors seulement sur 127.0.0.1).
- `core/…/net/GameClient`, `ClientState` : client et état du monde reçu.
- `core/…/net/LanDiscovery` : découverte des parties par diffusion UDP.
- `core/…/world/WorldMap` : carte 128×128 générée à partir d'une graine (identique chez tous les joueurs).
- `android/…/AndroidLauncher` : verrou multicast Wi-Fi pour recevoir la diffusion UDP.
