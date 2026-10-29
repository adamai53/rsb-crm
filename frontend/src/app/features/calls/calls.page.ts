import { Component, computed, effect, inject, signal, untracked } from '@angular/core';

import { DealRow, SalesApi, UpcomingCalls } from '../../core/api/sales';
import { fmtDate, fmtMT } from '../../core/format';
import { UiService } from '../../core/ui.service';
import { OwnerAvatar, StagePill } from '../../shared/ui/badges';

const MONTHS = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

function addDays(iso: string, n: number): string {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + n);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Calendrier des escales sur 21 jours : charge prévisionnelle par jour et liste des navires attendus. */
@Component({
  selector: 'rsb-calls',
  imports: [StagePill, OwnerAvatar],
  template: `
    <div class="page-head">
      <div>
        <h1>Calendrier des escales</h1>
        <p>Navires attendus à Djibouti sur 21 jours. Sert à planifier les barges et à relancer avant l'arrivée.</p>
      </div>
    </div>

    @if (data(); as d) {
      <div class="panel calls-strip">
        <h2>Charge prévisionnelle</h2>
        <p class="sub">Nombre d'escales par jour, du {{ fmtDate(days()[0]) }} au {{ fmtDate(days()[days().length - 1]) }}</p>
        <div class="strip">
          @for (day of strip(); track day.date; let i = $index) {
            <div [class.today]="i === 0" [title]="fmtDate(day.date) + ' : ' + day.count + ' escale(s)'">
              <i>@if (day.count) { <b [style.height.%]="(day.count / maxCount()) * 100"></b> }</i>
              <span>{{ day.dayOfMonth }}</span>
            </div>
          }
        </div>
      </div>

      <div class="panel">
        <div class="list">
          @for (g of groups(); track g.date) {
            @for (c of g.calls; track c.id; let first = $first) {
              <div class="li">
                <div class="datebox" [class.hidden-box]="!first">
                  <b>{{ g.dayOfMonth }}</b><span>{{ g.month }}</span>
                </div>
                <div class="grow">
                  <b><button class="lnk" type="button" (click)="ui.openRecord('deal', c.id)">{{ c.vesselName }}</button>
                    <span class="num faint imo">IMO {{ c.imo || '—' }}</span></b>
                  <span>{{ c.companyName || '—' }} · {{ c.deliveryLocationLabel || 'Lieu à confirmer' }}</span>
                </div>
                <span class="num call-qty"><span class="grade">{{ c.fuelGrade }}</span><br>{{ fmtMT(c.quantityMt) }}</span>
                <rsb-stage [code]="c.stage" [label]="c.stageLabel" />
                <rsb-owner [id]="c.ownerId" />
              </div>
            }
          } @empty {
            <p class="empty">Aucune escale prévue sur les 21 prochains jours.</p>
          }
        </div>
      </div>
    } @else {
      <div class="panel empty">Chargement…</div>
    }
  `,
})
export class CallsPage {
  private readonly api = inject(SalesApi);
  protected readonly ui = inject(UiService);
  protected readonly fmtDate = fmtDate;
  protected readonly fmtMT = fmtMT;

  protected readonly data = signal<UpcomingCalls | null>(null);

  /** Les 21 jours de la frise, à partir de la date métier renvoyée par l'API. */
  protected readonly days = computed(() => {
    const d = this.data();
    return d ? Array.from({ length: d.days }, (_, i) => addDays(d.today, i)) : [];
  });
  protected readonly strip = computed(() => {
    const calls = this.data()?.calls ?? [];
    return this.days().map((date) => ({
      date, dayOfMonth: Number(date.slice(8, 10)), count: calls.filter((c) => c.eta === date).length,
    }));
  });
  protected readonly maxCount = computed(() => Math.max(1, ...this.strip().map((d) => d.count)));
  protected readonly groups = computed(() => {
    const byDate = new Map<string, DealRow[]>();
    for (const c of this.data()?.calls ?? []) {
      const list = byDate.get(c.eta!) ?? [];
      list.push(c);
      byDate.set(c.eta!, list);
    }
    return [...byDate.entries()].map(([date, calls]) => ({
      date, calls, dayOfMonth: Number(date.slice(8, 10)), month: MONTHS[Number(date.slice(5, 7)) - 1],
    }));
  });

  constructor() {
    effect(() => {
      this.ui.version();
      untracked(() => this.api.upcomingCalls(21).subscribe((d) => this.data.set(d)));
    });
  }
}
