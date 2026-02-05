import yt_dlp
import os
import re
import zipfile
from robust_engine import get_bypass_config

def clean_progress_text(text):
    ansi_escape = re.compile(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-a-z])')
    return ansi_escape.sub('', text)

def progress_hook(d, bot, chat_id, message_id):
    if d['status'] == 'downloading':
        try:
            p_str = clean_progress_text(d.get('_percent_str', '0%'))
            p_float = float(p_str.replace('%', '').strip())
            speed = clean_progress_text(d.get('_speed_str', 'N/A'))
            eta = clean_progress_text(d.get('_eta_str', 'N/A'))
            filled = int(p_float // 10)
            bar = "▓" * filled + "░" * (10 - filled)
            msg = (f"📥 **Téléchargement Cloud...**\n\n"
                   f"[{bar}] {p_float:.1f}%\n"
                   f"⚡ {speed} | ⏳ {eta}")
            bot.edit_message_text(msg, chat_id, message_id, parse_mode="Markdown")
        except: pass

def download_content(url, mode, bot=None, chat_id=None, message_id=None):
    """
    Download content from YouTube using standard yt-dlp quality.
    
    Args:
        url: YouTube URL
        mode: 'mp3' or 'mp4'
        bot, chat_id, message_id: For Telegram progress updates
    
    Returns:
        tuple: (filename, info_dict) - filename and video info for display
    """
    download_path = "downloads"
    if not os.path.exists(download_path): os.makedirs(download_path)
    ydl_opts = get_bypass_config()
    
    # Standard yt-dlp format selection (simple and reliable)
    if mode == 'mp3':
        ydl_opts.update({
            'format': 'bestaudio/best',
            'postprocessors': [{
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'mp3',
                'preferredquality': '192',
            }],
        })
    else:
        # Standard best quality MP4
        ydl_opts.update({
            'format': 'best[ext=mp4]/best',
        })
    
    ydl_opts.update({
        'outtmpl': f'{download_path}/%(title)s.%(ext)s',
        'noplaylist': True, 'quiet': True, 'no_color': True,
    })
    
    if bot and chat_id and message_id:
        ydl_opts['progress_hooks'] = [lambda d: progress_hook(d, bot, chat_id, message_id)]

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=True)
        filename = ydl.prepare_filename(info)
        
        # Extract useful info for display
        download_info = {
            'title': info.get('title', 'Unknown'),
            'duration': info.get('duration', 0),
            'uploader': info.get('uploader', 'Unknown'),
            'view_count': info.get('view_count', 0),
            'resolution': info.get('resolution', 'N/A'),
            'filesize': info.get('filesize') or info.get('filesize_approx', 0),
        }
        
        if mode == 'mp3':
            # FFmpeg postprocessor changes extension to .mp3
            new_name = filename.rsplit('.', 1)[0] + '.mp3'
            if os.path.exists(new_name):
                return new_name, download_info
            # Fallback: rename if not auto-converted
            if os.path.exists(filename):
                os.rename(filename, new_name)
                return new_name, download_info
            # If neither exists, raise an error
            raise FileNotFoundError(f"Fichier audio non trouvé après conversion: {new_name}")
        return filename, download_info

def split_file(file_path, chunk_size=45 * 1024 * 1024):
    """Compresse en ZIP puis découpe en parties .zip.001, .zip.002..."""
    parts = []
    zip_filename = file_path + ".zip"
    
    # 1. Création de l'archive ZIP réelle
    with zipfile.ZipFile(zip_filename, 'w', zipfile.ZIP_STORED) as z:
        z.write(file_path, os.path.basename(file_path))
    
    # 2. Découpage binaire du fichier ZIP
    with open(zip_filename, 'rb') as f:
        part_num = 1
        while True:
            chunk = f.read(chunk_size)
            if not chunk: break
            part_name = f"{zip_filename}.{part_num:03d}" # Exemple: video.mp4.zip.001
            with open(part_name, 'wb') as p:
                p.write(chunk)
            parts.append(part_name)
            part_num += 1
            
    # Nettoyage du ZIP temporaire et du fichier original
    if os.path.exists(zip_filename): os.remove(zip_filename)
    if os.path.exists(file_path): os.remove(file_path)
    
    return parts