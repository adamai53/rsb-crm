import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { ROLE_LABELS } from '../core/auth/auth.models';
import { AuthService } from '../core/auth/auth.service';
import { ADMIN_NAV, NAV } from '../core/nav';
import { ReferenceService } from '../core/reference.service';
import { UiService } from '../core/ui.service';
import { CompanyDrawer } from '../features/companies/company-drawer';
import { CompanyForm } from '../features/companies/company-form';
import { ContactDrawer } from '../features/contacts/contact-drawer';
import { ContactForm } from '../features/contacts/contact-form';
import { GlobalSearch } from './global-search';

/**
 * Mise en page de l'application connectée : barre latérale, barre du haut, zone de travail.
 * Héberge aussi la fiche ouverte (paramètre d'URL « fiche ») et le formulaire modal en cours.
 */
@Component({
  selector: 'rsb-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, GlobalSearch, ContactDrawer, CompanyDrawer, ContactForm, CompanyForm],
  templateUrl: './shell.html',
  host: { class: 'app' },
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly ui = inject(UiService);
  protected readonly user = this.auth.user;
  protected readonly nav = computed(() => (this.auth.hasRole('ADMIN') ? [...NAV, ADMIN_NAV] : NAV));
  protected readonly roleLabel = computed(() => {
    const u = this.user();
    return u ? ROLE_LABELS[u.role] : '';
  });
  protected readonly menu = signal<'create' | 'user' | null>(null);

  /** Fiche ouverte : ?fiche=contact:<uuid> ou ?fiche=company:<uuid>. */
  protected readonly record = toSignal(
    inject(ActivatedRoute).queryParamMap.pipe(
      map((p) => {
        const [type, id] = (p.get('fiche') ?? '').split(':');
        return type && id ? { type, id } : null;
      }),
    ),
    { initialValue: null },
  );

  constructor() {
    inject(ReferenceService).load();
  }

  protected toggle(which: 'create' | 'user'): void {
    this.menu.update((m) => (m === which ? null : which));
  }

  protected create(kind: 'contact' | 'company'): void {
    this.menu.set(null);
    this.ui.openForm({ kind });
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
