# Utiliser une image de base officielle Python
FROM python:3.9-slim

# Mettre à jour les paquets et installer ffmpeg et aria2c (au cas où)
RUN apt-get update && apt-get install -y ffmpeg aria2c

# Définir le répertoire de travail
WORKDIR /app

# Copier le fichier des dépendances et les installer
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copier le reste du code de l'application
COPY . .

# Exposer le port que Render utilisera
EXPOSE 10000

# La commande pour démarrer l'application
CMD ["gunicorn", "ytt4:app", "--bind", "0.0.0.0:10000", "--timeout", "300"]
