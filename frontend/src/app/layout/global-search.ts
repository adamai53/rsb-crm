import { Component, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { CrmApi } from '../core/api/crm-api';
import { SearchResult } from '../core/api/models';
import { UiService } from '../core/ui.service';

/** Recherche globale de la barre du haut : contacts, entreprises, navires et IMO ; Ctrl+K pour y aller. */
@Component({
  selector: 'rsb-global-search',
  template: `
    <div class="search" role="combobox" [attr.aria-expanded]="open()" aria-haspopup="listbox" aria-owns="gres">
      <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"></circle><path d="m20 20-3.5-3.5"></path></svg>
      <input #box type="search" placeholder="Rechercher un contact, un navire, une entreprise…  (Ctrl+K)"
             autocomplete="off" aria-label="Recherche globale" aria-controls="gres" aria-autocomplete="list"
             [attr.aria-activedescendant]="open() && results().length ? 'gres-' + active() : null"
             [value]="term()" (input)="onInput($any($event.target).value)" (focus)="reopen()" (keydown)="onKey($event)">
      @if (open()) {
        <div class="gres" id="gres" role="listbox">
          @for (r of results(); track r.type + r.id; let i = $index) {
            <button type="button" role="option" [id]="'gres-' + i" [attr.aria-selected]="i === active()"
                    [class.active]="i === active()" (mousedown)="$event.preventDefault()" (click)="pick(r)"
                    (mouseenter)="active.set(i)">
              <span>{{ r.label }}</span><em>{{ r.sub }}</em>
            </button>
          } @empty {
            <div class="empty">{{ loading() ? 'Recherche…' : 'Aucun résultat' }}</div>
          }
        </div>
      }
    </div>
  `,
  host: { class: 'search-host' },
})
export class GlobalSearch {
  private readonly api = inject(CrmApi);
  private readonly ui = inject(UiService);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly box = viewChild.required<ElementRef<HTMLInputElement>>('box');

  protected readonly term = signal('');
  protected readonly results = signal<SearchResult[]>([]);
  protected readonly open = signal(false);
  protected readonly loading = signal(false);
  protected readonly active = signal(0);
  private readonly input$ = new Subject<string>();

  constructor() {
    this.input$
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        switchMap((q) => {
          if (q.length < 2) return of([] as SearchResult[]);
          this.loading.set(true);
          return this.api.search(q).pipe(catchError(() => of([] as SearchResult[])));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((list) => {
        this.loading.set(false);
        this.results.set(list);
        this.active.set(0);
        this.open.set(this.term().trim().length >= 2);
      });
  }

  protected onInput(value: string): void {
    this.term.set(value);
    if (value.trim().length < 2) this.open.set(false);
    this.input$.next(value.trim());
  }

  protected reopen(): void {
    if (this.term().trim().length >= 2) this.open.set(true);
  }

  protected onKey(e: KeyboardEvent): void {
    const n = this.results().length;
    if (e.key === 'ArrowDown' && n) {
      e.preventDefault();
      this.open.set(true);
      this.active.set((this.active() + 1) % n);
    } else if (e.key === 'ArrowUp' && n) {
      e.preventDefault();
      this.active.set((this.active() - 1 + n) % n);
    } else if (e.key === 'Enter' && this.open() && n) {
      e.preventDefault();
      this.pick(this.results()[this.active()]);
    } else if (e.key === 'Escape') {
      this.open.set(false);
    }
  }

  protected pick(r: SearchResult): void {
    this.open.set(false);
    this.term.set('');
    this.results.set([]);
    this.box().nativeElement.blur();
    this.ui.openRecord(r.type, r.id);
  }

  @HostListener('document:keydown', ['$event'])
  protected onShortcut(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      this.box().nativeElement.focus();
      this.box().nativeElement.select();
    }
  }

  @HostListener('document:click', ['$event'])
  protected onOutside(e: MouseEvent): void {
    if (!this.host.nativeElement.contains(e.target as Node)) this.open.set(false);
  }
}
