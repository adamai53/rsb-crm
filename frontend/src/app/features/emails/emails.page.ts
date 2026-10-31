import { Component, computed, effect, inject, signal, untracked } from '@angular/core';

import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { DocumentsApi, EmailTemplate, SentEmail, Sequence } from '../../core/api/documents';
import { rel } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';

const STEP_LABELS = { EMAIL: 'E-mail', CALL: 'Appel', TASK: 'Tâche' };
const SENT_STATUS = {
  ENVOYE: { label: 'Envoyé', cls: '' },
  OUVERT: { label: 'Ouvert', cls: 'p-good' },
  ERREUR: { label: 'Erreur', cls: 'p-bad' },
};

/** Séquence de relance, modèles avec champs de fusion et suivi des derniers envois. */
@Component({
  selector: 'rsb-emails',
  template: `
    <div class="page-head">
      <div>
        <h1>E-mails & séquences</h1>
        <p>Modèles avec champs de fusion, séquence de relance et suivi des envois.</p>
      </div>
      <div class="actions">
        <button class="btn primary" type="button" (click)="ui.openForm({ kind: 'compose' })">Rédiger un e-mail</button>
      </div>
    </div>

    @if (sequence(); as s) {
      <div class="panel calls-strip">
        <h2>Séquence « {{ s.name }} »</h2>
        <p class="sub">Inscrire un contact crée automatiquement les relances dans ses tâches. {{ s.enrolled.length }} contact(s) inscrit(s).</p>
        <div class="seq">
          @for (st of s.steps; track st.id) {
            <div class="step">
              <small>{{ st.dayOffset === 0 ? 'J0' : 'J+' + st.dayOffset }}</small>
              <div><b>{{ stepLabels[st.type] }}</b> · {{ st.label }}</div>
            </div>
          }
        </div>
        @if (s.enrolled.length) {
          <div class="chips enrolled">
            @for (e of s.enrolled; track e.id) {
              <span class="chip">
                <button class="lnk" type="button" (click)="ui.openRecord('contact', e.contactId)">{{ e.contactName }}</button>
                @if (canStop(e.ownerId)) {
                  <button class="chip-x" type="button" [attr.aria-label]="'Arrêter la séquence pour ' + e.contactName" (click)="stop(e.id)">×</button>
                }
              </span>
            }
          </div>
        }
        <div class="actions seq-actions">
          <button class="btn" type="button" (click)="ui.openForm({ kind: 'enroll' })">Inscrire un contact</button>
          @if (canManage()) {
            <label class="check">
              <input type="checkbox" [checked]="s.autoSend" (change)="setAutoSend(s, $any($event.target).checked)">
              Envoyer automatiquement les étapes e-mail
            </label>
          }
        </div>
      </div>
    }

    <div class="grid g-2">
      <div class="panel">
        <h2>Modèles</h2>
        <p class="sub">Champs : {{ fields }}</p>
        <div class="list">
          @for (t of templates(); track t.id) {
            <div class="li">
              <div class="grow"><b>{{ t.name }}</b><span>{{ t.subject }}</span></div>
              @if (isAdmin()) {
                <button class="btn sm" type="button" (click)="ui.openForm({ kind: 'template', id: t.id })">Modifier</button>
              }
              <button class="btn sm" type="button" (click)="ui.openForm({ kind: 'compose', templateId: t.id })">Utiliser</button>
            </div>
          }
        </div>
      </div>
      <div class="panel">
        <h2>Derniers envois</h2>
        <p class="sub">« Ouvert » s'affiche quand le destinataire charge les images de l'e-mail.</p>
        <div class="list">
          @for (e of sent(); track e.id) {
            <div class="li">
              <div class="grow"><b>{{ e.subject }}</b><span>À {{ e.contactName || e.toAddress }} · {{ rel(e.sentAt) }}</span></div>
              <span [class]="'pill ' + sentStatus[e.status].cls">{{ sentStatus[e.status].label }}</span>
            </div>
          } @empty { <p class="empty">Aucun envoi pour le moment.</p> }
        </div>
      </div>
    </div>
  `,
})
export class EmailsPage {
  private readonly api = inject(DocumentsApi);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly stepLabels = STEP_LABELS;
  protected readonly sentStatus = SENT_STATUS;
  protected readonly rel = rel;
  protected readonly fields = '{{prenom}}, {{entreprise}}, {{navire}}, {{produit}}, {{quantite}}, {{eta}}, {{commercial}}';

  private readonly sequences = signal<Sequence[]>([]);
  protected readonly sequence = computed(() => this.sequences()[0] ?? null);
  protected readonly templates = signal<EmailTemplate[]>([]);
  protected readonly sent = signal<SentEmail[]>([]);
  protected readonly isAdmin = computed(() => this.auth.hasRole('ADMIN'));
  protected readonly canManage = computed(() => this.auth.hasRole('ADMIN', 'MANAGER'));

  constructor() {
    effect(() => {
      this.ui.version();
      untracked(() => {
        this.api.sequences().subscribe((l) => this.sequences.set(l));
        this.api.templates().subscribe((l) => this.templates.set(l));
        this.api.sentEmails().subscribe((l) => this.sent.set(l));
      });
    });
  }

  protected canStop(ownerId: string | null): boolean {
    return this.canManage() || ownerId === this.auth.user()?.id;
  }

  protected stop(id: string): void {
    this.api.stopEnrollment(id).subscribe({
      next: () => {
        this.toast.show('Séquence arrêtée pour ce contact');
        this.ui.changed();
      },
      error: (e) => this.toast.show(errorMessage(e)),
    });
  }

  protected setAutoSend(s: Sequence, autoSend: boolean): void {
    this.api.setAutoSend(s.id, autoSend).subscribe({
      next: () => {
        this.toast.show(autoSend ? 'Envoi automatique activé' : 'Envoi automatique désactivé');
        this.ui.changed();
      },
      error: (e) => {
        this.toast.show(errorMessage(e));
        this.ui.changed();
      },
    });
  }
}
