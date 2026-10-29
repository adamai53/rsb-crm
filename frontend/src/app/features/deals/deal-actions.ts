import { Injectable, inject } from '@angular/core';

import { errorMessage } from '../../core/api-error';
import { SalesApi } from '../../core/api/sales';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';

/** Changement d'étape partagé par le pipeline et la fiche : le passage en « Perdue » demande d'abord le motif. */
@Injectable({ providedIn: 'root' })
export class DealActions {
  private readonly api = inject(SalesApi);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  private readonly ui = inject(UiService);

  move(deal: { id: string; vesselName: string; stage: string }, stage: string): void {
    if (deal.stage === stage) return;
    if (this.ref.stage(stage)?.lost) {
      this.ui.openForm({ kind: 'lost', dealId: deal.id, vesselName: deal.vesselName });
      return;
    }
    this.api.moveDeal(deal.id, stage).subscribe({
      next: () => {
        const target = this.ref.stage(stage);
        this.toast.show(target?.won ? 'Livraison enregistrée, contact passé en Client' : `Étape mise à jour : ${target?.label ?? stage}`);
        this.ui.changed();
      },
      error: (e) => {
        this.toast.show(errorMessage(e));
        this.ui.changed();
      },
    });
  }
}
