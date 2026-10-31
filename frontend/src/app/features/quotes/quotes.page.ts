import { Component, effect, inject, signal, untracked } from '@angular/core';

import { DocumentsApi, QUOTE_STATUS, Quote } from '../../core/api/documents';
import { fmtDate, fmtMT, fmtN, fmtUSD } from '../../core/format';
import { UiService } from '../../core/ui.service';

/** Devis générés depuis les transactions. */
@Component({
  selector: 'rsb-quotes',
  template: `
    <div class="page-head">
      <div>
        <h1>Devis</h1>
        <p>Cotations générées depuis les transactions. Un devis accepté fait passer la transaction en « Nomination confirmée ».</p>
      </div>
      <div class="actions">
        <button class="btn primary" type="button" (click)="ui.openForm({ kind: 'quote-new' })">Nouveau devis</button>
      </div>
    </div>

    <div class="tbl">
      <table>
        <thead>
          <tr>
            <th>Numéro</th><th>Navire</th><th>Client</th><th>Produit</th><th class="r">Quantité</th>
            <th class="r">Prix/MT</th><th class="r">Total</th><th>Créé</th><th>Statut</th>
          </tr>
        </thead>
        <tbody>
          @for (q of quotes(); track q.id) {
            <tr class="row" tabindex="0" (click)="ui.openForm({ kind: 'quote', id: q.id })" (keydown.enter)="ui.openForm({ kind: 'quote', id: q.id })">
              <td class="num"><b>{{ q.number }}</b></td>
              <td>{{ q.vesselName }}</td>
              <td>{{ q.companyName || '—' }}</td>
              <td><span class="grade">{{ q.fuelGrade }}</span></td>
              <td class="r num">{{ fmtMT(q.quantityMt) }}</td>
              <td class="r num">{{ fmtN(q.priceUsdPerMt) }}</td>
              <td class="r num">{{ fmtUSD(q.totalUsd) }}</td>
              <td class="muted">{{ fmtDate(q.createdAt) }}</td>
              <td><span [class]="'pill ' + status[q.status].cls">{{ status[q.status].label }}</span></td>
            </tr>
          } @empty {
            <tr><td colspan="9" class="empty">{{ loaded() ? 'Aucun devis pour le moment.' : 'Chargement…' }}</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class QuotesPage {
  private readonly api = inject(DocumentsApi);
  protected readonly ui = inject(UiService);
  protected readonly status = QUOTE_STATUS;
  protected readonly fmtDate = fmtDate;
  protected readonly fmtMT = fmtMT;
  protected readonly fmtN = fmtN;
  protected readonly fmtUSD = fmtUSD;

  protected readonly quotes = signal<Quote[]>([]);
  protected readonly loaded = signal(false);

  constructor() {
    effect(() => {
      this.ui.version();
      untracked(() =>
        this.api.quotes().subscribe((list) => {
          this.quotes.set(list);
          this.loaded.set(true);
        }),
      );
    });
  }
}
