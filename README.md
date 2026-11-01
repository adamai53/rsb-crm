# CRM Red Sea Bunkering

CRM commercial de Red Sea Bunkering (soutage maritime, Djibouti) : Spring Boot 3 + Angular 20 + PostgreSQL 16, déployé avec Docker.
Le prototype `rsb-crm.html` sert de spécification fonctionnelle et visuelle.

## Avancement

| Étape | Contenu | État |
|---|---|---|
| 1. Socle | Arborescence, Docker Compose dev, Flyway V1, healthchecks, CI de build | ✅ |
| 2. Authentification | Inscription, vérification e-mail, validation admin, JWT, rôles | ✅ |
| 3. Référentiel | Entreprises, contacts, score, import CSV, recherche | ✅ |
| 4. Ventes | Pipeline, leads, tâches, activités, escales | ✅ |
| 5. Devis et e-mails | PDF, SMTP, modèles, séquences, suivi d'ouverture | ✅ |
| 6. Marketing et rapports | Campagnes, formulaire public, tableau de bord, rapports | ✅ |
| 7. Données démo et polissage UI | | ✅ |
| 8. Production | Compose prod, Nginx + HTTPS, scripts VPS, sauvegardes, déploiement | Fichiers et tests prêts ; premier déploiement à faire |

## Arborescence

```
backend/    API Spring Boot (Java 21, Maven wrapper)
frontend/   Application Angular (standalone, signals, routes lazy)
deploy/     Docker Compose, configuration Nginx, scripts serveur, .env.example
e2e/        Parcours de bout en bout (Playwright)
.github/    Intégration continue et déploiement
```

## Démarrage rapide (tout en Docker)

Prérequis : Docker Desktop (ou Docker Engine + plugin Compose).

```bash
docker compose -f deploy/docker-compose.dev.yml up -d --build
```

| Service | URL |
|---|---|
| Application | http://localhost:8081 |
| API | http://localhost:8080 (Swagger : `/swagger-ui.html`) |
| Santé de l'API | http://localhost:8080/actuator/health |
| Mailpit (e-mails de dev) | http://localhost:8025 |
| PostgreSQL | `localhost:5433`, base/utilisateur/mot de passe `rsbcrm` |

Compte administrateur de développement : `admin@rsb.local` / `AdminRsb2026` (défini dans
`docker-compose.dev.yml`, uniquement pour le dev). Les e-mails d'inscription et de réinitialisation
arrivent dans Mailpit.

Arrêt : `docker compose -f deploy/docker-compose.dev.yml down` (ajouter `-v` pour effacer la base).

## Développement local

### Backend

Prérequis : JDK 21. Maven n'est pas nécessaire (wrapper `mvnw` fourni).

```bash
cd backend
docker compose -f ../deploy/docker-compose.dev.yml up -d db mailpit
DB_PORT=5433 SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
./mvnw verify          # tests (Testcontainers : Docker doit tourner)
```

Sous Windows, si Testcontainers ne trouve pas Docker (`NpipeSocketClientProviderStrategy … Status 500`),
pointez-le vers le pipe de Docker Desktop :

```bash
export DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine
```

Sans Docker, les tests peuvent viser une base PostgreSQL **vide** (15 ou plus, avec `pg_trgm`), à recréer entre deux
exécutions :

```bash
TEST_DB_URL=jdbc:postgresql://127.0.0.1:55432/crmtest TEST_DB_USER=rsbcrm ./mvnw verify
```

### Frontend

Prérequis : Node 22 ou 24.

```bash
cd frontend
npm ci
npx ng serve           # http://localhost:4200, /api relayé vers localhost:8080
npx ng test --watch=false --browsers=ChromeHeadless
```

## Base de données

Les migrations Flyway (`backend/src/main/resources/db/migration`) sont appliquées au démarrage :

- `V1__schema.sql` : schéma complet (utilisateurs et jetons, référentiels, entreprises, contacts,
  transactions avec montant calculé, leads, tâches, activités, devis, e-mails, séquences, campagnes, audit),
  index et recherche plein texte `pg_trgm`.
- `V2__reference_data.sql` : étapes du pipeline, produits et prix de référence, lieux de livraison.

## Authentification

