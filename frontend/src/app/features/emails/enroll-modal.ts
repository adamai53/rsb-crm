import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { CrmApi } from '../../core/api/crm-api';
import { ContactRow } from '../../core/api/models';
import { DocumentsApi, Sequence } from '../../core/api/documents';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { Modal } from '../../shared/ui/modal';

/** Inscription d'un contact à la séquence : une tâche par étape est créée pour son commercial. */
@Component({
  selector: 'rsb-enroll-modal',
  imports: [FormsModule, Modal],
  template: `
    <rsb-modal [heading]="'Inscrire à la séquence « ' + (sequence()?.name ?? '') + ' »'"
               sub="Quatre étapes sur six jours, créées dans les tâches du commercial." (closed)="ui.closeForm()">
      <form (ngSubmit)="submit()">
        <div class="fgrid">
          <label class="fld wide">
            <span>Contact</span>
            <select name="contact" [(ngModel)]="contact" required>
              <option [ngValue]="null" disabled>Choisir un contact…</option>
              @for (c of eligible(); track c.id) {
                <option [ngValue]="c.id">{{ c.firstName }} {{ c.lastName }}{{ c.companyName ? ' · ' + c.companyName : '' }}</option>
              }
            </select>
          </label>
        </div>
        @if (error()) { <p class="auth-error form-error" role="alert">{{ error() }}</p> }
        <footer class="m-foot">
          <button type="button" class="btn ghost" (click)="ui.closeForm()">Annuler</button>
          <button class="btn primary" type="submit" [disabled]="saving() || !contact">Inscrire</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class EnrollModal implements OnInit {
  readonly contactId = input<string>();

  private readonly crm = inject(CrmApi);
  private readonly api = inject(DocumentsApi);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly sequence = signal<Sequence | null>(null);
  private readonly contacts = signal<ContactRow[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected contact: string | null = null;

  /** Contacts modifiables par l'utilisateur et pas encore inscrits. */
  protected readonly eligible = computed(() => {
    const enrolled = new Set(this.sequence()?.enrolled.map((e) => e.contactId));
    const all = this.auth.hasRole('ADMIN', 'MANAGER');
    const me = this.auth.user()?.id;
    return this.contacts().filter((c) => !enrolled.has(c.id) && (all || c.ownerId === me));
  });

  ngOnInit(): void {
    this.contact = this.contactId() ?? null;
    this.api.sequences().subscribe((list) => this.sequence.set(list[0] ?? null));
    this.crm.contacts({ sort: 'name', dir: 'asc', size: 200 }).subscribe((p) => this.contacts.set(p.content));
  }

  submit(): void {
    const seq = this.sequence();
    if (!seq || !this.contact || this.saving()) return;
    this.saving.set(true);
    this.api.enroll(seq.id, this.contact).subscribe({
      next: () => {
        this.ui.closeForm();
        this.ui.changed();
        this.toast.show('Contact inscrit : relances ajoutées aux tâches');
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
