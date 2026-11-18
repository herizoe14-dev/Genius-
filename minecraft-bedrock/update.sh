#!/usr/bin/env bash
# Télécharge / met à jour Bedrock Dedicated Server en conservant les mondes
# et la configuration. Usage : bedrock-update [--force]
set -euo pipefail

MC_DIR="${MC_DIR:-/opt/bedrock}"
MC_USER="${MC_USER:-minecraft}"
UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
FORCE=0; [[ "${1:-}" == "--force" ]] && FORCE=1

ts() { date '+%F %T'; }

# 1) API officielle Mojang ; 2) repli : page de téléchargement minecraft.net
URL="$(curl -fsSL -A "$UA" --max-time 30 \
        https://net-secondary.web.minecraft-services.net/api/v1.0/download/links 2>/dev/null \
      | jq -r '.result.links[] | select(.downloadType=="serverBedrockLinux") | .downloadUrl' 2>/dev/null || true)"

if [[ -z "$URL" || "$URL" == "null" ]]; then
  URL="$(curl -fsSL -A "$UA" --max-time 30 https://www.minecraft.net/en-us/download/server/bedrock 2>/dev/null \
        | grep -oE 'https://[^"]+/bin-linux/bedrock-server-[0-9.]+\.zip' | head -n1 || true)"
fi
[[ -n "$URL" ]] || { echo "$(ts) ERREUR : impossible de trouver l'URL de téléchargement."; exit 1; }

NEW_VER="$(grep -oE '[0-9]+(\.[0-9]+)+' <<<"${URL##*/}" | head -n1)"
CUR_VER="$(cat "$MC_DIR/.version" 2>/dev/null || echo none)"

if [[ $FORCE -eq 0 && "$NEW_VER" == "$CUR_VER" ]]; then
  echo "$(ts) Déjà à jour ($CUR_VER)."
  exit 0
fi
echo "$(ts) Mise à jour : $CUR_VER -> $NEW_VER"

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
curl -fSL -A "$UA" --max-time 600 -o "$TMP/bds.zip" "$URL"

WAS_RUNNING=0
if [[ -z "${NO_RESTART:-}" ]] && systemctl is-active --quiet bedrock; then
  WAS_RUNNING=1
  systemctl stop bedrock
fi

mkdir -p "$MC_DIR"
# Sauvegarde des mondes avant la mise à jour
if [[ -d "$MC_DIR/worlds" ]]; then
  mkdir -p "$MC_DIR/backups"
  tar -czf "$MC_DIR/backups/worlds-$(date +%F-%H%M).tar.gz" -C "$MC_DIR" worlds
  ls -1t "$MC_DIR"/backups/worlds-*.tar.gz | tail -n +8 | xargs -r rm -f   # garde 7 sauvegardes
fi

# On n'écrase jamais la config ni les mondes existants
EXCLUDE=()
for f in server.properties allowlist.json permissions.json; do
  [[ -f "$MC_DIR/$f" ]] && EXCLUDE+=("$f")
done
unzip -oq "$TMP/bds.zip" -d "$MC_DIR" ${EXCLUDE[@]+-x "${EXCLUDE[@]}"}
chmod +x "$MC_DIR/bedrock_server"
echo "$NEW_VER" > "$MC_DIR/.version"
chown -R "$MC_USER:$MC_USER" "$MC_DIR" 2>/dev/null || true

[[ $WAS_RUNNING -eq 1 ]] && systemctl start bedrock
echo "$(ts) OK : version $NEW_VER installée."
