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
| 5. Devis et e-mails | PDF, SMTP, modèles, séquences, suivi d'ouverture | à faire |
| 6. Marketing et rapports | Campagnes, formulaire public, tableau de bord, rapports | à faire |
| 7. Données démo et polissage UI | | à faire |
| 8. Production | Compose prod, Nginx + HTTPS, scripts VPS, sauvegardes, déploiement | à faire |

## Arborescence

```
backend/    API Spring Boot (Java 21, Maven wrapper)
frontend/   Application Angular (standalone, signals, routes lazy)
deploy/     Docker Compose, configuration Nginx, scripts serveur, .env.example
.github/    Intégration continue
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

## Variables d'environnement

Voir [`deploy/.env.example`](deploy/.env.example). Aucun secret n'est versionné.

## À confirmer

- Nom de domaine définitif (exemple utilisé : `crm.redseabunkering.com`).
- Fournisseur SMTP de production.
- Captcha du formulaire public (Cloudflare Turnstile ou hCaptcha).
