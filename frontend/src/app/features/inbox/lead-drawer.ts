import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';

import { errorMessage } from '../../core/api-error';
import { Lead, SalesApi } from '../../core/api/sales';
import { fmtDateY, fmtMT, fmtUSD } from '../../core/format';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { Drawer } from '../../shared/ui/drawer';
import { LEAD_STATUS } from './inbox.page';

/** Fiche d'un lead entrant : coordonnées, escale demandée, message ; conversion ou rejet. */
@Component({
  selector: 'rsb-lead-drawer',
  imports: [Drawer],
  template: `
    @if (lead(); as l) {
      <rsb-drawer kind="Lead entrant" [heading]="l.name" (closed)="ui.closeRecord()">
        <span sub>{{ l.companyName || 'Entreprise non renseignée' }} · via {{ l.source || 'source inconnue' }}</span>
        <ng-container actions>
          @if (l.status === 'NOUVEAU') {
            <button class="btn primary sm" type="button" [disabled]="busy()" (click)="convert()">Qualifier et convertir</button>
            <button class="btn sm danger" type="button" [disabled]="busy()" (click)="reject()">Rejeter</button>
          } @else {
            <span [class]="'pill ' + status[l.status].cls">{{ l.status === 'CONVERTI' ? 'Converti en transaction' : 'Rejeté' }}</span>
            @if (l.convertedDealId) {
              <button class="btn sm" type="button" (click)="ui.openRecord('deal', l.convertedDealId)">Ouvrir la transaction</button>
            }
          }
        </ng-container>

        <div class="props">
          <div class="prop"><small>E-mail</small><div class="mono prop-copy">{{ l.email || '—' }}</div></div>
          <div class="prop"><small>Téléphone</small><div class="mono prop-copy">{{ l.phone || '—' }}</div></div>
          <div class="prop"><small>Navire · IMO</small><div>{{ l.vesselName || '—' }} · <span class="num">{{ l.imo || '—' }}</span></div></div>
          <div class="prop"><small>ETA</small><div>{{ fmtDateY(l.eta) }}</div></div>
          <div class="prop"><small>Produit</small><div><span class="grade">{{ gradeLabel() }}</span></div></div>
          <div class="prop"><small>Quantité</small><div class="num">{{ l.quantityMt ? fmtMT(l.quantityMt) : '—' }}</div></div>
          <div class="prop"><small>Lieu</small><div>{{ locationLabel() }}</div></div>
          <div class="prop"><small>Valeur estimée</small><div class="num">{{ l.estimatedValueUsd ? fmtUSD(l.estimatedValueUsd) : '—' }}</div></div>
        </div>
        <div class="panel">
          <h2>Message</h2>
          <p class="lead-message">{{ l.message || 'Aucun message.' }}</p>
        </div>
        @if (l.status === 'NOUVEAU') {
          <p class="muted small-note">La conversion crée l'entreprise si elle n'existe pas, le contact et une transaction à l'étape
            « Qualifiée », attribuée au commercial le moins chargé, avec une tâche « Envoyer la cotation » pour aujourd'hui.</p>
        }
      </rsb-drawer>
    }
  `,
})
export class LeadDrawer {
  readonly id = input.required<string>();

  private readonly api = inject(SalesApi);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);
  protected readonly status = LEAD_STATUS;
  protected readonly fmtDateY = fmtDateY;
  protected readonly fmtMT = fmtMT;
  protected readonly fmtUSD = fmtUSD;

  protected readonly lead = signal<Lead | null>(null);
  protected readonly busy = signal(false);
  protected readonly gradeLabel = computed(() => {
    const code = this.lead()?.fuelGrade;
    return this.ref.fuelGrade(code)?.label ?? code ?? '—';
  });
  protected readonly locationLabel = computed(() => {
    const code = this.lead()?.deliveryLocation;
    return this.ref.deliveryLocations().find((l) => l.code === code)?.label ?? code ?? '—';
  });

  constructor() {
    effect(() => {
      const id = this.id();
      this.ui.version();
      untracked(() =>
        this.api.lead(id).subscribe({ next: (l) => this.lead.set(l), error: () => this.ui.closeRecord() }),
      );
    });
  }

  protected convert(): void {
    this.busy.set(true);
    this.api.convertLead(this.id()).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.toast.show(`Converti et attribué à ${r.ownerName}`);
        this.ui.changed();
        this.ui.openRecord('deal', r.dealId);
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.show(errorMessage(e));
      },
    });
  }

  protected reject(): void {
    if (!confirm('Rejeter cette demande ? Elle restera visible dans « Rejetées ».')) return;
    this.busy.set(true);
    this.api.rejectLead(this.id()).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.show('Demande rejetée');
        this.ui.changed();
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.show(errorMessage(e));
      },
    });
  }
}
