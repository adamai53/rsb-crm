import { Component, computed, effect, inject, signal, untracked } from '@angular/core';

import { Lead, LeadStatus, SalesApi } from '../../core/api/sales';
import { fmtDate, fmtMT } from '../../core/format';
import { UiService } from '../../core/ui.service';

export const LEAD_STATUS: Record<LeadStatus, { label: string; cls: string }> = {
  NOUVEAU: { label: 'Nouveau', cls: 'p-accent' },
  CONVERTI: { label: 'Converti', cls: 'p-good' },
  REJETE: { label: 'Rejeté', cls: 'p-bad' },
};

/** « il y a 12 min », « il y a 3 h », « il y a 2 j ». */
export function ago(iso: string, now = Date.now()): string {
  const m = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000));
  return m < 60 ? `il y a ${m} min` : m < 1440 ? `il y a ${Math.round(m / 60)} h` : `il y a ${Math.round(m / 1440)} j`;
}

/** Leads entrants : demandes du site, des campagnes, des appels ; qualification en un clic. */
@Component({
  selector: 'rsb-inbox',
  template: `
    <div class="page-head">
      <div>
        <h1>Leads entrants</h1>
        <p>Demandes arrivées par le formulaire du site, les publicités et les réseaux sociaux. Qualifiez-les pour créer le contact, l'entreprise et la transaction en un clic.</p>
      </div>
      <div class="actions">
        <button class="btn" type="button" (click)="ui.openForm({ kind: 'lead' })">Saisir une demande</button>
      </div>
    </div>

    <div class="toolbar">
      <div class="chips" role="group" aria-label="Filtrer par statut">
        @for (f of filters; track f.id) {
          <button class="chip" type="button" [attr.aria-pressed]="filter() === f.id" (click)="filter.set(f.id)">
            {{ f.label }}<span class="mono">{{ count(f.id) }}</span>
          </button>
        }
      </div>
    </div>

    <div class="tbl">
      <table>
        <thead>
          <tr><th>Statut</th><th>Demandeur</th><th>Navire</th><th>Produit</th><th class="r">Quantité</th><th>ETA</th><th>Source</th><th>Reçu</th></tr>
        </thead>
        <tbody>
          @for (l of visible(); track l.id) {
            <tr class="row" tabindex="0" (click)="ui.openRecord('lead', l.id)" (keydown.enter)="ui.openRecord('lead', l.id)">
              <td><span [class]="'pill ' + status[l.status].cls">{{ status[l.status].label }}</span></td>
              <td><b>{{ l.name }}</b><div class="faint cell-sub">{{ l.companyName }}</div></td>
              <td>{{ l.vesselName || '—' }}</td>
              <td><span class="grade">{{ l.fuelGrade || '—' }}</span></td>
              <td class="r num">{{ l.quantityMt ? fmtMT(l.quantityMt) : '—' }}</td>
              <td class="num">{{ fmtDate(l.eta) }}</td>
              <td>{{ l.source || '—' }}</td>
              <td class="muted">{{ ago(l.receivedAt) }}</td>
            </tr>
          } @empty {
            <tr><td colspan="8" class="empty">{{ loaded() ? 'Aucune demande dans cette catégorie.' : 'Chargement…' }}</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class InboxPage {
  private readonly api = inject(SalesApi);
  protected readonly ui = inject(UiService);
  protected readonly status = LEAD_STATUS;
  protected readonly fmtMT = fmtMT;
  protected readonly fmtDate = fmtDate;
  protected readonly ago = ago;

  protected readonly filters: { id: LeadStatus | 'ALL'; label: string }[] = [
    { id: 'NOUVEAU', label: 'À traiter' },
    { id: 'ALL', label: 'Toutes' },
    { id: 'CONVERTI', label: 'Converties' },
    { id: 'REJETE', label: 'Rejetées' },
  ];
  protected readonly filter = signal<LeadStatus | 'ALL'>('NOUVEAU');
  private readonly leads = signal<Lead[]>([]);
  protected readonly loaded = signal(false);
  protected readonly visible = computed(() =>
    this.filter() === 'ALL' ? this.leads() : this.leads().filter((l) => l.status === this.filter()),
  );

  constructor() {
    effect(() => {
      this.ui.version();
      untracked(() =>
        this.api.leads().subscribe((list) => {
          this.leads.set(list);
          this.loaded.set(true);
        }),
      );
    });
  }

  protected count(id: LeadStatus | 'ALL'): number {
    return id === 'ALL' ? this.leads().length : this.leads().filter((l) => l.status === id).length;
  }
}
