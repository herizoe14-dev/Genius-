#!/usr/bin/env python3

import subprocess
import sys
import os

# --- Configuration ---
# Le chemin vers l'exécutable yt-dlp.
# Sur Termux, il est généralement dans le PATH après installation via 'pkg install yt-dlp'.
# Nous allons utiliser 'yt-dlp' directement.
YT_DLP_BIN = 'yt-dlp'

def run_yt_dlp(command):
    """Exécute la commande yt-dlp et gère les erreurs."""
    try:
        # Utilisation de subprocess.run pour une exécution simple et bloquante
        # text=True pour décoder la sortie en texte
        # check=True pour lever une exception en cas d'erreur (code de retour non nul)
        result = subprocess.run(
            [YT_DLP_BIN] + command,
            capture_output=False, # Afficher la sortie directement pour le feedback utilisateur
            text=True,
            check=True,
            encoding='utf-8'
        )
        return result
    except FileNotFoundError:
        print(f"\nERREUR: L'exécutable '{YT_DLP_BIN}' n'a pas été trouvé.")
        print("Veuillez vous assurer que yt-dlp est installé et accessible dans votre PATH.")
        print("Sur Termux, vous pouvez l'installer avec : pkg install yt-dlp")
        sys.exit(1)
    except subprocess.CalledProcessError as e:
        print(f"\nERREUR lors de l'exécution de yt-dlp:")
        # yt-dlp affiche généralement les erreurs sur stderr, mais comme nous n'avons pas capturé la sortie,
        # l'utilisateur a déjà vu le message d'erreur.
        print(f"Code de retour: {e.returncode}")
        # print(f"Sortie standard: {e.stdout}")
        # print(f"Sortie d'erreur: {e.stderr}")
        sys.exit(1)
    except Exception as e:
        print(f"\nUne erreur inattendue s'est produite: {e}")
        sys.exit(1)

def get_video_info(url):
    """Récupère les informations de la vidéo/playlist, y compris les formats disponibles."""
    print("Récupération des informations...")
    # --list-formats pour lister les formats, --flat-playlist pour ne pas télécharger les vidéos d'une playlist
    command = ['--list-formats', '--flat-playlist', url]
    
    # Nous devons capturer la sortie pour l'analyser, donc nous allons modifier run_yt_dlp ou le faire ici.
    # Nous allons utiliser --print-json pour obtenir les données structurées.
    import json # Correction de l'UnboundLocalError
    try:
        result = subprocess.run(
            [YT_DLP_BIN, '--dump-json', '--flat-playlist', url],
            capture_output=True,
            text=True,
            check=True,
            encoding='utf-8'
        )
        # Si c'est une playlist, yt-dlp retourne un JSON par entrée.
        # Pour simplifier, nous allons juste vérifier si c'est une playlist.
        if 'entries' in result.stdout:
            # C'est une playlist, nous n'avons pas besoin de lister les formats maintenant,
            # car nous allons télécharger la playlist entière avec le format choisi.
            # Nous retournons juste un indicateur de playlist.
            return {'is_playlist': True}
        else:
            # C'est une vidéo simple.
            info = json.loads(result.stdout)
            return info
            
    except subprocess.CalledProcessError as e:
        print(f"\nERREUR: Impossible de récupérer les informations pour l'URL fournie.")
        print("Veuillez vérifier l'URL et votre connexion internet.")
        sys.exit(1)
    except json.JSONDecodeError:
        print("\nERREUR: Impossible de décoder la réponse de yt-dlp. L'URL est-elle valide ?")
        sys.exit(1)
    except FileNotFoundError:
        print(f"\nERREUR: L'exécutable '{YT_DLP_BIN}' n'a pas été trouvé.")
        sys.exit(1)


