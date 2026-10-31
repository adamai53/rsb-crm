import { Component, computed, effect, inject, signal, untracked } from '@angular/core';

import { AnalyticsApi, Dashboard } from '../../core/api/analytics';
import { Activity, ActivityType } from '../../core/api/models';
import { SalesApi, TaskItem } from '../../core/api/sales';
import { AuthService } from '../../core/auth/auth.service';
import { daysFrom, fmtDateY, fmtK, fmtMT, rel } from '../../core/format';
import { ReferenceService, STAGE_CLASS } from '../../core/reference.service';
import { UiService } from '../../core/ui.service';
import { HBars, shortK } from '../../shared/ui/charts';
import { TaskItemRow } from '../../shared/ui/task-item';

const MONTHS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];
const LABELS: Record<ActivityType, string> = {
  NOTE: 'Note', EMAIL: 'E-mail', CALL: 'Appel', MEETING: 'Réunion', SYSTEM: 'Mise à jour',
};
const ICONS: Record<ActivityType, string> = { NOTE: 'NOTE', EMAIL: 'MAIL', CALL: 'APP', MEETING: 'RDV', SYSTEM: 'SYS' };

/** Tableau de bord commercial : indicateurs, pipeline, escales, tâches et activité récente. */
@Component({
  selector: 'rsb-dashboard',
  imports: [HBars, TaskItemRow],
  template: `
    @if (data(); as d) {
      <div class="page-head">
        <div>
          <h1>Tableau de bord commercial</h1>
          <p>Vue d'ensemble du pipeline de soutage au {{ fmtDateY(d.today) }}.</p>
        </div>
      </div>

      <div class="grid g-kpi">
        <div class="kpi">
          <span class="lbl">Pipeline pondéré</span><span class="val">{{ fmtK(d.weightedPipelineUsd) }}</span>
          <span class="foot">{{ d.openCount }} {{ d.openCount > 1 ? 'transactions ouvertes' : 'transaction ouverte' }} · {{ fmtK(d.openAmountUsd) }} brut</span>
        </div>
        <div class="kpi">
          <span class="lbl">Demandes · 30 jours</span><span class="val">{{ d.requests30Days }}</span>
          <span class="foot">{{ d.newLeads }} {{ d.newLeads > 1 ? 'leads entrants' : 'lead entrant' }} à traiter</span>
        </div>
        <div class="kpi">
          <span class="lbl">Taux de transformation</span><span class="val">{{ d.conversionRatePct }} %</span>
          <span class="foot">{{ d.wonCount }} {{ d.wonCount > 1 ? 'livrées' : 'livrée' }}, {{ d.lostCount }} {{ d.lostCount > 1 ? 'perdues' : 'perdue' }}</span>
        </div>
        <div class="kpi">
          <span class="lbl">Volume livré · 90 jours</span><span class="val">{{ fmtMT(d.deliveredMt90Days) }}</span>
          <span class="foot">Toutes qualités confondues</span>
        </div>
      </div>

      <div class="grid g-3-1 mt-16">
        <div class="panel">
          <h2>Pipeline par étape</h2>
          <p class="sub">Nombre de transactions et valeur brute (quantité × prix/MT)</p>
          <rsb-hbars [rows]="stageRows()" />
        </div>
        <div class="panel">
          <h2>Escales des 7 prochains jours</h2>
          <p class="sub">Navires attendus avec une transaction ouverte</p>
          <div class="list">
            @for (c of d.nextCalls; track c.id) {
              <div class="li">
                <div class="datebox"><b>{{ day(c.eta) }}</b><span>{{ month(c.eta) }}</span></div>
                <div class="grow">
                  <b><button class="lnk" type="button" (click)="ui.openRecord('deal', c.id)">{{ c.vesselName }}</button></b>
                  <span>{{ c.companyName || '—' }} · <span class="grade">{{ c.fuelGrade }}</span> {{ fmtMT(c.quantityMt) }}</span>
                </div>
                <span [class]="'pill ' + stageClass[c.stage]">{{ c.stageLabel }}</span>
              </div>
            } @empty {
              <p class="empty">Aucune escale prévue.</p>
            }
          </div>
        </div>
      </div>

      <div class="grid g-2 mt-16">
        <div class="panel">
          <h2>Transactions par source</h2>
          <p class="sub">D'où viennent les demandes de soutage</p>
          <rsb-hbars [rows]="sourceRows()" [accent]="true" />
        </div>
        <div class="panel">
          <h2>À faire aujourd'hui</h2>
          <p class="sub">Tâches en retard ou dues aujourd'hui</p>
          <div class="list">
            @for (t of dueTasks(); track t.id) {
              <rsb-task-item [task]="t" />
            } @empty {
              <p class="empty">Rien en attente.</p>
            }
          </div>
        </div>
      </div>

      <div class="panel mt-16">
        <h2>Activité récente</h2>
        <p class="sub">Notes, appels et e-mails enregistrés par l'équipe</p>
        @if (d.recentActivity.length) {
          <div class="timeline">
            @for (a of d.recentActivity; track a.id) {
              <div class="ev">
                <span [class]="'ic ' + a.type.toLowerCase()">{{ icons[a.type] }}</span>
                <div>
                  <div class="meta">
                    {{ labels[a.type] }} · {{ author(a) }} · {{ rel(a.occurredAt) }}
                    @if (a.dealVessel && a.dealId) {
                      · <button class="lnk ev-link" type="button" (click)="ui.openRecord('deal', a.dealId)">{{ a.dealVessel }}</button>
                    } @else if (a.contactName && a.contactId) {
                      · <button class="lnk ev-link" type="button" (click)="ui.openRecord('contact', a.contactId)">{{ a.contactName }}</button>
                    }
                  </div>
                  <div class="txt">{{ a.text }}</div>
                </div>
              </div>
            }
          </div>
        } @else {
          <p class="empty">Aucune activité pour le moment.</p>
        }
      </div>
    } @else {
      <p class="empty">Chargement…</p>
    }
  `,
})
export class DashboardPage {
  private readonly api = inject(AnalyticsApi);
  private readonly sales = inject(SalesApi);
  private readonly auth = inject(AuthService);
  private readonly ref = inject(ReferenceService);
  protected readonly ui = inject(UiService);

