import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { errorMessage } from '../../core/api-error';
import { SalesApi } from '../../core/api/sales';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { Modal } from '../../shared/ui/modal';

/** Règle 5 : une transaction ne passe en « Perdue » qu'avec un motif. */
@Component({
  selector: 'rsb-lost-reason-modal',
  imports: [FormsModule, Modal],
  template: `
    <rsb-modal heading="Motif de perte" [sub]="vesselName()" (closed)="cancel()">
      <form (ngSubmit)="submit()">
        <div class="fgrid">
          <label class="fld wide">
            <span>Motif</span>
            <select name="reason" [(ngModel)]="reason" required>
              @for (r of reasons(); track r) { <option [value]="r">{{ r }}</option> }
            </select>
          </label>
          @if (reason === 'Autre') {
            <label class="fld wide">
              <span>Précisez</span>
              <input name="detail" [(ngModel)]="detail" maxlength="60" placeholder="Ex. : armateur passé chez un concurrent">
            </label>
          }
        </div>
        <footer class="m-foot">
          <button type="button" class="btn ghost" (click)="cancel()">Annuler</button>
          <button class="btn primary" type="submit" [disabled]="saving() || !reason">Marquer comme perdue</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class LostReasonModal implements OnInit {
  readonly dealId = input.required<string>();
  readonly vesselName = input.required<string>();

  private readonly api = inject(SalesApi);
  private readonly toast = inject(ToastService);
  private readonly ui = inject(UiService);

  protected readonly reasons = signal<string[]>([]);
  protected readonly saving = signal(false);
  protected reason = '';
  protected detail = '';

  ngOnInit(): void {
    this.api.lostReasons().subscribe((r) => {
      this.reasons.set(r);
      this.reason = r[0] ?? '';
    });
  }

  protected cancel(): void {
    this.ui.closeForm();
    // Remet la carte dans sa colonne d'origine si elle avait été glissée
    this.ui.changed();
  }

  protected submit(): void {
    const reason = this.reason === 'Autre' && this.detail.trim() ? `Autre : ${this.detail.trim()}` : this.reason;
    this.saving.set(true);
    this.api.moveDeal(this.dealId(), 'PERDUE', reason).subscribe({
      next: () => {
        this.toast.show('Transaction marquée perdue');
        this.ui.closeForm();
        this.ui.changed();
      },
      error: (e) => {
        this.saving.set(false);
        this.toast.show(errorMessage(e));
      },
    });
  }
}
