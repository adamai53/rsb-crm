import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';

import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { DocumentsApi, QUOTE_STATUS, Quote } from '../../core/api/documents';
import { DealDetail, SalesApi } from '../../core/api/sales';
import { fmtDateY, fmtMT, fmtN, fmtUSD, rel } from '../../core/format';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { Drawer } from '../../shared/ui/drawer';
import { TaskItemRow } from '../../shared/ui/task-item';
import { Timeline } from '../../shared/ui/timeline';
import { DealActions } from './deal-actions';

type Tab = 'activity' | 'tasks' | 'quotes';

/** Fiche transaction : escale, produit, montant, étape et commercial modifiables, activité, tâches. */
@Component({
  selector: 'rsb-deal-drawer',
  imports: [Drawer, Timeline, TaskItemRow],
  template: `
    @if (detail(); as dd) {
      @let d = dd.deal;
      <rsb-drawer kind="Transaction · demande de soutage" [heading]="d.vesselName" (closed)="ui.closeRecord()">
        <span sub>
          @if (d.companyId) {
            <button class="lnk" type="button" (click)="ui.openRecord('company', d.companyId)">{{ d.companyName }}</button>
          }
          @if (d.contactId) {
            · <button class="lnk ev-link" type="button" (click)="ui.openRecord('contact', d.contactId)">{{ d.contactName }}</button>
          }
        </span>
        <ng-container actions>
          @if (dd.editable && !d.won && !d.lost) {
            <button class="btn sm primary" type="button" (click)="ui.openForm({ kind: 'quote-new', dealId: d.id })">Générer un devis</button>
          }
          @if (d.contactId) {
            <button class="btn sm" type="button" (click)="ui.openForm({ kind: 'compose', contactId: d.contactId, dealId: d.id })">Envoyer un e-mail</button>
          }
          <button class="btn sm" type="button" (click)="ui.openForm({ kind: 'task', dealId: d.id })">Nouvelle tâche</button>
          @if (dd.editable) {
            <button class="btn sm" type="button" (click)="ui.openForm({ kind: 'deal', id: d.id })">Modifier</button>
            <button class="btn sm danger" type="button" (click)="remove()">Supprimer</button>
          }
        </ng-container>

        <div class="props">
          <div class="prop">
            <small><label for="dr-stage">Étape</label></small>
            <select id="dr-stage" [disabled]="!dd.editable" (change)="move($any($event.target).value)">
              @for (s of stages(); track s.code) { <option [value]="s.code" [selected]="s.code === d.stage">{{ s.label }}</option> }
            </select>
          </div>
          <div class="prop">
            <small><label for="dr-d-owner">Commercial</label></small>
            <select id="dr-d-owner" [disabled]="!canReassign()" (change)="setOwner($any($event.target).value)">
              @for (u of owners(); track u.id) { <option [value]="u.id" [selected]="u.id === d.ownerId">{{ u.name }}</option> }
            </select>
          </div>
          <div class="prop"><small>Navire · IMO</small><div>{{ d.vesselName }} · <span class="num">{{ d.imo || '—' }}</span></div></div>
          <div class="prop"><small>ETA</small><div>{{ fmtDateY(d.eta) }} <span class="faint">({{ rel(d.eta) }})</span></div></div>
          <div class="prop"><small>Produit</small><div><span class="grade">{{ gradeLabel() }}</span></div></div>
          <div class="prop"><small>Quantité</small><div class="num">{{ fmtMT(d.quantityMt) }}</div></div>
          <div class="prop"><small>Prix</small><div class="num">{{ fmtN(d.priceUsdPerMt) }} USD/MT</div></div>
          <div class="prop"><small>Montant</small><div><b class="num">{{ fmtUSD(d.amountUsd) }}</b></div></div>
          <div class="prop"><small>Lieu de livraison</small><div>{{ d.deliveryLocationLabel || '—' }}</div></div>
          <div class="prop"><small>Source</small><div>{{ d.source || '—' }}</div></div>
          @if (d.lostReason) {
            <div class="prop"><small>Motif de perte</small><div class="lost-reason">{{ d.lostReason }}</div></div>
          } @else {
            <div class="prop"><small>Probabilité</small><div class="num">{{ d.probability }} %</div></div>
          }
          <div class="prop"><small>{{ d.closedAt ? 'Clôturée le' : 'Créée le' }}</small><div>{{ fmtDateY(d.closedAt ?? d.createdAt) }}</div></div>
        </div>

        <div class="tabs" role="tablist">
          @for (t of tabs(); track t.id) {
            <button class="tab" role="tab" type="button" [attr.aria-selected]="tab() === t.id" (click)="tab.set(t.id)">{{ t.label }}</button>
          }
        </div>

        @switch (tab()) {
          @case ('activity') { <rsb-timeline [dealId]="d.id" [contactId]="d.contactId" [companyId]="d.companyId" /> }
          @case ('tasks') {
            <div class="list">
              @for (t of dd.tasks; track t.id) { <rsb-task-item [task]="t" /> } @empty { <p class="empty">Aucune tâche.</p> }
            </div>
          }
          @case ('quotes') {
            <div class="list">
              @for (q of quotes(); track q.id) {
                <div class="li">
                  <div class="grow">
                    <b><button class="lnk" type="button" (click)="ui.openForm({ kind: 'quote', id: q.id })">{{ q.number }}</button></b>
                    <span>{{ fmtN(q.priceUsdPerMt) }} USD/MT · total {{ fmtUSD(q.totalUsd) }}</span>
                  </div>
                  <span [class]="'pill ' + quoteStatus[q.status].cls">{{ quoteStatus[q.status].label }}</span>
                </div>
              } @empty { <p class="empty">Aucun devis.</p> }
            </div>
          }
        }
      </rsb-drawer>
    }
  `,
})
export class DealDrawer {
  readonly id = input.required<string>();

