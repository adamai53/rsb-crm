import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { errorMessage } from '../../core/api-error';
import { CrmApi } from '../../core/api/crm-api';
import { Activity, ActivityType } from '../../core/api/models';
import { rel } from '../../core/format';
import { ReferenceService } from '../../core/reference.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';

const LABELS: Record<ActivityType, string> = {
  NOTE: 'Note', EMAIL: 'E-mail', CALL: 'Appel', MEETING: 'Réunion', SYSTEM: 'Mise à jour',
};
const ICONS: Record<ActivityType, string> = { NOTE: 'NOTE', EMAIL: 'MAIL', CALL: 'APP', MEETING: 'RDV', SYSTEM: 'SYS' };
const PLACEHOLDERS: Partial<Record<ActivityType, string>> = {
  NOTE: 'Ajouter une note…',
  CALL: "Résumé de l'appel…",
  EMAIL: "Résumé de l'e-mail envoyé ou reçu…",
  MEETING: 'Compte rendu de réunion…',
};

/** Composeur (Note / Appel / E-mail / Réunion) et fil d'activité d'une fiche. */
@Component({
  selector: 'rsb-timeline',
  imports: [FormsModule],
  template: `
    <div class="composer">
      <div class="ctypes" role="group" aria-label="Type d'activité">
        @for (t of types; track t) {
          <button type="button" [attr.aria-pressed]="type() === t" (click)="type.set(t)">{{ labels[t] }}</button>
        }
      </div>
      <textarea [(ngModel)]="text" [placeholder]="placeholders[type()]" aria-label="Contenu de l'activité"
                maxlength="5000" (keydown.control.enter)="log()"></textarea>
      <footer>
        <button class="btn primary sm" type="button" [disabled]="saving() || !text.trim()" (click)="log()">Enregistrer</button>
      </footer>
    </div>
    @if (items().length) {
      <div class="timeline">
        @for (a of items(); track a.id) {
          <div class="ev">
            <span [class]="'ic ' + a.type.toLowerCase()">{{ icons[a.type] }}</span>
            <div>
              <div class="meta">
                {{ labels[a.type] }} · {{ author(a) }} · {{ rel(a.occurredAt) }}
                @if (a.dealVessel && a.dealId && !dealId()) {
                  · <button class="lnk ev-link" type="button" (click)="ui.openRecord('deal', a.dealId)">{{ a.dealVessel }}</button>
                } @else if (a.contactName && a.contactId && !contactId()) {
                  · <button class="lnk ev-link" type="button" (click)="ui.openRecord('contact', a.contactId)">{{ a.contactName }}</button>
                }
              </div>
              <div class="txt">{{ a.text }}</div>
            </div>
          </div>
        }
      </div>
    } @else if (!loading()) {
      <p class="empty">Aucune activité pour le moment.</p>
    }
  `,
})
export class Timeline {
  /** Fiche concernée : les activités sont consignées sur le contact et son entreprise. */
  readonly contactId = input<string | null>(null);
  readonly companyId = input<string | null>(null);
  readonly dealId = input<string | null>(null);

  private readonly api = inject(CrmApi);
  private readonly ref = inject(ReferenceService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly types: ActivityType[] = ['NOTE', 'CALL', 'EMAIL', 'MEETING'];
  protected readonly labels = LABELS;
  protected readonly icons = ICONS;
  protected readonly placeholders = PLACEHOLDERS;
  protected readonly rel = rel;

  protected readonly type = signal<ActivityType>('NOTE');
  protected text = '';
  protected readonly items = signal<Activity[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);

  constructor() {
    effect(() => {
      const contactId = this.contactId();
      const companyId = this.companyId();
      const dealId = this.dealId();
      this.ui.version();
      untracked(() => this.load(contactId, companyId, dealId));
    });
  }

  protected author(a: Activity): string {
    return this.ref.user(a.authorId)?.name ?? 'Système';
  }

  protected log(): void {
    const text = this.text.trim();
    if (!text || this.saving()) return;
    this.saving.set(true);
    this.api
      .logActivity({ type: this.type(), text, contactId: this.contactId(), companyId: this.companyId(), dealId: this.dealId() }).subscribe({
      next: () => {
        this.text = '';
        this.saving.set(false);
        this.toast.show('Activité enregistrée');
        this.ui.changed();
      },
      error: (e) => {
        this.saving.set(false);
        this.toast.show(errorMessage(e));
      },
    });
  }

  /** Fil de la fiche la plus précise : transaction, sinon contact, sinon entreprise. */
  private load(contactId: string | null, companyId: string | null, dealId: string | null): void {
    const query = dealId ? { dealId } : contactId ? { contactId } : companyId ? { companyId } : null;
    if (!query) return;
    this.api.activities(query).subscribe({
      next: (list) => {
        this.items.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
