"""
web_notifications.py — Module for handling web notifications for users without Telegram.
"""
import json
import os
import time
from threading import Lock

WEB_NOTIFICATIONS_FILE = "web_notifications.json"
web_notifications_lock = Lock()


def load_web_notifications():
    """Load web notifications from file."""
    if not os.path.exists(WEB_NOTIFICATIONS_FILE):
        return {}
    try:
        with open(WEB_NOTIFICATIONS_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return {}


def save_web_notifications(data):
    """Save web notifications to file."""
    with open(WEB_NOTIFICATIONS_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def add_web_notification(user_id, message, notif_type="admin_message"):
    """Add a notification for a web user."""
    with web_notifications_lock:
        data = load_web_notifications()
        user_id = str(user_id)
        if user_id not in data:
            data[user_id] = []
        data[user_id].append({
            "type": notif_type,
            "message": message,
            "timestamp": int(time.time()),
            "read": False
        })
        save_web_notifications(data)


def get_user_web_notifications(user_id):
    """Get notifications for a specific user."""
    with web_notifications_lock:
        data = load_web_notifications()
        return data.get(str(user_id), [])
