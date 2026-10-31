-- Modèles d'e-mails et séquence « Relance cotation » (repris du prototype), suivi des envois automatiques

INSERT INTO email_templates (id, name, subject, body) VALUES
    (gen_random_uuid(), 'Accusé de réception', 'Votre demande de soutage pour {{navire}}',
     E'Bonjour {{prenom}},\n\nNous avons bien reçu votre demande pour {{navire}} ({{quantite}} de {{produit}}, ETA {{eta}}).\nNotre équipe revient vers vous avec une cotation sous 2 heures.\n\nCordialement,\n{{commercial}}\nRed Sea Bunkering'),
    (gen_random_uuid(), 'Envoi de cotation', 'Cotation {{produit}} — {{navire}}',
     E'Bonjour {{prenom}},\n\nVeuillez trouver ci-joint notre cotation pour {{quantite}} de {{produit}} livrés à Djibouti, ETA {{eta}}.\nValidité : 48 heures, sous réserve de disponibilité.\n\nCordialement,\n{{commercial}}'),
    (gen_random_uuid(), 'Relance cotation J+2', 'Suite à notre cotation pour {{navire}}',
     E'Bonjour {{prenom}},\n\nAvez-vous pu étudier notre cotation pour {{navire}} ? Nous pouvons ajuster le créneau de livraison si besoin.\n\nCordialement,\n{{commercial}}'),
    (gen_random_uuid(), 'Confirmation de nomination', 'Nomination confirmée — {{navire}}',
     E'Bonjour {{prenom}},\n\nNous confirmons la nomination de {{quantite}} de {{produit}} pour {{navire}}. La barge sera le long du bord à l''arrivée prévue le {{eta}}.\n\nCordialement,\n{{commercial}}'),
    (gen_random_uuid(), 'Suivi après livraison', 'BDN et suivi de livraison — {{navire}}',
     E'Bonjour {{prenom}},\n\nLa livraison pour {{navire}} est terminée. Vous trouverez le BDN et les références des échantillons en pièce jointe.\nMerci pour votre confiance, {{entreprise}}.\n\n{{commercial}}');

INSERT INTO sequences (id, name, description, auto_send) VALUES
    (gen_random_uuid(), 'Relance cotation', 'Quatre étapes sur six jours, créées dans les tâches du commercial.', false);

INSERT INTO sequence_steps (id, sequence_id, day_offset, type, label, email_template_id)
SELECT gen_random_uuid(), s.id, v.day_offset, v.type, v.label, t.id
FROM sequences s
CROSS JOIN (VALUES
    (0, 'EMAIL', 'Envoi de cotation', 'Envoi de cotation'),
    (2, 'EMAIL', 'Relance cotation', 'Relance cotation J+2'),
    (4, 'CALL', 'Point sur la décision', NULL),
    (6, 'TASK', 'Clôturer ou reprogrammer', NULL)
) AS v(day_offset, type, label, template)
LEFT JOIN email_templates t ON t.name = v.template
WHERE s.name = 'Relance cotation';

-- Un contact n'est inscrit qu'une fois à la fois à une même séquence
CREATE UNIQUE INDEX ux_enrollments_active ON sequence_enrollments (contact_id, sequence_id) WHERE status = 'ACTIVE';

-- Lien tâche ↔ étape de séquence : l'envoi automatique clôt la tâche correspondante et n'envoie qu'une fois
ALTER TABLE tasks
    ADD COLUMN enrollment_id UUID REFERENCES sequence_enrollments (id) ON DELETE SET NULL,
    ADD COLUMN sequence_step_id UUID REFERENCES sequence_steps (id) ON DELETE SET NULL;
CREATE INDEX ix_tasks_enrollment ON tasks (enrollment_id);

CREATE TABLE sequence_step_runs (
    enrollment_id  UUID        NOT NULL REFERENCES sequence_enrollments (id) ON DELETE CASCADE,
    step_id        UUID        NOT NULL REFERENCES sequence_steps (id) ON DELETE CASCADE,
    run_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (enrollment_id, step_id)
);