def download_video(url, quality_code):
    """Télécharge la vidéo avec le code de qualité spécifié."""
    print(f"\n--- Démarrage du téléchargement Vidéo (Qualité: {quality_code}) ---")
    # -f : format (code de qualité)
    # -o : template de nom de fichier
    # --merge-output-format : format de sortie pour le muxing (mp4 est un bon choix par défaut)
    command = [
        url,
        '-f', quality_code,
        '-o', '~/storage/shared/Download/Youtube_Downloads/%(title)s.%(ext)s',
        '--merge-output-format', 'mp4',
        '-c', # Reprendre les téléchargements interrompus
        '-w', # Ne pas écraser les fichiers existants
        '--concurrent-fragments', '4', # Téléchargement fragmenté en parallèle pour accélérer
        '--embed-metadata', # Intégrer les métadonnées
        '--restrict-filenames', # Noms de fichiers sûrs
        '--progress', # Afficher la barre de progression
        '--retries', 'infinite', # Robustesse pour faible réseau
        '--fragment-retries', 'infinite', # Robustesse pour faible réseau
        '--no-mtime', # Ne pas définir l'heure de modification du fichier
        '--no-warnings', # Supprimer les avertissements qui peuvent être confondus avec des erreurs
        '--force-overwrites', # Forcer l'écrasement des fichiers temporaires pour la reprise
        '--ignore-errors', # Ignorer les erreurs de téléchargement (utile pour les playlists) # Robustesse pour faible réseau
    ]
    run_yt_dlp(command)
    print("\n--- Téléchargement Vidéo Terminé ---")

def download_audio(url):
    """Télécharge l'audio en meilleure qualité (m4a ou mp3 si possible)."""
    print("\n--- Démarrage du téléchargement Audio (Meilleure Qualité) ---")
    # -x : extraire l'audio
    # --audio-format : format de l'audio (best par défaut, mais mp3 est souvent préféré)
    # --audio-quality : qualité de l'audio (0 est la meilleure)
    # -o : template de nom de fichier
    command = [
        url,
        '-x',
        '--audio-format', 'mp3', # mp3 est universel, sinon 'best' pour m4a/opus
        '--postprocessor-args', '-strict -2', # Ajout de cette option pour résoudre les problèmes de conversion ffmpeg
        '--audio-quality', '0',
        '-o', '~/storage/shared/Download/Youtube_Downloads/%(title)s.%(ext)s',
        '-c', # Reprendre les téléchargements interrompus
        '-w', # Ne pas écraser les fichiers existants
        '--concurrent-fragments', '4', # Téléchargement fragmenté en parallèle pour accélérer
        '--embed-metadata',
        '--restrict-filenames',
        '--progress',
        '--retries', 'infinite',
        '--fragment-retries', 'infinite', # Robustesse pour faible réseau
        '--no-mtime', # Ne pas définir l'heure de modification du fichier
        '--no-warnings', # Supprimer les avertissements qui peuvent être confondus avec des erreurs
        '--force-overwrites', # Forcer l'écrasement des fichiers temporaires pour la reprise
        '--ignore-errors', # Ignorer les erreurs de téléchargement (utile pour les playlists)
    ]
    run_yt_dlp(command)
    print("\n--- Téléchargement Audio Terminé ---")

def download_playlist(url, mode, quality_code=None):
    """Télécharge une playlist entière."""
    print(f"\n--- Démarrage du téléchargement de Playlist ({mode.upper()}) ---")
    
    base_command = [
        url,
        '-o', '~/storage/shared/Download/Youtube_Downloads/%(playlist)s/%(playlist_index)s - %(title)s.%(ext)s', # Organiser dans un dossier de playlist
        '-c', # Reprendre les téléchargements interrompus
        '-w', # Ne pas écraser les fichiers existants
        '--concurrent-fragments', '4', # Téléchargement fragmenté en parallèle pour accélérer
        '--embed-metadata',
        '--restrict-filenames',
        '--progress',
        '--retries', 'infinite',
        '--fragment-retries', 'infinite', # Robustesse pour faible réseau
        '--no-mtime', # Ne pas définir l'heure de modification du fichier
        '--no-warnings', # Supprimer les avertissements qui peuvent être confondus avec des erreurs
        '--force-overwrites', # Forcer l'écrasement des fichiers temporaires pour la reprise
        '--ignore-errors', # Ignorer les erreurs de téléchargement (utile pour les playlists)
    ]
    
    if mode == 'video':
        # Téléchargement vidéo avec le format spécifié
        command = base_command + [
            '-f', quality_code,
            '--merge-output-format', 'mp4',
        ]
    elif mode == 'audio':
        # Téléchargement audio en meilleure qualité
        command = base_command + [
            '-x',
            '--audio-format', 'mp3',
            '--audio-quality', '0',
        ]
    else:
        print("Mode de téléchargement non valide pour la playlist.")
        sys.exit(1)
        
    run_yt_dlp(command)
    print("\n--- Téléchargement de Playlist Terminé ---")


