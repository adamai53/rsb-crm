import { Component, effect, inject, signal, untracked } from '@angular/core';

import { errorMessage } from '../../core/api-error';
import { CrmApi } from '../../core/api/crm-api';
import { ContactList, Lifecycle } from '../../core/api/models';
import { rel, saveBlob } from '../../core/format';
import { LIFECYCLES, LIFECYCLE_LABELS, ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { LifecyclePill, OwnerAvatar, ScoreBar } from '../../shared/ui/badges';
import { Pager } from '../../shared/ui/pager';
import { ImportModal } from './import-modal';

type SortKey = 'name' | 'company' | 'lifecycle' | 'score' | 'lastActivity';
const PAGE_SIZE = 50;

/** Liste des contacts : filtres par cycle de vie, texte et propriétaire, tri, import et export CSV. */
@Component({
  selector: 'rsb-contacts',
  imports: [LifecyclePill, ScoreBar, OwnerAvatar, Pager, ImportModal],
  template: `
    <div class="page-head">
      <div>
        <h1>Contacts</h1>
        <p>Acheteurs, opérateurs et agents maritimes. Le score priorise les contacts à appeler en premier.</p>
      </div>
      <div class="actions">
        <button class="btn" type="button" (click)="importing.set(true)">Importer un CSV</button>
        <button class="btn" type="button" (click)="exportCsv()">Exporter</button>
        <button class="btn primary" type="button" (click)="ui.openForm({ kind: 'contact' })">Nouveau contact</button>
      </div>
    </div>

    <div class="toolbar">
      <div class="chips" role="group" aria-label="Filtrer par cycle de vie">
        <button class="chip" type="button" [attr.aria-pressed]="lifecycle() === null" (click)="setLifecycle(null)">
          Tous<span class="mono">{{ data()?.allCount ?? 0 }}</span>
        </button>
        @for (l of lifecycles; track l) {
          <button class="chip" type="button" [attr.aria-pressed]="lifecycle() === l" (click)="setLifecycle(l)">
            {{ labels[l] }}<span class="mono">{{ data()?.lifecycleCounts?.[l] ?? 0 }}</span>
          </button>
        }
      </div>
      <input type="search" placeholder="Filtrer par nom, e-mail, entreprise" aria-label="Filtrer les contacts"
             [value]="qInput()" (input)="onSearch($any($event.target).value)">
      <select aria-label="Propriétaire" [value]="ownerId() ?? 'all'" (change)="setOwner($any($event.target).value)">
        <option value="all">Tous les commerciaux</option>
        @for (u of users(); track u.id) { <option [value]="u.id">{{ u.name }}</option> }
      </select>
    </div>

    @if (data(); as d) {
      @if (d.content.length) {
        <div class="tbl">
          <table>
            <thead>
              <tr>
                <th class="sortable" [attr.aria-sort]="ariaSort('name')" (click)="sortBy('name')">Nom{{ arrow('name') }}</th>
                <th class="sortable" [attr.aria-sort]="ariaSort('company')" (click)="sortBy('company')">Entreprise{{ arrow('company') }}</th>
                <th>Poste</th>
                <th class="sortable" [attr.aria-sort]="ariaSort('lifecycle')" (click)="sortBy('lifecycle')">Cycle de vie{{ arrow('lifecycle') }}</th>
                <th class="sortable" [attr.aria-sort]="ariaSort('score')" (click)="sortBy('score')">Score{{ arrow('score') }}</th>
                <th>Source</th>
                <th>Propriétaire</th>
                <th class="sortable" [attr.aria-sort]="ariaSort('lastActivity')" (click)="sortBy('lastActivity')">Dernière activité{{ arrow('lastActivity') }}</th>
              </tr>
            </thead>
            <tbody>
              @for (c of d.content; track c.id) {
                <tr class="row" tabindex="0" (click)="ui.openRecord('contact', c.id)" (keydown.enter)="ui.openRecord('contact', c.id)">
                  <td><b>{{ c.firstName }} {{ c.lastName }}</b><div class="faint cell-sub">{{ c.email }}</div></td>
                  <td>{{ c.companyName || '—' }}</td>
                  <td class="muted">{{ c.title || '—' }}</td>
                  <td><rsb-lifecycle [value]="c.lifecycle" /></td>
                  <td><rsb-score [value]="c.score" /></td>
                  <td class="muted">{{ c.source || '—' }}</td>
                  <td><rsb-owner [id]="c.ownerId" /></td>
                  <td class="muted">{{ rel(c.lastActivityAt) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <rsb-pager [page]="d.page" [size]="d.size" [total]="d.totalElements" (pageChange)="page.set($event)" />
      } @else {
        <div class="panel empty">
          @if (d.allCount === 0 && !q() && !ownerId()) {
            Aucun contact pour le moment. Créez-en un ou importez un fichier CSV.
          } @else {
            Aucun contact ne correspond à ces filtres.
          }
        </div>
      }
    } @else {
      <div class="panel empty">Chargement…</div>
    }

    @if (importing()) { <rsb-import-modal (closed)="importing.set(false)" /> }
  `,
})
export class ContactsPage {
  private readonly api = inject(CrmApi);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly lifecycles = LIFECYCLES;
  protected readonly labels = LIFECYCLE_LABELS;
  protected readonly users = this.ref.users;
  protected readonly rel = rel;

  protected readonly lifecycle = signal<Lifecycle | null>(null);
  protected readonly qInput = signal('');
  protected readonly q = signal('');
  protected readonly ownerId = signal<string | null>(null);
  protected readonly sort = signal<{ key: SortKey; dir: 'asc' | 'desc' }>({ key: 'score', dir: 'desc' });
  protected readonly page = signal(0);
  protected readonly data = signal<ContactList | null>(null);
  protected readonly importing = signal(false);
  private searchTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => {
      const query = {
        q: this.q(), ownerId: this.ownerId(), lifecycle: this.lifecycle(), sort: this.sort().key,
        dir: this.sort().dir, page: this.page(), size: PAGE_SIZE,
      };
      this.ui.version();
      untracked(() => this.api.contacts(query).subscribe((d) => this.data.set(d)));
    });
  }

  protected onSearch(value: string): void {
    this.qInput.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.q.set(value.trim());
      this.page.set(0);
    }, 250);
  }

  protected setLifecycle(l: Lifecycle | null): void {
    this.lifecycle.set(l);
    this.page.set(0);
  }

  protected setOwner(value: string): void {
    this.ownerId.set(value === 'all' ? null : value);
    this.page.set(0);
  }

  protected sortBy(key: SortKey): void {
    const s = this.sort();
    // Premier clic : ordre naturel (A→Z pour les textes, plus fort d'abord pour score et activité)
    const natural: 'asc' | 'desc' = key === 'score' || key === 'lastActivity' ? 'desc' : 'asc';
    this.sort.set({ key, dir: s.key === key ? (s.dir === 'asc' ? 'desc' : 'asc') : natural });
    this.page.set(0);
  }

  protected arrow(key: SortKey): string {
    const s = this.sort();
    return s.key === key ? (s.dir === 'asc' ? ' ↑' : ' ↓') : '';
  }

  protected ariaSort(key: SortKey): string | null {
    const s = this.sort();
    return s.key === key ? (s.dir === 'asc' ? 'ascending' : 'descending') : null;
  }

  protected exportCsv(): void {
    this.api.exportContacts({ q: this.q(), ownerId: this.ownerId(), lifecycle: this.lifecycle() }).subscribe({
      next: (blob) => saveBlob(blob, 'contacts-rsb.csv'),
      error: (e) => this.toast.show(errorMessage(e, "L'export a échoué.")),
    });
  }
}
