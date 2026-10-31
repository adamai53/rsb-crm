import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { errorMessage, fieldErrors } from '../../core/api-error';
import { AnalyticsApi, CampaignRow, FormConfig, MarketingSummary } from '../../core/api/analytics';
import { SalesApi } from '../../core/api/sales';
import { AuthService } from '../../core/auth/auth.service';
import { fmtDate, fmtK, fmtN, fmtUSD } from '../../core/format';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { HBars } from '../../shared/ui/charts';
import { buildSnippet } from './snippet';

/** Campagnes, attribution par canal et formulaire « Demande de cotation » du site. */
@Component({
  selector: 'rsb-marketing',
  imports: [ReactiveFormsModule, HBars],
  template: `
    <div class="page-head">
      <div>
        <h1>Campagnes & formulaires</h1>
        <p>Attribution des demandes à chaque canal : site, Google Ads, LinkedIn, Meta, salons et newsletter.</p>
      </div>
      @if (canManage()) {
        <div class="actions">
          <button class="btn primary" type="button" (click)="ui.openForm({ kind: 'campaign' })">Nouvelle campagne</button>
        </div>
      }
    </div>

    @if (data(); as d) {
      <div class="grid g-kpi">
        <div class="kpi">
          <span class="lbl">Dépense marketing</span><span class="val">{{ fmtK(d.totals.spendUsd) }}</span>
          <span class="foot">{{ d.campaigns.length }} {{ d.campaigns.length > 1 ? 'campagnes' : 'campagne' }}</span>
        </div>
        <div class="kpi">
          <span class="lbl">Leads générés</span><span class="val">{{ d.totals.leads }}</span>
          <span class="foot">
            @if (d.totals.costPerLeadUsd !== null) { Coût moyen {{ fmtUSD(d.totals.costPerLeadUsd) }} par lead } @else { Coût par lead non calculable }
          </span>
        </div>
        <div class="kpi">
          <span class="lbl">Leads → clients</span><span class="val">{{ d.totals.leadToClientPct }} %</span>
          <span class="foot">{{ d.totals.won }} {{ d.totals.won > 1 ? 'livraisons attribuées' : 'livraison attribuée' }}</span>
        </div>
        <div class="kpi">
          <span class="lbl">CA attribué</span><span class="val">{{ fmtK(d.totals.revenueUsd) }}</span>
          <span class="foot">
            @if (d.totals.revenuePerUsd !== null) { {{ fmtN(d.totals.revenuePerUsd) }} USD par USD investi } @else { Aucune dépense saisie }
          </span>
        </div>
      </div>

      <div class="tbl mt-16">
        <table>
          <thead>
            <tr>
              <th>Campagne</th><th>Canal</th><th>Période</th><th class="r">Dépense</th><th class="r">Impressions</th>
              <th class="r">Clics</th><th class="r">Leads</th><th class="r">Coût/lead</th><th class="r">Clients</th>
              <th class="r">CA attribué</th>
            </tr>
          </thead>
          <tbody>
            @for (c of d.campaigns; track c.id) {
              <tr [class.row]="canManage()" [attr.tabindex]="canManage() ? 0 : null"
                  (click)="edit(c)" (keydown.enter)="edit(c)">
                <td><b>{{ c.name }}</b></td>
                <td><span class="pill nodot">{{ c.channel }}</span></td>
                <td class="muted">{{ period(c) }}</td>
                <td class="r num">{{ fmtUSD(c.spendUsd) }}</td>
                <td class="r num">{{ c.impressions ? fmtN(c.impressions) : '—' }}</td>
                <td class="r num">{{ c.clicks ? fmtN(c.clicks) : '—' }}</td>
                <td class="r num">{{ c.leads }}</td>
                <td class="r num">{{ c.costPerLeadUsd === null ? '—' : fmtUSD(c.costPerLeadUsd) }}</td>
                <td class="r num">{{ c.won }}</td>
                <td class="r num">{{ fmtK(c.revenueUsd) }}</td>
              </tr>
            } @empty {
              <tr><td colspan="10" class="empty">Aucune campagne pour le moment.</td></tr>
            }
          </tbody>
        </table>
      </div>

      <div class="grid g-2 mt-16">
        <div class="panel">
          <h2>Coût par lead selon le canal</h2>
          <p class="sub">En USD, plus court = plus rentable</p>
          <rsb-hbars [rows]="cplRows()" [accent]="true" />
          <h2 class="mt-16">CA par USD investi</h2>
          <p class="sub">ROI par canal, hors canaux sans dépense</p>
          <rsb-hbars [rows]="roiRows()" />
        </div>

        <div class="panel">
          <h2>Formulaire « Demande de cotation »</h2>
          <p class="sub">Aperçu du formulaire du site. L'envoi crée un vrai lead dans Leads entrants, à rejeter s'il s'agit d'un essai.</p>
          <form class="webform" [formGroup]="preview" (ngSubmit)="sendPreview()" novalidate>
            <label class="fld"><span>Nom</span><input formControlName="name" required maxlength="160"></label>
            <label class="fld"><span>Société</span><input formControlName="companyName" required maxlength="160"></label>
            <label class="fld">
              <span>E-mail</span><input type="email" formControlName="email" required maxlength="254">
              @if (previewErrors()['email']; as e) { <em class="fld-err">{{ e }}</em> }
            </label>
            <label class="fld"><span>Navire</span><input formControlName="vesselName" required maxlength="120"></label>
            <label class="fld">
              <span>Produit</span>
              <select formControlName="fuelGrade">
                @for (g of ref.fuelGrades(); track g.code) { <option [value]="g.code">{{ g.label }}</option> }
              </select>
            </label>
            <label class="fld"><span>Quantité (MT)</span><input type="number" min="1" formControlName="quantityMt" required></label>
            <label class="fld"><span>ETA Djibouti</span><input type="date" formControlName="eta"></label>
            <label class="fld">
              <span>Source</span>
              <select formControlName="source">
                @for (s of ref.sources(); track s) { <option [value]="s">{{ s }}</option> }
              </select>
            </label>
            <label class="fld wide"><span>Message</span><textarea rows="2" formControlName="message" maxlength="5000"></textarea></label>
            <div class="fld wide">
              <button class="btn primary" type="submit" [disabled]="sending() || preview.invalid">Envoyer la demande</button>
            </div>
          </form>
        </div>
      </div>

      @if (canManage()) {
        <div class="panel mt-16">
          <h2>Intégration au site WordPress</h2>
          <p class="sub">Collez ce code dans un bloc « HTML personnalisé » de la page de contact.</p>
          @if (config(); as c) {
            @if (!c.apiKey) {
              <p class="auth-error" role="alert">Le formulaire est fermé : renseignez APP_PUBLIC_LEAD_API_KEY sur le serveur.</p>
            }
            @if (!c.captchaEnabled) {
              <p class="auth-error" role="alert">Captcha inactif : renseignez CAPTCHA_SECRET et CAPTCHA_SITE_KEY (Cloudflare Turnstile) avant la mise en ligne.</p>
            } @else if (!c.captchaSiteKey) {
              <p class="auth-error" role="alert">CAPTCHA_SITE_KEY manque : le formulaire refuserait toutes les demandes.</p>
            }
            <p class="muted">
              Origines autorisées : {{ c.allowedOrigins.join(', ') }}. La clé API est visible dans le code de la page :
              c'est le captcha et la limitation de débit qui protègent réellement le formulaire.
            </p>
            <textarea class="snippet" rows="12" readonly aria-label="Code du formulaire à intégrer" [value]="snippet()"></textarea>
            <div class="mt-8"><button class="btn" type="button" (click)="copy()">Copier le code</button></div>
          } @else {
            <p class="empty">Chargement…</p>
          }
        </div>
      }
    } @else {
      <p class="empty">Chargement…</p>
    }
  `,
})
export class MarketingPage {
  private readonly api = inject(AnalyticsApi);
  private readonly sales = inject(SalesApi);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  protected readonly ref = inject(ReferenceService);
  protected readonly ui = inject(UiService);

