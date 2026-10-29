import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DealRow } from '../../core/api/sales';
import { ReferenceService } from '../../core/reference.service';
import { UiService } from '../../core/ui.service';
import { ago } from '../inbox/inbox.page';
import { DealActions } from './deal-actions';
import { DealsPage } from './deals.page';

const STAGES = [
  { code: 'RECUE', label: 'Demande reçue', probability: 10, won: false, lost: false },
  { code: 'NEGOCIATION', label: 'Négociation', probability: 70, won: false, lost: false },
  { code: 'LIVREE', label: 'Livrée (BDN signé)', probability: 100, won: true, lost: false },
  { code: 'PERDUE', label: 'Perdue', probability: 0, won: false, lost: true },
];

function deal(id: string, stage: string, amount: number, eta: string): DealRow {
  return {
    id, vesselName: `MV ${id}`, imo: null, companyId: null, companyName: 'Red Fleet', contactId: null, contactName: null,
    fuelGrade: 'VLSFO', quantityMt: 100, priceUsdPerMt: 612, amountUsd: amount, stage, stageLabel: stage, probability: 0,
    won: stage === 'LIVREE', lost: stage === 'PERDUE', ownerId: null, source: null, eta, deliveryLocation: null,
    deliveryLocationLabel: null, closedAt: null, lostReason: null, createdAt: '2026-10-01T00:00:00Z',
  };
}

describe('Ventes', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    // Données de référence normalement chargées par le shell
    TestBed.inject(ReferenceService).load();
    http.expectOne('/api/reference').flush({
      companyTypes: [], lifecycles: [], sources: [], stages: STAGES, fuelGrades: [], deliveryLocations: [], users: [],
    });
  });

  it('range les transactions par étape avec effectif et montant', () => {
    const fixture = TestBed.createComponent(DealsPage);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/deals').flush({
      content: [deal('A', 'NEGOCIATION', 520200, '2026-11-02'), deal('B', 'NEGOCIATION', 100000, '2026-11-01'),
        deal('C', 'LIVREE', 253500, '2026-10-01')],
      summary: { openCount: 2, openAmountUsd: 620200, weightedPipelineUsd: 434140, wonCount: 1, lostCount: 0, conversionRatePct: 100 },
    });
    fixture.detectChanges();

    const lanes = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.lane'));
    expect(lanes.length).toBe(4);
    expect(lanes[1].querySelector('.lane-h > span')?.textContent?.replace(/\s+/g, ' ')).toBe('2 · 620 k USD');
    // Étapes ouvertes : ETA la plus proche d'abord
    expect(Array.from(lanes[1].querySelectorAll('.dcard .v')).map((e) => e.textContent)).toEqual(['MV B', 'MV A']);
    expect(lanes[2].querySelectorAll('.dcard').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('transformation 100 %');
  });

  it('demande le motif avant de passer une transaction en perdue', () => {
    const ui = TestBed.inject(UiService);
    const actions = TestBed.inject(DealActions);

    actions.move({ id: 'd1', vesselName: 'MV LOST', stage: 'RECUE' }, 'PERDUE');
    http.expectNone((r) => r.url.includes('/stage'));
    expect(ui.form()).toEqual({ kind: 'lost', dealId: 'd1', vesselName: 'MV LOST' });

    actions.move({ id: 'd1', vesselName: 'MV LOST', stage: 'RECUE' }, 'NEGOCIATION');
    expect(http.expectOne('/api/deals/d1/stage').request.body).toEqual({ stage: 'NEGOCIATION', lostReason: null });
  });

  it('formate l’ancienneté des leads', () => {
    const now = new Date('2026-10-04T12:00:00Z').getTime();
    expect(ago('2026-10-04T11:48:00Z', now)).toBe('il y a 12 min');
    expect(ago('2026-10-04T09:00:00Z', now)).toBe('il y a 3 h');
    expect(ago('2026-10-02T12:00:00Z', now)).toBe('il y a 2 j');
  });
});
