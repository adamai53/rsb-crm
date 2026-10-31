import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { errorMessage } from '../../core/api-error';
import { DocumentsApi, Quote } from '../../core/api/documents';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { Modal } from '../../shared/ui/modal';

/** Envoi réel du devis : l'e-mail pré-rempli depuis le modèle part avec le PDF en pièce jointe. */
@Component({
  selector: 'rsb-quote-send-modal',
  imports: [ReactiveFormsModule, Modal],
  template: `
    <rsb-modal heading="Envoyer le devis" [sub]="subtitle()" [wide]="true" (closed)="back()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="fgrid">
          <label class="fld wide"><span>Objet</span><input formControlName="subject" required maxlength="255"></label>
          <label class="fld wide"><span>Message</span><textarea formControlName="body" rows="9" required></textarea></label>
        </div>
        @if (quote(); as q) { <p class="muted attach-note">Pièce jointe : Devis-{{ q.number }}.pdf</p> }
        @if (error()) { <p class="auth-error form-error" role="alert">{{ error() }}</p> }
        <footer class="m-foot">
          <button type="button" class="btn ghost" (click)="back()">Retour</button>
          <button class="btn primary" type="submit" [disabled]="saving() || !canSend()">{{ saving() ? 'Envoi…' : 'Envoyer' }}</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class QuoteSendModal implements OnInit {
  readonly id = input.required<string>();

  private readonly api = inject(DocumentsApi);
  private readonly toast = inject(ToastService);
  private readonly ui = inject(UiService);

  protected readonly quote = signal<Quote | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    subject: ['', [Validators.required, Validators.maxLength(255)]],
    body: ['', Validators.required],
  });

  ngOnInit(): void {
    forkJoin({ quote: this.api.quote(this.id()), draft: this.api.quoteEmailDraft(this.id()) }).subscribe({
      next: ({ quote, draft }) => {
        this.quote.set(quote);
        this.form.patchValue({ subject: draft.subject, body: draft.body });
        if (!quote.contactEmail) this.error.set("Le contact de cette transaction n'a pas d'adresse e-mail.");
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected subtitle(): string {
    const q = this.quote();
    return q?.contactEmail ? `À ${q.contactName} <${q.contactEmail}>` : 'Destinataire : contact de la transaction';
  }

  protected canSend(): boolean {
    return !!this.quote()?.contactEmail;
  }

  protected back(): void {
    this.ui.openForm({ kind: 'quote', id: this.id() });
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    this.api.sendQuote(this.id(), this.form.getRawValue()).subscribe({
      next: () => {
        this.toast.show(`Devis envoyé à ${this.quote()?.contactEmail}`);
        this.ui.changed();
        this.back();
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
