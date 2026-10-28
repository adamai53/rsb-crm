import { Injectable, signal } from '@angular/core';

/** Message éphémère en bas d'écran, comme dans le prototype (un seul à la fois). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal<string | null>(null);
  private timer?: ReturnType<typeof setTimeout>;

  show(message: string, durationMs = 3200): void {
    clearTimeout(this.timer);
    this.message.set(message);
    this.timer = setTimeout(() => this.message.set(null), durationMs);
  }
}
