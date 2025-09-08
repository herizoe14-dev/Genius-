# Étape 1: On part d'une image Python officielle et légère
FROM python:3.9-slim

# Étape 2: On met à jour le système et on installe ffmpeg de manière robuste
# L'option -qq est plus silencieuse, et on nettoie après pour garder l'image petite.
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends ffmpeg && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Étape 3: On définit le dossier de travail
WORKDIR /app

# Étape 4: On copie et on installe les dépendances Python
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Étape 5: On copie tout le reste de notre code
COPY . .

# Étape 6: On définit la commande pour démarrer notre serveur web sur le bon port
CMD ["gunicorn", "ytt4:app", "--bind", "0.0.0.0:10000"]
