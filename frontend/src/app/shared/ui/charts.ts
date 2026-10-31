import { Component, computed, input } from '@angular/core';

export interface BarRow {
  label: string;
  value: number;
  display?: string;
  /** Barre mise en avant (mois en cours). */
  now?: boolean;
}

/** Barres horizontales du prototype. */
@Component({
  selector: 'rsb-hbars',
  template: `
    <div class="hbars">
      @for (r of rows(); track r.label) {
        <div class="hb">
          <span class="l" [title]="r.label">{{ r.label }}</span>
          <span class="t"><i [class.acc]="accent()" [style.width.%]="width(r.value)"></i></span>
          <span class="v">{{ r.display ?? r.value }}</span>
        </div>
      } @empty {
        <p class="empty">{{ empty() }}</p>
      }
    </div>
  `,
})
export class HBars {
  readonly rows = input.required<BarRow[]>();
  readonly accent = input(false);
  readonly empty = input('Aucune donnée pour le moment.');

  private readonly max = computed(() => Math.max(1, ...this.rows().map((r) => r.value)));

  protected width(value: number): number {
    return (value / this.max()) * 100;
  }
}

/** Arrondit l'échelle à une valeur lisible (1, 2, 2,5, 5 ou 10 × 10ⁿ). */
export function niceMax(v: number): number {
  if (v <= 0) return 1;
  const p = Math.pow(10, Math.floor(Math.log10(v)));
  const n = v / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * p;
}

/** Montant très compact pour les axes : 612 k, 1,3 M. */
export function shortK(v: number): string {
  if (v >= 1e6) return `${(v / 1e6).toFixed(1).replace('.', ',')} M`;
  if (v >= 1e3) return `${Math.round(v / 1e3)} k`;
  return String(Math.round(v));
}

const W = 600;
const H = 230;
const L = 64;
const B = 28;
const T = 22;
const R = 8;

/** Histogramme SVG du prototype. */
@Component({
  selector: 'rsb-vbars',
  template: `
    <svg class="chart" [attr.viewBox]="'0 0 ' + w + ' ' + h" role="img" [attr.aria-label]="label()">
      @for (g of grid(); track g.y) {
        <line class="grid-l" [attr.x1]="left" [attr.x2]="w - right" [attr.y1]="g.y" [attr.y2]="g.y" />
        <text [attr.x]="left - 8" [attr.y]="g.y + 4" text-anchor="end">{{ g.text }}</text>
      }
      @for (b of bars(); track b.label) {
        <rect class="bar" [class.now]="b.now" [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="b.h" rx="3" />
        <text [attr.x]="b.cx" [attr.y]="h - 8" text-anchor="middle">{{ b.label }}</text>
        @if (b.text) { <text class="val" [attr.x]="b.cx" [attr.y]="b.y - 6" text-anchor="middle">{{ b.text }}</text> }
      }
    </svg>
  `,
})
export class VBars {
  readonly rows = input.required<BarRow[]>();
  readonly label = input('Histogramme');

  protected readonly w = W;
  protected readonly h = H;
  protected readonly left = L;
  protected readonly right = R;

  private readonly max = computed(() => niceMax(Math.max(1, ...this.rows().map((r) => r.value))));

  protected readonly grid = computed(() =>
    [0, 1, 2, 3, 4].map((i) => ({ y: T + (H - T - B) * (1 - i / 4), text: shortK((this.max() * i) / 4) })),
  );

  protected readonly bars = computed(() => {
    const rows = this.rows();
    const bw = (W - L - R) / Math.max(1, rows.length);
    const ih = H - T - B;
    return rows.map((r, i) => {
      const h = (ih * r.value) / this.max();
      const x = L + i * bw + bw * 0.2;
      return { label: r.label, now: !!r.now, x, y: T + ih - h, w: bw * 0.6, h, cx: x + bw * 0.3, text: r.value ? shortK(r.value) : '' };
    });
  });
}
