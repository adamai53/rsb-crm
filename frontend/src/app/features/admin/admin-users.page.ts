import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { errorMessage } from '../../core/api-error';
import { ROLE_LABELS, Role, STATUS_LABELS, User, UserStatus } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/toast.service';

type Filter = 'ALL' | UserStatus;

const STATUS_CLASS: Record<UserStatus, string> = {
  PENDING: 'p-info',
  PENDING_APPROVAL: 'p-warn',
  ACTIVE: 'p-good',
  DISABLED: 'p-bad',
};

/** Administration des comptes (ADMIN) : valider, désactiver, changer le rôle, renvoyer la vérification. */
@Component({
  selector: 'rsb-admin-users',
  imports: [DatePipe],
  template: `
    <div class="page-head">
      <div>
        <h1>Administration</h1>
        <p>Validez les nouveaux comptes, ajustez les rôles et désactivez les accès qui ne sont plus utilisés.</p>
      </div>
    </div>

    <div class="toolbar">
      <div class="chips" role="group" aria-label="Filtrer par statut">
        @for (f of filters; track f.id) {
          <button type="button" class="chip" [attr.aria-pressed]="filter() === f.id" (click)="filter.set(f.id)">
            {{ f.label }}<span class="mono">{{ count(f.id) }}</span>
          </button>
        }
      </div>
    </div>

    <div class="tbl">
      <table>
        <thead>
          <tr>
            <th>Utilisateur</th>
            <th>Rôle</th>
            <th>Statut</th>
            <th>Dernière connexion</th>
            <th>Inscrit le</th>
            <th class="r">Actions</th>
          </tr>
        </thead>
        <tbody>
          @for (u of visible(); track u.id) {
            <tr>
              <td>
                <div class="who-cell">
                  <span class="av sm">{{ u.initials }}</span>
                  <div><b>{{ u.firstName }} {{ u.lastName }}</b><br><span class="muted">{{ u.email }}</span></div>
                </div>
              </td>
              <td>
                <select [value]="u.role" [disabled]="u.id === me()?.id || busy() === u.id"
                        [attr.aria-label]="'Rôle de ' + u.firstName + ' ' + u.lastName"
                        (change)="changeRole(u, $any($event.target).value)">
                  @for (r of roles; track r) { <option [value]="r">{{ roleLabels[r] }}</option> }
                </select>
              </td>
              <td><span [class]="'pill ' + statusClass[u.status]">{{ statusLabels[u.status] }}</span></td>
              <td class="mono">{{ u.lastLoginAt ? (u.lastLoginAt | date: 'dd/MM/yyyy HH:mm') : '—' }}</td>
              <td class="mono">{{ u.createdAt | date: 'dd/MM/yyyy' }}</td>
              <td class="r">
                <div class="actions row-actions">
                  @if (u.status === 'PENDING_APPROVAL') {
                    <button class="btn sm primary" type="button" [disabled]="busy() === u.id" (click)="approve(u)">Valider</button>
                  }
                  @if (u.status === 'PENDING') {
                    <button class="btn sm" type="button" [disabled]="busy() === u.id" (click)="resend(u)">Renvoyer le lien</button>
                  }
                  @if (u.status === 'DISABLED' && u.emailVerifiedAt) {
                    <button class="btn sm" type="button" [disabled]="busy() === u.id" (click)="approve(u)">Réactiver</button>
                  }
                  @if (u.status !== 'DISABLED' && u.id !== me()?.id) {
                    <button class="btn sm danger" type="button" [disabled]="busy() === u.id" (click)="disable(u)">Désactiver</button>
                  }
                </div>
              </td>
            </tr>
          } @empty {
            <tr><td colspan="6" class="empty">{{ loading() ? 'Chargement…' : 'Aucun utilisateur dans cette catégorie.' }}</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class AdminUsersPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  protected readonly me = inject(AuthService).user;

  protected readonly roles: Role[] = ['ADMIN', 'MANAGER', 'SALES'];
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly statusClass = STATUS_CLASS;
  protected readonly filters: { id: Filter; label: string }[] = [
    { id: 'ALL', label: 'Tous' },
    { id: 'PENDING_APPROVAL', label: 'À valider' },
    { id: 'PENDING', label: 'E-mail non vérifié' },
    { id: 'ACTIVE', label: 'Actifs' },
    { id: 'DISABLED', label: 'Désactivés' },
  ];

  protected readonly users = signal<User[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal<string | null>(null);
  protected readonly filter = signal<Filter>('ALL');
  protected readonly visible = computed(() =>
    this.filter() === 'ALL' ? this.users() : this.users().filter((u) => u.status === this.filter()),
  );

  ngOnInit(): void {
    this.http.get<User[]>('/api/users').subscribe({
      next: (list) => {
        this.users.set(list);
        this.loading.set(false);
        if (list.some((u) => u.status === 'PENDING_APPROVAL')) this.filter.set('PENDING_APPROVAL');
      },
      error: () => this.loading.set(false),
    });
  }

  protected count(f: Filter): number {
    return f === 'ALL' ? this.users().length : this.users().filter((u) => u.status === f).length;
  }

  protected approve(u: User): void {
    this.act(u, this.http.post<User>(`/api/users/${u.id}/approve`, null), `Compte de ${u.firstName} activé.`);
  }

  protected disable(u: User): void {
    if (!confirm(`Désactiver le compte de ${u.firstName} ${u.lastName} ? Ses sessions seront fermées.`)) return;
    this.act(u, this.http.post<User>(`/api/users/${u.id}/disable`, null), `Compte de ${u.firstName} désactivé.`);
  }

  protected changeRole(u: User, role: Role): void {
    this.act(u, this.http.patch<User>(`/api/users/${u.id}/role`, { role }),
      `${u.firstName} est maintenant ${ROLE_LABELS[role].toLowerCase()}.`);
  }

  protected resend(u: User): void {
    this.busy.set(u.id);
    this.http.post<void>(`/api/users/${u.id}/resend-verification`, null).subscribe({
      next: () => {
        this.busy.set(null);
        this.toast.show(`Nouveau lien envoyé à ${u.email}.`);
      },
      error: (e) => this.fail(e),
    });
  }

  private act(u: User, request: Observable<User>, success: string): void {
    this.busy.set(u.id);
    request.subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
        this.busy.set(null);
        this.toast.show(success);
      },
      error: (e) => this.fail(e),
    });
  }

  private fail(e: unknown): void {
    this.busy.set(null);
    this.toast.show(errorMessage(e));
    // Remet la liste à jour (ex. sélecteur de rôle resté sur la valeur refusée)
    this.users.update((list) => [...list]);
  }
}
