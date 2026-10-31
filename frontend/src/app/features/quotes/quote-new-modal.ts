import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { errorMessage } from '../../core/api-error';
import { DocumentsApi } from '../../core/api/documents';
import { DealRow, SalesApi } from '../../core/api/sales';
import { UiService } from '../../core/ui.service';
import { Modal } from '../../shared/ui/modal';

/** Nouveau devis : transaction ouverte, prix (celui de la transaction par défaut), frais de barge (1 450 USD). */
@Component({
  selector: 'rsb-quote-new-modal',
  imports: [ReactiveFormsModule, Modal],
  template: `
    <rsb-modal heading="Nouveau devis" sub="Validité 48 heures à partir de maintenant." (closed)="ui.closeForm()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="fgrid">
          <label class="fld wide">
            <span>Transaction</span>
            <select formControlName="dealId" required (change)="applyDealPrice()">
              <option [ngValue]="null" disabled>Choisir une transaction…</option>
              @for (d of deals(); track d.id) {
                <option [ngValue]="d.id">{{ d.vesselName }} · {{ d.fuelGrade }} {{ d.quantityMt }} MT · {{ d.companyName || 'sans entreprise' }}</option>
              }
            </select>
            @if (form.controls.dealId.touched && form.controls.dealId.invalid) { <em class="fld-err">Choisissez une transaction.</em> }
          </label>
          <label class="fld">
            <span>Prix (USD/MT)</span>
            <input type="number" formControlName="priceUsdPerMt" min="0" step="0.5">
          </label>
          <label class="fld">
            <span>Frais de barge (USD)</span>
            <input type="number" formControlName="bargeFeeUsd" min="0" step="50">
          </label>
        </div>
        @if (error()) { <p class="auth-error form-error" role="alert">{{ error() }}</p> }
        <footer class="m-foot">
          <button type="button" class="btn ghost" (click)="ui.closeForm()">Annuler</button>
          <button class="btn primary" type="submit" [disabled]="saving()">Créer le devis</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class QuoteNewModal implements OnInit {
  readonly dealId = input<string>();

  private readonly sales = inject(SalesApi);
  private readonly api = inject(DocumentsApi);
  protected readonly ui = inject(UiService);

  protected readonly deals = signal<DealRow[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).group({
    dealId: [null as string | null, Validators.required],
    priceUsdPerMt: [null as number | null, Validators.min(0)],
    bargeFeeUsd: [1450 as number | null, Validators.min(0)],
  });

  ngOnInit(): void {
    this.sales.deals().subscribe((list) => {
      this.deals.set(list.content.filter((d) => !d.won && !d.lost));
      if (this.dealId()) {
        this.form.patchValue({ dealId: this.dealId()! });
        this.applyDealPrice();
      }
    });
  }

  protected applyDealPrice(): void {
    const deal = this.deals().find((d) => d.id === this.form.value.dealId);
    if (deal) this.form.patchValue({ priceUsdPerMt: deal.priceUsdPerMt });
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    this.api.createQuote({ dealId: v.dealId!, priceUsdPerMt: v.priceUsdPerMt, bargeFeeUsd: v.bargeFeeUsd }).subscribe({
      next: (res) => {
        this.ui.changed();
        this.ui.openForm({ kind: 'quote', id: res.id });
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
