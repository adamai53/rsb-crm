import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { errorMessage, fieldErrors } from '../../core/api-error';
import { CrmApi } from '../../core/api/crm-api';
import { ContactRow } from '../../core/api/models';
import { DocumentsApi, EmailTemplate } from '../../core/api/documents';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { controlError } from '../../shared/form-utils';
import { Modal } from '../../shared/ui/modal';

/** Rédiger un e-mail : le modèle est fusionné avec le contact et sa transaction, puis modifiable avant l'envoi réel. */
@Component({
  selector: 'rsb-compose-modal',
  imports: [ReactiveFormsModule, Modal],
  template: `
    <rsb-modal heading="Rédiger un e-mail" sub="L'e-mail est réellement envoyé et enregistré dans l'historique du contact." [wide]="true" (closed)="ui.closeForm()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="fgrid">
          <label class="fld">
            <span>Destinataire</span>
            <select formControlName="contactId" required (change)="refresh()">
              <option [ngValue]="null" disabled>Choisir un contact…</option>
              @for (c of contacts(); track c.id) {
                <option [ngValue]="c.id" [disabled]="!c.email">{{ c.firstName }} {{ c.lastName }}{{ c.companyName ? ' · ' + c.companyName : '' }}{{ c.email ? '' : ' (sans e-mail)' }}</option>
              }
            </select>
            @if (err('contactId'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Modèle</span>
            <select formControlName="templateId" (change)="refresh()">
              <option [ngValue]="null">Sans modèle</option>
              @for (t of templates(); track t.id) { <option [ngValue]="t.id">{{ t.name }}</option> }
            </select>
          </label>
          <label class="fld wide">
            <span>Objet</span>
            <input formControlName="subject" required maxlength="255" [attr.aria-invalid]="!!err('subject')">
            @if (err('subject'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld wide">
            <span>Message</span>
            <textarea formControlName="body" rows="9" required [attr.aria-invalid]="!!err('body')"></textarea>
            @if (err('body'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
        </div>
        @if (error()) { <p class="auth-error form-error" role="alert">{{ error() }}</p> }
        <footer class="m-foot">
          <button type="button" class="btn ghost" (click)="ui.closeForm()">Annuler</button>
          <button class="btn primary" type="submit" [disabled]="saving()">{{ saving() ? 'Envoi…' : 'Envoyer' }}</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class ComposeModal implements OnInit {
  readonly contactId = input<string>();
  readonly dealId = input<string>();
  readonly templateId = input<string>();

  private readonly crm = inject(CrmApi);
  private readonly api = inject(DocumentsApi);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly contacts = signal<ContactRow[]>([]);
  protected readonly templates = signal<EmailTemplate[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly server = signal<Record<string, string>>({});
  private effectiveDeal: string | null = null;

  protected readonly form = inject(FormBuilder).group({
    contactId: [null as string | null, Validators.required],
    templateId: [null as string | null],
    subject: ['', [Validators.required, Validators.maxLength(255)]],
    body: ['', Validators.required],
  });

  ngOnInit(): void {
    this.effectiveDeal = this.dealId() ?? null;
    this.crm.contacts({ sort: 'name', dir: 'asc', size: 200 }).subscribe((p) => this.contacts.set(p.content));
    this.form.patchValue({ contactId: this.contactId() ?? null, templateId: this.templateId() ?? null });
    this.api.templates().subscribe((list) => {
      this.templates.set(list);
      if (!this.form.value.templateId) {
        this.form.patchValue({ templateId: list.find((t) => t.name === 'Envoi de cotation')?.id ?? null });
      }
      this.refresh();
    });
  }

  protected err(name: 'contactId' | 'subject' | 'body'): string | null {
    const labels = { contactId: 'Le destinataire', subject: "L'objet", body: 'Le message' };
    return controlError(this.form.controls[name], this.server()[name], labels[name]);
  }

  /** Recalcule objet et message quand le destinataire ou le modèle change. */
  protected refresh(): void {
    const { templateId, contactId } = this.form.getRawValue();
    if (!templateId) return;
    this.api.draft(templateId, contactId, this.dealId() ?? null).subscribe({
      next: (d) => {
        this.effectiveDeal = d.dealId;
        this.form.patchValue({ subject: d.subject, body: d.body });
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  submit(): void {
    this.form.markAllAsTouched();
    this.server.set({});
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.api.sendEmail({ contactId: v.contactId!, dealId: this.effectiveDeal, subject: v.subject!, body: v.body! }).subscribe({
      next: () => {
        this.ui.closeForm();
        this.ui.changed();
        this.toast.show('E-mail envoyé');
      },
      error: (e) => {
        this.saving.set(false);
        this.server.set(fieldErrors(e));
        this.error.set(errorMessage(e));
      },
    });
  }
}