- Access token JWT (HS256, 15 min) gardé en mémoire par Angular ; refresh token opaque (7 jours)
  dans un cookie `HttpOnly; SameSite=Strict; Path=/api/auth`, avec rotation à chaque usage.
  Réutiliser un refresh token déjà échangé ferme toute la session (détection de vol).
- Mots de passe BCrypt (coût 12) : 10 caractères minimum, une majuscule, un chiffre.
- 5 échecs de connexion consécutifs → compte bloqué 15 min. Messages génériques
  (on ne révèle pas si un e-mail est inscrit), journal des connexions dans `login_events`.
- Parcours : inscription → lien de vérification (24 h) → validation par un admin
  (`APP_SIGNUP_REQUIRES_APPROVAL`) → connexion. Mot de passe oublié : lien valable 1 h.
- Rôles `ADMIN`, `MANAGER`, `SALES`, vérifiés côté API (`@PreAuthorize`). Le premier admin est créé
  au démarrage depuis `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` s'il n'en existe aucun.

| Route | Accès |
|---|---|
| `POST /api/auth/register · login · refresh · logout · verify-email · forgot-password · reset-password` | public |
| `GET /api/auth/me`, `POST /api/auth/password`, `PATCH /api/users/me` | connecté |
| `GET /api/users`, `POST /api/users/{id}/approve · disable · resend-verification`, `PATCH /api/users/{id}/role` | ADMIN |

## Référentiel

- **Entreprises** : liste triée par CA livré (seules les livraisons avec BDN signé comptent), contacts,
  transactions ouvertes, volume livré ; fiche avec activité, contacts et transactions.
- **Contacts** : filtres par cycle de vie, texte et propriétaire ; tri par nom, entreprise, cycle de vie, score
  ou dernière activité ; fiche avec activité (note, appel, e-mail, réunion), transactions, tâches et détail du score.
- **Score sur 100** (`ContactScoring`, seule source de vérité pour la liste et la fiche) : source (Recommandation 20 …
  autre 5), cycle de vie (×7), interactions (5 par activité, plafond 20), transactions ouvertes (> 400 k → 18,
  > 100 k → 12, > 0 → 6), décideur achats carburant (12).
- **Droits** : tout le monde lit tout ; un SALES ne modifie que ses dossiers ; seuls MANAGER et ADMIN réattribuent.
  Les activités peuvent être ajoutées par tous (elles sont signées par leur auteur).
- **Import CSV** (`POST /api/contacts/import`, 5 Mo max) : séparateur `,` ou `;`, guillemets, BOM, fichiers Excel
  en Windows-1252 ; colonnes `prenom, nom, email, telephone, poste, entreprise, source` (accents et majuscules
  tolérés). Les entreprises inconnues sont créées ; les doublons d'e-mail et e-mails invalides sont ignorés et listés.
- **Export CSV** (`GET /api/contacts/export`) : format Excel français (`;`, UTF-8 avec BOM), réimportable,
  formules neutralisées.
- **Recherche globale** (`GET /api/search?q=`, Ctrl+K) : contacts, entreprises, navires et IMO dès 2 caractères.
- La fiche d'un enregistrement est dans l'URL (`?fiche=contact:<id>`) : on peut la partager ou revenir en arrière.

## Ventes

- **Pipeline** (`/deals`) : kanban par étape avec glisser-déposer (Angular CDK) ou vue liste ; bandeau
  « ouvertes · montant · pondéré · transformation » calculé sur le même filtre.
