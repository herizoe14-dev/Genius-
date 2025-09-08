from flask import Flask, render_template, request, Response
import subprocess
import re

app = Flask(__name__)

@app.route('/')
def index():
    """Affiche la page d'accueil simple."""
    return render_template('index.html')

@app.route('/download')
def download():
    """
    Prend une URL, la streame directement au navigateur en essayant
    d'être discret pour éviter le blocage de YouTube.
    """
    url = request.args.get('url')
    if not url:
        return "Erreur: URL manquante.", 400

    try:
        # On essaie de deviner un nom de fichier pour le téléchargement
        # On met un timeout court pour ne pas bloquer si ça échoue.
        try:
            title_bytes = subprocess.check_output(["yt-dlp", "--print", "title", url], timeout=5)
            # On nettoie le titre pour qu'il soit un nom de fichier valide
            safe_title = re.sub(r'[\\/*?:"<>|]', "", title_bytes.decode('utf-8', 'ignore').strip())
        except:
            # Si on n'arrive pas à obtenir le titre, on utilise un nom par défaut
            safe_title = "video"

        # C'est la commande la plus importante.
        # On demande à yt-dlp le meilleur format MP4 qui contient déjà l'audio et la vidéo.
        # On ajoute des options pour paraître moins comme un robot.
        commande = [
            "yt-dlp",
            "--no-check-certificate",  # Ignore les erreurs de certificat SSL
            "-r", "3M",                # Limite la vitesse à 3 Mo/s pour être moins suspect
            "-f", "best[ext=mp4]",     # Demande le meilleur format mp4 pré-fusionné
            "-o", "-",                 # Envoie le résultat vers la sortie standard (pour le streaming)
            url                        # L'URL de la vidéo
        ]
        
        # On lance le processus en arrière-plan
        process = subprocess.Popen(commande, stdout=subprocess.PIPE)
        
        # On prépare les en-têtes pour dire au navigateur de télécharger le fichier
        headers = {
            'Content-Disposition': f'attachment; filename="{safe_title}.mp4"',
            'Content-Type': 'video/mp4'
        }
        
        # On renvoie le flux de données de la vidéo en temps réel au navigateur
        return Response(process.stdout, headers=headers)

    except Exception as e:
        # Si une erreur majeure se produit, on l'affiche
        return f"Une erreur majeure est survenue: {str(e)}", 500

