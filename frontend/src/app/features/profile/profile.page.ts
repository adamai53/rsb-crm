import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { errorMessage, fieldErrors } from '../../core/api-error';
import { ROLE_LABELS } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/toast.service';
import { PASSWORD_RULE, controlError, sameAs, strongPassword } from '../../shared/form-utils';

@Component({
  selector: 'rsb-profile',
  imports: [ReactiveFormsModule],
  template: `
    <div class="page-head">
      <div>
        <h1>Mon profil</h1>
        <p>{{ user()?.email }} · {{ roleLabel() }}</p>
      </div>
    </div>

    <div class="grid g-2">
      <form class="panel" [formGroup]="profile" (ngSubmit)="saveProfile()" novalidate>
        <h2>Informations</h2>
        <p class="sub">Votre nom apparaît sur les dossiers, devis et e-mails envoyés.</p>
        <div class="form-stack">
          <label class="fld">
            <span>Prénom</span>
            <input formControlName="firstName" autocomplete="given-name" maxlength="80" required>
            @if (pErr('firstName'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Nom</span>
            <input formControlName="lastName" autocomplete="family-name" maxlength="80" required>
            @if (pErr('lastName'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Langue de l'interface</span>
            <select formControlName="language">
              <option value="fr">Français</option>
              <option value="en">English (bientôt)</option>
            </select>
          </label>
        </div>
        <div class="actions form-actions">
          <button class="btn primary" type="submit" [disabled]="savingProfile()">Enregistrer</button>
        </div>
      </form>

      <form class="panel" [formGroup]="password" (ngSubmit)="savePassword()" novalidate>
        <h2>Mot de passe</h2>
        <p class="sub">Vos autres sessions seront fermées ; celle-ci reste ouverte.</p>
        <div class="form-stack">
          <label class="fld">
            <span>Mot de passe actuel</span>
            <input type="password" formControlName="currentPassword" autocomplete="current-password" required>
            @if (wErr('currentPassword'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
          <label class="fld">
            <span>Nouveau mot de passe</span>
            <input type="password" formControlName="newPassword" autocomplete="new-password" required
                   aria-describedby="pwd-rule">
            @if (wErr('newPassword'); as e) { <em class="fld-err">{{ e }}</em> }
            @else { <small id="pwd-rule" class="fld-hint">{{ rule }}</small> }
          </label>
          <label class="fld">
            <span>Confirmation</span>
            <input type="password" formControlName="confirm" autocomplete="new-password" required>
            @if (wErr('confirm'); as e) { <em class="fld-err">{{ e }}</em> }
          </label>
        </div>
        <div class="actions form-actions">
          <button class="btn primary" type="submit" [disabled]="savingPassword()">Changer le mot de passe</button>
        </div>
      </form>
    </div>
  `,
})
export class ProfilePage {
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly user = this.auth.user;
  protected readonly rule = PASSWORD_RULE;
  protected readonly savingProfile = signal(false);
  protected readonly savingPassword = signal(false);
  private readonly serverErrors = signal<Record<string, string>>({});

  protected readonly profile = this.fb.group({
    firstName: [this.user()?.firstName ?? '', [Validators.required, Validators.maxLength(80)]],
    lastName: [this.user()?.lastName ?? '', [Validators.required, Validators.maxLength(80)]],
    language: [this.user()?.language ?? 'fr'],
  });

  protected readonly password = this.fb.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', strongPassword],
      confirm: ['', Validators.required],
    },
    { validators: sameAs('newPassword', 'confirm') },
  );

  protected roleLabel(): string {
    const u = this.user();
    return u ? ROLE_LABELS[u.role] : '';
  }

  protected pErr(name: 'firstName' | 'lastName') {
    return controlError(this.profile.controls[name], undefined, name === 'firstName' ? 'Le prénom' : 'Le nom');
  }

  protected wErr(name: 'currentPassword' | 'newPassword' | 'confirm') {
    const c = this.password.controls[name];
    if (name === 'confirm' && c.touched && this.password.hasError('mismatch')) {
      return 'Les deux mots de passe ne correspondent pas.';
    }
    return controlError(c, this.serverErrors()[name], 'Ce champ');
  }

  saveProfile(): void {
    this.profile.markAllAsTouched();
    if (this.profile.invalid) return;
    this.savingProfile.set(true);
    this.auth.updateProfile(this.profile.getRawValue()).subscribe({
      next: () => {
        this.savingProfile.set(false);
        this.toast.show('Profil enregistré.');
      },
      error: (e) => {
        this.savingProfile.set(false);
        this.toast.show(errorMessage(e));
      },
    });
  }

  savePassword(): void {
    this.password.markAllAsTouched();
    this.serverErrors.set({});
    if (this.password.invalid) return;
    this.savingPassword.set(true);
    const { currentPassword, newPassword } = this.password.getRawValue();
    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.password.reset();
        this.toast.show('Mot de passe modifié.');
      },
      error: (e) => {
        this.savingPassword.set(false);
        const errors = fieldErrors(e);
        const msg = errorMessage(e);
        // « Mot de passe actuel incorrect » est une erreur 400 sans champ : on la rattache au bon champ
        this.serverErrors.set(Object.keys(errors).length ? errors : { currentPassword: msg });
      },
    });
  }
}