def main():
    """Fonction principale du script."""
    if len(sys.argv) < 2:
        url = input("Veuillez entrer l'URL de la vidéo ou de la playlist YouTube: ").strip()
        if not url:
            print("Aucune URL fournie. Annulation.")
            sys.exit(1)
    else:
        url = sys.argv[1]
    info = get_video_info(url)

    if info.get('is_playlist'):
        print(f"\nL'URL est une playlist. Que souhaitez-vous télécharger ?")
        print("1. Vidéo (avec sélection de qualité)")
        print("2. Audio (meilleure qualité)")
        
        choice = input("Votre choix (1 ou 2): ").strip()
        
        if choice == '1':
            mode = 'video'
            # Pour les playlists, nous allons offrir un choix de qualité simplifiée
            print("\nSélectionnez la qualité vidéo pour la playlist:")
            print("1. Meilleure qualité (souvent 1080p ou plus)")
            print("2. Bonne qualité (720p)")
            print("3. Qualité standard (480p)")
            
            q_choice = input("Votre choix (1, 2 ou 3): ").strip()
            
            if q_choice == '1':
                quality_code = 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best'
            elif q_choice == '2':
                quality_code = 'bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best'
            elif q_choice == '3':
                quality_code = 'bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best'
            else:
                print("Choix de qualité non valide. Utilisation de la meilleure qualité par défaut.")
                quality_code = 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best'
                
            download_playlist(url, mode, quality_code)
            
        elif choice == '2':
            mode = 'audio'
            download_playlist(url, mode)
            
        else:
            print("Choix non valide. Annulation.")
            sys.exit(1)
            
    else:
        # C'est une vidéo simple
        print(f"\nVidéo trouvée: {info.get('title', 'Titre inconnu')}")
        print("Que souhaitez-vous télécharger ?")
        print("1. Vidéo (avec sélection de qualité)")
        print("2. Audio (meilleure qualité)")
        
        choice = input("Votre choix (1 ou 2): ").strip()
        
        if choice == '1':
            # Afficher les options de qualité vidéo
            print("\nFormats vidéo disponibles (yt-dlp sélectionnera le meilleur codec pour la résolution):")
            
            # Filtrer et présenter les formats vidéo/audio combinés ou séparés (bestvideo+bestaudio)
            # Pour simplifier l'interface utilisateur, nous allons proposer des résolutions prédéfinies
            print("1. Meilleure qualité (souvent 1080p ou plus)")
            print("2. Bonne qualité (720p)")
            print("3. Qualité standard (480p)")
            
            q_choice = input("Votre choix (1, 2 ou 3): ").strip()
            
            # Codes de format yt-dlp pour muxer la meilleure vidéo sans audio avec le meilleur audio
            # Nous privilégions mp4 pour une meilleure compatibilité sur mobile
            if q_choice == '1':
                # bestvideo[ext=mp4]+bestaudio[ext=m4a] : Meilleure vidéo mp4 + meilleur audio m4a (muxing)
                # /best[ext=mp4] : ou le meilleur format mp4 combiné si le muxing n'est pas possible
                # /best : ou le meilleur format tout court
                quality_code = 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best'
            elif q_choice == '2':
                # Limiter la hauteur à 720p
                quality_code = 'bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best'
            elif q_choice == '3':
                # Limiter la hauteur à 480p
                quality_code = 'bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best'
            else:
                print("Choix non valide. Utilisation de la meilleure qualité par défaut.")
                quality_code = 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best'
                
            download_video(url, quality_code)
            
        elif choice == '2':
            download_audio(url)
            
        else:
            print("Choix non valide. Annulation.")
            sys.exit(1)

if __name__ == "__main__":
    # S'assurer que le script est exécutable
    os.chmod(sys.argv[0], 0o755)
    main()
