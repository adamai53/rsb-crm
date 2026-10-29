import {
  CdkDrag,
  CdkDragDrop,
  CdkDragPlaceholder,
  CdkDropList,
  CdkDropListGroup,
  transferArrayItem,
} from '@angular/cdk/drag-drop';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';

import { DealList, DealRow, SalesApi } from '../../core/api/sales';
import { daysFrom, fmtDate, fmtK, fmtMT, fmtN, fmtUSD } from '../../core/format';
import { ReferenceService } from '../../core/reference.service';
import { UiService } from '../../core/ui.service';
import { OwnerAvatar, StagePill } from '../../shared/ui/badges';
import { DealActions } from './deal-actions';

interface Lane {
  code: string;
  label: string;
  probability: number;
  closed: boolean;
  lost: boolean;
  deals: DealRow[];
  amount: number;
}

/** Montant court des cartes : « 520 k », « 1,2 M ». */
function shortK(n: number): string {
  if (n >= 1e6) return `${(n / 1e6).toFixed(1).replace('.', ',')} M`;
  return `${Math.round(n / 1e3)} k`;
}

/** Transactions : pipeline kanban (glisser-déposer pour changer d'étape) ou vue liste. */
@Component({
  selector: 'rsb-deals',
  imports: [CdkDropListGroup, CdkDropList, CdkDrag, CdkDragPlaceholder, OwnerAvatar, StagePill],
  template: `
    <div class="page-head">
      <div>
        <h1>Transactions</h1>
        <p>Chaque transaction correspond à une escale de soutage : navire, IMO, ETA, produit et quantité. Glissez une carte pour changer d'étape.</p>
      </div>
      <div class="actions">
        <button class="btn" type="button" (click)="view.set(view() === 'board' ? 'list' : 'board')">
          {{ view() === 'board' ? 'Vue liste' : 'Vue pipeline' }}
        </button>
        <button class="btn primary" type="button" (click)="ui.openForm({ kind: 'deal' })">Nouvelle transaction</button>
      </div>
    </div>

    <div class="toolbar">
      <select aria-label="Commercial" (change)="setOwner($any($event.target).value)">
        <option value="all">Tous les commerciaux</option>
        @for (u of users(); track u.id) { <option [value]="u.id" [selected]="u.id === ownerId()">{{ u.name }}</option> }
      </select>
      <input type="search" placeholder="Navire, IMO, entreprise, contact" aria-label="Filtrer les transactions"
             [value]="qInput()" (input)="onSearch($any($event.target).value)">
      @if (data(); as d) {
        <span class="muted">
          {{ d.summary.openCount }} ouvertes · {{ fmtK(d.summary.openAmountUsd) }} · pondéré {{ fmtK(d.summary.weightedPipelineUsd) }}
          · transformation {{ d.summary.conversionRatePct }} %
        </span>
      }
    </div>

    @if (data()) {
      @if (view() === 'board') {
        <div class="board" cdkDropListGroup>
          @for (lane of lanes(); track lane.code) {
            <div class="lane" cdkDropList [cdkDropListData]="lane.deals" [id]="'lane-' + lane.code"
                 (cdkDropListDropped)="drop($event, lane)" [attr.aria-label]="'Étape ' + lane.label">
              <div class="lane-h">
                <b>{{ lane.label }}<span class="prob">{{ lane.lost ? '' : lane.probability + ' %' }}</span></b>
                <span>{{ lane.deals.length }} · {{ fmtK(lane.amount) }}</span>
              </div>
              @for (d of lane.deals; track d.id) {
                <div class="dcard" cdkDrag [cdkDragData]="d" tabindex="0" role="button"
                     [attr.aria-label]="d.vesselName + ', ' + lane.label"
                     (cdkDragStarted)="dragging = true" (cdkDragEnded)="endDrag()"
                     (click)="open(d)" (keydown.enter)="open(d)">
                  <span class="v">{{ d.vesselName }}</span>
                  <span class="c">{{ d.companyName || '—' }}</span>
                  <div class="row2">
                    <span><span class="grade">{{ d.fuelGrade }}</span> · <span class="num">{{ fmtMT(d.quantityMt) }}</span></span>
                    <span class="num">{{ shortK(d.amountUsd) }} $</span>
                  </div>
                  <div class="row2">
                    @if (d.eta) {
                      <span class="eta" [class.soon]="isSoon(d)">ETA {{ fmtDate(d.eta) }}{{ etaSuffix(d) }}</span>
                    } @else { <span class="eta">ETA —</span> }
                    <rsb-owner [id]="d.ownerId" />
                  </div>
                  <div class="dcard dcard-placeholder" *cdkDragPlaceholder></div>
                </div>
              }
            </div>
          }
        </div>
      } @else {
        <div class="tbl">
          <table>
            <thead>
              <tr>
                <th>Navire</th><th>IMO</th><th>Entreprise</th><th>Produit</th><th class="r">Quantité</th>
                <th class="r">Prix/MT</th><th class="r">Montant</th><th>ETA</th><th>Étape</th><th>Commercial</th>
              </tr>
            </thead>
            <tbody>
              @for (d of listRows(); track d.id) {
                <tr class="row" tabindex="0" (click)="open(d)" (keydown.enter)="open(d)">
                  <td><b>{{ d.vesselName }}</b></td>
                  <td class="num muted">{{ d.imo || '—' }}</td>
                  <td>{{ d.companyName || '—' }}</td>
                  <td><span class="grade">{{ d.fuelGrade }}</span></td>
                  <td class="r num">{{ fmtMT(d.quantityMt) }}</td>
                  <td class="r num">{{ fmtN(d.priceUsdPerMt) }}</td>
                  <td class="r num">{{ fmtUSD(d.amountUsd) }}</td>
                  <td class="num">{{ fmtDate(d.eta) }}</td>
                  <td><rsb-stage [code]="d.stage" [label]="d.stageLabel" /></td>
                  <td><rsb-owner [id]="d.ownerId" /></td>
                </tr>
              } @empty {
                <tr><td colspan="10" class="empty">Aucune transaction.</td></tr>
              }
            </tbody>
          </table>
        </div>
      }
    } @else {
      <div class="panel empty">Chargement…</div>
    }
  `,
})
export class DealsPage {
  private readonly api = inject(SalesApi);
  private readonly ref = inject(ReferenceService);
  private readonly actions = inject(DealActions);
  protected readonly ui = inject(UiService);

