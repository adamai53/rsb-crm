import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';

import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { CrmApi } from '../../core/api/crm-api';
import { CompanyDetail } from '../../core/api/models';
import { fmtDate, fmtDateY, fmtMT, fmtUSD } from '../../core/format';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { LifecyclePill, StagePill } from '../../shared/ui/badges';
import { Drawer } from '../../shared/ui/drawer';
import { Timeline } from '../../shared/ui/timeline';

type Tab = 'activity' | 'contacts' | 'deals';

/** Fiche entreprise : indicateurs, propriétaire, activité, contacts et transactions. */
@Component({
  selector: 'rsb-company-drawer',
  imports: [Drawer, Timeline, LifecyclePill, StagePill],
  template: `
    @if (detail(); as d) {
      <rsb-drawer kind="Entreprise" [heading]="d.company.name" (closed)="ui.closeRecord()">
        <span sub>{{ typeLabel() }} · {{ place() }}</span>
        <ng-container actions>
          <button class="btn sm" type="button" (click)="ui.openForm({ kind: 'contact', companyId: d.company.id })">Ajouter un contact</button>
          <button class="btn sm" type="button" (click)="ui.openForm({ kind: 'deal', companyId: d.company.id })">Nouvelle transaction</button>
          @if (d.editable) {
            <button class="btn sm" type="button" (click)="ui.openForm({ kind: 'company', id: d.company.id })">Modifier</button>
            <button class="btn sm danger" type="button" (click)="remove()">Supprimer</button>
          }
        </ng-container>

        <div class="props">
          <div class="prop"><small>Site web</small>
            <div class="mono prop-copy">
              @if (d.company.website) { <a [href]="websiteUrl()" target="_blank" rel="noopener noreferrer">{{ d.company.website }}</a> } @else { — }
            </div>
          </div>
          <div class="prop"><small>Flotte</small><div>{{ d.company.fleetSize ? d.company.fleetSize + ' navires' : '—' }}</div></div>
          <div class="prop"><small>CA livré cumulé</small><div class="num">{{ fmtUSD(d.company.revenueUsd) }}</div></div>
          <div class="prop"><small>Volume livré</small><div class="num">{{ fmtMT(d.company.deliveredVolumeMt) }}</div></div>
          <div class="prop">
            <small><label for="dr-co-owner">Propriétaire</label></small>
            <select id="dr-co-owner" [disabled]="!canReassign()" (change)="setOwner($any($event.target).value)">
              @for (u of owners(); track u.id) { <option [value]="u.id" [selected]="u.id === d.company.ownerId">{{ u.name }}</option> }
            </select>
          </div>
          <div class="prop"><small>Dans le CRM depuis</small><div>{{ fmtDateY(d.company.createdAt) }}</div></div>
        </div>

        <div class="tabs" role="tablist">
          @for (t of tabs(); track t.id) {
            <button class="tab" role="tab" type="button" [attr.aria-selected]="tab() === t.id" (click)="tab.set(t.id)">{{ t.label }}</button>
          }
        </div>

        @switch (tab()) {
          @case ('activity') { <rsb-timeline [companyId]="d.company.id" /> }
          @case ('contacts') {
            <div class="list">
              @for (c of d.contacts; track c.id) {
                <div class="li">
                  <span class="av">{{ c.firstName.charAt(0) }}{{ c.lastName.charAt(0) }}</span>
                  <div class="grow">
                    <b><button class="lnk" type="button" (click)="ui.openRecord('contact', c.id)">{{ c.firstName }} {{ c.lastName }}</button></b>
                    <span>{{ c.title || '—' }}</span>
                  </div>
                  <rsb-lifecycle [value]="c.lifecycle" />
                </div>
              } @empty { <p class="empty">Aucun contact.</p> }
            </div>
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
        }
      </rsb-drawer>
    }
  `,
})
export class CompanyDrawer {
  readonly id = input.required<string>();

  private readonly api = inject(CrmApi);
  private readonly auth = inject(AuthService);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly owners = this.ref.activeUsers;
  protected readonly canReassign = computed(() => this.auth.hasRole('ADMIN', 'MANAGER'));
  protected readonly fmtDate = fmtDate;
  protected readonly fmtDateY = fmtDateY;
  protected readonly fmtMT = fmtMT;
  protected readonly fmtUSD = fmtUSD;

  protected readonly detail = signal<CompanyDetail | null>(null);
  protected readonly tab = signal<Tab>('activity');
  protected readonly tabs = computed(() => {
    const d = this.detail();
    return [
      { id: 'activity' as Tab, label: 'Activité' },
      { id: 'contacts' as Tab, label: `Contacts · ${d?.contacts.length ?? 0}` },
      { id: 'deals' as Tab, label: `Transactions · ${d?.deals.length ?? 0}` },
    ];
  });
  protected readonly typeLabel = computed(() => {
    const d = this.detail();
    return d ? this.ref.companyTypeLabel(d.company.type) : '';
  });
  protected readonly place = computed(() => {
    const c = this.detail()?.company;
    return [c?.city, c?.country].filter(Boolean).join(', ') || 'Localisation non renseignée';
  });
  /** Lien externe sûr : uniquement http(s). */
  protected readonly websiteUrl = computed(() => {
    const w = this.detail()?.company.website ?? '';
    return /^https?:\/\//i.test(w) ? w : `https://${w}`;
  });

  constructor() {
    let lastId: string | null = null;
    effect(() => {
      const id = this.id();
      this.ui.version();
      untracked(() => {
        if (id !== lastId) this.tab.set('activity');
        lastId = id;
        this.api.company(id).subscribe({
          next: (d) => this.detail.set(d),
          error: () => this.ui.closeRecord(),
        });
      });
    });
  }

  protected setOwner(ownerId: string): void {
    this.api.setCompanyOwner(this.id(), ownerId).subscribe({
      next: () => {
        this.toast.show(`Entreprise attribuée à ${this.ref.user(ownerId)?.name ?? 'un autre commercial'}`);
        this.ui.changed();
      },
      error: (e) => {
        this.toast.show(errorMessage(e));
        this.ui.changed();
      },
    });
  }

  protected remove(): void {
    const c = this.detail()?.company;
    if (!c || !confirm(`Supprimer l'entreprise ${c.name} ? Ses contacts seront conservés, sans entreprise.`)) return;
    this.api.deleteCompany(c.id).subscribe({
      next: () => {
        this.toast.show('Entreprise supprimée');
        this.ui.closeRecord();
        this.ui.changed();
      },
      error: (e) => this.toast.show(errorMessage(e)),
    });
  }
}
