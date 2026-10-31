import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { errorMessage } from '../../core/api-error';
import { DocumentsApi } from '../../core/api/documents';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { Modal } from '../../shared/ui/modal';

/** Modification d'un modèle d'e-mail (ADMIN). */
@Component({
  selector: 'rsb-template-modal',
  imports: [ReactiveFormsModule, Modal],
  template: `
    <rsb-modal heading="Modifier le modèle" sub="Champs : {{ fields }}" [wide]="true" (closed)="ui.closeForm()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="fgrid">
          <label class="fld wide"><span>Nom</span><input formControlName="name" required maxlength="120"></label>
          <label class="fld wide"><span>Objet</span><input formControlName="subject" required maxlength="255"></label>
          <label class="fld wide"><span>Message</span><textarea formControlName="body" rows="10" required></textarea></label>
        </div>
        @if (error()) { <p class="auth-error form-error" role="alert">{{ error() }}</p> }
        <footer class="m-foot">
          <button type="button" class="btn ghost" (click)="ui.closeForm()">Annuler</button>
          <button class="btn primary" type="submit" [disabled]="saving() || form.invalid">Enregistrer</button>
        </footer>
      </form>
    </rsb-modal>
  `,
})
export class TemplateModal implements OnInit {
  readonly id = input.required<string>();

  private readonly api = inject(DocumentsApi);
  private readonly toast = inject(ToastService);
  protected readonly ui = inject(UiService);

  protected readonly fields = '{{prenom}}, {{entreprise}}, {{navire}}, {{produit}}, {{quantite}}, {{eta}}, {{commercial}}';
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required],
    subject: ['', Validators.required],
    body: ['', Validators.required],
  });

  ngOnInit(): void {
    this.api.templates().subscribe((list) => {
      const t = list.find((x) => x.id === this.id());
      if (t) this.form.patchValue({ name: t.name, subject: t.subject, body: t.body });
    });
  }

  submit(): void {
    if (this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.api.updateTemplate(this.id(), this.form.getRawValue()).subscribe({
      next: () => {
        this.ui.closeForm();
        this.ui.changed();
        this.toast.show('Modèle enregistré');
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
