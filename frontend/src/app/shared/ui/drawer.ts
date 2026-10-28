import { Component, ElementRef, HostListener, afterNextRender, inject, input, output } from '@angular/core';

/** Tiroir latéral droit des fiches (contact, entreprise…), avec fond cliquable et fermeture par Échap. */
@Component({
  selector: 'rsb-drawer',
  template: `
    <div class="backdrop" (click)="closed.emit()"></div>
    <aside class="drawer" role="dialog" aria-modal="true" [attr.aria-label]="kind() + ' : ' + heading()" tabindex="-1">
      <div class="dr-head">
        <div class="dr-top">
          <div>
            <div class="dr-kind">{{ kind() }}</div>
            <h2>{{ heading() }}</h2>
            <div class="muted dr-sub"><ng-content select="[sub]" /></div>
          </div>
          <button class="x" type="button" aria-label="Fermer la fiche" (click)="closed.emit()">×</button>
        </div>
        <div class="actions"><ng-content select="[actions]" /></div>
      </div>
      <div class="dr-body"><ng-content /></div>
    </aside>
  `,
})
export class Drawer {
  readonly kind = input.required<string>();
  readonly heading = input.required<string>();
  readonly closed = output<void>();

  private readonly host = inject(ElementRef<HTMLElement>);

  constructor() {
    afterNextRender(() => (this.host.nativeElement.querySelector('.drawer') as HTMLElement | null)?.focus());
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    // Une fenêtre modale ouverte par-dessus se ferme d'abord
    if (!document.querySelector('.modal-wrap')) this.closed.emit();
  }
}
