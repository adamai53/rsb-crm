-- =====================================================================
-- CRM Red Sea Bunkering — schéma initial
-- Toutes les tables métier portent id UUID + colonnes d'audit.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------
-- Utilisateurs et jetons
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    email              VARCHAR(254) NOT NULL,
    password_hash      VARCHAR(100) NOT NULL,
    first_name         VARCHAR(80)  NOT NULL,
    last_name          VARCHAR(80)  NOT NULL,
    initials           VARCHAR(4)   NOT NULL,
    role               VARCHAR(20)  NOT NULL DEFAULT 'SALES'
                       CHECK (role IN ('ADMIN', 'MANAGER', 'SALES')),
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'PENDING_APPROVAL', 'ACTIVE', 'DISABLED')),
    language           VARCHAR(5)   NOT NULL DEFAULT 'fr',
    email_verified_at  TIMESTAMPTZ,
    last_login_at      TIMESTAMPTZ,
    failed_logins      INT          NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(128) NOT NULL UNIQUE,
    family_id    UUID         NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID,
    user_agent   VARCHAR(255),
    ip_address   VARCHAR(64),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE TABLE login_events (
    id          UUID PRIMARY KEY,
    user_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    email       VARCHAR(254) NOT NULL,
    success     BOOLEAN      NOT NULL,
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(255),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_login_events_user ON login_events (user_id, occurred_at DESC);

-- ---------------------------------------------------------------------
-- Référentiels paramétrables
-- ---------------------------------------------------------------------
CREATE TABLE pipeline_stages (
    id           UUID PRIMARY KEY,
    code         VARCHAR(30)  NOT NULL UNIQUE,
    label        VARCHAR(80)  NOT NULL,
    probability  INT          NOT NULL CHECK (probability BETWEEN 0 AND 100),
    sort_order   INT          NOT NULL,
    is_won       BOOLEAN      NOT NULL DEFAULT false,
    is_lost      BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    CHECK (NOT (is_won AND is_lost))
);

CREATE TABLE fuel_grades (
    id               UUID PRIMARY KEY,
    code             VARCHAR(20)    NOT NULL UNIQUE,
    label            VARCHAR(80)    NOT NULL,
    reference_price  NUMERIC(12, 2) NOT NULL CHECK (reference_price >= 0),
    sort_order       INT            NOT NULL DEFAULT 0,
    active           BOOLEAN        NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID
);

CREATE TABLE delivery_locations (
    id          UUID PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    label       VARCHAR(120) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

-- ---------------------------------------------------------------------
-- Référentiel commercial
-- ---------------------------------------------------------------------
CREATE TABLE companies (
    id          UUID PRIMARY KEY,
    name        VARCHAR(160) NOT NULL,
    type        VARCHAR(20)  NOT NULL
                CHECK (type IN ('ARMATEUR', 'SHIP_MANAGER', 'AGENT_MARITIME', 'TRADER', 'AFFRETEUR')),
    country     VARCHAR(80),
    city        VARCHAR(80),
    fleet_size  INT CHECK (fleet_size >= 0),
    website     VARCHAR(255),
    owner_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);
CREATE UNIQUE INDEX ux_companies_name ON companies (lower(name));
CREATE INDEX ix_companies_name_trgm ON companies USING gin (name gin_trgm_ops);

CREATE TABLE contacts (
    id          UUID PRIMARY KEY,
    first_name  VARCHAR(80)  NOT NULL,
    last_name   VARCHAR(80)  NOT NULL,
    title       VARCHAR(120),
    email       VARCHAR(254),
    phone       VARCHAR(40),
    company_id  UUID REFERENCES companies (id) ON DELETE SET NULL,
    lifecycle   VARCHAR(20)  NOT NULL DEFAULT 'LEAD'
                CHECK (lifecycle IN ('LEAD', 'MQL', 'SQL', 'OPPORTUNITE', 'CLIENT')),
    source      VARCHAR(40),
    owner_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);
CREATE INDEX ix_contacts_lifecycle_owner ON contacts (lifecycle, owner_id);
CREATE INDEX ix_contacts_company ON contacts (company_id);
CREATE INDEX ix_contacts_name_trgm ON contacts USING gin ((first_name || ' ' || last_name) gin_trgm_ops);
CREATE INDEX ix_contacts_email_trgm ON contacts USING gin (email gin_trgm_ops);

-- Une transaction = une escale de soutage
CREATE TABLE deals (
    id                 UUID PRIMARY KEY,
    vessel_name        VARCHAR(120)   NOT NULL,
    imo                CHAR(7)        CHECK (imo ~ '^[0-9]{7}$'),
    company_id         UUID REFERENCES companies (id) ON DELETE SET NULL,
    contact_id         UUID REFERENCES contacts (id) ON DELETE SET NULL,
    fuel_grade         VARCHAR(20)    NOT NULL REFERENCES fuel_grades (code),
    quantity_mt        NUMERIC(12, 2) NOT NULL CHECK (quantity_mt > 0),
    price_usd_per_mt   NUMERIC(12, 2) NOT NULL CHECK (price_usd_per_mt >= 0),
    amount_usd         NUMERIC(16, 2) GENERATED ALWAYS AS (quantity_mt * price_usd_per_mt) STORED,
    stage              VARCHAR(30)    NOT NULL REFERENCES pipeline_stages (code),
    owner_id           UUID REFERENCES users (id) ON DELETE SET NULL,
    source             VARCHAR(40),
    eta                DATE,
    delivery_location  VARCHAR(20)    REFERENCES delivery_locations (code),
    closed_at          DATE,
    lost_reason        VARCHAR(80),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID
);
CREATE INDEX ix_deals_stage_owner_eta ON deals (stage, owner_id, eta);
CREATE INDEX ix_deals_company ON deals (company_id);
CREATE INDEX ix_deals_contact ON deals (contact_id);
CREATE INDEX ix_deals_vessel_trgm ON deals USING gin (vessel_name gin_trgm_ops);
CREATE INDEX ix_deals_imo ON deals (imo);

-- Demandes entrantes (formulaire du site, e-mails…)
CREATE TABLE leads (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(160)   NOT NULL,
    company_name       VARCHAR(160),
    email              VARCHAR(254),
    phone              VARCHAR(40),
    vessel_name        VARCHAR(120),
    imo                CHAR(7)        CHECK (imo ~ '^[0-9]{7}$'),
    fuel_grade         VARCHAR(20)    REFERENCES fuel_grades (code),
    quantity_mt        NUMERIC(12, 2) CHECK (quantity_mt > 0),
    eta                DATE,
    delivery_location  VARCHAR(20)    REFERENCES delivery_locations (code),
    source             VARCHAR(40),
    message            TEXT,
    status             VARCHAR(20)    NOT NULL DEFAULT 'NOUVEAU'
                       CHECK (status IN ('NOUVEAU', 'CONVERTI', 'REJETE')),
    received_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    converted_deal_id  UUID REFERENCES deals (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID
);
CREATE INDEX ix_leads_status ON leads (status, received_at DESC);

CREATE TABLE tasks (
    id          UUID PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    due_date    DATE         NOT NULL,
    priority    VARCHAR(10)  NOT NULL DEFAULT 'MOYENNE'
                CHECK (priority IN ('HAUTE', 'MOYENNE', 'BASSE')),
    owner_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    done        BOOLEAN      NOT NULL DEFAULT false,
    done_at     TIMESTAMPTZ,
    deal_id     UUID REFERENCES deals (id) ON DELETE CASCADE,
    contact_id  UUID REFERENCES contacts (id) ON DELETE CASCADE,
    company_id  UUID REFERENCES companies (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);
CREATE INDEX ix_tasks_owner_done_due ON tasks (owner_id, done, due_date);

CREATE TABLE activities (
    id           UUID PRIMARY KEY,
    type         VARCHAR(10)  NOT NULL
                 CHECK (type IN ('NOTE', 'CALL', 'EMAIL', 'MEETING', 'SYSTEM')),
    text         TEXT         NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    author_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    deal_id      UUID REFERENCES deals (id) ON DELETE CASCADE,
    contact_id   UUID REFERENCES contacts (id) ON DELETE CASCADE,
    company_id   UUID REFERENCES companies (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID
);
CREATE INDEX ix_activities_deal ON activities (deal_id, occurred_at DESC);
CREATE INDEX ix_activities_contact ON activities (contact_id, occurred_at DESC);
CREATE INDEX ix_activities_company ON activities (company_id, occurred_at DESC);

-- ---------------------------------------------------------------------
-- Devis
-- ---------------------------------------------------------------------
CREATE TABLE quote_number_sequences (
    year        INT PRIMARY KEY,
    last_value  INT NOT NULL DEFAULT 0
);

CREATE TABLE quotes (
    id                UUID PRIMARY KEY,
    number            VARCHAR(20)    NOT NULL UNIQUE CHECK (number ~ '^RSB-Q-[0-9]{4}-[0-9]{4}$'),
    deal_id           UUID           NOT NULL REFERENCES deals (id) ON DELETE CASCADE,
    price_usd_per_mt  NUMERIC(12, 2) NOT NULL CHECK (price_usd_per_mt >= 0),
    barge_fee_usd     NUMERIC(12, 2) NOT NULL DEFAULT 1450 CHECK (barge_fee_usd >= 0),
    status            VARCHAR(20)    NOT NULL DEFAULT 'BROUILLON'
                      CHECK (status IN ('BROUILLON', 'ENVOYE', 'ACCEPTE', 'REFUSE', 'EXPIRE')),
    valid_until       TIMESTAMPTZ    NOT NULL,
    sent_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID
);
CREATE INDEX ix_quotes_deal ON quotes (deal_id);

-- ---------------------------------------------------------------------
-- E-mails et séquences
-- ---------------------------------------------------------------------
CREATE TABLE email_templates (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL UNIQUE,
    subject     VARCHAR(255) NOT NULL,
    body        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE TABLE sent_emails (
    id             UUID PRIMARY KEY,
    to_contact_id  UUID REFERENCES contacts (id) ON DELETE SET NULL,
    to_address     VARCHAR(254) NOT NULL,
    deal_id        UUID REFERENCES deals (id) ON DELETE SET NULL,
    quote_id       UUID REFERENCES quotes (id) ON DELETE SET NULL,
    subject        VARCHAR(255) NOT NULL,
    body           TEXT         NOT NULL,
    status         VARCHAR(10)  NOT NULL DEFAULT 'ENVOYE'
                   CHECK (status IN ('ENVOYE', 'OUVERT', 'ERREUR')),
    error_message  VARCHAR(500),
    sent_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    opened_at      TIMESTAMPTZ,
    tracking_id    UUID         NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID
);
CREATE INDEX ix_sent_emails_contact ON sent_emails (to_contact_id, sent_at DESC);

CREATE TABLE sequences (
    id           UUID PRIMARY KEY,
    name         VARCHAR(120) NOT NULL UNIQUE,
    description  VARCHAR(500),
    auto_send    BOOLEAN      NOT NULL DEFAULT false,
    active       BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID
);

CREATE TABLE sequence_steps (
    id                 UUID PRIMARY KEY,
    sequence_id        UUID         NOT NULL REFERENCES sequences (id) ON DELETE CASCADE,
    day_offset         INT          NOT NULL CHECK (day_offset >= 0),
    type               VARCHAR(10)  NOT NULL CHECK (type IN ('EMAIL', 'CALL', 'TASK')),
    label              VARCHAR(200) NOT NULL,
    email_template_id  UUID REFERENCES email_templates (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID,
    updated_by         UUID,
    UNIQUE (sequence_id, day_offset, type)
);

CREATE TABLE sequence_enrollments (
    id           UUID PRIMARY KEY,
    contact_id   UUID         NOT NULL REFERENCES contacts (id) ON DELETE CASCADE,
    sequence_id  UUID         NOT NULL REFERENCES sequences (id) ON DELETE CASCADE,
    enrolled_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                 CHECK (status IN ('ACTIVE', 'TERMINEE', 'ARRETEE')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID
);
CREATE INDEX ix_enrollments_contact ON sequence_enrollments (contact_id);

-- ---------------------------------------------------------------------
-- Marketing
-- ---------------------------------------------------------------------
CREATE TABLE campaigns (
    id            UUID PRIMARY KEY,
    name          VARCHAR(160)   NOT NULL,
    channel       VARCHAR(40)    NOT NULL,
    spend_usd     NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (spend_usd >= 0),
    impressions   BIGINT         NOT NULL DEFAULT 0 CHECK (impressions >= 0),
    clicks        BIGINT         NOT NULL DEFAULT 0 CHECK (clicks >= 0),
    period_start  DATE,
    period_end    DATE,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    CHECK (period_end IS NULL OR period_start IS NULL OR period_end >= period_start)
);

-- ---------------------------------------------------------------------
-- Journal d'audit
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id           UUID PRIMARY KEY,
    actor_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    action       VARCHAR(40)  NOT NULL,
    entity_type  VARCHAR(40)  NOT NULL,
    entity_id    UUID         NOT NULL,
    details      JSONB,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id, occurred_at DESC);
