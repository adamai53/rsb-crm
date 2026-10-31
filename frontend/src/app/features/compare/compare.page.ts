import { Component } from '@angular/core';

type Level = 'yes' | 'part' | 'no';

interface Row {
  feature: string;
  hubspot: string;
  level: Level;
  detail: string;
}

/** Couverture du CRM livré ; à tenir à jour quand une fonction arrive ou change de périmètre. */
export const ROWS: Row[] = [
  { feature: 'Contacts et entreprises', hubspot: 'Fiches contact et entreprise reliées', level: 'yes', detail: 'Fiches reliées, propriétaire, cycle de vie' },
  { feature: 'Pipeline de transactions', hubspot: 'Pipeline glisser-déposer', level: 'yes', detail: '7 étapes propres au soutage, de la demande au BDN' },
  { feature: 'Leads entrants et formulaires', hubspot: 'Formulaires et boîte de réception des leads', level: 'yes', detail: 'Formulaire du site et conversion en un clic en contact, entreprise et transaction' },
  { feature: 'Lead scoring', hubspot: 'Score selon propriétés et engagement', level: 'part', detail: 'Règles fixes et explicables dans la fiche' },
  { feature: 'Tâches et rappels', hubspot: 'Tâches et file de travail', level: 'yes', detail: "Retard, aujourd'hui, à venir" },
  { feature: "Journal d'activité", hubspot: 'Notes, appels, e-mails, réunions', level: 'yes', detail: "Visible sur le contact, l'entreprise et la transaction" },
  { feature: "Modèles d'e-mails", hubspot: 'Modèles et champs de personnalisation', level: 'yes', detail: 'Champs maritimes : navire, produit, ETA' },
  { feature: 'Séquences de relance', hubspot: 'Séquences automatisées', level: 'part', detail: 'Crée les relances en tâches ; envoi automatique en option, sans arrêt sur réponse du client' },
  { feature: 'Devis', hubspot: 'Devis avec lignes produit', level: 'yes', detail: 'Prix/MT, frais de barge, validité 48 h, PDF' },
  { feature: 'Attribution marketing', hubspot: 'Campagnes et sources', level: 'part', detail: 'Calculée depuis la source des demandes ; dépenses saisies, pas encore reliées aux régies' },
  { feature: 'Rapports et tableaux de bord', hubspot: 'Rapports personnalisables', level: 'part', detail: 'Rapports prédéfinis' },
  { feature: 'Import CSV', hubspot: 'Import de fichiers', level: 'yes', detail: 'Import de contacts' },
  { feature: 'Recherche globale', hubspot: 'Recherche sur tous les objets', level: 'yes', detail: 'Contacts, entreprises, navires' },
  { feature: 'Propriétés soutage (IMO, ETA, produit, MT, BDN)', hubspot: 'À configurer en propriétés personnalisées', level: 'yes', detail: 'Natives dans le modèle de données' },
  { feature: 'Calendrier des escales', hubspot: 'Non prévu, à construire', level: 'yes', detail: 'Charge par jour sur 21 jours' },
  { feature: 'Devis au format soutage (ISO 8217, MARPOL)', hubspot: 'Modèle à adapter', level: 'yes', detail: 'Natif : prix/MT, barge, conditions de livraison' },
  { feature: "Envoi réel et suivi d'ouverture", hubspot: 'Oui, via la boîte mail connectée', level: 'yes', detail: "Envoi SMTP et pixel d'ouverture, indicatif" },
  { feature: 'Synchronisation Gmail / Outlook', hubspot: 'Oui', level: 'no', detail: 'Prévu en phase 2' },
  { feature: "Workflows d'automatisation", hubspot: 'Oui, sur les offres supérieures', level: 'no', detail: 'Prévu en phase 2' },
  { feature: 'Multi-utilisateurs et droits', hubspot: 'Oui', level: 'yes', detail: 'Rôles ADMIN, MANAGER et SALES, journal des connexions' },
  { feature: 'Application mobile', hubspot: 'Application iOS et Android', level: 'part', detail: 'Interface web adaptée au mobile' },
];

const LEVELS: Record<Level, string> = { yes: 'Inclus', part: 'Partiel', no: 'Non inclus' };

/** Comparatif avec les fonctions CRM essentielles de HubSpot. */
@Component({
  selector: 'rsb-compare',
  template: `
    <div class="page-head">
      <div>
        <h1>Comparatif avec HubSpot</h1>
        <p>Ce que couvre ce CRM par rapport aux fonctions CRM essentielles de HubSpot, et ce qui reste à construire.</p>
      </div>
    </div>
    <div class="grid g-kpi cmp-kpis">
      <div class="kpi"><span class="lbl">Inclus</span><span class="val good">{{ count('yes') }}</span><span class="foot">Fonctionnels dans ce CRM</span></div>
      <div class="kpi"><span class="lbl">Partiels</span><span class="val warn">{{ count('part') }}</span><span class="foot">Version simplifiée</span></div>
      <div class="kpi"><span class="lbl">Phase 2</span><span class="val bad">{{ count('no') }}</span><span class="foot">Nécessitent des intégrations</span></div>
      <div class="kpi"><span class="lbl">Spécifique soutage</span><span class="val">3</span><span class="foot">Absent de HubSpot en standard</span></div>
    </div>
    <div class="tbl">
      <table class="compare">
        <thead><tr><th>Fonctionnalité</th><th>HubSpot</th><th>CRM RSB</th><th>Détail</th></tr></thead>
        <tbody>
          @for (r of rows; track r.feature) {
            <tr>
              <td>{{ r.feature }}</td>
              <td class="muted">{{ r.hubspot }}</td>
              <td [class]="r.level">{{ levels[r.level] }}</td>
              <td class="muted">{{ r.detail }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class ComparePage {
  protected readonly rows = ROWS;
  protected readonly levels = LEVELS;

  protected count(level: Level): number {
    return ROWS.filter((r) => r.level === level).length;
  }
}
