/** Navigation de la barre latérale, reprise du prototype (groupes Ventes / Marketing / Analyse). */
export interface NavItem {
  path: string;
  label: string;
  description: string;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

export const NAV: NavGroup[] = [
  {
    label: 'Ventes',
    items: [
      { path: 'dashboard', label: 'Tableau de bord', description: "Vue d'ensemble du pipeline de soutage." },
      { path: 'inbox', label: 'Leads entrants', description: 'Demandes reçues du site, des campagnes et par e-mail.' },
      { path: 'contacts', label: 'Contacts', description: 'Acheteurs, opérateurs et agents, avec score et cycle de vie.' },
      { path: 'companies', label: 'Entreprises', description: 'Armateurs, ship managers, agents, traders et affréteurs.' },
      { path: 'deals', label: 'Transactions', description: 'Pipeline des escales de soutage, en kanban ou en liste.' },
      { path: 'tasks', label: 'Tâches', description: 'Relances et actions à faire, par échéance et priorité.' },
      { path: 'calls', label: 'Escales', description: 'Calendrier des navires attendus sur 21 jours.' },
      { path: 'quotes', label: 'Devis', description: 'Cotations envoyées, acceptées ou expirées.' },
    ],
  },
  {
    label: 'Marketing',
    items: [
      { path: 'emails', label: 'E-mails & séquences', description: "Modèles d'e-mails, envois et séquences de relance." },
      { path: 'marketing', label: 'Campagnes & formulaires', description: 'Campagnes, coût par lead, ROI et formulaire du site.' },
    ],
  },
  {
    label: 'Analyse',
    items: [
      { path: 'reports', label: 'Rapports', description: 'CA mensuel, entonnoir, volumes, motifs de perte, performance.' },
      { path: 'compare', label: 'Comparatif HubSpot', description: 'Ce que couvre ce CRM face à HubSpot.' },
    ],
  },
];

/** Groupe visible uniquement par les administrateurs. */
export const ADMIN_NAV: NavGroup = {
  label: 'Paramètres',
  items: [{ path: 'admin', label: 'Administration', description: 'Comptes utilisateurs, rôles et validations.' }],
};
