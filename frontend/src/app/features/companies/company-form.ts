import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { errorMessage, fieldErrors } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { CrmApi } from '../../core/api/crm-api';
import { CompanyRequest, CompanyType } from '../../core/api/models';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { controlError } from '../../shared/form-utils';
import { Modal } from '../../shared/ui/modal';

/** Création ou modification d'une entreprise. */
@Component({
  selector: 'rsb-company-form',
  imports: [ReactiveFormsModule, Modal],
  template: `
    <rsb-modal [heading]="id() ? 'Modifier l’entreprise' : 'Nouvelle entreprise'" (closed)="ui.closeForm()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="fgrid">
          <label class="fld wide">
            <span>Nom</span>
            <input formControlName="name" required maxlength="160" autocomplete="off" [attr.aria-invalid]="!!nameError()">
            @if (nameError(); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Type</span>
            <select formControlName="type">
              @for (t of types(); track t.code) { <option [ngValue]="t.code">{{ t.label }}</option> }
            </select>
          </label>
          <label class="fld">
            <span>Taille de flotte</span>
            <input type="number" formControlName="fleetSize" min="0" step="1" inputmode="numeric">
          </label>
          <label class="fld">
            <span>Pays</span>
            <input formControlName="country" maxlength="80">
          </label>
          <label class="fld">
            <span>Ville</span>
            <input formControlName="city" maxlength="80">
          </label>
          <label class="fld wide">
            <span>Site web</span>
            <input formControlName="website" maxlength="255" placeholder="exemple.com">
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
          <button class="btn primary" type="submit" [disabled]="saving()">{{ id() ? 'Enregistrer' : 'Créer l’entreprise' }}</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class CompanyForm implements OnInit {
  readonly id = input<string>();

  private readonly api = inject(CrmApi);
  private readonly auth = inject(AuthService);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly types = this.ref.companyTypes;
  protected readonly owners = this.ref.activeUsers;
  protected readonly canAssign = computed(() => this.auth.hasRole('ADMIN', 'MANAGER'));
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly server = signal<Record<string, string>>({});

  protected readonly form = inject(FormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    type: ['ARMATEUR' as CompanyType, Validators.required],
    fleetSize: [null as number | null, Validators.min(0)],
    country: ['Djibouti' as string | null],
    city: ['Djibouti' as string | null],
    website: [null as string | null],
    ownerId: [this.auth.user()?.id ?? null],
  });

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.api.company(id).subscribe(({ company: c }) =>
        this.form.patchValue({
          name: c.name, type: c.type, fleetSize: c.fleetSize, country: c.country, city: c.city,
          website: c.website, ownerId: c.ownerId,
        }),
      );
    }
  }

  protected nameError(): string | null {
    return controlError(this.form.controls.name, this.server()['name'], 'Le nom');
  }

  submit(): void {
    this.form.markAllAsTouched();
    this.server.set({});
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const body: CompanyRequest = {
      name: v.name!, type: v.type!, fleetSize: v.fleetSize ?? null, country: v.country || null,
      city: v.city || null, website: v.website || null, ownerId: this.canAssign() ? v.ownerId : null,
    };
    const id = this.id();
    const request: Observable<{ id: string } | void> = id
      ? this.api.updateCompany(id, body)
      : this.api.createCompany(body);
    request.subscribe({
      next: (res) => {
        this.ui.closeForm();
        this.ui.changed();
        this.toast.show(id ? 'Entreprise enregistrée' : 'Entreprise créée');
        if (!id && res) this.ui.openRecord('company', res.id);
      },
      error: (e) => {
        this.saving.set(false);
        this.server.set(fieldErrors(e));
        this.error.set(errorMessage(e));
      },
    });
  }
}
