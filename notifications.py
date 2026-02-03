import json
import os
import time
from datetime import datetime
from threading import Lock

NOTIFICATIONS_FILE = "web_notifications.json"
MAX_NOTIFICATIONS = 50
_NOTIFICATION_LOCK = Lock()
TIMESTAMP_FORMAT = "%d/%m/%Y %H:%M"


def _load_notifications():
    if not os.path.exists(NOTIFICATIONS_FILE):
        return {}
    try:
        with open(NOTIFICATIONS_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, dict) else {}
    except (json.JSONDecodeError, OSError):
        return {}


def _save_notifications(data):
    tmp_path = f"{NOTIFICATIONS_FILE}.tmp"
    try:
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        os.replace(tmp_path, NOTIFICATIONS_FILE)
    except OSError:
        try:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
        except OSError:
            pass


def add_notification(user_id, message, level="info"):
    """Store a notification for a user (levels: info, success, warning, danger)."""
    if not user_id or not message:
        return False
    with _NOTIFICATION_LOCK:
        data = _load_notifications()
        user_key = str(user_id)
        notifications = data.get(user_key, [])
        if not isinstance(notifications, list):
            notifications = []
        if len(notifications) >= MAX_NOTIFICATIONS:
            notifications = notifications[-MAX_NOTIFICATIONS + 1:]
        notifications.append(
            {
                "message": message,
                "timestamp": int(time.time()),
                "level": level,
            }
        )
        data[user_key] = notifications
        _save_notifications(data)
    return True


def get_notifications(user_id, limit=5):
    """Return newest notifications for a user, sorted by most recent first."""
    if not user_id:
        return []
    with _NOTIFICATION_LOCK:
        data = _load_notifications()
        notifications = data.get(str(user_id), [])
        if not isinstance(notifications, list):
            return []
        notifications = sorted(
            notifications, key=lambda item: item.get("timestamp", 0), reverse=True
        )
        if limit:
            notifications = notifications[:limit]
        for notif in notifications:
            notif["time"] = format_timestamp(notif.get("timestamp"))
        return notifications


def format_timestamp(timestamp):
    if not timestamp:
        return ""
    try:
        return datetime.fromtimestamp(int(timestamp)).strftime(TIMESTAMP_FORMAT)
    except (ValueError, OSError):
        return ""
