"""
data_store.py — Thread-safe persistence for purchase requests in purchases.json

This module provides thread-safe operations for managing purchase requests 
shared between the Flask webapp and Telegram bots.

WARNING: This implementation uses a file-based JSON store on the filesystem.
If the webapp and bots run on different machines, migrate to a shared database
(e.g., PostgreSQL, Redis) or network-accessible storage.
"""
import json
import os
import time
from threading import Lock
from typing import List, Dict, Optional, Any

# File path for purchases storage
PURCHASES_FILE = "purchases.json"

# Thread-safety lock for file operations
_purchases_lock = Lock()


def ensure_file():
    """
    Ensure purchases.json exists. Create it with an empty array if missing.
    """
    if not os.path.exists(PURCHASES_FILE):
        with _purchases_lock:
            # Double-check after acquiring lock
            if not os.path.exists(PURCHASES_FILE):
                with open(PURCHASES_FILE, "w", encoding="utf-8") as f:
                    json.dump([], f, indent=2)


def read_all() -> List[Dict[str, Any]]:
    """
    Read all purchases from purchases.json in a thread-safe manner.
    
    Returns:
        List of purchase dictionaries
    """
    ensure_file()
    with _purchases_lock:
        with open(PURCHASES_FILE, "r", encoding="utf-8") as f:
            return json.load(f)


def write_all(purchases: List[Dict[str, Any]]):
    """
    Write all purchases to purchases.json in a thread-safe manner.
    
    Args:
        purchases: List of purchase dictionaries to write
    """
    ensure_file()
    with _purchases_lock:
        with open(PURCHASES_FILE, "w", encoding="utf-8") as f:
            json.dump(purchases, f, indent=2, ensure_ascii=False)


def add_purchase(user_id: str, pack: str, source: str = "web") -> Dict[str, Any]:
    """
    Add a new purchase request to purchases.json.
    
    Args:
        user_id: User identifier (username for web, telegram ID for telegram)
        pack: Pack name/type (e.g., "10", "50", "100", "PREMIUM")
        source: Origin of request ("web" or "telegram")
    
    Returns:
        The created purchase entry
    """
    entry = {
        "user_id": str(user_id),
        "pack": str(pack),
        "status": "pending",
        "message": "",
        "seen": False,
        "source": source,
        "ts": int(time.time()),
        "ts_processed": None
    }
    
    purchases = read_all()
    purchases.append(entry)
    write_all(purchases)
    
    return entry


def find_latest_pending(user_id: str, pack: Optional[str] = None) -> Optional[Dict[str, Any]]:
    """
    Find the most recent pending purchase for a user.
    
    Args:
        user_id: User identifier
        pack: Optional pack filter. If None, matches any pack.
    
    Returns:
        The latest pending purchase entry, or None if not found
    """
    purchases = read_all()
    user_id_str = str(user_id)
    
    # Filter pending purchases for this user
    matching = [
        p for p in purchases
        if p.get("user_id") == user_id_str
        and p.get("status") == "pending"
        and (pack is None or p.get("pack") == str(pack))
    ]
    
    if not matching:
        return None
    
    # Return the one with the highest timestamp
    return max(matching, key=lambda x: x.get("ts", 0))


def update_status_for_entry(entry: Dict[str, Any], status: str, message: str = ""):
    """
    Update the status of a specific purchase entry.
    
    Args:
        entry: The purchase entry to update (must have same reference in the list)
        status: New status ("accepted", "refused", "off")
        message: Optional message to store with the status change
    """
    purchases = read_all()
    
    # Find the entry by matching key fields
    for i, p in enumerate(purchases):
        if (p.get("user_id") == entry.get("user_id") 
            and p.get("ts") == entry.get("ts")
            and p.get("pack") == entry.get("pack")):
            purchases[i]["status"] = status
            purchases[i]["message"] = message
            purchases[i]["ts_processed"] = int(time.time())
            purchases[i]["seen"] = False  # Reset seen flag so user is notified
            break
    
    write_all(purchases)


def get_unseen_for_user(user_id: str) -> List[Dict[str, Any]]:
    """
    Get all unseen purchase notifications for a specific user.
    
    Args:
        user_id: User identifier
    
    Returns:
        List of unseen purchase entries for this user
    """
    purchases = read_all()
    user_id_str = str(user_id)
    
    return [
        p for p in purchases
        if p.get("user_id") == user_id_str
        and not p.get("seen", False)
        and p.get("status") != "pending"  # Only show processed purchases
    ]


def mark_seen_for_user(user_id: str):
    """
    Mark all unseen purchases for a user as seen.
    
    Args:
        user_id: User identifier
    """
    purchases = read_all()
    user_id_str = str(user_id)
    
    modified = False
    for p in purchases:
        if (p.get("user_id") == user_id_str 
            and not p.get("seen", False)
            and p.get("status") != "pending"):
            p["seen"] = True
            modified = True
    
    if modified:
        write_all(purchases)


def mark_all_pending_as_off(message: str = "Achat indisponible"):
    """
    Mark all pending purchases as "off" (unavailable).
    Used for broadcast when purchases are temporarily unavailable.
    
    Args:
        message: Message to store with the status change
    
    Returns:
        Number of purchases marked as off
    """
    purchases = read_all()
    count = 0
    ts_now = int(time.time())
    
    for p in purchases:
        if p.get("status") == "pending":
            p["status"] = "off"
            p["message"] = message
            p["ts_processed"] = ts_now
            p["seen"] = False
            count += 1
    
    if count > 0:
        write_all(purchases)
    
    return count
