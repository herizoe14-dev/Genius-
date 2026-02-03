import json
import os
import time
import logging
from threading import Lock

NOTIFICATIONS_FILE = "notifications.json"
_notifications_lock = Lock()


def _load_notifications():
    if not os.path.exists(NOTIFICATIONS_FILE):
        return {}
    try:
        with open(NOTIFICATIONS_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError:
        logging.warning("Fichier notifications.json invalide, données ignorées.")
        return {}


def _save_notifications(data):
    with open(NOTIFICATIONS_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=4, ensure_ascii=False)


def add_notification(user_id, message):
    with _notifications_lock:
        data = _load_notifications()
        key = str(user_id)
        items = data.get(key, [])
        items.append({"message": message, "ts": int(time.time())})
        data[key] = items
        _save_notifications(data)


def get_notifications(user_id):
    with _notifications_lock:
        data = _load_notifications()
        return data.get(str(user_id), [])


def clear_notifications(user_id):
    with _notifications_lock:
        data = _load_notifications()
        key = str(user_id)
        if key in data:
            data[key] = []
            _save_notifications(data)
