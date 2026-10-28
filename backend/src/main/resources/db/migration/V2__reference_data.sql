-- Valeurs initiales des référentiels (modifiables ensuite par un ADMIN)

INSERT INTO pipeline_stages (id, code, label, probability, sort_order, is_won, is_lost) VALUES
    (gen_random_uuid(), 'RECUE',       'Demande reçue',        10, 1, false, false),
    (gen_random_uuid(), 'QUALIFIEE',   'Qualifiée',            25, 2, false, false),
    (gen_random_uuid(), 'COTATION',    'Cotation envoyée',     50, 3, false, false),
    (gen_random_uuid(), 'NEGOCIATION', 'Négociation',          70, 4, false, false),
    (gen_random_uuid(), 'NOMINATION',  'Nomination confirmée', 90, 5, false, false),
    (gen_random_uuid(), 'LIVREE',      'Livrée (BDN signé)',  100, 6, true,  false),
    (gen_random_uuid(), 'PERDUE',      'Perdue',                0, 7, false, true);

INSERT INTO fuel_grades (id, code, label, reference_price, sort_order) VALUES
    (gen_random_uuid(), 'VLSFO', 'VLSFO 0,5 % S',   612, 1),
    (gen_random_uuid(), 'HSFO',  'HSFO 380 cSt',    468, 2),
    (gen_random_uuid(), 'MGO',   'MGO DMA 0,1 % S', 845, 3),
    (gen_random_uuid(), 'LSMGO', 'LSMGO 0,1 % S',   862, 4);

INSERT INTO delivery_locations (id, code, label, sort_order) VALUES
    (gen_random_uuid(), 'DMP',       'Port de Doraleh (DMP)',          1),
    (gen_random_uuid(), 'ANCHORAGE', 'Djibouti — mouillage extérieur', 2),
    (gen_random_uuid(), 'PAID',      'Port de Djibouti (PAID)',        3);
