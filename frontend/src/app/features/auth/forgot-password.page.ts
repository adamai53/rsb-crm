import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { controlError } from '../../shared/form-utils';
import { AuthCard } from './auth-card';

@Component({
  selector: 'rsb-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, AuthCard],
  template: `
    <rsb-auth-card heading="Mot de passe oublié"
                   [subheading]="done() ? '' : 'Indiquez votre e-mail : nous vous enverrons un lien de réinitialisation.'">
      @if (done(); as message) {
        <div class="auth-success" role="status">
          <b>Demande envoyée</b>
          <p>{{ message }}</p>
          <p class="muted">Le lien est valable 1 heure.</p>
        </div>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="auth-form">
          <label class="fld">
            <span>E-mail professionnel</span>
            <input type="email" formControlName="email" autocomplete="email" required
                   [attr.aria-invalid]="!!emailError()">
            @if (emailError(); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          @if (error()) {
            <p class="auth-error" role="alert">{{ error() }}</p>
          }
          <button class="btn primary auth-submit" type="submit" [disabled]="busy()">
            {{ busy() ? 'Envoi…' : 'Envoyer le lien' }}
          </button>
        </form>
      }
      <p class="auth-alt"><a routerLink="/login">Retour à la connexion</a></p>
    </rsb-auth-card>
  `,
})
export class ForgotPasswordPage {
  private readonly auth = inject(AuthService);

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly done = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected emailError() {
    return controlError(this.form.controls.email, undefined, "L'e-mail");
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) return;
    this.busy.set(true);
    this.auth.forgotPassword(this.form.getRawValue().email).subscribe({
      next: (res) => this.done.set(res.message),
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e));
      },
    });
  }
}
