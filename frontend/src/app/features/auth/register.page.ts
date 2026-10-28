import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { errorMessage, fieldErrors } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { PASSWORD_RULE, controlError, sameAs, strongPassword } from '../../shared/form-utils';
import { AuthCard } from './auth-card';

type Field = 'firstName' | 'lastName' | 'email' | 'password' | 'confirm';

@Component({
  selector: 'rsb-register',
  imports: [ReactiveFormsModule, RouterLink, AuthCard],
  template: `
    <rsb-auth-card heading="Créer un compte"
                   [subheading]="done() ? '' : 'Réservé aux équipes Red Sea Bunkering. Un administrateur valide chaque compte.'">
      @if (done(); as message) {
        <div class="auth-success" role="status">
          <b>Vérifiez votre boîte de réception</b>
          <p>{{ message }}</p>
          <p class="muted">Le lien est valable 24 heures. Après confirmation, un administrateur activera votre compte.</p>
        </div>
        <p class="auth-alt"><a routerLink="/login">Retour à la connexion</a></p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="auth-form">
          <div class="auth-row">
            <label class="fld">
              <span>Prénom</span>
              <input formControlName="firstName" autocomplete="given-name" required maxlength="80"
                     [attr.aria-invalid]="!!err('firstName')">
              @if (err('firstName'); as e) { <em class="fld-err">{{ e }}</em> }
            </label>
            <label class="fld">
              <span>Nom</span>
              <input formControlName="lastName" autocomplete="family-name" required maxlength="80"
                     [attr.aria-invalid]="!!err('lastName')">
              @if (err('lastName'); as e) { <em class="fld-err">{{ e }}</em> }
            </label>
          </div>
          <label class="fld">
            <span>E-mail professionnel</span>
            <input type="email" formControlName="email" autocomplete="email" required
                   [attr.aria-invalid]="!!err('email')">
            @if (err('email'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Mot de passe</span>
            <input type="password" formControlName="password" autocomplete="new-password" required
                   aria-describedby="pwd-rule" [attr.aria-invalid]="!!err('password')">
            @if (err('password'); as e) { <em class="fld-err">{{ e }}</em> }
            @else { <small id="pwd-rule" class="fld-hint">{{ rule }}</small> }
          </label>
          <label class="fld">
            <span>Confirmation du mot de passe</span>
            <input type="password" formControlName="confirm" autocomplete="new-password" required
                   [attr.aria-invalid]="!!err('confirm')">
            @if (err('confirm'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          @if (error()) {
            <p class="auth-error" role="alert">{{ error() }}</p>
          }
          <button class="btn primary auth-submit" type="submit" [disabled]="busy()">
            {{ busy() ? 'Création…' : 'Créer mon compte' }}
          </button>
        </form>
        <p class="auth-alt">Déjà un compte ? <a routerLink="/login">Se connecter</a></p>
      }
    </rsb-auth-card>
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);

  protected readonly rule = PASSWORD_RULE;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly server = signal<Record<string, string>>({});
  protected readonly done = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      firstName: ['', [Validators.required, Validators.maxLength(80)]],
      lastName: ['', [Validators.required, Validators.maxLength(80)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', strongPassword],
      confirm: ['', Validators.required],
    },
    { validators: sameAs('password', 'confirm') },
  );

  private static readonly LABELS: Record<Field, string> = {
    firstName: 'Le prénom',
    lastName: 'Le nom',
    email: "L'e-mail",
    password: 'Le mot de passe',
    confirm: 'La confirmation',
  };

  protected err(name: Field): string | null {
    const c = this.form.controls[name];
    if (name === 'confirm' && c.touched && this.form.hasError('mismatch')) {
      return 'Les deux mots de passe ne correspondent pas.';
    }
    return controlError(c, this.server()[name], RegisterPage.LABELS[name]);
  }

  submit(): void {
    this.form.markAllAsTouched();
    this.server.set({});
    if (this.form.invalid || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    const { confirm: _confirm, ...body } = this.form.getRawValue();
    this.auth.register(body).subscribe({
      next: (res) => this.done.set(res.message),
      error: (e) => {
        this.busy.set(false);
        this.server.set(fieldErrors(e));
        this.error.set(errorMessage(e, 'Inscription impossible.'));
      },
    });
  }
}
