import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ContactList } from '../../core/api/models';
import { fmtK, fmtUSD, rel } from '../../core/format';
import { GlobalSearch } from '../../layout/global-search';
import { ContactsPage } from './contacts.page';

const LIST: ContactList = {
  content: [
    {
      id: 'c1', firstName: 'Amina', lastName: 'Said', title: 'Bunker buyer', email: 'amina@ship.dj', phone: null,
      companyId: 'co1', companyName: 'Maersk Tankers', lifecycle: 'CLIENT', source: 'Recommandation',
      ownerId: null, score: 88, lastActivityAt: new Date().toISOString(), createdAt: '2026-09-01T00:00:00Z',
    },
  ],
  page: 0, size: 50, totalElements: 1, totalPages: 1,
  lifecycleCounts: { LEAD: 3, MQL: 0, SQL: 1, OPPORTUNITE: 0, CLIENT: 1 },
  allCount: 5,
};

describe('Référentiel', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('formate montants et dates à la française', () => {
    expect(fmtUSD(520200)).toBe('520 200 USD'.replace(' ', ' '));
    expect(fmtK(612000)).toBe('612 k USD');
    expect(fmtK(1254000)).toBe('1,25 M USD');
    const now = new Date(2026, 9, 4, 15, 0);
    expect(rel('2026-10-04', now)).toBe("aujourd'hui");
    expect(rel('2026-10-01', now)).toBe('il y a 3 j');
    expect(rel('2026-10-06', now)).toBe('dans 2 j');
  });

  it('affiche les contacts, les compteurs et trie au clic', () => {
    const fixture = TestBed.createComponent(ContactsPage);
    fixture.detectChanges();
    const first = http.expectOne((r) => r.url === '/api/contacts');
    expect(first.request.params.get('sort')).toBe('score');
    expect(first.request.params.get('dir')).toBe('desc');
    first.flush(LIST);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('tbody tr')?.textContent).toContain('Amina Said');
    expect(el.querySelector('tbody tr')?.textContent).toContain('Maersk Tankers');
    expect(el.querySelector('.score.hot .num')?.textContent).toBe('88');
    const chips = Array.from(el.querySelectorAll('.chip')).map((c) => c.textContent?.replace(/\s+/g, ' ').trim());
    expect(chips[0]).toBe('Tous5');
    expect(chips).toContain('Lead3');

    (el.querySelector('th.sortable') as HTMLElement).click(); // Nom
    fixture.detectChanges();
    const sorted = http.expectOne((r) => r.url === '/api/contacts');
    expect(sorted.request.params.get('sort')).toBe('name');
    expect(sorted.request.params.get('dir')).toBe('asc');
  });

  it('la recherche globale attend 2 caractères et ouvre la fiche choisie au clavier', async () => {
    const fixture = TestBed.createComponent(GlobalSearch);
    fixture.detectChanges();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');

    input.value = 'm';
    input.dispatchEvent(new Event('input'));
    await new Promise((r) => setTimeout(r, 250));
    http.expectNone('/api/search');

    input.value = 'mv';
    input.dispatchEvent(new Event('input'));
    await new Promise((r) => setTimeout(r, 250));
    http.expectOne((r) => r.url === '/api/search' && r.params.get('q') === 'mv').flush([
      { type: 'contact', id: 'c1', label: 'Amina Said', sub: 'Contact' },
      { type: 'company', id: 'co1', label: 'Maersk Tankers', sub: 'Entreprise' },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.gres button').length).toBe(2);

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.gres button.active')?.textContent).toContain('Maersk Tankers');
  });
});
