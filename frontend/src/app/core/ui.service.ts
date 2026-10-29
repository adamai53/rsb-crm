import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

export type RecordType = 'contact' | 'company' | 'deal' | 'lead';

export type FormRequest =
  | { kind: 'contact'; id?: string; companyId?: string }
  | { kind: 'company'; id?: string }
  | { kind: 'deal'; id?: string; companyId?: string; contactId?: string }
  | { kind: 'task'; dealId?: string; contactId?: string }
  | { kind: 'lead' }
  | { kind: 'lost'; dealId: string; vesselName: string };

/**
 * État d'interface partagé : fiche ouverte (dans l'URL, ?fiche=contact:<id>, pour pouvoir la partager
 * et revenir en arrière), formulaire modal ouvert, et compteur de rafraîchissement des listes.
 */
@Injectable({ providedIn: 'root' })
export class UiService {
  private readonly router = inject(Router);

  readonly form = signal<FormRequest | null>(null);
  /** Incrémenté après chaque modification : les listes, fiches et compteurs se rechargent. */
  readonly version = signal(0);

  openRecord(type: RecordType, id: string): void {
    this.router.navigate([], { queryParams: { fiche: `${type}:${id}` }, queryParamsHandling: 'merge' });
  }

  closeRecord(): void {
    this.router.navigate([], { queryParams: { fiche: null }, queryParamsHandling: 'merge' });
  }

  openForm(request: FormRequest): void {
    this.form.set(request);
  }

  closeForm(): void {
    this.form.set(null);
  }

  changed(): void {
    this.version.update((v) => v + 1);
  }
}
