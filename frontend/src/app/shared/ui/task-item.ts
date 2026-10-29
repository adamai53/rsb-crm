import { Component, computed, inject, input } from '@angular/core';

import { errorMessage } from '../../core/api-error';
import { SalesApi, TaskItem as Task } from '../../core/api/sales';
import { daysFrom, fmtDate } from '../../core/format';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';

/** Ligne de tâche du prototype : case à cocher, titre, transaction liée, responsable, échéance colorée. */
@Component({
  selector: 'rsb-task-item',
  template: `
    @let t = task();
    <div class="li">
      <input type="checkbox" [checked]="t.done" [attr.aria-label]="t.done ? 'Rouvrir la tâche' : 'Marquer comme terminée'"
             (change)="toggle($any($event.target).checked)">
      <div class="grow">
        <b [class.done]="t.done">{{ t.title }}</b>
        <span>
          @if (t.dealId && t.dealVessel) {
            <button class="lnk ev-link" type="button" (click)="ui.openRecord('deal', t.dealId)">{{ t.dealVessel }}</button> ·
          }
          {{ owner() }} · priorité {{ t.priority.toLowerCase() }}
        </span>
      </div>
      <span [class]="'pill nodot ' + dueClass()">{{ dueLabel() }}</span>
    </div>
  `,
})
export class TaskItemRow {
  readonly task = input.required<Task>();

  private readonly api = inject(SalesApi);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly owner = computed(() => this.ref.user(this.task().ownerId)?.name ?? '—');
  private readonly days = computed(() => daysFrom(this.task().dueDate));
  protected readonly dueClass = computed(() => (this.task().done ? '' : this.days() < 0 ? 'p-bad' : this.days() === 0 ? 'p-warn' : ''));
  protected readonly dueLabel = computed(() => {
    if (this.task().done) return 'Terminée';
    const n = this.days();
    return n < 0 ? `En retard · ${fmtDate(this.task().dueDate)}` : n === 0 ? "Aujourd'hui" : fmtDate(this.task().dueDate);
  });

  protected toggle(done: boolean): void {
    this.api.setTaskDone(this.task().id, done).subscribe({
      next: () => {
        this.toast.show(done ? 'Tâche terminée' : 'Tâche rouverte');
        this.ui.changed();
      },
      error: (e) => {
        this.toast.show(errorMessage(e));
        this.ui.changed();
      },
    });
  }
}
