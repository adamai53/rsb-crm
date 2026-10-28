import { Component, effect, inject, signal, untracked } from '@angular/core';

import { CrmApi } from '../../core/api/crm-api';
import { CompanyRow, Page } from '../../core/api/models';
import { fmtK, fmtMT } from '../../core/format';
import { ReferenceService } from '../../core/reference.service';
import { UiService } from '../../core/ui.service';
import { OwnerAvatar } from '../../shared/ui/badges';
import { Pager } from '../../shared/ui/pager';

const PAGE_SIZE = 50;

/** Liste des entreprises, triée par CA livré (seules les livraisons avec BDN signé comptent). */
@Component({
  selector: 'rsb-companies',
  imports: [OwnerAvatar, Pager],
  template: `
    <div class="page-head">
      <div>
        <h1>Entreprises</h1>
        <p>Armateurs, ship managers, traders et agents. Le chiffre d'affaires cumulé ne compte que les livraisons avec BDN signé.</p>
      </div>
      <div class="actions">
        <button class="btn primary" type="button" (click)="ui.openForm({ kind: 'company' })">Nouvelle entreprise</button>
      </div>
    </div>

    <div class="toolbar">
      <input type="search" placeholder="Filtrer par nom, pays, type" aria-label="Filtrer les entreprises"
             [value]="qInput()" (input)="onSearch($any($event.target).value)">
      <select aria-label="Trier par" [value]="sort()" (change)="setSort($any($event.target).value)">
        <option value="revenue">Trier par CA livré</option>
        <option value="openDeals">Trier par transactions ouvertes</option>
        <option value="fleet">Trier par taille de flotte</option>
        <option value="name">Trier par nom</option>
        <option value="createdAt">Les plus récentes</option>
      </select>
    </div>

    @if (data(); as d) {
      @if (d.content.length) {
        <div class="tbl">
          <table>
            <thead>
              <tr>
                <th>Entreprise</th><th>Type</th><th>Pays</th><th class="r">Flotte</th><th class="r">Contacts</th>
                <th class="r">Transactions ouvertes</th><th class="r">Volume livré</th><th class="r">CA cumulé</th><th>Propriétaire</th>
              </tr>
            </thead>
            <tbody>
              @for (c of d.content; track c.id) {
                <tr class="row" tabindex="0" (click)="ui.openRecord('company', c.id)" (keydown.enter)="ui.openRecord('company', c.id)">
                  <td><b>{{ c.name }}</b><div class="faint cell-sub">{{ c.website }}</div></td>
                  <td><span class="pill nodot">{{ ref.companyTypeLabel(c.type) }}</span></td>
                  <td class="muted">{{ place(c) }}</td>
                  <td class="r num">{{ c.fleetSize ? c.fleetSize + ' navires' : '—' }}</td>
                  <td class="r num">{{ c.contactsCount }}</td>
                  <td class="r num">{{ c.openDealsCount }}</td>
                  <td class="r num">{{ c.deliveredVolumeMt ? fmtMT(c.deliveredVolumeMt) : '—' }}</td>
                  <td class="r num">{{ c.revenueUsd ? fmtK(c.revenueUsd) : '—' }}</td>
                  <td><rsb-owner [id]="c.ownerId" /></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <rsb-pager [page]="d.page" [size]="d.size" [total]="d.totalElements" (pageChange)="page.set($event)" />
      } @else {
        <div class="panel empty">{{ q() ? 'Aucune entreprise ne correspond à ce filtre.' : 'Aucune entreprise pour le moment.' }}</div>
      }
    } @else {
      <div class="panel empty">Chargement…</div>
    }
  `,
})
export class CompaniesPage {
  private readonly api = inject(CrmApi);
  protected readonly ref = inject(ReferenceService);
  protected readonly ui = inject(UiService);
  protected readonly fmtK = fmtK;
  protected readonly fmtMT = fmtMT;

  protected readonly qInput = signal('');
  protected readonly q = signal('');
  protected readonly sort = signal('revenue');
  protected readonly page = signal(0);
  protected readonly data = signal<Page<CompanyRow> | null>(null);
  private searchTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => {
      const query = { q: this.q(), sort: this.sort(), page: this.page(), size: PAGE_SIZE };
      this.ui.version();
      untracked(() => this.api.companies(query).subscribe((d) => this.data.set(d)));
    });
  }

  protected place(c: CompanyRow): string {
    return [c.city, c.country].filter(Boolean).join(', ') || '—';
  }

  protected onSearch(value: string): void {
    this.qInput.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.q.set(value.trim());
      this.page.set(0);
    }, 250);
  }

  protected setSort(value: string): void {
    this.sort.set(value);
    this.page.set(0);
  }
}
