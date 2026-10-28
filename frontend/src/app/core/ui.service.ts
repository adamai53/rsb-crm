import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

export type RecordType = 'contact' | 'company' | 'deal';

export type FormRequest =
  | { kind: 'contact'; id?: string; companyId?: string }
  | { kind: 'company'; id?: string };

/**
 * État d'interface partagé : fiche ouverte (dans l'URL, ?fiche=contact:<id>, pour pouvoir la partager
 * et revenir en arrière), formulaire modal ouvert, et compteur de rafraîchissement des listes.
 */
@Injectable({ providedIn: 'root' })
export class UiService {
  private readonly router = inject(Router);

  readonly form = signal<FormRequest | null>(null);
  /** Incrémenté après chaque modification : les listes et fiches se rechargent. */
  readonly version = signal(0);

  openRecord(type: RecordType, id: string): void {
    if (type === 'deal') {
      // La fiche transaction arrive avec le module Ventes (étape 4)
      this.router.navigate(['/deals'], { queryParams: { fiche: `deal:${id}` } });
      return;
    }
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
