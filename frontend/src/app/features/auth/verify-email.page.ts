import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { AuthCard } from './auth-card';

@Component({
  selector: 'rsb-verify-email',
  imports: [RouterLink, AuthCard],
  template: `
    <rsb-auth-card heading="Vérification de l'adresse e-mail">
      @switch (state()) {
        @case ('loading') {
          <p class="muted" role="status">Vérification en cours…</p>
        }
        @case ('ok') {
          <div class="auth-success" role="status"><b>Adresse confirmée</b><p>{{ message() }}</p></div>
          <p class="auth-alt"><a class="btn primary" routerLink="/login">Aller à la connexion</a></p>
        }
        @case ('error') {
          <p class="auth-error" role="alert">{{ message() }}</p>
          <p class="auth-alt muted">Un administrateur peut vous renvoyer un nouveau lien.</p>
          <p class="auth-alt"><a routerLink="/login">Retour à la connexion</a></p>
        }
      }
    </rsb-auth-card>
  `,
})
export class VerifyEmailPage implements OnInit {
  private readonly auth = inject(AuthService);

  readonly token = input<string>();

  protected readonly state = signal<'loading' | 'ok' | 'error'>('loading');
  protected readonly message = signal('');

  ngOnInit(): void {
    const token = this.token();
    if (!token) {
      this.state.set('error');
      this.message.set('Ce lien est incomplet.');
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: (res) => {
        this.message.set(res.message);
        this.state.set('ok');
      },
      error: (e) => {
        this.message.set(errorMessage(e, 'Ce lien de vérification est invalide ou a expiré.'));
        this.state.set('error');
      },
    });
  }
}
