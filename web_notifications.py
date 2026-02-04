"""
web_notifications.py — Module for handling web notifications for users without Telegram.
"""
import json
import os
import time
from threading import Lock

WEB_NOTIFICATIONS_FILE = "web_notifications.json"
web_notifications_lock = Lock()


def _load_web_notifications():
    """Internal: Load web notifications from file (must be called within lock)."""
    if not os.path.exists(WEB_NOTIFICATIONS_FILE):
        return {}
    try:
        with open(WEB_NOTIFICATIONS_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return {}


def _save_web_notifications(data):
    """Internal: Save web notifications to file (must be called within lock)."""
    with open(WEB_NOTIFICATIONS_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def add_web_notification(user_id, message, notif_type="admin_message"):
    """Add a notification for a web user."""
    with web_notifications_lock:
        data = _load_web_notifications()
        user_id = str(user_id)
        if user_id not in data:
            data[user_id] = []
        data[user_id].append({
            "type": notif_type,
            "message": message,
            "timestamp": int(time.time()),
            "read": False
        })
        _save_web_notifications(data)


def get_user_web_notifications(user_id):
    """Get notifications for a specific user."""
    with web_notifications_lock:
        data = _load_web_notifications()
        return data.get(str(user_id), [])


def delete_web_notification(user_id, index):
    """Delete a specific notification by index for a user.
    
    Returns True if deleted successfully, False otherwise.
    """
    with web_notifications_lock:
        data = _load_web_notifications()
        user_id = str(user_id)
        if user_id not in data:
            return False
        notifications = data[user_id]
        if not isinstance(index, int) or index < 0 or index >= len(notifications):
            return False
        del notifications[index]
        data[user_id] = notifications
        _save_web_notifications(data)
        return True
