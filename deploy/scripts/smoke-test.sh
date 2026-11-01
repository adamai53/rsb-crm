#!/usr/bin/env bash
# Vérifie la stack de production sur une machine jetable (CI) : ne jamais lancer sur le VPS, deploy/.env serait écrasé.
# Usage : deploy/scripts/smoke-test.sh up        construit et démarre, contrôle HTTP puis HTTPS auto-signé
#         deploy/scripts/smoke-test.sh restore   contrôle sauvegarde et restauration
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="$ROOT/deploy/.env"
compose() { docker compose -f "$ROOT/deploy/docker-compose.yml" "$@"; }
smoke() { compose -f "$ROOT/deploy/docker-compose.smoke.yml" "$@"; }
sql() { compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA -c "$1"' sh "$1"; }
fail() { echo "ÉCHEC : $1" >&2; exit 1; }

up() {
  if [ -f "$ENV_FILE" ]; then
    fail "deploy/.env existe déjà : ce script l'écraserait"
  fi
  cat > "$ENV_FILE" <<EOF
DOMAIN=localhost
LETSENCRYPT_EMAIL=smoke@example.com
IMAGE_TAG=smoke
POSTGRES_DB=rsbcrm
POSTGRES_USER=rsbcrm
POSTGRES_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
APP_ADMIN_EMAIL=admin@rsb.local
APP_ADMIN_PASSWORD=AdminRsb2026
APP_SEED_DEMO=true
APP_DEMO_PASSWORD=DemoRsb2026
APP_PUBLIC_LEAD_API_KEY=smoke-lead-key
SMTP_HOST=mailpit
SMTP_PORT=1025
SMTP_AUTH=false
SMTP_STARTTLS=false
MAIL_FROM=crm@redseabunkering.com
BACKUP_DIR=$(mktemp -d)
EOF

  smoke up -d --build --wait --wait-timeout 300

  echo "--- HTTP sans certificat"
  [ "$(curl -s http://localhost/healthz)" = "ok" ] || fail "healthz"
  [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost/)" = "503" ] || fail "attendu 503 sans certificat"

  echo "--- Certificat auto-signé"
  local certs
  certs="$(mktemp -d)"
  openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj /CN=localhost -addext subjectAltName=DNS:localhost \
    -keyout "$certs/privkey.pem" -out "$certs/fullchain.pem" 2> /dev/null
  chmod 644 "$certs"/*.pem
  compose run --rm --no-deps -T -v "$certs:/src:ro" --entrypoint sh certbot \
    -c 'mkdir -p /etc/letsencrypt/live/localhost && cp /src/*.pem /etc/letsencrypt/live/localhost/'
  compose restart proxy
  smoke up -d --wait --wait-timeout 60 proxy

  echo "--- HTTPS"
  [ "$(curl -s -o /dev/null -w '%{redirect_url}' http://localhost/login)" = "https://localhost/login" ] || fail "redirection HTTPS"
  local headers
  headers="$(curl -ksI https://localhost/)"
  for h in strict-transport-security content-security-policy x-frame-options x-content-type-options; do
    grep -qi "^$h:" <<< "$headers" || fail "en-tête $h absent"
  done
  curl -ks https://localhost/api/public/info | grep -q '"demo":true' || fail "API derrière le proxy"
  if curl -ks https://localhost/actuator/health | grep -q '"status"'; then
    fail "/actuator exposé"
  fi
  [ "$(compose port db 5432 2> /dev/null || true)" = "" ] || fail "base exposée"
  echo "Stack OK"
}

restore() {
  local before after dump dir
  before="$(sql 'SELECT count(*) FROM activities')"
  [ "$before" -gt 0 ] || fail "base vide"
  "$ROOT/deploy/scripts/backup.sh"
  dir="$(sed -n 's/^BACKUP_DIR=//p' "$ENV_FILE")"
  dump="$(ls -t "$dir"/rsbcrm-*.dump | head -n 1)"

  # Un fichier corrompu doit être refusé sans toucher à la base
  head -c 2000 /dev/urandom > "$dir/corrompu.dump"
  if "$ROOT/deploy/scripts/restore.sh" "$dir/corrompu.dump" --yes 2> /dev/null; then
    fail "sauvegarde corrompue acceptée"
  fi
  rm -f "$dir/corrompu.dump"

  sql 'DELETE FROM activities' > /dev/null
  "$ROOT/deploy/scripts/restore.sh" "$dump" --yes
  after="$(sql 'SELECT count(*) FROM activities')"
  [ "$after" = "$before" ] || fail "activités après restauration : $after au lieu de $before"
  [ "$(curl -ks -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
       -d '{"email":"admin@rsb.local","password":"AdminRsb2026"}' https://localhost/api/auth/login)" = "200" ] \
    || fail "connexion après restauration"
  echo "Sauvegarde et restauration OK ($after activités)"
}

case "${1:-}" in
  up) up ;;
  restore) restore ;;
  *) echo "Usage : $0 up|restore" >&2; exit 2 ;;
esac
