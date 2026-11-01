#!/usr/bin/env bash
# Sauvegarde PostgreSQL compressée, vérifiée, avec rétention locale et copie S3 optionnelle.
# Usage : deploy/scripts/backup.sh   (quotidien via /etc/cron.d/rsb-crm-backup)
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="$ROOT/deploy/.env"
compose() { docker compose -f "$ROOT/deploy/docker-compose.yml" "$@"; }
env_get() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1; }

BACKUP_DIR="${BACKUP_DIR:-$(env_get BACKUP_DIR)}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/rsb-crm}"
RETENTION_DAYS="$(env_get BACKUP_RETENTION_DAYS)"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"
target="$BACKUP_DIR/rsbcrm-$(date +%Y%m%d-%H%M%S).dump"
partial="$target.partial"
trap 'rm -f "$partial"' EXIT

compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --compress=6' > "$partial"
# Un fichier illisible ne doit jamais prendre la place d'une sauvegarde valide
compose exec -T db pg_restore --list < "$partial" > /dev/null
mv "$partial" "$target"
echo "Sauvegarde : $target ($(du -h "$target" | cut -f1))"

find "$BACKUP_DIR" -maxdepth 1 -name 'rsbcrm-*.dump' -mtime "+$((RETENTION_DAYS - 1))" -print -delete

S3_BUCKET="$(env_get BACKUP_S3_BUCKET)"
if [ -n "$S3_BUCKET" ]; then
  S3_ENDPOINT="$(env_get BACKUP_S3_ENDPOINT)"
  docker run --rm -i \
    -e AWS_ACCESS_KEY_ID="$(env_get BACKUP_S3_ACCESS_KEY)" \
    -e AWS_SECRET_ACCESS_KEY="$(env_get BACKUP_S3_SECRET_KEY)" \
    -e AWS_DEFAULT_REGION="$(env_get BACKUP_S3_REGION)" \
    amazon/aws-cli:2.27.0 ${S3_ENDPOINT:+--endpoint-url "$S3_ENDPOINT"} \
    s3 cp - "s3://$S3_BUCKET/$(basename "$target")" < "$target"
  echo "Copie hors serveur : s3://$S3_BUCKET/$(basename "$target")"
fi
