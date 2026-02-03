"""
notifications.py - Système de gestion des notifications
"""
import json
import os
import time
from threading import Lock
from datetime import datetime

NOTIFICATIONS_FILE = "notifications.json"
MAINTENANCE_ALERT_FILE = "maintenance_alert.json"
notifications_lock = Lock()

def load_notifications():
    """Charge les notifications depuis le fichier JSON"""
    if not os.path.exists(NOTIFICATIONS_FILE):
        return {}
    try:
        with open(NOTIFICATIONS_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    except:
        return {}

def save_notifications(data):
    """Sauvegarde les notifications dans le fichier JSON"""
    with notifications_lock:
        with open(NOTIFICATIONS_FILE, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

def get_user_notifications(user_id):
    """Récupère les notifications d'un utilisateur"""
    data = load_notifications()
    user_notifs = data.get(str(user_id), [])
    
    # Tri par timestamp décroissant (plus récentes en premier)
    user_notifs.sort(key=lambda x: x.get('timestamp', 0), reverse=True)
    
    # Formater les timestamps
    for notif in user_notifs:
        ts = notif.get('timestamp', 0)
        if ts:
            dt = datetime.fromtimestamp(ts)
            now = datetime.now()
            diff = now - dt
            
            if diff.seconds < 60:
                notif['time'] = "À l'instant"
            elif diff.seconds < 3600:
                minutes = diff.seconds // 60
                notif['time'] = f"Il y a {minutes} min"
            elif diff.seconds < 86400:
                hours = diff.seconds // 3600
                notif['time'] = f"Il y a {hours}h"
            else:
                days = diff.days
                notif['time'] = f"Il y a {days}j"
        else:
            notif['time'] = ""
    
    return user_notifs

def count_unread_notifications(user_id):
    """Compte les notifications non lues d'un utilisateur"""
    notifs = get_user_notifications(user_id)
    return sum(1 for n in notifs if not n.get('read', False))

def add_notification(user_id, title, message, notif_type='info'):
    """Ajoute une notification pour un utilisateur"""
    data = load_notifications()
    user_id = str(user_id)
    
    if user_id not in data:
        data[user_id] = []
    
    notif_id = int(time.time() * 1000)  # ID unique basé sur le timestamp
    
    notification = {
        'id': notif_id,
        'title': title,
        'message': message,
        'type': notif_type,
        'timestamp': int(time.time()),
        'read': False
    }
    
    data[user_id].append(notification)
    save_notifications(data)
    return notif_id

def delete_notification(user_id, notif_id):
    """Supprime une notification"""
    data = load_notifications()
    user_id = str(user_id)
    
    if user_id in data:
        data[user_id] = [n for n in data[user_id] if n.get('id') != notif_id]
        save_notifications(data)
        return True
    return False

def mark_as_read(user_id, notif_id):
    """Marque une notification comme lue"""
    data = load_notifications()
    user_id = str(user_id)
    
    if user_id in data:
        for notif in data[user_id]:
            if notif.get('id') == notif_id:
                notif['read'] = True
                save_notifications(data)
                return True
    return False

def mark_all_as_read(user_id):
    """Marque toutes les notifications comme lues"""
    data = load_notifications()
    user_id = str(user_id)
    
    if user_id in data:
        for notif in data[user_id]:
            notif['read'] = True
        save_notifications(data)
        return True
    return False

# Système d'alerte de maintenance
def set_maintenance_alert(message, alert_type='warning'):
    """Définit une alerte de maintenance globale"""
    alert = {
        'message': message,
        'type': alert_type,
        'timestamp': int(time.time())
    }
    with open(MAINTENANCE_ALERT_FILE, 'w', encoding='utf-8') as f:
        json.dump(alert, f, indent=2, ensure_ascii=False)

def get_maintenance_alert():
    """Récupère l'alerte de maintenance actuelle"""
    if not os.path.exists(MAINTENANCE_ALERT_FILE):
        return None
    try:
        with open(MAINTENANCE_ALERT_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    except:
        return None

def clear_maintenance_alert():
    """Efface l'alerte de maintenance"""
    if os.path.exists(MAINTENANCE_ALERT_FILE):
        os.remove(MAINTENANCE_ALERT_FILE)
