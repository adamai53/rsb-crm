import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Quote } from '../../core/api/documents';
import { AuthService } from '../../core/auth/auth.service';
import { User } from '../../core/auth/auth.models';
import { ComposeModal } from '../emails/compose-modal';
import { QuoteModal } from './quote-modal';

const QUOTE: Quote = {
  id: 'q1', number: 'RSB-Q-2026-0145', dealId: 'd1', vesselName: 'MV KESTREL', imo: '9301234', companyId: 'co1',
  companyName: 'Red Fleet', contactId: 'c1', contactName: 'Amina Said', contactEmail: 'amina@ship.example',
  fuelGrade: 'VLSFO', fuelGradeLabel: 'VLSFO 0,5 % S', quantityMt: 850, priceUsdPerMt: 612, bargeFeeUsd: 0,
  productAmountUsd: 520200, totalUsd: 520200, status: 'ENVOYE', validUntil: '2026-11-02T10:00:00Z', sentAt: null,
  createdAt: '2026-10-31T10:00:00Z', eta: '2026-11-06', deliveryLocationLabel: 'Port de Doraleh (DMP)',
  ownerId: 'owner', ownerName: 'Hassan Doualeh',
};

describe('Devis et e-mails', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  function signIn(id: string, role: User['role']): void {
    TestBed.inject(AuthService).login('a@b.c', 'x').subscribe();
    http.expectOne('/api/auth/login').flush({
      accessToken: 't', expiresAt: '',
      user: { id, email: 'a@b.c', firstName: 'A', lastName: 'B', initials: 'AB', role, status: 'ACTIVE', language: 'fr',
        emailVerifiedAt: null, lastLoginAt: null, createdAt: '' },
    });
  }

  function openQuote(quote: Quote): HTMLElement {
    const fixture = TestBed.createComponent(QuoteModal);
    fixture.componentRef.setInput('id', quote.id);
    fixture.detectChanges();
    http.expectOne(`/api/quotes/${quote.id}`).flush(quote);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  const buttons = (el: HTMLElement) => Array.from(el.querySelectorAll('footer button')).map((b) => b.textContent?.trim());

  it('affiche le devis et ses totaux, frais de barge offerts', () => {
    signIn('owner', 'SALES');
    const el = openQuote(QUOTE);
    expect(el.querySelector('.qmeta')?.textContent).toContain('RSB-Q-2026-0145');
    expect(el.querySelector('.tot')?.textContent?.replace(/\s/g, '')).toContain('520200');
    expect(el.textContent).toContain('Offerts');
  });

  it('réserve les actions au commercial du dossier et aux managers', () => {
    signIn('owner', 'SALES');
    expect(buttons(openQuote(QUOTE))).toContain('Marquer comme accepté');
  });

  it('un autre commercial peut seulement consulter et télécharger', () => {
    signIn('autre', 'SALES');
    const list = buttons(openQuote(QUOTE));
    expect(list).toContain('Télécharger le PDF');
    expect(list).not.toContain('Marquer comme accepté');
  });

  it('un devis clôturé ne propose plus d’action, même à un manager', () => {
    signIn('m', 'MANAGER');
    const list = buttons(openQuote({ ...QUOTE, status: 'ACCEPTE' }));
    expect(list).not.toContain('Marquer comme accepté');
    expect(list).not.toContain('Envoyer par e-mail');
  });

  it('le composeur fusionne le modèle et envoie le texte modifié avec la transaction', () => {
    const fixture = TestBed.createComponent(ComposeModal);
    fixture.componentRef.setInput('contactId', 'c1');
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/contacts').flush({ content: [], lifecycleCounts: {}, allCount: 0 });
    http.expectOne('/api/email-templates').flush([{ id: 't1', name: 'Envoi de cotation', subject: 'S', body: 'B' }]);
    const draft = http.expectOne((r) => r.url === '/api/emails/draft');
    expect(draft.request.params.get('templateId')).toBe('t1');
    expect(draft.request.params.get('contactId')).toBe('c1');
    draft.flush({ subject: 'Cotation MV X', body: 'Bonjour Amina', dealId: 'd9' });
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const textarea = el.querySelector('textarea') as HTMLTextAreaElement;
    expect(textarea.value).toBe('Bonjour Amina');
    textarea.value = 'Bonjour Amina, modifié';
    textarea.dispatchEvent(new Event('input'));
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    expect(http.expectOne('/api/emails/send').request.body).toEqual({
      contactId: 'c1', dealId: 'd9', subject: 'Cotation MV X', body: 'Bonjour Amina, modifié',
    });
  });
});
