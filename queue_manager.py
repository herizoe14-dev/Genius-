import threading
import time

download_queue = []
queue_lock = threading.Lock()

def add_to_queue(user_id, url, mode, message_id, bot, chat_id):
    """Ajoute à la file avec les infos pour la mise à jour réelle."""
    with queue_lock:
        task = {
            "user_id": user_id,
            "url": url,
            "mode": mode,
            "message_id": message_id,
            "bot": bot,
            "chat_id": chat_id,
            "status": "waiting"
        }
        download_queue.append(task)
        return len(download_queue)

def update_queue_display():
    """Met à jour les messages de tous ceux qui attendent."""
    with queue_lock:
        for i, task in enumerate(download_queue):
            pos = i + 1
            if pos == 1:
                text = "🚀 **C'est votre tour !**\nPréparation du téléchargement..."
            else:
                text = f"⏳ **File d'attente...**\nVotre position : **{pos}** / {len(download_queue)}"
            
            try:
                task['bot'].edit_message_text(text, task['chat_id'], task['message_id'])
            except:
                pass # Évite les erreurs si le message est déjà identique

def remove_from_queue(user_id, url):
    """Supprime et lance la mise à jour pour les suivants."""
    with queue_lock:
        global download_queue
        download_queue = [t for t in download_queue if not (t['user_id'] == user_id and t['url'] == url)]
    # Mise à jour immédiate des positions pour les autres
    update_queue_display()

def get_queue_position(user_id, url):
    with queue_lock:
        for i, task in enumerate(download_queue):
            if task['user_id'] == user_id and task['url'] == url:
                return i + 1
    return 0