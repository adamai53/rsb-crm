import { Component, inject, output, signal } from '@angular/core';

import { errorMessage } from '../../core/api-error';
import { CrmApi } from '../../core/api/crm-api';
import { ImportReport } from '../../core/api/models';
import { UiService } from '../../core/ui.service';
import { Modal } from '../../shared/ui/modal';

/** Import CSV de contacts : choix du fichier, envoi, puis rapport ligne par ligne. */
@Component({
  selector: 'rsb-import-modal',
  imports: [Modal],
  template: `
    <rsb-modal heading="Importer des contacts" sub="Fichier CSV, séparateur « , » ou « ; » (export Excel accepté)." (closed)="closed.emit()">
      @if (report(); as r) {
        <div class="import-report">
          <div class="grid g-kpi import-kpis">
            <div class="kpi"><span class="lbl">Lignes</span><span class="val">{{ r.rows }}</span></div>
            <div class="kpi"><span class="lbl">Contacts créés</span><span class="val">{{ r.created }}</span></div>
            <div class="kpi"><span class="lbl">Ignorées</span><span class="val">{{ r.skipped }}</span></div>
            <div class="kpi"><span class="lbl">Entreprises créées</span><span class="val">{{ r.companiesCreated }}</span></div>
          </div>
          @if (r.issues.length) {
            <h4>Lignes ignorées</h4>
            <ul class="import-issues">
              @for (i of r.issues; track i.line + i.message) { <li><span class="mono">Ligne {{ i.line }}</span> {{ i.message }}</li> }
            </ul>
          }
        </div>
        <footer class="m-foot">
          <button class="btn primary" type="button" (click)="closed.emit()">Terminer</button>
        </footer>
      } @else {
        <div class="import-body">
          <p class="muted">Colonnes reconnues : <span class="mono">prenom, nom, email, telephone, poste, entreprise, source</span>.
            Les entreprises inconnues sont créées, les e-mails déjà présents sont ignorés. Les contacts importés vous sont attribués, au stade Lead.</p>
          <label class="fld">
            <span>Fichier CSV</span>
            <input type="file" accept=".csv,text/csv" (change)="pick($any($event.target).files)">
          </label>
          @if (error()) { <p class="auth-error" role="alert">{{ error() }}</p> }
        </div>
        <footer class="m-foot">
          <button type="button" class="btn ghost" (click)="closed.emit()">Annuler</button>
          <button class="btn primary" type="button" [disabled]="!file() || busy()" (click)="send()">
            {{ busy() ? 'Import en cours…' : 'Importer' }}
          </button>
        </footer>
      }
    </rsb-modal>
  `,
})
export class ImportModal {
  readonly closed = output<void>();

  private readonly api = inject(CrmApi);
  private readonly ui = inject(UiService);

  protected readonly file = signal<File | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly report = signal<ImportReport | null>(null);

  protected pick(files: FileList | null): void {
    const f = files?.[0] ?? null;
    this.error.set(f && f.size > 5 * 1024 * 1024 ? 'Fichier trop volumineux (5 Mo maximum).' : null);
    this.file.set(this.error() ? null : f);
  }

  protected send(): void {
    const f = this.file();
    if (!f) return;
    this.busy.set(true);
    this.api.importContacts(f).subscribe({
      next: (r) => {
        this.report.set(r);
        this.ui.changed();
      },
      error: (e) => {
        this.busy.set(false);
        this.error.set(errorMessage(e, "L'import a échoué."));
      },
    });
  }
}
