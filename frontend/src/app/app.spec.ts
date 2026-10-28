import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { AuthResponse, User } from './core/auth/auth.models';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { NAV } from './core/nav';
import { Shell } from './layout/shell';

function user(role: User['role']): User {
  return {
    id: 'u1', email: 'hd@rsb.dj', firstName: 'Hassan', lastName: 'Doualeh', initials: 'HD', role,
    status: 'ACTIVE', language: 'fr', emailVerifiedAt: null, lastLoginAt: null, createdAt: '2026-10-01T00:00:00Z',
  };
}

function session(role: User['role'], token = 'access-1'): AuthResponse {
  return { accessToken: token, expiresAt: '2026-10-04T10:00:00Z', user: user(role) };
}

describe('RSB CRM', () => {
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  function signIn(role: User['role']): void {
    auth.login('hd@rsb.dj', 'Soutage2026').subscribe();
    http.expectOne('/api/auth/login').flush(session(role));
  }

  describe('Shell', () => {
    it('affiche la marque, les modules et le nom de l’utilisateur', () => {
      signIn('SALES');
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;

      expect(el.querySelector('.brand b')?.textContent).toContain('Red Sea Bunkering');
      expect(el.querySelectorAll('a.nav-btn').length).toBe(NAV.flatMap((g) => g.items).length);
      expect(el.querySelector('.who-btn')?.textContent).toContain('Hassan D.');
      expect(el.textContent).not.toContain('Administration');
    });

    it('montre l’administration aux seuls administrateurs', () => {
      signIn('ADMIN');
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();
      const labels = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a.nav-btn')).map((a) =>
        a.textContent?.trim(),
      );
      expect(labels).toContain('Administration');
    });
  });

  describe('intercepteur', () => {
    it('ajoute le jeton, rafraîchit la session sur 401 et rejoue la requête', () => {
      signIn('SALES');
      let result: unknown;
      TestBed.inject(HttpClient).get('/api/contacts').subscribe((r) => (result = r));

      const first = http.expectOne('/api/contacts');
      expect(first.request.headers.get('Authorization')).toBe('Bearer access-1');
      first.flush(null, { status: 401, statusText: 'Unauthorized' });

      const refresh = http.expectOne('/api/auth/refresh');
      expect(refresh.request.withCredentials).toBeTrue();
      refresh.flush(session('SALES', 'access-2'));

      const retry = http.expectOne('/api/contacts');
      expect(retry.request.headers.get('Authorization')).toBe('Bearer access-2');
      retry.flush([{ id: 'c1' }]);
      expect(result).toEqual([{ id: 'c1' }]);
    });

    it('renvoie vers la connexion si le refresh échoue', () => {
      signIn('SALES');
      const router = TestBed.inject(Router);
      const navigate = spyOn(router, 'navigate').and.resolveTo(true);
      TestBed.inject(HttpClient).get('/api/contacts').subscribe({ error: () => undefined });

      http.expectOne('/api/contacts').flush(null, { status: 401, statusText: 'Unauthorized' });
      http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(auth.isAuthenticated()).toBeFalse();
      expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: 1 } });
    });

    it('n’ajoute pas de jeton sur les routes publiques', () => {
      signIn('SALES');
      TestBed.inject(HttpClient).get('/api/public/info').subscribe();
      expect(http.expectOne('/api/public/info').request.headers.has('Authorization')).toBeFalse();
    });
  });

  describe('gardes', () => {
    it('redirige un visiteur non connecté vers la connexion', async () => {
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/deals');
      expect(router.url).toBe('/login?returnUrl=%2Fdeals');
    });

    it('interdit l’administration à un commercial', async () => {
      signIn('SALES');
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/admin');
      expect(router.url).toBe('/dashboard');
    });
  });
});
