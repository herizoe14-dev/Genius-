from flask import Flask, render_template, request, jsonify, send_from_directory
import subprocess
import os
import re
import uuid
from threading import Thread
import time

app = Flask(__name__)

# --- CONFIGURATION POUR RENDER (PLAN GRATUIT) ---
# On utilise /tmp, un dossier qui existe en RAM sur les systèmes Linux.
RAM_DISK_PATH = "/tmp/downloads"
os.makedirs(RAM_DISK_PATH, exist_ok=True)

# Dictionnaire pour suivre l'état des tâches
tasks = {}

def cleanup_old_files():
    """Nettoie les fichiers de plus de 30 minutes pour libérer la RAM."""
    now = time.time()
    for filename in os.listdir(RAM_DISK_PATH):
        file_path = os.path.join(RAM_DISK_PATH, filename)
        if os.path.getmtime(file_path) < now - 1800: # 1800 secondes = 30 minutes
            os.remove(file_path)

def run_download_task(task_id, url, format_choice, quality):
    """S'exécute en arrière-plan pour télécharger le fichier dans la RAM."""
    tasks[task_id] = {"status": "running", "progress": "Démarrage..."}
        
    try:
        # Nettoyer les vieux fichiers avant de commencer un nouveau téléchargement
        cleanup_old_files()

        output_template = os.path.join(RAM_DISK_PATH, f"{task_id}.%(ext)s")
        commande = ["yt-dlp", "--ffmpeg-location", "/usr/bin/ffmpeg"]

        if format_choice == 'audio':
            commande.extend(["-x", "--audio-format", "mp3", "-o", output_template])
        else:
            format_string = f"bestvideo[height<=?{quality}]+bestaudio/best[height<=?{quality}]"
            commande.extend(["-f", format_string, "--merge-output-format", "mp4", "-o", output_template])
            
        commande.append(url)
            
        process = subprocess.run(commande, capture_output=True, text=True, timeout=300) # Timeout de 5 min

        if process.returncode == 0:
            created_file = next((f for f in os.listdir(RAM_DISK_PATH) if f.startswith(task_id)), None)
            if created_file:
                tasks[task_id]["status"] = "completed"
                tasks[task_id]["filename"] = created_file
            else:
                tasks[task_id]["status"] = "error"
                tasks[task_id]["progress"] = "Fichier non trouvé après téléchargement."
        else:
            tasks[task_id]["status"] = "error"
            tasks[task_id]["progress"] = (process.stderr or process.stdout)[-500:] # On garde les 500 derniers caractères de l'erreur

    except subprocess.TimeoutExpired:
        tasks[task_id]["status"] = "error"
        tasks[task_id]["progress"] = "Le téléchargement a pris trop de temps (plus de 5 minutes)."
    except Exception as e:
        tasks[task_id]["status"] = "error"
        tasks[task_id]["progress"] = str(e)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/start-download', methods=['POST'])
def start_download():
    data = request.get_json()
    task_id = str(uuid.uuid4())
    thread = Thread(target=run_download_task, args=(task_id, data.get('url'), data.get('format'), data.get('quality')))
    thread.start()
    return jsonify({"task_id": task_id})

@app.route('/task-status/<task_id>')
def task_status(task_id):
    return jsonify(tasks.get(task_id, {"status": "not_found"}))

@app.route('/download-file/<filename>')
def download_file(filename):
    return send_from_directory(RAM_DISK_PATH, filename, as_attachment=True)

