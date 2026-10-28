import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { controlError } from '../../shared/form-utils';
import { AuthCard } from './auth-card';

@Component({
  selector: 'rsb-login',
  imports: [ReactiveFormsModule, RouterLink, AuthCard],
  template: `
    <rsb-auth-card heading="Connexion" subheading="Accédez au pipeline de soutage et à vos dossiers.">
      @if (expired()) {
        <p class="auth-note" role="status">Votre session a expiré. Reconnectez-vous pour continuer.</p>
      }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="auth-form">
        <label class="fld">
          <span>E-mail professionnel</span>
          <input type="email" formControlName="email" autocomplete="username" required
                 [attr.aria-invalid]="!!err('email')">
          @if (err('email'); as e) { <em class="fld-err">{{ e }}</em> }
        </label>
        <label class="fld">
          <span class="fld-row">Mot de passe <a routerLink="/forgot-password">Mot de passe oublié ?</a></span>
          <input type="password" formControlName="password" autocomplete="current-password" required
                 [attr.aria-invalid]="!!err('password')">
          @if (err('password'); as e) { <em class="fld-err">{{ e }}</em> }
        </label>
        @if (error()) {
          <p class="auth-error" role="alert">{{ error() }}</p>
        }
        <button class="btn primary auth-submit" type="submit" [disabled]="busy()">
          {{ busy() ? 'Connexion…' : 'Se connecter' }}
        </button>
      </form>
      <p class="auth-alt">Pas encore de compte ? <a routerLink="/register">Créer un compte</a></p>
    </rsb-auth-card>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly returnUrl = input<string>();
  readonly expired = input<string>();

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected err(name: 'email' | 'password') {
    return controlError(this.form.controls[name], undefined, name === 'email' ? "L'e-mail" : 'Le mot de passe');
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => {
        const target = this.returnUrl();
        // Uniquement des chemins internes
        this.router.navigateByUrl(target?.startsWith('/') && !target.startsWith('//') ? target : '/dashboard');
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e, 'Connexion impossible.'));
      },
    });
  }
}
