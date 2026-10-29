import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { DealRow, SalesApi, TaskPriority } from '../../core/api/sales';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { controlError } from '../../shared/form-utils';
import { Modal } from '../../shared/ui/modal';

function tomorrow(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Nouvelle tâche, éventuellement liée à une transaction ou à un contact. */
@Component({
  selector: 'rsb-task-form',
  imports: [ReactiveFormsModule, Modal],
  template: `
    <rsb-modal heading="Nouvelle tâche" (closed)="ui.closeForm()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="fgrid">
          <label class="fld wide">
            <span>Titre</span>
            <input formControlName="title" required maxlength="200" [attr.aria-invalid]="!!titleError()">
            @if (titleError(); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Échéance</span>
            <input type="date" formControlName="dueDate" required>
          </label>
          <label class="fld">
            <span>Priorité</span>
            <select formControlName="priority">
              <option ngValue="HAUTE">Haute</option>
              <option ngValue="MOYENNE">Moyenne</option>
              <option ngValue="BASSE">Basse</option>
            </select>
          </label>
          @if (canAssign()) {
            <label class="fld">
              <span>Assignée à</span>
              <select formControlName="ownerId">
                @for (u of owners(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }
              </select>
            </label>
          }
          @if (!contactId()) {
            <label class="fld">
              <span>Transaction liée</span>
              <select formControlName="dealId" (change)="onDealChange()">
                <option [ngValue]="null">—</option>
                @for (d of deals(); track d.id) { <option [ngValue]="d.id">{{ d.vesselName }}</option> }
              </select>
            </label>
          }
        </div>
        @if (error()) { <p class="auth-error form-error" role="alert">{{ error() }}</p> }
        <footer class="m-foot">
          <button type="button" class="btn ghost" (click)="ui.closeForm()">Annuler</button>
          <button class="btn primary" type="submit" [disabled]="saving()">Créer la tâche</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class TaskForm implements OnInit {
  readonly dealId = input<string>();
  readonly contactId = input<string>();

  private readonly api = inject(SalesApi);
  private readonly auth = inject(AuthService);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly owners = this.ref.activeUsers;
  protected readonly canAssign = computed(() => this.auth.hasRole('ADMIN', 'MANAGER'));
  protected readonly deals = signal<DealRow[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    dueDate: [tomorrow(), Validators.required],
    priority: ['MOYENNE' as TaskPriority],
    ownerId: [this.auth.user()?.id ?? null],
    dealId: [null as string | null],
  });

  ngOnInit(): void {
    this.api.deals().subscribe((list) => {
      const open = list.content.filter((d) => !d.won && !d.lost);
      this.deals.set(open);
      const preset = this.dealId();
      if (preset) {
        const d = list.content.find((x) => x.id === preset);
        this.form.patchValue({ dealId: preset, title: d ? `Relancer ${d.vesselName}` : '', ownerId: this.ownerFor(d) });
      }
    });
  }

  protected titleError(): string | null {
    return controlError(this.form.controls.title, undefined, 'Le titre');
  }

  /** Par défaut, la tâche va au commercial de la transaction (si on a le droit d'attribuer). */
  protected onDealChange(): void {
    const d = this.deals().find((x) => x.id === this.form.value.dealId);
    if (d && !this.form.value.title) this.form.patchValue({ title: `Relancer ${d.vesselName}` });
    if (d) this.form.patchValue({ ownerId: this.ownerFor(d) });
  }

  private ownerFor(d: DealRow | undefined): string | null {
    return this.canAssign() && d?.ownerId ? d.ownerId : (this.auth.user()?.id ?? null);
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    this.api
      .createTask({
        title: v.title!, dueDate: v.dueDate!, priority: v.priority!, ownerId: this.canAssign() ? v.ownerId : null,
        dealId: v.dealId, contactId: this.contactId() ?? null, companyId: null,
      })
      .subscribe({
        next: () => {
          this.ui.closeForm();
          this.ui.changed();
          this.toast.show('Tâche créée');
        },
        error: (e) => {
          this.saving.set(false);
          this.error.set(errorMessage(e));
        },
      });
  }
}
