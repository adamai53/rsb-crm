#!/usr/bin/env bash
# Prépare un Ubuntu 24.04 neuf : utilisateur de déploiement, SSH par clé seule, UFW, fail2ban, Docker, sauvegarde quotidienne.
# À lancer une fois en root, connecté par clé SSH : sudo bash deploy/scripts/setup-vps.sh [utilisateur]
set -euo pipefail

DEPLOY_USER="${1:-deploy}"
APP_DIR=/opt/rsb-crm

if [ "$(id -u)" -ne 0 ]; then
  echo "À lancer en root." >&2
  exit 1
fi
. /etc/os-release
if [ "${ID:-}" != "ubuntu" ]; then
  echo "Script prévu pour Ubuntu (détecté : ${PRETTY_NAME:-inconnu})." >&2
  exit 1
fi

# Sans clé pour le nouvel utilisateur, désactiver root et les mots de passe enfermerait dehors : on s'arrête avant tout changement
SOURCE_KEYS=/root/.ssh/authorized_keys
if [ -f "/home/$DEPLOY_USER/.ssh/authorized_keys" ]; then
  SOURCE_KEYS="/home/$DEPLOY_USER/.ssh/authorized_keys"
fi
if ! grep -qE '^(ssh-|ecdsa-|sk-)' "$SOURCE_KEYS" 2>/dev/null; then
  echo "Aucune clé SSH dans $SOURCE_KEYS : ajoutez-en une, sinon ce script vous enfermerait dehors." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q ca-certificates curl git ufw fail2ban unattended-upgrades

# Docker Engine et plugin Compose, dépôt officiel
if ! command -v docker > /dev/null; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker

# Utilisateur de déploiement
if ! id "$DEPLOY_USER" > /dev/null 2>&1; then
  adduser --disabled-password --gecos "" "$DEPLOY_USER"
fi
usermod -aG sudo,docker "$DEPLOY_USER"
# Le compte n'a pas de mot de passe : sudo sans mot de passe, sinon il serait inutilisable
echo "$DEPLOY_USER ALL=(ALL) NOPASSWD:ALL" > "/etc/sudoers.d/90-$DEPLOY_USER"
chmod 440 "/etc/sudoers.d/90-$DEPLOY_USER"
visudo -cf "/etc/sudoers.d/90-$DEPLOY_USER"
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"
if [ "$SOURCE_KEYS" = /root/.ssh/authorized_keys ]; then
  install -m 600 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$SOURCE_KEYS" "/home/$DEPLOY_USER/.ssh/authorized_keys"
fi

# Dossiers de l'application et des sauvegardes
install -d -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR"
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" /var/backups/rsb-crm
touch /var/log/rsb-crm-backup.log
chown "$DEPLOY_USER" /var/log/rsb-crm-backup.log

# Pare-feu. Seul le proxy publie des ports Docker (80 et 443) : la base reste inaccessible
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

cat > /etc/fail2ban/jail.d/rsb-sshd.conf <<'EOF'
[sshd]
enabled = true
maxretry = 5
findtime = 10m
bantime = 1h
EOF
systemctl enable --now fail2ban
systemctl restart fail2ban

# Sauvegarde quotidienne à 2 h 30
cat > /etc/cron.d/rsb-crm-backup <<EOF
30 2 * * * $DEPLOY_USER $APP_DIR/deploy/scripts/backup.sh >> /var/log/rsb-crm-backup.log 2>&1
EOF
chmod 644 /etc/cron.d/rsb-crm-backup
cat > /etc/logrotate.d/rsb-crm-backup <<'EOF'
/var/log/rsb-crm-backup.log {
  monthly
  rotate 6
  compress
  missingok
  notifempty
}
EOF

# SSH en dernier. Préfixe 00 : la première valeur lue gagne, et cloud-init en pose souvent une dans 50-cloud-init.conf
cat > /etc/ssh/sshd_config.d/00-rsb-hardening.conf <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
EOF
sshd -t
systemctl reload ssh

echo
echo "Terminé. AVANT de fermer cette session, vérifiez dans un autre terminal : ssh $DEPLOY_USER@<ip-du-serveur>"
