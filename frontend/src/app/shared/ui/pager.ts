import { Component, computed, input, output } from '@angular/core';

/** Pagination sous les tableaux : « 1–50 sur 132 » et boutons précédent / suivant. */
@Component({
  selector: 'rsb-pager',
  template: `
    @if (total() > size()) {
      <nav class="pager" aria-label="Pagination">
        <span class="muted">{{ from() }}–{{ to() }} sur {{ total() }}</span>
        <button class="btn sm" type="button" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)">Précédent</button>
        <button class="btn sm" type="button" [disabled]="to() >= total()" (click)="pageChange.emit(page() + 1)">Suivant</button>
      </nav>
    }
  `,
})
export class Pager {
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly total = input.required<number>();
  readonly pageChange = output<number>();

  protected readonly from = computed(() => this.page() * this.size() + 1);
  protected readonly to = computed(() => Math.min(this.total(), (this.page() + 1) * this.size()));
}
