from flask import Flask, render_template, request, jsonify, Response
import subprocess
import json
import os
import re

app = Flask(__name__)

# Crée un sous-dossier pour les archives de reprise
ARCHIVE_FOLDER = os.path.join(os.path.dirname(__file__), "archives")
if not os.path.exists(ARCHIVE_FOLDER):
    os.makedirs(ARCHIVE_FOLDER, exist_ok=True)

# Définit le dossier de téléchargement final dans le stockage partagé du téléphone
DOWNLOAD_FOLDER = os.path.join(os.path.expanduser("~"), "storage", "downloads", "YouTube-DL")
if not os.path.exists(DOWNLOAD_FOLDER):
    os.makedirs(DOWNLOAD_FOLDER, exist_ok=True)

@app.route('/', methods=['GET', 'POST'])
def index():
    """Affiche la page d'accueil."""
    return render_template('index.html')

@app.route('/analyze', methods=['POST'])
def analyze():
    """Analyse l'URL pour obtenir le titre et le nombre de vidéos."""
    url = request.get_json().get('url')
    if not url:
        return jsonify({"error": "URL manquante"}), 400
    try:
        cmd = ["yt-dlp", "--flat-playlist", "-j", url]
        process = subprocess.run(cmd, capture_output=True, text=True, check=True)
        videos_info = [json.loads(line) for line in process.stdout.strip().split('\n')]
        if not videos_info:
            return jsonify({"error": "Aucune vidéo trouvée pour cette URL."}), 404
        title = videos_info[0].get('playlist_title') or videos_info[0].get('title', 'Titre inconnu')
        video_count = len(videos_info)
        return jsonify({"title": title, "video_count": video_count})
    except (subprocess.CalledProcessError, json.JSONDecodeError) as e:
        return jsonify({"error": f"Erreur lors de l'analyse du lien : {e}"}), 500

@app.route('/download', methods=['POST'])
def download():
    """Gère le téléchargement avec reprise et accélération via aria2c."""
    data = request.get_json()
    url = data.get('url')
    format_choice = data.get('format')
    quality = data.get('quality', '720')

    def generate_output():
        est_playlist = 'list=' in url
        
        # Commande de base avec accélération aria2c
        commande = [
            "yt-dlp",
            "--newline",
            "--ignore-errors",
            "--concurrent-fragments", "16",
            # Arguments pour aria2c : 16 connexions/segments pour maximiser la vitesse
            "--retries", "infinite", 
        ]

        # Ajout de la logique de reprise pour les playlists
        if est_playlist:
            match = re.search(r"list=([\w-]+)", url)
            if match:
                playlist_id = match.group(1)
                archive_file = os.path.join(ARCHIVE_FOLDER, f"{playlist_id}.txt")
                commande.extend(["--download-archive", archive_file])
                yield f"Journal de reprise activé : {archive_file}\n"
                yield "Les vidéos déjà téléchargées seront ignorées.\n\n"
            else:
                yield "AVERTISSEMENT : Impossible de trouver l'ID de la playlist, la reprise ne sera pas activée.\n\n"

        # Configuration du format (audio ou vidéo)
        if format_choice == 'audio':
            output_template = os.path.join(DOWNLOAD_FOLDER, "%(playlist_title)s/%(playlist_index)s - %(title)s.%(ext)s" if est_playlist else "%(title)s.%(ext)s")
            commande.extend(["-x", "--audio-format", "mp3", "-o", output_template])
        else: # Vidéo
            format_string = f"bestvideo[height<=?{quality}]+bestaudio/best[height<=?{quality}]"
            video_template = os.path.join(DOWNLOAD_FOLDER, "%(playlist_title)s/%(playlist_index)s - %(title)s - %(resolution)s.%(ext)s" if est_playlist else "%(title)s - %(resolution)s.%(ext)s")
            commande.extend(["-f", format_string, "--merge-output-format", "mp4", "-o", video_template])
        
        commande.append(url)
        
        yield f"🚀 Téléchargement accéléré avec 16 fragments concurrents et reprise infinie !\n\n"

        # Exécution et streaming de la sortie
        try:
            process = subprocess.Popen(commande, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, bufsize=1)
            for line in iter(process.stdout.readline, ''):
                yield line
            process.stdout.close()
            process.wait()
            yield f"\n✅ Opération terminée ! Fichiers dans : {DOWNLOAD_FOLDER}\n"
        except Exception as e:
            yield f"\n❌ Erreur critique : {e}\n"

    return Response(generate_output(), mimetype='text/plain')

# if __name__ == '__main__':
#     app.run(host='0.0.0.0', port=5000)
