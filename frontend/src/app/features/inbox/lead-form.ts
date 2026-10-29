import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { errorMessage, fieldErrors } from '../../core/api-error';
import { SalesApi } from '../../core/api/sales';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { controlError } from '../../shared/form-utils';
import { Modal } from '../../shared/ui/modal';

/** Saisie manuelle d'une demande reçue par téléphone ou par e-mail. */
@Component({
  selector: 'rsb-lead-form',
  imports: [ReactiveFormsModule, Modal],
  template: `
    <rsb-modal heading="Saisir une demande" sub="Demande reçue par téléphone, e-mail ou WhatsApp." [wide]="true" (closed)="ui.closeForm()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="fgrid">
          <label class="fld">
            <span>Demandeur</span>
            <input formControlName="name" required maxlength="160" placeholder="Prénom Nom" [attr.aria-invalid]="!!err('name')">
            @if (err('name'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld"><span>Entreprise</span><input formControlName="companyName" maxlength="160"></label>
          <label class="fld">
            <span>E-mail</span>
            <input type="email" formControlName="email" maxlength="254" [attr.aria-invalid]="!!err('email')">
            @if (err('email'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld"><span>Téléphone</span><input type="tel" formControlName="phone" maxlength="40"></label>
          <label class="fld">
            <span>Navire</span>
            <input formControlName="vesselName" required maxlength="120" [attr.aria-invalid]="!!err('vesselName')">
            @if (err('vesselName'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>N° IMO</span>
            <input formControlName="imo" inputmode="numeric" maxlength="7" [attr.aria-invalid]="!!err('imo')">
            @if (err('imo'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Produit</span>
            <select formControlName="fuelGrade">
              @for (g of grades(); track g.code) { <option [ngValue]="g.code">{{ g.label }}</option> }
            </select>
          </label>
          <label class="fld"><span>Quantité (MT)</span><input type="number" formControlName="quantityMt" min="1"></label>
          <label class="fld"><span>ETA Djibouti</span><input type="date" formControlName="eta"></label>
          <label class="fld">
            <span>Lieu de livraison</span>
            <select formControlName="deliveryLocation">
              @for (l of locations(); track l.code) { <option [ngValue]="l.code">{{ l.label }}</option> }
            </select>
          </label>
          <label class="fld">
            <span>Source</span>
            <select formControlName="source">
              @for (s of sources(); track s) { <option [ngValue]="s">{{ s }}</option> }
            </select>
          </label>
          <label class="fld wide"><span>Message</span><textarea formControlName="message" rows="3" maxlength="5000"></textarea></label>
        </div>
        @if (error()) { <p class="auth-error form-error" role="alert">{{ error() }}</p> }
        <footer class="m-foot">
          <button type="button" class="btn ghost" (click)="ui.closeForm()">Annuler</button>
          <button class="btn primary" type="submit" [disabled]="saving()">Enregistrer la demande</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class LeadForm {
  private readonly api = inject(SalesApi);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly grades = this.ref.fuelGrades;
  protected readonly locations = this.ref.deliveryLocations;
  protected readonly sources = this.ref.sources;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly server = signal<Record<string, string>>({});

  protected readonly form = inject(FormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    companyName: [null as string | null],
    email: [null as string | null, Validators.email],
    phone: [null as string | null],
    vesselName: ['', Validators.required],
    imo: [null as string | null, Validators.pattern(/^\d{7}$/)],
    fuelGrade: ['VLSFO'],
    quantityMt: [null as number | null, Validators.min(1)],
    eta: [null as string | null],
    deliveryLocation: ['ANCHORAGE' as string | null],
    source: ['Appel entrant' as string | null],
    message: [null as string | null],
  });

  protected err(name: 'name' | 'email' | 'vesselName' | 'imo'): string | null {
    const c = this.form.controls[name];
    if (name === 'imo' && c.touched && c.errors?.['pattern']) return 'Le numéro IMO compte 7 chiffres.';
    const labels = { name: 'Le demandeur', email: "L'e-mail", vesselName: 'Le navire', imo: "L'IMO" };
    return controlError(c, this.server()[name], labels[name]);
  }

  submit(): void {
    this.form.markAllAsTouched();
    this.server.set({});
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    this.api
      .createLead({
        name: v.name!, companyName: v.companyName || null, email: v.email || null, phone: v.phone || null,
        vesselName: v.vesselName!, imo: v.imo || null, fuelGrade: v.fuelGrade!, quantityMt: v.quantityMt,
        eta: v.eta || null, deliveryLocation: v.deliveryLocation, source: v.source, message: v.message || null,
      })
      .subscribe({
        next: (res) => {
          this.ui.closeForm();
          this.ui.changed();
          this.toast.show('Demande enregistrée');
          this.ui.openRecord('lead', res.id);
        },
        error: (e) => {
          this.saving.set(false);
          this.server.set(fieldErrors(e));
          this.error.set(errorMessage(e));
        },
      });
  }
}
