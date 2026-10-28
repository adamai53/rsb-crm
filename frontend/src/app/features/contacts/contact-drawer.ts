import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';

import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { CrmApi } from '../../core/api/crm-api';
import { ContactDetail, Lifecycle } from '../../core/api/models';
import { daysFrom, fmtDate, fmtMT, fmtUSD } from '../../core/format';
import { LIFECYCLES, LIFECYCLE_LABELS, ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { ScoreBar, StagePill } from '../../shared/ui/badges';
import { Drawer } from '../../shared/ui/drawer';
import { Timeline } from '../../shared/ui/timeline';

type Tab = 'activity' | 'deals' | 'tasks' | 'score';

/** Fiche contact : coordonnées, cycle de vie, propriétaire, activité, transactions, tâches, détail du score. */
@Component({
  selector: 'rsb-contact-drawer',
  imports: [Drawer, Timeline, ScoreBar, StagePill],
  template: `
    @if (detail(); as d) {
      <rsb-drawer kind="Contact" [heading]="d.contact.firstName + ' ' + d.contact.lastName" (closed)="ui.closeRecord()">
        <span sub>
          {{ d.contact.title || 'Poste non renseigné' }}
          @if (d.contact.companyId) {
            · <button class="lnk" type="button" (click)="ui.openRecord('company', d.contact.companyId)">{{ d.contact.companyName }}</button>
          }
        </span>
        <ng-container actions>
          @if (d.editable) {
            <button class="btn sm" type="button" (click)="ui.openForm({ kind: 'contact', id: d.contact.id })">Modifier</button>
          }
          @if (d.contact.email) {
            <a class="btn sm" [href]="'mailto:' + d.contact.email">Écrire un e-mail</a>
          }
          @if (d.editable) {
            <button class="btn sm danger" type="button" (click)="remove()">Supprimer</button>
          }
        </ng-container>

        <div class="props">
          <div class="prop"><small>E-mail</small><div class="mono prop-copy">{{ d.contact.email || '—' }}</div></div>
          <div class="prop"><small>Téléphone</small><div class="mono prop-copy">{{ d.contact.phone || '—' }}</div></div>
          <div class="prop">
            <small><label for="dr-lc">Cycle de vie</label></small>
            <select id="dr-lc" [value]="d.contact.lifecycle" [disabled]="!d.editable"
                    (change)="setLifecycle($any($event.target).value)">
              @for (l of lifecycles; track l) { <option [value]="l">{{ lifecycleLabels[l] }}</option> }
            </select>
          </div>
          <div class="prop">
            <small><label for="dr-owner">Propriétaire</label></small>
            <select id="dr-owner" [value]="d.contact.ownerId" [disabled]="!canReassign()"
                    (change)="setOwner($any($event.target).value)">
              @for (u of owners(); track u.id) { <option [value]="u.id">{{ u.name }}</option> }
            </select>
          </div>
          <div class="prop"><small>Source</small><div>{{ d.contact.source || '—' }}</div></div>
          <div class="prop"><small>Score</small><div><rsb-score [value]="d.score.total" /></div></div>
        </div>

        <div class="tabs" role="tablist">
          @for (t of tabs(); track t.id) {
            <button class="tab" role="tab" type="button" [attr.aria-selected]="tab() === t.id" (click)="tab.set(t.id)">{{ t.label }}</button>
          }
        </div>

        @switch (tab()) {
          @case ('activity') {
            <rsb-timeline [contactId]="d.contact.id" [companyId]="d.contact.companyId" />
          }
          @case ('deals') {
            @for (deal of d.deals; track deal.id) {
              <div class="li">
                <div class="grow">
                  <b><button class="lnk" type="button" (click)="ui.openRecord('deal', deal.id)">{{ deal.vesselName }}</button></b>
                  <span><span class="grade">{{ deal.fuelGrade }}</span> · {{ fmtMT(deal.quantityMt) }} · {{ fmtUSD(deal.amountUsd) }} · ETA {{ fmtDate(deal.eta) }}</span>
                </div>
                <rsb-stage [code]="deal.stage" [label]="deal.stageLabel" />
              </div>
            } @empty { <p class="empty">Aucune transaction.</p> }
          }
          @case ('tasks') {
            @for (t of d.tasks; track t.id) {
              <div class="li">
                <div class="grow"><b [class.done]="t.done">{{ t.title }}</b><span>Échéance {{ fmtDate(t.dueDate) }} · priorité {{ t.priority.toLowerCase() }}</span></div>
                @if (!t.done && daysFrom(t.dueDate) < 0) { <span class="pill p-bad">En retard</span> }
              </div>
            } @empty { <p class="empty">Aucune tâche.</p> }
          }
          @case ('score') {
            <div class="panel">
              <h2>Score : {{ d.score.total }}/100</h2>
              <p class="sub">Calculé à partir de règles simples : source, cycle de vie, interactions, transactions ouvertes, poste.</p>
              <div class="hbars">
                @for (p of d.score.parts; track p.label) {
                  <div class="hb">
                    <span class="l">{{ p.label }}</span>
                    <span class="t"><i [style.width.%]="(p.points / maxPart) * 100"></i></span>
                    <span class="v">+{{ p.points }}</span>
                  </div>
                }
              </div>
            </div>
          }
        }
      </rsb-drawer>
    }
  `,
})
export class ContactDrawer {
  readonly id = input.required<string>();

  private readonly api = inject(CrmApi);
  private readonly auth = inject(AuthService);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly lifecycles = LIFECYCLES;
  protected readonly lifecycleLabels = LIFECYCLE_LABELS;
  protected readonly owners = this.ref.activeUsers;
  protected readonly canReassign = computed(() => this.auth.hasRole('ADMIN', 'MANAGER'));
  protected readonly fmtDate = fmtDate;
  protected readonly fmtMT = fmtMT;
  protected readonly fmtUSD = fmtUSD;
  protected readonly daysFrom = daysFrom;
  /** Plus forte contribution possible d'un critère (cycle de vie « Client » : 28). */
  protected readonly maxPart = 28;

  protected readonly detail = signal<ContactDetail | null>(null);
  protected readonly tab = signal<Tab>('activity');
  protected readonly tabs = computed(() => {
    const d = this.detail();
    return [
      { id: 'activity' as Tab, label: 'Activité' },
      { id: 'deals' as Tab, label: `Transactions · ${d?.deals.length ?? 0}` },
      { id: 'tasks' as Tab, label: `Tâches · ${d?.tasks.filter((t) => !t.done).length ?? 0}` },
      { id: 'score' as Tab, label: 'Score' },
    ];
  });

  constructor() {
    let lastId: string | null = null;
    effect(() => {
      const id = this.id();
      this.ui.version();
      untracked(() => {
        if (id !== lastId) this.tab.set('activity');
        lastId = id;
        this.api.contact(id).subscribe({
          next: (d) => this.detail.set(d),
          error: () => this.ui.closeRecord(),
        });
      });
    });
  }

  protected setLifecycle(lifecycle: Lifecycle): void {
    this.patch({ lifecycle }, 'Cycle de vie mis à jour');
  }

  protected setOwner(ownerId: string): void {
    this.patch({ ownerId }, `Contact attribué à ${this.ref.user(ownerId)?.name ?? 'un autre commercial'}`);
  }

  protected remove(): void {
    const c = this.detail()?.contact;
    if (!c || !confirm(`Supprimer le contact ${c.firstName} ${c.lastName} ? Ses activités et tâches seront supprimées.`)) return;
    this.api.deleteContact(c.id).subscribe({
      next: () => {
        this.toast.show('Contact supprimé');
        this.ui.closeRecord();
        this.ui.changed();
      },
      error: (e) => this.toast.show(errorMessage(e)),
    });
  }

  private patch(body: { lifecycle?: Lifecycle; ownerId?: string }, message: string): void {
    this.api.patchContact(this.id(), body).subscribe({
      next: () => {
        this.toast.show(message);
        this.ui.changed();
      },
      error: (e) => {
        this.toast.show(errorMessage(e));
        this.ui.changed();
      },
    });
  }
}
