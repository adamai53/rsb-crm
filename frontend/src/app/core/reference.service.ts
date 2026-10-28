import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';

import { CompanyType, Lifecycle, ReferenceData, UserRef } from './api/models';

const EMPTY: ReferenceData = {
  companyTypes: [], lifecycles: [], sources: [], stages: [], fuelGrades: [], deliveryLocations: [], users: [],
};

/** Listes de valeurs de l'API (/api/reference), chargées une fois après connexion. */
@Injectable({ providedIn: 'root' })
export class ReferenceService {
  private readonly http = inject(HttpClient);
  private readonly data = signal<ReferenceData>(EMPTY);
  private loaded = false;

  readonly companyTypes = computed(() => this.data().companyTypes);
  readonly sources = computed(() => this.data().sources);
  readonly users = computed(() => this.data().users);
  readonly activeUsers = computed(() => this.data().users.filter((u) => u.active));

  load(force = false): void {
    if (this.loaded && !force) return;
    this.loaded = true;
    this.http.get<ReferenceData>('/api/reference').subscribe({
      next: (d) => this.data.set(d),
      error: () => (this.loaded = false),
    });
  }

  user(id: string | null | undefined): UserRef | undefined {
    return id ? this.data().users.find((u) => u.id === id) : undefined;
  }

  companyTypeLabel(code: CompanyType): string {
    return this.data().companyTypes.find((t) => t.code === code)?.label ?? code;
  }
}

export const LIFECYCLES: Lifecycle[] = ['LEAD', 'MQL', 'SQL', 'OPPORTUNITE', 'CLIENT'];

export const LIFECYCLE_LABELS: Record<Lifecycle, string> = {
  LEAD: 'Lead', MQL: 'MQL', SQL: 'SQL', OPPORTUNITE: 'Opportunité', CLIENT: 'Client',
};

/** Couleur des pastilles de cycle de vie (reprise du prototype). */
export const LIFECYCLE_CLASS: Record<Lifecycle, string> = {
  LEAD: '', MQL: 'p-info', SQL: 'p-sea', OPPORTUNITE: 'p-accent', CLIENT: 'p-good',
};

/** Couleur des pastilles d'étape du pipeline. */
export const STAGE_CLASS: Record<string, string> = {
  RECUE: '', QUALIFIEE: 'p-info', COTATION: 'p-sea', NEGOCIATION: 'p-warn', NOMINATION: 'p-accent',
  LIVREE: 'p-good', PERDUE: 'p-bad',
};
