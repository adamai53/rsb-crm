import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { errorMessage, fieldErrors } from '../../core/api-error';
import { AnalyticsApi, CampaignRow } from '../../core/api/analytics';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { Modal } from '../../shared/ui/modal';

/** Création et modification d'une campagne (MANAGER, ADMIN). Leads et CA sont calculés, pas saisis. */
@Component({
  selector: 'rsb-campaign-form',
  imports: [ReactiveFormsModule, Modal],
  template: `
    <rsb-modal [heading]="campaign() ? 'Modifier la campagne' : 'Nouvelle campagne'"
               sub="Leads, clients et CA sont calculés depuis les demandes de ce canal arrivées pendant la période."
               [wide]="true" (closed)="ui.closeForm()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="fgrid">
          <label class="fld wide">
            <span>Nom</span><input formControlName="name" required maxlength="160">
            @if (errors()['name']; as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Canal</span>
            <select formControlName="channel">
              @for (s of ref.sources(); track s) { <option [value]="s">{{ s }}</option> }
            </select>
          </label>
          <label class="fld">
            <span>Dépense (USD)</span><input type="number" min="0" formControlName="spendUsd" required>
            @if (errors()['spendUsd']; as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld"><span>Impressions</span><input type="number" min="0" formControlName="impressions"></label>
          <label class="fld"><span>Clics</span><input type="number" min="0" formControlName="clicks"></label>
          <label class="fld"><span>Début</span><input type="date" formControlName="periodStart"></label>
          <label class="fld"><span>Fin</span><input type="date" formControlName="periodEnd"></label>
        </div>
        @if (error()) { <p class="auth-error form-error" role="alert">{{ error() }}</p> }
        <footer class="m-foot">
          @if (campaign()) {
            <button type="button" class="btn ghost m-left" [disabled]="saving()" (click)="remove()">Supprimer</button>
          }
          <button type="button" class="btn ghost" (click)="ui.closeForm()">Annuler</button>
          <button class="btn primary" type="submit" [disabled]="saving() || form.invalid">Enregistrer</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class CampaignForm implements OnInit {
  readonly campaign = input<CampaignRow | undefined>();

  private readonly api = inject(AnalyticsApi);
  private readonly toast = inject(ToastService);
  protected readonly ref = inject(ReferenceService);
  protected readonly ui = inject(UiService);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errors = signal<Record<string, string>>({});
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required],
    channel: ['Site web', Validators.required],
    spendUsd: [0, [Validators.required, Validators.min(0)]],
    impressions: [0, Validators.min(0)],
    clicks: [0, Validators.min(0)],
    periodStart: [''],
    periodEnd: [''],
  });

  ngOnInit(): void {
    const c = this.campaign();
    if (c) {
      this.form.patchValue({
        name: c.name, channel: c.channel, spendUsd: c.spendUsd, impressions: c.impressions, clicks: c.clicks,
        periodStart: c.periodStart ?? '', periodEnd: c.periodEnd ?? '',
      });
    }
  }

  protected remove(): void {
    const c = this.campaign();
    if (!c || !confirm(`Supprimer la campagne « ${c.name} » ? Les leads et transactions ne sont pas touchés.`)) return;
    this.saving.set(true);
    this.api.deleteCampaign(c.id).subscribe({
      next: () => {
        this.toast.show('Campagne supprimée.');
        this.ui.changed();
        this.ui.closeForm();
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }

  protected submit(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const body = {
      ...v, impressions: v.impressions ?? 0, clicks: v.clicks ?? 0,
      periodStart: v.periodStart || null, periodEnd: v.periodEnd || null,
    };
    const c = this.campaign();
    this.saving.set(true);
    this.error.set(null);
    this.errors.set({});
    const request: Observable<unknown> = c ? this.api.updateCampaign(c.id, body) : this.api.createCampaign(body);
    request.subscribe({
      next: () => {
        this.toast.show('Campagne enregistrée.');
        this.ui.changed();
        this.ui.closeForm();
      },
      error: (e) => {
        this.saving.set(false);
        this.errors.set(fieldErrors(e));
        this.error.set(errorMessage(e));
      },
    });
  }
}
