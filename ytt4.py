from flask import Flask, render_template, request, Response
import subprocess
import re

app = Flask(__name__)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/download')
def download():
    url = request.args.get('url')
    if not url:
        return "Erreur: URL manquante.", 400

    try:
        # On demande à yt-dlp le meilleur format MP4 qui contient déjà l'audio et la vidéo
        # C'est la clé pour éviter d'utiliser ffmpeg
        commande = ["yt-dlp", "-f", "best[ext=mp4]", "-o", "-", url]
        process = subprocess.Popen(commande, stdout=subprocess.PIPE)
            
        # On essaie de deviner un nom de fichier
        try:
            title_bytes = subprocess.check_output(["yt-dlp", "--print", "title", url], timeout=5)
            safe_title = re.sub(r'[\\/*?:"<>|]', "", title_bytes.decode('utf-8', 'ignore').strip())
        except:
            safe_title = "video"

        headers = {
            'Content-Disposition': f'attachment; filename="{safe_title}.mp4"',
            'Content-Type': 'video/mp4'
        }
            
        return Response(process.stdout, headers=headers)
    except Exception as e:
        return f"Une erreur majeure est survenue: {str(e)}", 500
