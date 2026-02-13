"""
Email utility module for sending OTP codes and notifications.
"""
import os
import random
import string
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from datetime import datetime, timedelta
import json
import config

# OTP storage file
OTP_FILE = "otp_data.json"
OTP_VALIDITY_MINUTES = 10  # OTP valid for 10 minutes

def generate_otp(length=6):
    """Generate a random OTP code."""
    return ''.join(random.choices(string.digits, k=length))

def load_otp_data():
    """Load OTP data from file."""
    if not os.path.exists(OTP_FILE):
        return {}
    try:
        with open(OTP_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}

def save_otp_data(data):
    """Save OTP data to file."""
    with open(OTP_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=4, ensure_ascii=False)

def store_otp(email, otp):
    """Store OTP for email with expiration time."""
    data = load_otp_data()
    expiry = (datetime.utcnow() + timedelta(minutes=OTP_VALIDITY_MINUTES)).isoformat()
    data[email] = {
        "otp": otp,
        "expiry": expiry,
        "attempts": 0
    }
    save_otp_data(data)

def verify_otp(email, otp):
    """
    Verify OTP for email.
    Returns (True, "") if valid, (False, reason) if invalid.
    """
    data = load_otp_data()
    
    if email not in data:
        return False, "Code de vérification non trouvé ou expiré."
    
    stored = data[email]
    
    # Check expiry
    expiry = datetime.fromisoformat(stored["expiry"])
    if datetime.utcnow() > expiry:
        del data[email]
        save_otp_data(data)
        return False, "Code de vérification expiré. Demandez-en un nouveau."
    
    # Check attempts
    if stored["attempts"] >= 3:
        del data[email]
        save_otp_data(data)
        return False, "Trop de tentatives. Demandez un nouveau code."
    
    # Check OTP
    if stored["otp"] != otp:
        stored["attempts"] += 1
        save_otp_data(data)
        attempts_left = 3 - stored["attempts"]
        return False, f"Code incorrect. Tentatives restantes : {attempts_left}"
    
    # Success - remove OTP
    del data[email]
    save_otp_data(data)
    return True, ""

def clear_otp(email):
    """Clear OTP for email."""
    data = load_otp_data()
    if email in data:
        del data[email]
        save_otp_data(data)

def send_otp_email(email, otp):
    """
    Send OTP via email.
    Returns (True, "") if sent successfully, (False, reason) if failed.
    """
    # Check if email is configured
    if not config.MAIL_USERNAME or not config.MAIL_PASSWORD:
        return False, "Configuration email manquante sur le serveur."
    
    try:
        # Create message
        msg = MIMEMultipart('alternative')
        msg['Subject'] = 'Code de vérification Genius Bot'
        msg['From'] = config.MAIL_USERNAME
        msg['To'] = email
        
        # Create plain text and HTML versions
        text = f"""
Bonjour,

Voici votre code de vérification pour Genius Bot :

{otp}

Ce code est valide pendant {OTP_VALIDITY_MINUTES} minutes.

Si vous n'avez pas demandé ce code, ignorez ce message.

Cordialement,
L'équipe Genius Bot
"""
        
        html = f"""
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <style>
        body {{ font-family: Arial, sans-serif; line-height: 1.6; color: #333; }}
        .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
        .header {{ background: linear-gradient(135deg, #00d17a, #00ff9b); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }}
        .content {{ background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }}
        .otp-code {{ background: white; border: 2px solid #00d17a; padding: 20px; text-align: center; font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #00d17a; margin: 20px 0; border-radius: 8px; }}
        .footer {{ text-align: center; margin-top: 20px; font-size: 12px; color: #999; }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🎯 Genius Bot</h1>
            <p>Code de Vérification</p>
        </div>
        <div class="content">
            <p>Bonjour,</p>
            <p>Voici votre code de vérification pour Genius Bot :</p>
            <div class="otp-code">{otp}</div>
            <p>Ce code est valide pendant <strong>{OTP_VALIDITY_MINUTES} minutes</strong>.</p>
            <p>Si vous n'avez pas demandé ce code, ignorez ce message.</p>
            <p>Cordialement,<br>L'équipe Genius Bot</p>
        </div>
        <div class="footer">
            <p>© 2024 Genius Bot - Téléchargement YouTube Premium</p>
        </div>
    </div>
</body>
</html>
"""
        
        # Attach parts
        part1 = MIMEText(text, 'plain')
        part2 = MIMEText(html, 'html')
        msg.attach(part1)
        msg.attach(part2)
        
        # Send email
        with smtplib.SMTP(config.MAIL_SERVER, config.MAIL_PORT) as server:
            server.starttls()
            server.login(config.MAIL_USERNAME, config.MAIL_PASSWORD)
            server.send_message(msg)
        
        return True, ""
        
    except smtplib.SMTPAuthenticationError:
        return False, "Erreur d'authentification email. Contactez l'administrateur."
    except smtplib.SMTPException as e:
        return False, f"Erreur d'envoi email : {str(e)}"
    except Exception as e:
        return False, f"Erreur inattendue : {str(e)}"

def is_valid_email(email):
    """Basic email validation."""
    import re
    pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
    return bool(re.match(pattern, email))
