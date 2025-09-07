from flask import Flask, render_template, request, Response
import subprocess
import re

app = Flask(__name__)

@app.route('/')
def index():
    """Affiche la page d'accueil."""
    return render_template('index.html')

@app.route('/download')
def download():
    """
    Récupère l'URL et le format, puis streame le fichier directement
    au navigateur de l'utilisateur.
    """
    url = request.args.get('url')
    format_choice = request.args.get('format')
    quality = request.args.get('quality', '720') # On récupère la qualité, 720p par défaut

    if not url:
        return "Erreur: URL manquante.", 400

    # On essaie de récupérer le titre pour nommer le fichier
    try:
        title_bytes = subprocess.check_output(["yt-dlp", "--print", "title", url])
        title = title_bytes.decode('utf-8', errors='ignore').strip()
        safe_title = re.sub(r'[\\/*?:"<>|]', "", title)
    except Exception:
        safe_title = "video_telechargee"

    commande = ["yt-dlp"]
    extension = "mp4"

    if format_choice == 'audio':
        # Pour l'audio, on prend le meilleur format disponible (souvent m4a)
        commande.extend(["-f", "bestaudio[ext=m4a]", "-o", "-"])
        extension = "m4a"
    else: # Vidéo
        # Pour la vidéo, on doit utiliser des formats qui n'ont pas besoin de fusion
        # car la fusion nécessite d'écrire sur le disque.
        # On prend le meilleur format mp4 jusqu'à la qualité choisie.
        format_string = f"best[ext=mp4][height<=?{quality}]/best[ext=mp4]"
        commande.extend(["-f", format_string, "-o", "-"])

    commande.append(url)
    process = subprocess.Popen(commande, stdout=subprocess.PIPE)

    # On prépare les en-têtes pour dire au navigateur de télécharger le fichier
    headers = {
        'Content-Disposition': f'attachment; filename="{safe_title}.{extension}"',
        'Content-Type': 'application/octet-stream'
    }
        
    # On renvoie le flux de données en temps réel
    return Response(process.stdout, headers=headers)

