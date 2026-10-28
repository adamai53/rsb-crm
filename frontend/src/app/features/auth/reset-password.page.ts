import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { errorMessage, fieldErrors } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { PASSWORD_RULE, controlError, sameAs, strongPassword } from '../../shared/form-utils';
import { AuthCard } from './auth-card';

@Component({
  selector: 'rsb-reset-password',
  imports: [ReactiveFormsModule, RouterLink, AuthCard],
  template: `
    <rsb-auth-card heading="Nouveau mot de passe">
      @if (!token()) {
        <p class="auth-error" role="alert">Ce lien est incomplet. Refaites une demande de réinitialisation.</p>
        <p class="auth-alt"><a routerLink="/forgot-password">Mot de passe oublié</a></p>
      } @else if (done(); as message) {
        <div class="auth-success" role="status"><b>C'est fait</b><p>{{ message }}</p></div>
        <p class="auth-alt"><a class="btn primary" routerLink="/login">Se connecter</a></p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="auth-form">
          <label class="fld">
            <span>Nouveau mot de passe</span>
            <input type="password" formControlName="password" autocomplete="new-password" required
                   aria-describedby="pwd-rule" [attr.aria-invalid]="!!pwdError()">
            @if (pwdError(); as e) { <em class="fld-err">{{ e }}</em> }
            @else { <small id="pwd-rule" class="fld-hint">{{ rule }}</small> }
          </label>
          <label class="fld">
            <span>Confirmation</span>
            <input type="password" formControlName="confirm" autocomplete="new-password" required
                   [attr.aria-invalid]="!!confirmError()">
            @if (confirmError(); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          @if (error()) {
            <p class="auth-error" role="alert">{{ error() }}</p>
          }
          <button class="btn primary auth-submit" type="submit" [disabled]="busy()">
            {{ busy() ? 'Enregistrement…' : 'Enregistrer' }}
          </button>
        </form>
        <p class="auth-alt"><a routerLink="/login">Retour à la connexion</a></p>
      }
    </rsb-auth-card>
  `,
})
export class ResetPasswordPage {
  private readonly auth = inject(AuthService);

  readonly token = input<string>();

  protected readonly rule = PASSWORD_RULE;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly serverPwd = signal<string | undefined>(undefined);
  protected readonly done = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group(
    { password: ['', strongPassword], confirm: ['', Validators.required] },
    { validators: sameAs('password', 'confirm') },
  );

  protected pwdError() {
    return controlError(this.form.controls.password, this.serverPwd(), 'Le mot de passe');
  }

  protected confirmError() {
    const c = this.form.controls.confirm;
    if (c.touched && this.form.hasError('mismatch')) return 'Les deux mots de passe ne correspondent pas.';
    return controlError(c, undefined, 'La confirmation');
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.auth.resetPassword(this.token()!, this.form.getRawValue().password).subscribe({
      next: (res) => this.done.set(res.message),
      error: (e) => {
        this.busy.set(false);
        this.serverPwd.set(fieldErrors(e)['password']);
        this.error.set(errorMessage(e));
      },
    });
  }
}
