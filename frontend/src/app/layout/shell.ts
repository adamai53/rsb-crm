import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ROLE_LABELS } from '../core/auth/auth.models';
import { AuthService } from '../core/auth/auth.service';
import { ADMIN_NAV, NAV } from '../core/nav';

/** Mise en page de l'application connectée : barre latérale, barre du haut, zone de travail. */
@Component({
  selector: 'rsb-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  host: { class: 'app' },
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly user = this.auth.user;
  protected readonly nav = computed(() => (this.auth.hasRole('ADMIN') ? [...NAV, ADMIN_NAV] : NAV));
  protected readonly roleLabel = computed(() => {
    const u = this.user();
    return u ? ROLE_LABELS[u.role] : '';
  });
  protected readonly menu = signal<'create' | 'user' | null>(null);

  protected toggle(which: 'create' | 'user'): void {
    this.menu.update((m) => (m === which ? null : which));
  }

  /** Ferme les menus au clic extérieur ou sur Échap. */
  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    if (!(event.target as HTMLElement).closest('.menu-wrap')) this.menu.set(null);
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.menu.set(null);
  }
}