- **Règles métier** (testées dans `SalesRulesTests` et `PipelineMetricsTests`) :
  1. pipeline pondéré = Σ montant ouvert × probabilité de l'étape ;
  2. taux de transformation = livrées / (livrées + perdues) ;
  3. conversion d'un lead en une transaction SQL : entreprise retrouvée par nom (casse ignorée) ou créée, contact SQL
     (réutilisé si l'e-mail existe déjà), transaction « Qualifiée » au prix de référence, tâche « Envoyer la cotation »
     du jour en priorité haute, attribution au commercial actif le moins chargé, activité système avec le message ;
  4. livrée : date de clôture, contact Client, montant compté dans le CA ;
  5. perdue : motif obligatoire (fenêtre de choix à l'écran, refus 400 côté API sinon) ;
  6. étape ouverte : clôture et motif effacés, contact au moins Opportunité (comme le prototype, à chaque passage
     sur une étape ouverte, sans jamais faire redescendre un Client) ;
  7. chaque changement d'étape : activité système « Étape : X → Y » et ligne dans `audit_log`.
- **Leads entrants** (`/inbox`) : demandes à traiter, conversion ou rejet ; saisie manuelle d'une demande reçue par
  téléphone ou e-mail (le formulaire public du site arrive à l'étape 6).
- **Tâches** (`/tasks`) : en retard, aujourd'hui, à venir, terminées ; une tâche liée à une transaction hérite de
  son contact et de son entreprise. Pastilles de la barre latérale : leads à traiter, tâches dues.
- **Escales** (`/calls`) : frise de charge sur 21 jours et liste des navires attendus (hors transactions perdues),
  calculées sur la date de Djibouti.

## Devis et e-mails

- **Devis** (`/quotes`, onglet de la fiche transaction) : numéro annuel `RSB-Q-AAAA-NNNN`, validité 48 h, barge 1 450 USD
  par défaut, aperçu et PDF (OpenPDF). Pas de devis sur une transaction clôturée.
- **Envoi** : e-mail réel avec le PDF en pièce jointe ; transaction amenée au moins à « Cotation », activité e-mail.
  Si le SMTP échoue, le devis et la transaction ne changent pas et l'échec est tracé (`sent_emails`, statut `ERREUR`).
- **Acceptation** : le prix du devis devient celui de la transaction, étape « Nomination confirmée ».
  Un devis expiré ne peut plus être accepté ; un job horaire passe les devis envoyés dépassés en « Expiré ».
- **Modèles** : champs `{{prenom}}`, `{{entreprise}}`, `{{navire}}`, `{{produit}}`, `{{quantite}}`, `{{eta}}`,
  `{{commercial}}` ; modification réservée à l'ADMIN.
- **Suivi d'ouverture** : pixel public `/api/public/t/{id}.png`. Indicatif : certains clients bloquent les images,
  d'autres les préchargent.
- **Séquence « Relance cotation »** : l'inscription crée 4 tâches (J0, J+2, J+4, J+6) pour le commercial du contact.
  L'envoi automatique des étapes e-mail est **désactivé par défaut** (case à cocher, MANAGER ou ADMIN) : chaque étape
  n'est tentée qu'une fois, un échec laisse la tâche ouverte.

Limite connue : le modèle « Envoi de cotation » dit « ci-joint », mais l'envoi automatique de la séquence ne joint
pas de devis. Avant d'activer l'envoi automatique, adaptez ce modèle ou envoyez la cotation depuis le devis.

## Marketing et rapports

- **Attribution** : leads, clients et CA d'une campagne sont calculés. Une demande revient à la campagne dont le canal
  est sa source et dont la période contient sa date d'arrivée. Leads = demandes non rejetées + transactions saisies
  directement. Deux campagnes d'un même canal ne peuvent pas se chevaucher, sinon leurs leads seraient comptés deux fois.
- **Droits** : tout le monde lit les campagnes ; seuls MANAGER et ADMIN les modifient et voient le code du formulaire.
- **Rapports** : complets pour MANAGER et ADMIN, limités à ses propres dossiers pour un SALES. Le tableau de bord reste
  une vue d'équipe ; ses tâches sont celles du commercial connecté, ou de toute l'équipe pour un manager.

### Formulaire du site

`POST /api/public/leads` crée un lead entrant. Le code à coller dans WordPress (bloc « HTML personnalisé ») est généré
dans **Campagnes & formulaires**. Il déduit la source du paramètre `utm_source` de la page d'arrivée.

| Protection | Fonctionnement |
|---|---|
| Clé API (`APP_PUBLIC_LEAD_API_KEY`) | Sans clé configurée, le formulaire est fermé. La clé reste visible dans le code de la page : elle identifie le site, elle ne le protège pas à elle seule. |
| Captcha Cloudflare Turnstile (`CAPTCHA_SECRET`, `CAPTCHA_SITE_KEY`) | **Sans secret, le captcha est désactivé** : à renseigner avant la mise en ligne. Si Turnstile est injoignable, la demande est refusée. |
| Limitation de débit (`PUBLIC_FORM_MAX_PER_IP`, 5 par défaut) | Par adresse IP sur 10 minutes, en mémoire : remise à zéro au redémarrage, valable pour une seule instance du backend. |
| CORS (`PUBLIC_FORM_ALLOWED_ORIGINS`) | Seul le site vitrine peut appeler cette route depuis un navigateur, et aucune autre route de l'API. |
| Champ piège | Un robot qui remplit le champ caché reçoit une réponse normale, sans lead créé. |

Le proxy devant le backend doit écraser `X-Forwarded-For` avec l'adresse réelle du client, sinon la limitation de débit
se contourne en falsifiant cet en-tête. `frontend/nginx.conf` et `deploy/nginx/https.conf.template` le font. Avec le proxy
Cloudflare activé (nuage orange), Nginx verrait l'adresse de Cloudflare et tous les visiteurs partageraient la même limite :
gardez l'enregistrement DNS en « DNS only », ou configurez `set_real_ip_from`.

## Données de démonstration

Activées par `APP_SEED_DEMO=true` (ou le profil Spring `demo`), jamais en production réelle : 12 entreprises, 20 contacts,
22 transactions, 6 leads, 9 tâches, 14 activités, 3 devis et 6 campagnes, repris du prototype.

- **Commerciaux** : `hassan.doualeh@rsb.local`, `fatouma.ali@rsb.local`, `ibrahim.omar@rsb.local`, mot de passe commun
  `APP_DEMO_PASSWORD`. Sans cette variable, ces comptes existent mais ne peuvent pas se connecter.
- **Jour J** : `APP_DEMO_REFERENCE_DATE`, par défaut `2026-10-04`. Après cette date, les tâches apparaissent en retard, les
  escales passées et les devis envoyés expirent au passage du job horaire. Avec `today`, les échéances restent à venir ;
  le changement s'applique au prochain chargement (base vide ou réinitialisation).
- **Campagnes** : dépense et audience reprises du prototype, mais leads, clients et CA sont calculés. Ils sont donc plus
  petits que les chiffres saisis à la main du prototype (129 leads pour 6 demandes réelles).
- **Réinitialisation** : bouton de la page Administration, réservé à l'ADMIN et désactivé sans démo. Elle efface toutes
  les données métier ; comptes, référentiels et modèles d'e-mails sont conservés.
- **Garde-fous** : le chargement n'a lieu que sur une base métier vide, et la réinitialisation est refusée si la base n'a
  pas été remplie par la démo, même avec `APP_SEED_DEMO=true`.

## Tests de bout en bout

`e2e/` déroule le parcours démo dans un vrai navigateur : formulaire public → conversion → devis envoyé → accepté → livré,
et échoue sur toute violation de la CSP. Il lui faut une stack avec les données de démo et un serveur SMTP de test.

```bash
cd e2e
npm ci
E2E_BASE_URL=http://localhost:8081 E2E_LEAD_API_KEY=dev-lead-key E2E_CHANNEL=chrome npx playwright test
```

En CI, le job `stack` lance `deploy/scripts/smoke-test.sh` : stack de production construite avec PostgreSQL 16, HTTPS
auto-signé, contrôle des en-têtes et des routes exposées, ce parcours, puis sauvegarde et restauration. Ce script écrit
`deploy/.env` : il est réservé à une machine jetable.

## Mise en production

Architecture : `proxy` (Nginx, seuls ports publiés 80 et 443) → `frontend` et `backend` → `db` (réseau interne, sans accès
Internet). `certbot` renouvelle le certificat. `/actuator` et Swagger ne passent pas par le proxy.

### 1. Préparer le serveur (Ubuntu 24.04)

Connecté en root **par clé SSH** :

```bash
git clone https://github.com/adamai53/rsb-crm.git /opt/rsb-crm
bash /opt/rsb-crm/deploy/scripts/setup-vps.sh deploy
```

Le script s'arrête s'il ne trouve aucune clé. Il crée l'utilisateur `deploy` (sudo sans mot de passe, groupe docker), installe
Docker, UFW (22, 80, 443) et fail2ban, programme la sauvegarde de 2 h 30, puis désactive root et les mots de passe SSH.
**Vérifiez `ssh deploy@<ip>` dans un autre terminal avant de fermer la session.** Ensuite, connecté en `deploy` :
`sudo chown -R deploy:deploy /opt/rsb-crm`.

Dépôt privé : ajoutez au serveur une clé de déploiement GitHub en lecture seule, et clonez en SSH.

### 2. DNS et configuration

- Enregistrement A `crm.redseabunkering.com` → IP du VPS (Cloudflare : « DNS only »).
- `cp deploy/.env.example deploy/.env`, puis `chmod 600 deploy/.env` et remplacer chaque valeur. `deploy.sh` refuse de
  démarrer s'il reste un `change-me`. Secrets : `openssl rand -base64 48` pour `JWT_SECRET`, `openssl rand -hex 24` pour
  `POSTGRES_PASSWORD` et `APP_PUBLIC_LEAD_API_KEY`.
- Laisser `APP_SEED_DEMO=false` en production réelle.

### 3. Premier démarrage et certificat

```bash
deploy/scripts/deploy.sh                       # sans certificat, le proxy répond 503 en HTTP
deploy/scripts/init-letsencrypt.sh --staging   # essai facultatif, sans quota
deploy/scripts/init-letsencrypt.sh             # accepte les conditions de Let's Encrypt
```

Après un essai `--staging`, supprimez ce certificat avant de demander le vrai :
`docker compose -f deploy/docker-compose.yml run --rm --entrypoint certbot certbot delete --cert-name <domaine>`.
Sans images publiées sur GHCR, construisez-les d'abord : `docker compose -f deploy/docker-compose.yml build`.

### 4. Déploiement automatique

Chaque push sur `main` : tests, stack complète, images `ghcr.io/adamai53/rsb-crm-{backend,frontend}` (tags SHA et `latest`).
Le déploiement SSH ne s'active qu'avec la variable de dépôt `DEPLOY_ENABLED=true` et ces secrets GitHub :

| Secret | Valeur |
|---|---|
| `VPS_HOST` | IP ou nom du serveur |
| `VPS_USER` | `deploy` |
| `VPS_SSH_KEY` | Clé privée dédiée au déploiement, sa clé publique dans `authorized_keys` |
| `VPS_KNOWN_HOSTS` | Sortie de `ssh-keyscan <serveur>`, à vérifier : protège contre un faux serveur |

### 5. Exploitation

| Besoin | Commande |
|---|---|
| Mise à jour manuelle | `git pull --ff-only && deploy/scripts/deploy.sh` |
| Revenir à une version | `deploy/scripts/deploy.sh <sha>` |
| Sauvegarde immédiate | `deploy/scripts/backup.sh` |
| Restauration | `deploy/scripts/restore.sh /var/backups/rsb-crm/<fichier>.dump` |
| État et journaux | `docker compose -f deploy/docker-compose.yml ps` · `logs -f backend` |

- **Retour arrière** : Flyway ne défait pas les migrations. Si la version annulée en a appliqué une, le backend ancien peut
  refuser de démarrer : restaurez alors la sauvegarde que `deploy.sh` a faite juste avant la mise à jour.
- **Sauvegardes** : `pg_dump` vérifié, rétention de 14 jours (`/var/log/rsb-crm-backup.log`). Tant que `BACKUP_S3_BUCKET` est
  vide, elles restent sur le serveur et ne protègent pas contre sa perte.
- **Restauration** : vérifie le fichier, fait une copie de la base actuelle, restaure dans une base temporaire et ne remplace
  la base qu'en cas de réussite.
- **Supervision** : healthchecks Docker, journaux limités à 5 × 10 Mo par service. Pour être alerté d'une panne, ajoutez une
  sonde externe (Uptime Kuma par exemple) sur `https://<domaine>/api/public/info`.
- **Limites mémoire** : db 512 Mo, backend 1 Go, frontend 64 Mo, proxy et certbot 128 Mo. Prévoir un VPS d'au moins 2 Go.

## Variables d'environnement

Voir [`deploy/.env.example`](deploy/.env.example). Aucun secret n'est versionné.

## À confirmer

- Nom de domaine définitif (exemple utilisé : `crm.redseabunkering.com`).
- Fournisseur SMTP de production.
- Compte Cloudflare Turnstile pour le captcha du formulaire public (clés `CAPTCHA_SITE_KEY` et `CAPTCHA_SECRET`).
- Adresse de contact Let's Encrypt (`LETSENCRYPT_EMAIL`) et stockage S3 pour la copie hors serveur des sauvegardes.
