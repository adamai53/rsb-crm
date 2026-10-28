import { Component, input } from '@angular/core';

/** Cadre commun des pages publiques : marque RSB, titre, contenu, pied de page. */
@Component({
  selector: 'rsb-auth-card',
  template: `
    <main class="auth">
      <section class="auth-card" [attr.aria-labelledby]="'auth-title'">
        <header class="auth-brand">
          <img src="assets/logo-rsb.png" alt="" width="40">
          <div>
            <b>Red Sea Bunkering</b>
            <small>CRM commercial · Soutage maritime, Djibouti</small>
          </div>
        </header>
        <h1 id="auth-title">{{ heading() }}</h1>
        @if (subheading()) {
          <p class="auth-sub">{{ subheading() }}</p>
        }
        <ng-content />
      </section>
      <p class="auth-foot">© Red Sea Bunkering · Usage interne</p>
    </main>
  `,
})
export class AuthCard {
  readonly heading = input.required<string>();
  readonly subheading = input<string>('');
}
