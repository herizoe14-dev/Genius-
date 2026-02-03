"""
data_store.py — Gestion thread-safe de purchases.json

Ce module gère la persistance des demandes d'achat dans un fichier JSON partagé
entre la webapp Flask et les bots Telegram.

Structure d'une entrée dans purchases.json :
{
  "id": "unique_id",
  "user_id": "username or telegram_id",
  "pack": "10|50|100|PREMIUM",
  "source": "web|telegram",
  "status": "pending|accepted|rejected|off",
  "timestamp": 1234567890,
  "seen": false,
  "response_message": "Message d'acceptation/refus/indisponible"
}
"""
import json
import os
import time
import threading
from typing import List, Dict, Optional

PURCHASES_FILE = "purchases.json"
_lock = threading.Lock()

def _load_purchases() -> List[Dict]:
    """Charge les achats depuis le fichier JSON."""
    if not os.path.exists(PURCHASES_FILE):
        return []
    try:
        with open(PURCHASES_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
            if isinstance(data, list):
                return data
            return []
    except Exception:
        return []

def _save_purchases(purchases: List[Dict]) -> None:
    """Sauvegarde les achats dans le fichier JSON."""
    with open(PURCHASES_FILE, "w", encoding="utf-8") as f:
        json.dump(purchases, f, ensure_ascii=False, indent=2)

def add_purchase(user_id: str, pack: str, source: str = "web") -> str:
    """
    Ajoute une nouvelle demande d'achat.
    
    Args:
        user_id: Identifiant de l'utilisateur
        pack: Pack acheté (10, 50, 100, PREMIUM, etc.)
        source: Source de la demande (web ou telegram)
    
    Returns:
        ID unique de la demande créée
    """
    with _lock:
        purchases = _load_purchases()
        
        # Génère un ID unique basé sur le timestamp et un compteur
        purchase_id = f"{user_id}_{int(time.time() * 1000)}"
        
        entry = {
            "id": purchase_id,
            "user_id": str(user_id),
            "pack": str(pack),
            "source": source,
            "status": "pending",
            "timestamp": int(time.time()),
            "seen": False,
            "response_message": ""
        }
        
        purchases.append(entry)
        _save_purchases(purchases)
        return purchase_id

def find_latest_pending(user_id: str) -> Optional[Dict]:
    """
    Trouve la dernière demande pending pour un utilisateur.
    
    Args:
        user_id: Identifiant de l'utilisateur
    
    Returns:
        Dict de la demande ou None si aucune demande pending
    """
    with _lock:
        purchases = _load_purchases()
        # Cherche la dernière demande pending pour cet utilisateur
        for entry in reversed(purchases):
            if entry.get("user_id") == str(user_id) and entry.get("status") == "pending":
                return entry
        return None

def update_status_for_entry(entry_id: str, new_status: str, message: str = "") -> bool:
    """
    Met à jour le statut d'une entrée spécifique.
    
    Args:
        entry_id: ID de l'entrée à mettre à jour
        new_status: Nouveau statut (accepted, rejected, off)
        message: Message de réponse associé
    
    Returns:
        True si la mise à jour a réussi, False sinon
    """
    with _lock:
        purchases = _load_purchases()
        updated = False
        
        for entry in purchases:
            if entry.get("id") == entry_id:
                entry["status"] = new_status
                entry["response_message"] = message
                entry["seen"] = False  # Marque comme non vu pour notification
                updated = True
                break
        
        if updated:
            _save_purchases(purchases)
        return updated

def get_unseen_for_user(user_id: str) -> List[Dict]:
    """
    Récupère toutes les demandes non vues pour un utilisateur.
    
    Args:
        user_id: Identifiant de l'utilisateur
    
    Returns:
        Liste des demandes non vues (statut != pending et seen = False)
    """
    with _lock:
        purchases = _load_purchases()
        unseen = []
        
        for entry in purchases:
            if (entry.get("user_id") == str(user_id) and 
                not entry.get("seen", False) and 
                entry.get("status") != "pending"):
                unseen.append(entry)
        
        return unseen

def mark_seen_for_user(user_id: str, entry_ids: List[str] = None) -> int:
    """
    Marque des demandes comme vues pour un utilisateur.
    
    Args:
        user_id: Identifiant de l'utilisateur
        entry_ids: Liste des IDs à marquer (None = toutes les non vues)
    
    Returns:
        Nombre d'entrées marquées comme vues
    """
    with _lock:
        purchases = _load_purchases()
        marked_count = 0
        
        for entry in purchases:
            if entry.get("user_id") == str(user_id):
                # Si entry_ids est spécifié, ne marquer que ceux-là
                if entry_ids is None or entry.get("id") in entry_ids:
                    if not entry.get("seen", False) and entry.get("status") != "pending":
                        entry["seen"] = True
                        marked_count += 1
        
        if marked_count > 0:
            _save_purchases(purchases)
        
        return marked_count

def mark_all_pending_as_off(message: str = "Achats temporairement indisponibles") -> int:
    """
    Marque toutes les demandes pending comme 'off' (indisponible).
    Utilisé lors d'un broadcast admin "Achat indisponible".
    
    Args:
        message: Message à associer aux demandes marquées off
    
    Returns:
        Nombre de demandes marquées comme off
    """
    with _lock:
        purchases = _load_purchases()
        marked_count = 0
        
        for entry in purchases:
            if entry.get("status") == "pending":
                entry["status"] = "off"
                entry["response_message"] = message
                entry["seen"] = False  # Non vu pour notification
                marked_count += 1
        
        if marked_count > 0:
            _save_purchases(purchases)
        
        return marked_count

def get_all_user_ids() -> List[str]:
    """
    Récupère tous les user_ids uniques dans purchases.json.
    
    Returns:
        Liste des user_ids uniques
    """
    with _lock:
        purchases = _load_purchases()
        user_ids = set()
        for entry in purchases:
            user_ids.add(entry.get("user_id"))
        return list(user_ids)
