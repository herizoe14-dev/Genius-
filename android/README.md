# Genius — téléchargeur Android (sans serveur)

Application Android (Kotlin + Jetpack Compose, thème sombre) pour récupérer vidéos, audio et images
depuis **TikTok, YouTube, Instagram, X/Twitter, Pixiv, Facebook** (et +1000 sites via yt-dlp), **sans filigrane**.

Tout tourne **sur le téléphone** : python + yt-dlp + ffmpeg sont embarqués dans l'APK
([youtubedl-android](https://github.com/yausername/youtubedl-android)). Pas de serveur externe, pas de Termux.

## Utilisation
1. Copiez un lien (ou « Partager » → Genius depuis TikTok/YouTube/Instagram…).
2. Ouvrez Genius : le lien est collé automatiquement.
3. Appuyez sur le **gros bouton rond lumineux** → l'app analyse le lien.
4. Choisissez :
   - **Vidéo** : liste des qualités disponibles (4K, 1080p, 720p…) avec taille estimée ;
   - **Audio** : meilleure qualité disponible (M4A/Opus d'origine, ou MP3 dans les réglages) ;
   - **Médias** : toutes les images/vidéos d'un post (carrousel Instagram, diaporama TikTok, tweet multi-photos, Pixiv multi-pages) — sélection libre ;
   - **Playlist** : sélection des éléments + mode vidéo (qualité max choisie) ou audio.
5. Les fichiers vont dans `Films/Genius`, `Musique/Genius`, `Images/Genius`.

## Moteurs d'extraction
| Site | Méthode |
|---|---|
| YouTube, Facebook, playlists, autres | yt-dlp embarqué |
| TikTok vidéo | yt-dlp (formats « watermarked » exclus), secours natif |
| TikTok photos (diaporama) | extracteur natif Kotlin |
| X / Twitter | extracteur natif (photos + vidéos), secours yt-dlp |
| Instagram | extracteur natif (posts, carrousels, reels), secours yt-dlp puis OpenGraph |
| Pixiv | extracteur natif (toutes les pages en qualité originale) |

yt-dlp se met à jour automatiquement une fois par jour (désactivable) ou via Réglages → « Mettre à jour yt-dlp ».

## Compiler
```bash
cd android
./gradlew assembleRelease
# APK : app/build/outputs/apk/release/app-arm64-v8a-release.apk (la plupart des téléphones)
```
Le workflow GitHub Actions `Android APK` compile aussi les APK à chaque push (onglet Actions → artefact `genius-apk`).

Choix de l'APK : `arm64-v8a` (téléphones récents), `armeabi-v7a` (anciens), `universal` (tous, plus lourd).
