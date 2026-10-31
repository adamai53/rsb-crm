import { Component, computed, effect, inject, signal, untracked } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { SalesApi, TaskItem } from '../../core/api/sales';
import { daysFrom } from '../../core/format';
import { ReferenceService } from '../../core/reference.service';
import { UiService } from '../../core/ui.service';
import { TaskItemRow } from '../../shared/ui/task-item';

/** Tâches groupées : en retard, aujourd'hui, à venir, terminées. Par défaut, celles de l'utilisateur. */
@Component({
  selector: 'rsb-tasks',
  imports: [TaskItemRow],
  template: `
    <div class="page-head">
      <div>
        <h1>Tâches</h1>
        <p>Rappels d'appel, relances de cotation et préparation des livraisons.</p>
      </div>
      <div class="actions">
        <button class="btn primary" type="button" (click)="ui.openForm({ kind: 'task' })">Nouvelle tâche</button>
      </div>
    </div>

    <div class="toolbar">
      <select aria-label="Responsable" (change)="setOwner($any($event.target).value)">
        <option value="all">Toute l'équipe</option>
        @for (u of users(); track u.id) { <option [value]="u.id" [selected]="u.id === ownerId()">{{ u.id === me()?.id ? 'Mes tâches' : u.name }}</option> }
      </select>
    </div>

    <div class="grid g-2">
      @for (g of groups(); track g.label) {
        <div class="panel">
          <h2>{{ g.label }} <span class="mono faint count">{{ g.items.length }}</span></h2>
          <div class="list task-list">
            @for (t of g.items; track t.id) { <rsb-task-item [task]="t" /> } @empty { <p class="empty">Aucune tâche.</p> }
          </div>
        </div>
      }
    </div>
  `,
})
export class TasksPage {
  private readonly api = inject(SalesApi);
  private readonly ref = inject(ReferenceService);
  private readonly auth = inject(AuthService);
  protected readonly me = this.auth.user;
  protected readonly ui = inject(UiService);

  protected readonly users = this.ref.users;
  protected readonly ownerId = signal<string | null>(this.auth.hasRole('SALES') ? (this.me()?.id ?? null) : null);
  private readonly tasks = signal<TaskItem[]>([]);

  protected readonly groups = computed(() => {
    const all = this.tasks();
    const open = all.filter((t) => !t.done);
    return [
      { label: 'En retard', items: open.filter((t) => daysFrom(t.dueDate) < 0) },
      { label: "Aujourd'hui", items: open.filter((t) => daysFrom(t.dueDate) === 0) },
      { label: 'À venir', items: open.filter((t) => daysFrom(t.dueDate) > 0) },
      { label: 'Terminées', items: all.filter((t) => t.done) },
    ];
  });

  constructor() {
    effect(() => {
      const owner = this.ownerId();
      this.ui.version();
      untracked(() => this.api.tasks(owner).subscribe((list) => this.tasks.set(list)));
    });
  }

  protected setOwner(value: string): void {
    this.ownerId.set(value === 'all' ? null : value);
  }
}
