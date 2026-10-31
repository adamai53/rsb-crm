import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';

import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { DocumentsApi, QUOTE_STATUS, Quote } from '../../core/api/documents';
import { fmtDateY, fmtMT, fmtN, saveBlob } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { Modal } from '../../shared/ui/modal';

/** Aperçu du devis (même contenu que le PDF) et actions : télécharger, envoyer, accepter, refuser. */
@Component({
  selector: 'rsb-quote-modal',
  imports: [Modal],
  template: `
    @if (quote(); as q) {
      <rsb-modal [heading]="'Devis ' + q.number" [sub]="q.vesselName + ' · statut : ' + status[q.status].label" [wide]="true" (closed)="ui.closeForm()">
        <div class="qwrap">
          <div class="quote">
            <div class="qh">
              <div class="qbrand">
                <img src="assets/logo-rsb-lg.png" alt="Red Sea Bunkering">
                <small>Fourniture de combustibles marins · Djibouti</small>
              </div>
              <div class="qmeta">DEVIS {{ q.number }}<br>Émis le {{ fmtDateY(q.createdAt) }}<br>Valable jusqu'au {{ validUntil() }}</div>
            </div>
            <div class="qgrid">
              <div><small>Client</small><b>{{ q.companyName || '—' }}</b>
                @if (q.contactName) { <br>À l'attention de {{ q.contactName }} }
              </div>
              <div><small>Navire</small><b>{{ q.vesselName }}</b> · IMO {{ q.imo || '—' }}<br>ETA {{ q.eta ? fmtDateY(q.eta) : 'à confirmer' }}</div>
              <div><small>Lieu de livraison</small>{{ q.deliveryLocationLabel || '—' }}<br>Livraison par barge</div>
              <div><small>Commercial</small>{{ q.ownerName || '—' }}</div>
            </div>
            <table>
              <thead><tr><th>Désignation</th><th class="r">Quantité</th><th class="r">PU (USD)</th><th class="r">Montant (USD)</th></tr></thead>
              <tbody>
                <tr><td>{{ q.fuelGradeLabel }} · conforme ISO 8217</td><td class="r num">{{ fmtMT(q.quantityMt) }}</td><td class="r num">{{ fmtN(q.priceUsdPerMt) }}/MT</td><td class="r num">{{ fmtN(q.productAmountUsd) }}</td></tr>
                <tr><td>Frais de barge et d'accostage</td><td class="r num">1</td><td class="r num">{{ fmtN(q.bargeFeeUsd) }}</td><td class="r num">{{ q.bargeFeeUsd ? fmtN(q.bargeFeeUsd) : 'Offerts' }}</td></tr>
                <tr class="tot"><td colspan="3">Total hors taxes</td><td class="r num">{{ fmtN(q.totalUsd) }}</td></tr>
              </tbody>
            </table>
            <ul>
              <li>Prix indicatif, soumis à confirmation de disponibilité au moment de la nomination.</li>
              <li>Tolérance de quantité ± 5 % au choix de l'acheteur.</li>
              <li>BDN et échantillons MARPOL remis à bord à la fin de la livraison.</li>
              <li>Paiement à 30 jours après livraison, sous réserve de l'accord de crédit.</li>
            </ul>
          </div>
        </div>
        <footer class="m-foot">
          <button class="btn ghost" type="button" (click)="ui.closeForm()">Fermer</button>
          <button class="btn" type="button" (click)="openDeal(q)">Ouvrir la transaction</button>
          <button class="btn" type="button" (click)="download(q)">Télécharger le PDF</button>
          @if (canAct()) {
            <button class="btn danger" type="button" [disabled]="busy()" (click)="refuse(q)">Refusé</button>
            <button class="btn" type="button" [disabled]="busy()" (click)="ui.openForm({ kind: 'quote-send', id: q.id })">
              {{ q.status === 'ENVOYE' ? 'Renvoyer par e-mail' : 'Envoyer par e-mail' }}
            </button>
            <button class="btn primary" type="button" [disabled]="busy()" (click)="accept(q)">Marquer comme accepté</button>
          }
        </footer>
      </rsb-modal>
    }
  `,
})
export class QuoteModal {
  readonly id = input.required<string>();

  private readonly api = inject(DocumentsApi);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);
  protected readonly status = QUOTE_STATUS;
  protected readonly fmtDateY = fmtDateY;
  protected readonly fmtMT = fmtMT;
  protected readonly fmtN = fmtN;

  protected readonly quote = signal<Quote | null>(null);
  protected readonly busy = signal(false);
  protected readonly validUntil = computed(() => {
    const q = this.quote();
    return q
      ? new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'short', timeZone: 'Africa/Djibouti' }).format(new Date(q.validUntil))
      : '';
  });
  /** Actions réservées au commercial du dossier et aux managers ; l'API le vérifie aussi. */
  protected readonly canAct = computed(() => {
    const q = this.quote();
    if (!q || (q.status !== 'BROUILLON' && q.status !== 'ENVOYE')) return false;
    return this.auth.hasRole('ADMIN', 'MANAGER') || q.ownerId === this.auth.user()?.id;
  });

  constructor() {
    effect(() => {
      const id = this.id();
      this.ui.version();
      untracked(() =>
        this.api.quote(id).subscribe({
          next: (q) => this.quote.set(q),
          error: (e) => {
            this.toast.show(errorMessage(e));
            this.ui.closeForm();
          },
        }),
      );
    });
  }

  protected openDeal(q: Quote): void {
    this.ui.closeForm();
    this.ui.openRecord('deal', q.dealId);
  }

  protected download(q: Quote): void {
    this.api.quotePdf(q.id).subscribe({
      next: (blob) => saveBlob(blob, `Devis-${q.number}.pdf`),
      error: (e) => this.toast.show(errorMessage(e, 'Le PDF est indisponible.')),
    });
  }

  protected accept(q: Quote): void {
    this.act(this.api.acceptQuote(q.id), 'Devis accepté : transaction en « Nomination confirmée »');
  }

  protected refuse(q: Quote): void {
    if (confirm(`Marquer le devis ${q.number} comme refusé par le client ?`)) {
      this.act(this.api.refuseQuote(q.id), 'Devis marqué comme refusé');
    }
  }

  private act(request: ReturnType<DocumentsApi['acceptQuote']>, message: string): void {
    this.busy.set(true);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.show(message);
        this.ui.changed();
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.show(errorMessage(e));
      },
    });
  }
}
