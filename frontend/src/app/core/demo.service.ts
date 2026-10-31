import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

/** Indique si le serveur tourne avec les données de démonstration (APP_SEED_DEMO). */
@Injectable({ providedIn: 'root' })
export class DemoService {
  private readonly http = inject(HttpClient);
  readonly active = signal(false);

  constructor() {
    this.http.get<{ demo: boolean }>('/api/public/info').subscribe({
      next: (info) => this.active.set(info.demo),
      error: () => this.active.set(false),
    });
  }

  reset() {
    return this.http.post<void>('/api/admin/demo/reset', null);
  }
}