  protected readonly fmtK = fmtK;
  protected readonly fmtN = fmtN;
  protected readonly fmtUSD = fmtUSD;

  protected readonly data = signal<MarketingSummary | null>(null);
  protected readonly config = signal<FormConfig | null>(null);
  protected readonly sending = signal(false);
  protected readonly previewErrors = signal<Record<string, string>>({});
  protected readonly canManage = computed(() => this.auth.hasRole('ADMIN', 'MANAGER'));

  protected readonly cplRows = computed(() =>
    (this.data()?.channels ?? [])
      .filter((c) => c.costPerLeadUsd !== null)
      .map((c) => ({ label: c.channel, value: c.costPerLeadUsd as number, display: fmtUSD(c.costPerLeadUsd) }))
      .sort((a, b) => a.value - b.value),
  );
  protected readonly roiRows = computed(() =>
    (this.data()?.channels ?? [])
      .filter((c) => c.revenuePerUsd !== null)
      .map((c) => ({ label: c.channel, value: c.revenuePerUsd as number, display: `${fmtN(c.revenuePerUsd)} USD` }))
      .sort((a, b) => b.value - a.value),
  );
  protected readonly snippet = computed(() => {
    const c = this.config();
    return c ? buildSnippet(c, this.ref.fuelGrades()) : '';
  });

  protected readonly preview = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required],
    companyName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    vesselName: ['', Validators.required],
    fuelGrade: ['VLSFO', Validators.required],
    quantityMt: [null as number | null, [Validators.required, Validators.min(1)]],
    eta: [''],
    source: ['Site web'],
    message: [''],
  });

  constructor() {
    effect(() => {
      this.ui.version();
      untracked(() => this.api.marketing().subscribe((d) => this.data.set(d)));
    });
    if (this.canManage()) {
      this.api.formConfig().subscribe((c) => this.config.set(c));
    }
  }

  protected period(c: CampaignRow): string {
    if (!c.periodStart && !c.periodEnd) return 'Sans limite';
    return `${c.periodStart ? fmtDate(c.periodStart) : '…'} → ${c.periodEnd ? fmtDate(c.periodEnd) : '…'}`;
  }

  protected edit(campaign: CampaignRow): void {
    if (this.canManage()) this.ui.openForm({ kind: 'campaign', campaign });
  }

  protected sendPreview(): void {
    if (this.preview.invalid) return;
    const v = this.preview.getRawValue();
    this.sending.set(true);
    this.previewErrors.set({});
    this.sales
      .createLead({
        name: v.name, companyName: v.companyName, email: v.email, phone: null, vesselName: v.vesselName, imo: null,
        fuelGrade: v.fuelGrade, quantityMt: v.quantityMt, eta: v.eta || null, deliveryLocation: null, source: v.source,
        message: v.message || null,
      })
      .subscribe({
        next: () => {
          this.sending.set(false);
          this.preview.reset();
          this.toast.show('Demande reçue dans Leads entrants.');
          this.ui.changed();
        },
        error: (e) => {
          this.sending.set(false);
          this.previewErrors.set(fieldErrors(e));
          this.toast.show(errorMessage(e));
        },
      });
  }

  protected copy(): void {
    navigator.clipboard.writeText(this.snippet()).then(
      () => this.toast.show('Code copié.'),
      () => this.toast.show('Copie impossible : sélectionnez le code à la main.'),
    );
  }
}
