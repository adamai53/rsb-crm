import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { errorMessage, fieldErrors } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { CrmApi } from '../../core/api/crm-api';
import { CompanyRow, ContactRow } from '../../core/api/models';
import { DealRequest, SalesApi } from '../../core/api/sales';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { controlError } from '../../shared/form-utils';
import { Modal } from '../../shared/ui/modal';

function isoInDays(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Nouvelle demande de soutage ou modification d'une transaction. */
@Component({
  selector: 'rsb-deal-form',
  imports: [ReactiveFormsModule, Modal],
  template: `
    <rsb-modal [heading]="id() ? 'Modifier la transaction' : 'Nouvelle demande de soutage'"
               sub="Les informations de l'escale servent au calendrier et aux devis." [wide]="true" (closed)="ui.closeForm()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="fgrid">
          <label class="fld">
            <span>Navire</span>
            <input formControlName="vesselName" required maxlength="120" placeholder="MV …" [attr.aria-invalid]="!!err('vesselName')">
            @if (err('vesselName'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>N° IMO</span>
            <input formControlName="imo" inputmode="numeric" maxlength="7" placeholder="7 chiffres" [attr.aria-invalid]="!!err('imo')">
            @if (err('imo'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Entreprise</span>
            <select formControlName="companyId" (change)="onCompanyChange()">
              <option [ngValue]="null">—</option>
              @for (c of companies(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
            </select>
          </label>
          <label class="fld">
            <span>Contact</span>
            <select formControlName="contactId">
              <option [ngValue]="null">—</option>
              @for (c of visibleContacts(); track c.id) {
                <option [ngValue]="c.id">{{ c.firstName }} {{ c.lastName }}{{ c.companyName ? ' · ' + c.companyName : '' }}</option>
              }
            </select>
          </label>
          <label class="fld">
            <span>Produit</span>
            <select formControlName="fuelGrade" (change)="onGradeChange()">
              @for (g of grades(); track g.code) { <option [ngValue]="g.code">{{ g.label }}</option> }
            </select>
          </label>
          <label class="fld">
            <span>Quantité (MT)</span>
            <input type="number" formControlName="quantityMt" min="1" step="1" [attr.aria-invalid]="!!err('quantityMt')">
            @if (err('quantityMt'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Prix (USD/MT)</span>
            <input type="number" formControlName="priceUsdPerMt" min="0" step="0.5" (input)="priceTouched = true">
            <small class="fld-hint">Montant : {{ amountLabel() }}</small>
          </label>
          <label class="fld">
            <span>ETA Djibouti</span>
            <input type="date" formControlName="eta">
          </label>
          <label class="fld">
            <span>Lieu de livraison</span>
            <select formControlName="deliveryLocation">
              @for (l of locations(); track l.code) { <option [ngValue]="l.code">{{ l.label }}</option> }
            </select>
          </label>
          <label class="fld">
            <span>Étape</span>
            <select formControlName="stage">
              @for (s of stageOptions(); track s.code) { <option [ngValue]="s.code">{{ s.label }}</option> }
            </select>
          </label>
          <label class="fld">
            <span>Source</span>
            <select formControlName="source">
              <option [ngValue]="null">—</option>
              @for (s of sources(); track s) { <option [ngValue]="s">{{ s }}</option> }
            </select>
          </label>
          @if (canAssign()) {
            <label class="fld">
              <span>Commercial</span>
              <select formControlName="ownerId">
                @for (u of owners(); track u.id) { <option [ngValue]="u.id">{{ u.name }}</option> }
              </select>
            </label>
          }
        </div>
        @if (error()) { <p class="auth-error form-error" role="alert">{{ error() }}</p> }
        <footer class="m-foot">
          <button type="button" class="btn ghost" (click)="ui.closeForm()">Annuler</button>
          <button class="btn primary" type="submit" [disabled]="saving()">{{ id() ? 'Enregistrer' : 'Créer la transaction' }}</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class DealForm implements OnInit {
  readonly id = input<string>();
  readonly companyId = input<string>();
  readonly contactId = input<string>();

  private readonly crm = inject(CrmApi);
  private readonly api = inject(SalesApi);
  private readonly auth = inject(AuthService);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly grades = this.ref.fuelGrades;
  protected readonly locations = this.ref.deliveryLocations;
  protected readonly sources = this.ref.sources;
  protected readonly owners = this.ref.activeUsers;
  protected readonly canAssign = computed(() => this.auth.hasRole('ADMIN', 'MANAGER'));
  /** En création, seules les étapes ouvertes ; « Perdue » se choisit depuis la fiche (motif obligatoire). */
  protected readonly stageOptions = computed(() =>
    this.id() ? this.ref.stages().filter((s) => !s.lost) : this.ref.openStages(),
  );
  protected readonly companies = signal<CompanyRow[]>([]);
  private readonly contacts = signal<ContactRow[]>([]);
  private readonly selectedCompany = signal<string | null>(null);
  protected readonly visibleContacts = computed(() => {
    const co = this.selectedCompany();
    return co ? this.contacts().filter((c) => c.companyId === co) : this.contacts();
  });
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly server = signal<Record<string, string>>({});
  protected priceTouched = false;

  protected readonly form = inject(FormBuilder).group({
    vesselName: ['MV ', [Validators.required, Validators.maxLength(120)]],
    imo: [null as string | null, Validators.pattern(/^\d{7}$/)],
    companyId: [null as string | null],
    contactId: [null as string | null],
    fuelGrade: ['VLSFO', Validators.required],
    quantityMt: [500 as number | null, [Validators.required, Validators.min(1)]],
    priceUsdPerMt: [null as number | null],
    eta: [isoInDays(7) as string | null],
    deliveryLocation: ['DMP' as string | null],
    stage: ['RECUE'],
    source: ['Site web' as string | null],
    ownerId: [this.auth.user()?.id ?? null],
  });
  private readonly formValue = signal(this.form.getRawValue());
  protected readonly amountLabel = computed(() => {
    const v = this.formValue();
    const amount = (v.quantityMt ?? 0) * (v.priceUsdPerMt ?? 0);
    return `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(amount)} USD`;
  });

  constructor() {
    this.form.valueChanges.subscribe(() => this.formValue.set(this.form.getRawValue()));
  }

  ngOnInit(): void {
    this.crm.companies({ sort: 'name', size: 200 }).subscribe((p) => this.companies.set(p.content));
    this.crm.contacts({ sort: 'name', dir: 'asc', size: 200 }).subscribe((p) => {
      this.contacts.set(p.content);
      // Contact imposé : on en déduit l'entreprise
      const preset = this.contactId();
      const contact = preset ? p.content.find((c) => c.id === preset) : undefined;
      if (contact && !this.form.value.companyId) {
        this.form.patchValue({ companyId: contact.companyId });
        this.selectedCompany.set(contact.companyId);
      }
    });
    this.form.patchValue({ companyId: this.companyId() ?? null, contactId: this.contactId() ?? null });
    this.selectedCompany.set(this.companyId() ?? null);

    const id = this.id();
    if (id) {
      this.priceTouched = true;
      this.api.deal(id).subscribe(({ deal: d }) => {
        this.form.patchValue({
          vesselName: d.vesselName, imo: d.imo, companyId: d.companyId, contactId: d.contactId, fuelGrade: d.fuelGrade,
          quantityMt: d.quantityMt, priceUsdPerMt: d.priceUsdPerMt, eta: d.eta, deliveryLocation: d.deliveryLocation,
          stage: d.stage, source: d.source, ownerId: d.ownerId,
        });
        this.selectedCompany.set(d.companyId);
      });
    } else {
      this.applyReferencePrice();
    }
  }

  protected err(name: 'vesselName' | 'imo' | 'quantityMt'): string | null {
    const c = this.form.controls[name];
    if (name === 'imo' && c.touched && c.errors?.['pattern']) return 'Le numéro IMO compte 7 chiffres.';
    if (name === 'quantityMt' && c.touched && c.errors?.['min']) return 'La quantité doit être positive.';
    return controlError(c, this.server()[name], name === 'vesselName' ? 'Le nom du navire' : 'La quantité');
  }

  protected onCompanyChange(): void {
    const co = this.form.value.companyId ?? null;
    this.selectedCompany.set(co);
    const contact = this.contacts().find((c) => c.id === this.form.value.contactId);
    if (contact && co && contact.companyId !== co) this.form.patchValue({ contactId: null });
  }

  /** Le prix suit le prix de référence du produit tant que l'utilisateur ne l'a pas saisi. */
  protected onGradeChange(): void {
    if (!this.priceTouched) this.applyReferencePrice();
  }

  private applyReferencePrice(): void {
    const price = this.ref.fuelGrade(this.form.value.fuelGrade)?.referencePrice;
    if (price != null) this.form.patchValue({ priceUsdPerMt: price });
  }

  submit(): void {
    this.form.markAllAsTouched();
    this.server.set({});
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const body: DealRequest = {
      vesselName: v.vesselName!.trim(), imo: v.imo || null, companyId: v.companyId, contactId: v.contactId,
      fuelGrade: v.fuelGrade!, quantityMt: v.quantityMt!, priceUsdPerMt: v.priceUsdPerMt, stage: v.stage,
      source: v.source, eta: v.eta || null, deliveryLocation: v.deliveryLocation, lostReason: null,
      ownerId: this.canAssign() ? v.ownerId : null,
    };
    const id = this.id();
    const request: Observable<{ id: string } | void> = id ? this.api.updateDeal(id, body) : this.api.createDeal(body);
    request.subscribe({
      next: (res) => {
        this.ui.closeForm();
        this.ui.changed();
        this.toast.show(id ? 'Transaction mise à jour' : 'Transaction créée');
        this.ui.openRecord('deal', id ?? (res as { id: string }).id);
      },
      error: (e) => {
        this.saving.set(false);
        this.server.set(fieldErrors(e));
        this.error.set(errorMessage(e));
      },
    });
  }
}
