import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { FormConfig } from '../../core/api/analytics';
import { ComparePage, ROWS } from '../compare/compare.page';
import { HBars, VBars, niceMax, shortK } from '../../shared/ui/charts';
import { ReportsPage } from '../reports/reports.page';
import { buildSnippet } from './snippet';

const CONFIG: FormConfig = {
  endpoint: 'https://crm.example/api/public/leads', apiKey: 'cle', captchaSiteKey: '', captchaEnabled: false, allowedOrigins: [],
};

describe('Snippet WordPress', () => {
  it('inclut le point de terminaison, la clé, les produits et le champ piège', () => {
    const s = buildSnippet(CONFIG, [{ code: 'VLSFO', label: 'VLSFO 0,5 %' }]);
    expect(s).toContain('"https://crm.example/api/public/leads"');
    expect(s).toContain('"cle"');
    expect(s).toContain('<option value="VLSFO">VLSFO 0,5 %</option>');
    expect(s).toContain('name="website"');
    expect(s).not.toContain('turnstile/v0/api.js');
  });

  it('ajoute Turnstile seulement si le captcha est actif', () => {
    const s = buildSnippet({ ...CONFIG, captchaEnabled: true, captchaSiteKey: 'site123' }, []);
    expect(s).toContain('turnstile/v0/api.js');
    expect(s).toContain('data-sitekey="site123"');
  });

  it('neutralise une valeur capable de fermer le script ou un attribut', () => {
    const s = buildSnippet({ ...CONFIG, apiKey: '</script><b>', captchaEnabled: true, captchaSiteKey: '"><i>' }, [
      { code: 'A"B', label: '<u>' },
    ]);
    expect(s).not.toContain('</script><b>');
    expect(s).not.toContain('"><i>');
    expect(s).not.toContain('<u>');
    expect(s).toContain('value="A&quot;B"');
  });
});

describe('Graphiques', () => {
  it('arrondit les échelles et les montants', () => {
    expect(niceMax(0)).toBe(1);
    expect(niceMax(1300)).toBe(2000);
    expect(niceMax(4_100_000)).toBe(5_000_000);
    expect(shortK(612_000)).toBe('612 k');
    expect(shortK(1_250_000)).toBe('1,3 M');
    expect(shortK(40)).toBe('40');
  });

  it('dessine les barres horizontales proportionnellement à la plus grande', () => {
    const f = TestBed.createComponent(HBars);
    f.componentRef.setInput('rows', [{ label: 'A', value: 50 }, { label: 'B', value: 100, display: '100 MT' }]);
    f.detectChanges();
    const bars = f.nativeElement.querySelectorAll('.t i') as NodeListOf<HTMLElement>;
    expect(bars[0].style.width).toBe('50%');
    expect(bars[1].style.width).toBe('100%');
    expect(f.nativeElement.textContent).toContain('100 MT');
  });

  it('ne casse pas sans donnée', () => {
    const h = TestBed.createComponent(HBars);
    h.componentRef.setInput('rows', []);
    h.detectChanges();
    expect(h.nativeElement.textContent).toContain('Aucune donnée');
    const v = TestBed.createComponent(VBars);
    v.componentRef.setInput('rows', [{ label: 'oct.', value: 0, now: true }]);
    v.detectChanges();
    expect(v.nativeElement.querySelector('rect').getAttribute('height')).toBe('0');
  });
});

describe('Comparatif', () => {
  it('compte les lignes par niveau', () => {
    const f = TestBed.createComponent(ComparePage);
    f.detectChanges();
    const vals = f.nativeElement.querySelectorAll('.kpi .val');
    const total = Number(vals[0].textContent) + Number(vals[1].textContent) + Number(vals[2].textContent);
    expect(total).toBe(ROWS.length);
    expect(f.nativeElement.querySelectorAll('tbody tr').length).toBe(ROWS.length);
  });
});

describe('Rapports', () => {
  it('affiche les mois, marque le mois en cours et gère le cycle vide', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(ReportsPage);
    f.detectChanges();
    http.expectOne('/api/reports/monthly-revenue').flush([
      { month: '2026-09', revenueUsd: 100000 }, { month: '2026-10', revenueUsd: 50000 },
    ]);
    http.expectOne('/api/reports/funnel').flush([{ code: 'LEAD', label: 'Lead', count: 3 }]);
    http.expectOne('/api/reports/product-volumes').flush([{ code: 'VLSFO', label: 'VLSFO', volumeMt: 100 }]);
    http.expectOne('/api/reports/lost-reasons').flush([]);
    http.expectOne('/api/reports/sales-performance').flush([]);
    http.expectOne('/api/reports/sales-cycle').flush({ averageDays: null, delivered: 0 });
    f.detectChanges();
    const text = f.nativeElement.textContent as string;
    expect(text).toContain('sept.');
    expect(text).toContain('aucune livraison');
    expect(text).toContain('Aucune transaction perdue');
    expect(f.nativeElement.querySelectorAll('rect.now').length).toBe(1);
    http.verify();
  });
});
