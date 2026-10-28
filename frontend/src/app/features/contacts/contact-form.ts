import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { errorMessage, fieldErrors } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { CrmApi } from '../../core/api/crm-api';
import { CompanyRow, ContactRequest, Lifecycle } from '../../core/api/models';
import { LIFECYCLES, LIFECYCLE_LABELS, ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { controlError } from '../../shared/form-utils';
import { Modal } from '../../shared/ui/modal';

/** Création ou modification d'un contact. */
@Component({
  selector: 'rsb-contact-form',
  imports: [ReactiveFormsModule, Modal],
  template: `
    <rsb-modal [heading]="id() ? 'Modifier le contact' : 'Nouveau contact'" (closed)="ui.closeForm()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="fgrid">
          <label class="fld">
            <span>Prénom</span>
            <input formControlName="firstName" required maxlength="80" autocomplete="off" [attr.aria-invalid]="!!err('firstName')">
            @if (err('firstName'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Nom</span>
            <input formControlName="lastName" required maxlength="80" autocomplete="off" [attr.aria-invalid]="!!err('lastName')">
            @if (err('lastName'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Poste</span>
            <input formControlName="title" maxlength="120" placeholder="Bunker buyer">
          </label>
          <label class="fld">
            <span>Entreprise</span>
            <select formControlName="companyId">
              <option [ngValue]="null">—</option>
              @for (c of companies(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
            </select>
          </label>
          <label class="fld">
            <span>E-mail</span>
            <input type="email" formControlName="email" maxlength="254" autocomplete="off" [attr.aria-invalid]="!!err('email')">
            @if (err('email'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Téléphone</span>
            <input type="tel" formControlName="phone" maxlength="40" autocomplete="off" placeholder="+253 …">
          </label>
          <label class="fld">
            <span>Source</span>
            <select formControlName="source">
              <option [ngValue]="null">—</option>
              @for (s of sources(); track s) { <option [ngValue]="s">{{ s }}</option> }
            </select>
          </label>
          <label class="fld">
            <span>Cycle de vie</span>
            <select formControlName="lifecycle">
              @for (l of lifecycles; track l) { <option [ngValue]="l">{{ lifecycleLabels[l] }}</option> }
            </select>
          </label>
          @if (canAssign()) {
            <label class="fld">
              <span>Propriétaire</span>
              <select formControlName="ownerId">
                @for (u of owners(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }
              </select>
            </label>
          }
        </div>
        @if (error()) { <p class="auth-error form-error" role="alert">{{ error() }}</p> }
        <footer class="m-foot">
          <button type="button" class="btn ghost" (click)="ui.closeForm()">Annuler</button>
          <button class="btn primary" type="submit" [disabled]="saving()">{{ id() ? 'Enregistrer' : 'Créer le contact' }}</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class ContactForm implements OnInit {
  readonly id = input<string>();
  readonly companyId = input<string>();

  private readonly api = inject(CrmApi);
  private readonly auth = inject(AuthService);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly lifecycles = LIFECYCLES;
  protected readonly lifecycleLabels = LIFECYCLE_LABELS;
  protected readonly sources = this.ref.sources;
  protected readonly owners = this.ref.activeUsers;
  protected readonly canAssign = computed(() => this.auth.hasRole('ADMIN', 'MANAGER'));
  protected readonly companies = signal<CompanyRow[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly server = signal<Record<string, string>>({});

  protected readonly form = inject(FormBuilder).group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    title: [null as string | null],
    companyId: [null as string | null],
    email: [null as string | null, Validators.email],
    phone: [null as string | null],
    source: [null as string | null],
    lifecycle: ['LEAD' as Lifecycle, Validators.required],
    ownerId: [this.auth.user()?.id ?? null],
  });

  ngOnInit(): void {
    this.api.companies({ sort: 'name', size: 200 }).subscribe((p) => this.companies.set(p.content));
    if (this.companyId()) this.form.patchValue({ companyId: this.companyId()! });
    const id = this.id();
    if (id) {
      this.api.contact(id).subscribe(({ contact: c }) =>
        this.form.patchValue({
          firstName: c.firstName, lastName: c.lastName, title: c.title, companyId: c.companyId, email: c.email,
          phone: c.phone, source: c.source, lifecycle: c.lifecycle, ownerId: c.ownerId,
        }),
      );
    }
  }

  protected err(name: 'firstName' | 'lastName' | 'email'): string | null {
    const label = { firstName: 'Le prénom', lastName: 'Le nom', email: "L'e-mail" }[name];
    return controlError(this.form.controls[name], this.server()[name], label);
  }

  submit(): void {
    this.form.markAllAsTouched();
    this.server.set({});
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const body: ContactRequest = {
      firstName: v.firstName!, lastName: v.lastName!, title: v.title || null, companyId: v.companyId,
      email: v.email || null, phone: v.phone || null, source: v.source, lifecycle: v.lifecycle!,
      ownerId: this.canAssign() ? v.ownerId : null,
    };
    const id = this.id();
    const request: Observable<{ id: string } | void> = id
      ? this.api.updateContact(id, body)
      : this.api.createContact(body);
    request.subscribe({
      next: (res) => {
        this.ui.closeForm();
        this.ui.changed();
        this.toast.show(id ? 'Contact enregistré' : 'Contact créé');
        if (!id && res) this.ui.openRecord('contact', res.id);
      },
      error: (e) => {
        this.saving.set(false);
        this.server.set(fieldErrors(e));
        this.error.set(errorMessage(e));
      },
    });
  }
}
