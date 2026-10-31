import { Component, computed, effect, inject, signal, untracked } from '@angular/core';

import { AnalyticsApi, Reports } from '../../core/api/analytics';
import { AuthService } from '../../core/auth/auth.service';
import { DemoService } from '../../core/demo.service';
import { fmtK, fmtMT } from '../../core/format';
import { UiService } from '../../core/ui.service';
import { BarRow, HBars, VBars } from '../../shared/ui/charts';

const MONTHS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

/** Rapports : complets pour un manager, limités à son périmètre pour un commercial. */
@Component({
  selector: 'rsb-reports',
  imports: [HBars, VBars],
  template: `
    <div class="page-head">
      <div>
        <h1>Rapports</h1>
        <p>
          Chiffre d'affaires livré, entonnoir de conversion et performance
          {{ scoped() ? 'sur vos dossiers.' : "de l'équipe." }}
        </p>
      </div>
      @if (demo.active()) { <span class="demo-note">Données de démonstration</span> }
    </div>

    @if (data(); as r) {
      <div class="grid g-3-1">
        <div class="panel">
          <h2>CA livré par mois</h2>
          <p class="sub">Livraisons avec BDN signé, en USD</p>
          <rsb-vbars [rows]="revenue()" label="Chiffre d'affaires livré sur six mois" />
        </div>
        <div class="panel">
          <h2>Entonnoir du cycle de vie</h2>
          <p class="sub">Contacts ayant atteint au moins chaque étape</p>
          <rsb-hbars [rows]="funnel()" />
          <p class="sub cycle">
            @if (r.cycle.averageDays !== null) {
              Cycle de vente moyen : <b class="num">{{ r.cycle.averageDays }} {{ r.cycle.averageDays > 1 ? 'jours' : 'jour' }}</b> entre la demande et la livraison.
            } @else {
              Cycle de vente moyen : aucune livraison pour le moment.
            }
          </p>
        </div>
      </div>

      <div class="grid g-2 mt-16">
        <div class="panel">
          <h2>Volume livré par produit</h2>
          <p class="sub">Tonnes métriques, 6 derniers mois</p>
          <rsb-hbars [rows]="volumes()" [accent]="true" />
        </div>
        <div class="panel">
          <h2>Motifs de perte</h2>
          <p class="sub">À suivre pour ajuster prix et disponibilité</p>
          <rsb-hbars [rows]="lost()" empty="Aucune transaction perdue." />
        </div>
      </div>

      <div class="tbl mt-16">
        <table>
          <thead>
            <tr>
              <th>Commercial</th><th class="r">Livraisons</th><th class="r">CA livré</th>
              <th class="r">Taux de transformation</th><th class="r">Pipeline ouvert</th>
            </tr>
          </thead>
          <tbody>
            @for (p of r.performance; track p.userId) {
              <tr>
                <td><span class="who-cell"><span class="av">{{ p.initials }}</span><b>{{ p.name }}</b></span></td>
                <td class="r num">{{ p.delivered }}</td>
                <td class="r num">{{ fmtK(p.revenueUsd) }}</td>
                <td class="r num">{{ p.conversionRatePct }} %</td>
                <td class="r num">{{ fmtK(p.openAmountUsd) }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="empty">Aucun commercial à afficher.</td></tr>
            }
          </tbody>
        </table>
      </div>
    } @else {
      <p class="empty">Chargement…</p>
    }
  `,
})
export class ReportsPage {
  private readonly api = inject(AnalyticsApi);
  private readonly auth = inject(AuthService);
  private readonly ui = inject(UiService);
  protected readonly demo = inject(DemoService);
  protected readonly fmtK = fmtK;

  protected readonly data = signal<Reports | null>(null);
  protected readonly scoped = computed(() => this.auth.hasRole('SALES'));

  protected readonly revenue = computed<BarRow[]>(() => {
    const rows = this.data()?.revenue ?? [];
    return rows.map((m, i) => ({
      label: MONTHS[Number(m.month.slice(5, 7)) - 1], value: m.revenueUsd, now: i === rows.length - 1,
    }));
  });
  protected readonly funnel = computed<BarRow[]>(() =>
    (this.data()?.funnel ?? []).map((s) => ({ label: s.label, value: s.count })),
  );
  protected readonly volumes = computed<BarRow[]>(() =>
    (this.data()?.volumes ?? []).map((v) => ({ label: v.label, value: v.volumeMt, display: fmtMT(v.volumeMt) })),
  );
  protected readonly lost = computed<BarRow[]>(() =>
    (this.data()?.lostReasons ?? []).map((l) => ({ label: l.label, value: l.count })),
  );

  constructor() {
    effect(() => {
      this.ui.version();
      untracked(() => this.api.reports().subscribe((r) => this.data.set(r)));
    });
  }
}
