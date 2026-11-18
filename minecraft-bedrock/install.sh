#!/usr/bin/env bash
# =============================================================================
#  Installation d'un serveur Minecraft Bedrock (Bedrock Dedicated Server)
#  Cible : Ubuntu 22.04 / 24.04 (ex. Hetzner Cloud CX/CAX 1 Go RAM)
#
#  - Télécharge TOUJOURS la dernière version officielle de Mojang
#    (compatible Minecraft 1.26.44.x et versions suivantes)
#  - Seed du monde : 4208396003150133985
#  - Service systemd (démarrage auto) + console via `screen`
#  - Mise à jour automatique chaque nuit (garde le serveur à jour avec les
#    téléphones qui se mettent à jour)
#  - Swap de 2 Go (indispensable avec 1 Go de RAM)
#  - Pare-feu UFW : SSH + ports Bedrock 19132/19133 UDP
#
#  Utilisation (en root sur le serveur) :
#     bash install.sh
# =============================================================================
set -euo pipefail

# ----------------------------- Réglages --------------------------------------
SEED="${SEED:-4208396003150133985}"
SERVER_NAME="${SERVER_NAME:-Serveur Genius}"
LEVEL_NAME="${LEVEL_NAME:-Monde Genius}"
GAMEMODE="${GAMEMODE:-survival}"          # survival | creative | adventure
DIFFICULTY="${DIFFICULTY:-normal}"        # peaceful | easy | normal | hard
MAX_PLAYERS="${MAX_PLAYERS:-10}"
VIEW_DISTANCE="${VIEW_DISTANCE:-10}"
PORT_V4=19132
PORT_V6=19133

MC_USER="minecraft"
MC_DIR="/opt/bedrock"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# -----------------------------------------------------------------------------

log()  { echo -e "\e[1;32m[+]\e[0m $*"; }
warn() { echo -e "\e[1;33m[!]\e[0m $*"; }
die()  { echo -e "\e[1;31m[x]\e[0m $*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "Lance ce script en root (ou avec sudo)."

log "Installation des paquets nécessaires…"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y curl unzip jq screen ufw libcurl4 ca-certificates

# ----------------------------- Swap ------------------------------------------
if ! swapon --show | grep -q '/swapfile'; then
  log "Création d'un swap de 2 Go…"
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -w vm.swappiness=10 >/dev/null
  echo 'vm.swappiness=10' > /etc/sysctl.d/99-minecraft.conf
fi

# ----------------------------- Utilisateur -----------------------------------
id "$MC_USER" &>/dev/null || useradd --system --create-home --home-dir "$MC_DIR" --shell /usr/sbin/nologin "$MC_USER"
mkdir -p "$MC_DIR"

# ----------------------------- Scripts ---------------------------------------
install -m 755 "$SCRIPT_DIR/update.sh" /usr/local/bin/bedrock-update
install -m 755 "$SCRIPT_DIR/console.sh" /usr/local/bin/bedrock-console
install -m 755 "$SCRIPT_DIR/stop.sh" /usr/local/bin/bedrock-stop

# ----------------------------- Téléchargement --------------------------------
log "Téléchargement de la dernière version de Bedrock Dedicated Server…"
MC_DIR="$MC_DIR" MC_USER="$MC_USER" NO_RESTART=1 /usr/local/bin/bedrock-update --force

# ----------------------------- server.properties ------------------------------
set_prop() {
  local key="$1" value="$2" file="$MC_DIR/server.properties"
  if grep -q "^${key}=" "$file"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$file"
  else
    echo "${key}=${value}" >> "$file"
  fi
}

log "Configuration de server.properties (seed $SEED)…"
set_prop server-name        "$SERVER_NAME"
set_prop level-name         "$LEVEL_NAME"
set_prop level-seed         "$SEED"
set_prop gamemode           "$GAMEMODE"
set_prop difficulty         "$DIFFICULTY"
set_prop max-players        "$MAX_PLAYERS"
set_prop online-mode        "true"
set_prop allow-cheats       "false"
set_prop server-port        "$PORT_V4"
set_prop server-portv6      "$PORT_V6"
set_prop view-distance      "$VIEW_DISTANCE"
set_prop tick-distance      "4"
set_prop allow-list         "false"

if [[ -d "$MC_DIR/worlds/$LEVEL_NAME" ]]; then
  warn "Le monde « $LEVEL_NAME » existe déjà : le seed ne s'applique qu'aux nouveaux mondes."
fi

chown -R "$MC_USER:$MC_USER" "$MC_DIR"

# ----------------------------- systemd ---------------------------------------
log "Création du service systemd « bedrock »…"
cat > /etc/systemd/system/bedrock.service <<EOF
[Unit]
Description=Minecraft Bedrock Dedicated Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$MC_USER
WorkingDirectory=$MC_DIR
Environment=LD_LIBRARY_PATH=$MC_DIR
ExecStart=/usr/bin/screen -DmS bedrock $MC_DIR/bedrock_server
ExecStop=/usr/local/bin/bedrock-stop
Restart=on-failure
RestartSec=10
TimeoutStopSec=60

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now bedrock

# ----------------------------- Mise à jour auto ------------------------------
log "Mise à jour automatique chaque nuit à 05:00…"
cat > /etc/cron.d/bedrock-update <<'EOF'
0 5 * * * root /usr/local/bin/bedrock-update >> /var/log/bedrock-update.log 2>&1
EOF

# ----------------------------- Pare-feu --------------------------------------
log "Configuration du pare-feu UFW…"
ufw allow OpenSSH >/dev/null
ufw allow ${PORT_V4}/udp >/dev/null
ufw allow ${PORT_V6}/udp >/dev/null
ufw --force enable >/dev/null

IP4="$(curl -4 -s --max-time 5 https://api.ipify.org || hostname -I | awk '{print $1}')"
VERSION="$(cat "$MC_DIR/.version" 2>/dev/null || echo '?')"

echo
log "=============================================================="
log " Serveur Minecraft Bedrock $VERSION installé et démarré !"
log "   Adresse : $IP4"
log "   Port    : $PORT_V4"
log "   Seed    : $SEED"
log ""
log " Commandes utiles :"
log "   systemctl status bedrock      → état du serveur"
log "   bedrock-console               → console (Ctrl+A puis D pour quitter)"
log "   bedrock-update                → mise à jour manuelle"
log "=============================================================="