  private readonly api = inject(SalesApi);
  private readonly documents = inject(DocumentsApi);
  private readonly auth = inject(AuthService);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  private readonly actions = inject(DealActions);
  protected readonly ui = inject(UiService);

  protected readonly stages = this.ref.stages;
  protected readonly owners = this.ref.activeUsers;
  protected readonly canReassign = computed(() => this.auth.hasRole('ADMIN', 'MANAGER'));
  protected readonly fmtDateY = fmtDateY;
  protected readonly fmtMT = fmtMT;
  protected readonly fmtN = fmtN;
  protected readonly fmtUSD = fmtUSD;
  protected readonly rel = rel;

  protected readonly quoteStatus = QUOTE_STATUS;
  protected readonly quotes = signal<Quote[]>([]);
  protected readonly detail = signal<DealDetail | null>(null);
  protected readonly tab = signal<Tab>('activity');
  protected readonly gradeLabel = computed(() => {
    const code = this.detail()?.deal.fuelGrade;
    return this.ref.fuelGrade(code)?.label ?? code ?? '';
  });
  protected readonly tabs = computed(() => [
    { id: 'activity' as Tab, label: 'Activité' },
    { id: 'tasks' as Tab, label: `Tâches · ${this.detail()?.tasks.filter((t) => !t.done).length ?? 0}` },
    { id: 'quotes' as Tab, label: `Devis · ${this.quotes().length}` },
  ]);

  constructor() {
    let lastId: string | null = null;
    effect(() => {
      const id = this.id();
      this.ui.version();
      untracked(() => {
        if (id !== lastId) this.tab.set('activity');
        lastId = id;
        this.api.deal(id).subscribe({
          next: (d) => this.detail.set(d),
          error: () => this.ui.closeRecord(),
        });
        this.documents.quotes(id).subscribe((q) => this.quotes.set(q));
      });
    });
  }

  protected move(stage: string): void {
    const d = this.detail()?.deal;
    if (d) this.actions.move(d, stage);
  }

  protected setOwner(ownerId: string): void {
    this.api.setDealOwner(this.id(), ownerId).subscribe({
      next: () => {
        this.toast.show(`Transaction attribuée à ${this.ref.user(ownerId)?.name ?? 'un autre commercial'}`);
        this.ui.changed();
      },
      error: (e) => {
        this.toast.show(errorMessage(e));
        this.ui.changed();
      },
    });
  }

  protected remove(): void {
    const d = this.detail()?.deal;
    if (!d || !confirm(`Supprimer la transaction ${d.vesselName} ? Son activité et ses tâches seront supprimées.`)) return;
    this.api.deleteDeal(d.id).subscribe({
      next: () => {
        this.toast.show('Transaction supprimée');
        this.ui.closeRecord();
        this.ui.changed();
      },
      error: (e) => this.toast.show(errorMessage(e)),
    });
  }
}
