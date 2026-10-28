import { Component, input } from '@angular/core';

/** Page provisoire d'un module, remplacée par le vrai écran aux étapes suivantes. */
@Component({
  selector: 'rsb-coming-soon',
  template: `
    <div class="page-head">
      <div>
        <h1>{{ title() }}</h1>
        <p>{{ description() }}</p>
      </div>
    </div>
    <div class="panel">
      <h2>Module en préparation</h2>
      <p class="sub">Cet écran sera branché sur l'API lors d'une prochaine étape de livraison.</p>
    </div>
  `,
})
export class ComingSoonPage {
  readonly title = input('');
  readonly description = input('');
}
