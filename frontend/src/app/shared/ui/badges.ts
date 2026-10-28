import { Component, computed, inject, input } from '@angular/core';

import { Lifecycle } from '../../core/api/models';
import { LIFECYCLE_CLASS, LIFECYCLE_LABELS, ReferenceService, STAGE_CLASS } from '../../core/reference.service';

/** Pastille de cycle de vie (Lead, MQL, SQL, Opportunité, Client). */
@Component({
  selector: 'rsb-lifecycle',
  template: `<span [class]="'pill ' + cls()">{{ label() }}</span>`,
})
export class LifecyclePill {
  readonly value = input.required<Lifecycle>();
  protected readonly cls = computed(() => LIFECYCLE_CLASS[this.value()]);
  protected readonly label = computed(() => LIFECYCLE_LABELS[this.value()]);
}

/** Pastille d'étape du pipeline. */
@Component({
  selector: 'rsb-stage',
  template: `<span [class]="'pill ' + cls[code()]">{{ label() }}</span>`,
})
export class StagePill {
  readonly code = input.required<string>();
  readonly label = input.required<string>();
  protected readonly cls = STAGE_CLASS;
}

/** Barre de score sur 100 ; rouge à partir de 60 (contact « chaud »). */
@Component({
  selector: 'rsb-score',
  template: `
    <span class="score" [class.hot]="value() >= 60" [attr.aria-label]="'Score ' + value() + ' sur 100'">
      <span class="bar"><i [style.width.%]="value()"></i></span><span class="num">{{ value() }}</span>
    </span>
  `,
})
export class ScoreBar {
  readonly value = input.required<number>();
}

/** Pastille du propriétaire d'un dossier (initiales, nom au survol). */
@Component({
  selector: 'rsb-owner',
  template: `
    @if (user(); as u) {
      <span class="av sm" [class.inactive]="!u.active" [title]="u.name + (u.active ? '' : ' (inactif)')">{{ u.initials }}</span>
    } @else {
      <span class="faint">—</span>
    }
  `,
})
export class OwnerAvatar {
  readonly id = input<string | null>();
  private readonly ref = inject(ReferenceService);
  protected readonly user = computed(() => this.ref.user(this.id()));
}
