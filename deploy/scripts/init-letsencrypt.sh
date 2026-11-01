#!/usr/bin/env bash
# Obtient le premier certificat HTTPS. La stack doit tourner et le DNS du domaine pointer vers ce serveur.
# Usage : deploy/scripts/init-letsencrypt.sh [--staging]   (--staging : essai, sans quota, certificat non reconnu)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="$ROOT/deploy/.env"
compose() { docker compose -f "$ROOT/deploy/docker-compose.yml" "$@"; }
env_get() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1; }

domain="$(env_get DOMAIN)"
email="$(env_get LETSENCRYPT_EMAIL)"
if [ -z "$domain" ] || [ -z "$email" ]; then
  echo "DOMAIN et LETSENCRYPT_EMAIL doivent être renseignés dans deploy/.env" >&2
  exit 1
fi

compose run --rm --entrypoint certbot certbot certonly --webroot -w /var/www/certbot \
  -d "$domain" --email "$email" --agree-tos --no-eff-email --non-interactive ${1:+"$1"}

compose restart proxy
echo "Certificat installé pour $domain"
