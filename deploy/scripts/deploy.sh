#!/usr/bin/env bash
# Met à jour la production : sauvegarde, récupération des images, migrations Flyway au démarrage du backend.
# Usage : deploy/scripts/deploy.sh [tag]   (tag d'image GHCR, latest par défaut ; un SHA de commit pour revenir en arrière)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="$ROOT/deploy/.env"
compose() { docker compose -f "$ROOT/deploy/docker-compose.yml" "$@"; }

if [ ! -f "$ENV_FILE" ]; then
  echo "deploy/.env manquant : copiez deploy/.env.example et remplissez-le." >&2
  exit 1
fi
if grep -q 'change-me' "$ENV_FILE"; then
  echo "deploy/.env contient encore des valeurs change-me :" >&2
  grep 'change-me' "$ENV_FILE" | cut -d= -f1 >&2
  exit 1
fi

export IMAGE_TAG="${1:-latest}"

compose pull backend frontend
# Rien à sauvegarder au premier déploiement
if [ -n "$(compose ps --status running -q db)" ]; then
  "$ROOT/deploy/scripts/backup.sh"
fi
compose up -d --wait --wait-timeout 240 --remove-orphans
docker image prune -f > /dev/null
compose ps
echo "Déployé : $IMAGE_TAG"