  protected readonly stageClass = STAGE_CLASS;
  protected readonly labels = LABELS;
  protected readonly icons = ICONS;
  protected readonly fmtDateY = fmtDateY;
  protected readonly fmtK = fmtK;
  protected readonly fmtMT = fmtMT;
  protected readonly rel = rel;

  protected readonly data = signal<Dashboard | null>(null);
  private readonly tasks = signal<TaskItem[]>([]);

  protected readonly stageRows = computed(() =>
    (this.data()?.byStage ?? []).map((s) => ({
      label: s.label, value: s.amountUsd, display: `${s.count} · ${shortK(s.amountUsd)}`,
    })),
  );
  protected readonly sourceRows = computed(() =>
    (this.data()?.bySource ?? []).map((s) => ({ label: s.label, value: s.count })),
  );
  protected readonly dueTasks = computed(() =>
    this.tasks()
      .filter((t) => !t.done && daysFrom(t.dueDate) <= 0)
      .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
      .slice(0, 8),
  );

  constructor() {
    effect(() => {
      this.ui.version();
      untracked(() => {
        this.api.dashboard().subscribe((d) => this.data.set(d));
        // Un commercial voit ses tâches, un manager celles de l'équipe
        const mine = this.auth.hasRole('SALES') ? this.auth.user()?.id : null;
        this.sales.tasks(mine).subscribe((t) => this.tasks.set(t));
      });
    });
  }

  protected day(iso: string | null): number | string {
    return iso ? Number(iso.slice(8, 10)) : '—';
  }

  protected month(iso: string | null): string {
    return iso ? MONTHS[Number(iso.slice(5, 7)) - 1] : '';
  }

  protected author(a: Activity): string {
    return this.ref.user(a.authorId)?.name ?? 'Système';
  }
}
