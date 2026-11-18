#!/usr/bin/env bash
# Arrêt propre : envoie « stop » à la console (sauvegarde le monde) puis attend.
screen -S bedrock -p 0 -X stuff "stop$(printf '\r')" 2>/dev/null || exit 0
for _ in $(seq 1 30); do
  pgrep -u "$(id -u)" -x bedrock_server >/dev/null || exit 0
  sleep 1
done
