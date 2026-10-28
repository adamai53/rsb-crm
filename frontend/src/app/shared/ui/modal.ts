import { Component, ElementRef, HostListener, afterNextRender, inject, input, output } from '@angular/core';

/** Fenêtre modale du prototype (.modal-wrap / .modal) : focus sur le premier champ, Échap et clic extérieur ferment. */
@Component({
  selector: 'rsb-modal',
  template: `
    <div class="modal-wrap" (mousedown)="onBackdrop($event)">
      <div class="modal" [class.wide]="wide()" role="dialog" aria-modal="true" [attr.aria-labelledby]="titleId">
        <header class="m-head">
          <h3 [id]="titleId">{{ heading() }}</h3>
          @if (sub()) { <p>{{ sub() }}</p> }
        </header>
        <ng-content />
      </div>
    </div>
  `,
})
export class Modal {
  readonly heading = input.required<string>();
  readonly sub = input('');
  readonly wide = input(false);
  readonly closed = output<void>();

  private static counter = 0;
  protected readonly titleId = `modal-title-${++Modal.counter}`;
  private readonly host = inject(ElementRef<HTMLElement>);

  constructor() {
    afterNextRender(() => {
      const first = this.host.nativeElement.querySelector(
        'input:not([type=hidden]):not([type=file]), select, textarea, button.primary',
      ) as HTMLElement | null;
      first?.focus();
    });
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.closed.emit();
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.closed.emit();
  }
}
