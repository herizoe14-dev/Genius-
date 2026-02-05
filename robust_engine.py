import random

def get_bypass_config():
    """Retourne la configuration de contournement la plus puissante actuelle."""
    
    user_agents = [
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Android 14; Mobile; rv:122.0) Gecko/122.0 Firefox/122.0',
        'Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1'
    ]

    return {
        'user_agent': random.choice(user_agents),
        'referer': 'https://www.youtube.com/',
        'http_headers': {
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
            'Accept-Language': 'fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7',
            'Sec-Fetch-Mode': 'navigate',
            'Sec-Fetch-Dest': 'document',
            'Sec-Fetch-Site': 'none',
            'Upgrade-Insecure-Requests': '1',
        },
        'nocheckcertificate': True,
        'geo_bypass': True,
        # Use web client to get higher quality uncompressed streams
        # Android/iOS clients return compressed lower bitrate streams
        'extractor_args': {'youtube': {'player_client': ['web', 'android']}},
        # GESTION DES TIMEOUTS (Évite que Termux ne bloque)
        'socket_timeout': 60,
        'retries': 10,
        'fragment_retries': 10,
    }