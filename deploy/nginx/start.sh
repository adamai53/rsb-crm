#!/bin/sh
# Choisit la configuration du proxy : HTTPS si le certificat existe, sinon HTTP seul le temps de l'obtenir.
set -eu

cert="/etc/letsencrypt/live/${DOMAIN}/fullchain.pem"
if [ -f "$cert" ]; then
  template=/etc/rsb/https.conf.template
else
  template=/etc/rsb/http-only.conf.template
  echo "Aucun certificat pour ${DOMAIN} : HTTP seul, lancez deploy/scripts/init-letsencrypt.sh" >&2
fi

# Seul DOMAIN est remplacé : les variables Nginx ($host, $uri…) restent intactes
envsubst '${DOMAIN}' < "$template" > /etc/nginx/conf.d/default.conf

# Prend en compte les certificats renouvelés
(while sleep 6h; do nginx -s reload; done) &

exec nginx -g 'daemon off;'
