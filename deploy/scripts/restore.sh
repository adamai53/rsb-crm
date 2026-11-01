#!/usr/bin/env bash
# Remplace la base par une sauvegarde. Une copie de la base actuelle est faite juste avant.
# Usage : deploy/scripts/restore.sh <fichier.dump> [--yes]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
compose() { docker compose -f "$ROOT/deploy/docker-compose.yml" "$@"; }

file="${1:-}"
if [ -z "$file" ] || [ ! -f "$file" ]; then
  echo "Usage : $0 <fichier.dump> [--yes]" >&2
  exit 2
fi

# Vérifie le fichier avant de toucher à quoi que ce soit
if ! compose exec -T db pg_restore --list < "$file" > /dev/null; then
  echo "Sauvegarde illisible, base inchangée : $file" >&2
  exit 1
fi

if [ "${2:-}" != "--yes" ]; then
  read -r -p "Remplacer TOUTE la base par $(basename "$file") ? Tapez RESTAURER pour confirmer : " answer
  if [ "$answer" != "RESTAURER" ]; then
    echo "Annulé."
    exit 1
  fi
fi

"$ROOT/deploy/scripts/backup.sh"

# Le backend repart même si la restauration échoue
trap 'compose start backend' EXIT
compose stop backend

# Restaure dans une base temporaire : en cas d'erreur, la base en service reste intacte
compose exec -T db sh -c '
  set -e
  export PGDATABASE=postgres
  psql -U "$POSTGRES_USER" -v ON_ERROR_STOP=1 -q -c "DROP DATABASE IF EXISTS rsb_restore" -c "CREATE DATABASE rsb_restore"
'
compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d rsb_restore --no-owner --exit-on-error --single-transaction' < "$file"
compose exec -T db sh -c '
  set -e
  export PGDATABASE=postgres
  psql -U "$POSTGRES_USER" -v ON_ERROR_STOP=1 -q \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '"'"'$POSTGRES_DB'"'"' AND pid <> pg_backend_pid()" \
    -c "DROP DATABASE \"$POSTGRES_DB\"" \
    -c "ALTER DATABASE rsb_restore RENAME TO \"$POSTGRES_DB\""
'

trap - EXIT
compose up -d --wait --wait-timeout 180 backend
echo "Restauration terminée : $file"