  protected readonly users = this.ref.users;
  protected readonly fmtK = fmtK;
  protected readonly fmtMT = fmtMT;
  protected readonly fmtN = fmtN;
  protected readonly fmtUSD = fmtUSD;
  protected readonly fmtDate = fmtDate;
  protected readonly shortK = shortK;

  protected readonly view = signal<'board' | 'list'>('board');
  protected readonly ownerId = signal<string | null>(null);
  protected readonly qInput = signal('');
  private readonly q = signal('');
  protected readonly data = signal<DealList | null>(null);
  protected dragging = false;
  private searchTimer?: ReturnType<typeof setTimeout>;

  /** Colonnes du pipeline ; les étapes clôturées montrent d'abord les plus récentes. */
  protected readonly lanes = computed<Lane[]>(() => {
    const deals = this.data()?.content ?? [];
    return this.ref.stages().map((s) => {
      const closed = s.won || s.lost;
      const xs = deals
        .filter((d) => d.stage === s.code)
        .sort((a, b) => (closed ? -1 : 1) * (a.eta ?? '9999').localeCompare(b.eta ?? '9999'));
      return {
        code: s.code, label: s.label, probability: s.probability, closed, lost: s.lost, deals: xs,
        amount: xs.reduce((t, d) => t + d.amountUsd, 0),
      };
    });
  });

  protected readonly listRows = computed(() =>
    [...(this.data()?.content ?? [])].sort((a, b) => (b.eta ?? '').localeCompare(a.eta ?? '')),
  );

  constructor() {
    effect(() => {
      const query = { ownerId: this.ownerId(), q: this.q() };
      this.ui.version();
      untracked(() => this.api.deals(query).subscribe((d) => this.data.set(d)));
    });
  }

  protected isSoon(d: DealRow): boolean {
    const n = daysFrom(d.eta!);
    return n >= 0 && n <= 3 && !d.won && !d.lost;
  }

  protected etaSuffix(d: DealRow): string {
    const n = daysFrom(d.eta!);
    return n >= 0 && !d.won && !d.lost ? ` · J-${n}` : '';
  }

  protected drop(event: CdkDragDrop<DealRow[]>, lane: Lane): void {
    if (event.previousContainer === event.container) return;
    const deal = event.item.data as DealRow;
    // Déplacement immédiat à l'écran ; la liste se recharge après la réponse (ou l'annulation du motif de perte)
    transferArrayItem(event.previousContainer.data, event.container.data, event.previousIndex, event.currentIndex);
    this.actions.move(deal, lane.code);
  }

  protected endDrag(): void {
    // Le clic qui suit un glisser-déposer ne doit pas ouvrir la fiche
    setTimeout(() => (this.dragging = false), 50);
  }

  protected open(d: DealRow): void {
    if (!this.dragging) this.ui.openRecord('deal', d.id);
  }

  protected setOwner(value: string): void {
    this.ownerId.set(value === 'all' ? null : value);
  }

  protected onSearch(value: string): void {
    this.qInput.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.q.set(value.trim()), 250);
  }
}
