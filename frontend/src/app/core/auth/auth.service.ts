import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';

import { AuthResponse, MessageResponse, Role, User } from './auth.models';

/**
 * Session côté navigateur : l'access token reste en mémoire (jamais dans le stockage local),
 * le refresh token vit dans un cookie HttpOnly posé par l'API sur /api/auth.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(null);
  readonly user = signal<User | null>(null);
  readonly isAuthenticated = computed(() => this.user() !== null);

  private refreshInFlight: Observable<boolean> | null = null;

  accessToken(): string | null {
    return this.token();
  }

  hasRole(...roles: Role[]): boolean {
    const u = this.user();
    return !!u && roles.includes(u.role);
  }

  /** Au démarrage : tente de rouvrir la session depuis le cookie de refresh. */
  restoreSession(): Observable<boolean> {
    return this.refresh();
  }

  login(email: string, password: string): Observable<User> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, password }, { withCredentials: true })
      .pipe(map((res) => this.open(res)));
  }

  /** Un seul refresh à la fois, partagé par toutes les requêtes en attente. */
  refresh(): Observable<boolean> {
    this.refreshInFlight ??= this.http
      .post<AuthResponse>('/api/auth/refresh', null, { withCredentials: true })
      .pipe(
        map((res) => {
          this.open(res);
          return true;
        }),
        catchError(() => {
          this.clear();
          return of(false);
        }),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay(1),
      );
    return this.refreshInFlight;
  }

  logout(): void {
    this.http
      .post('/api/auth/logout', null, { withCredentials: true })
      .pipe(catchError(() => of(null)))
      .subscribe(() => {
        this.clear();
        this.router.navigateByUrl('/login');
      });
  }

  /** Session perdue (refresh refusé) : retour à l'écran de connexion. */
  sessionExpired(): void {
    this.clear();
    this.router.navigate(['/login'], { queryParams: { expired: 1 } });
  }

  register(body: { firstName: string; lastName: string; email: string; password: string }) {
    return this.http.post<MessageResponse>('/api/auth/register', body);
  }

  verifyEmail(token: string) {
    return this.http.post<MessageResponse>('/api/auth/verify-email', { token });
  }

  forgotPassword(email: string) {
    return this.http.post<MessageResponse>('/api/auth/forgot-password', { email });
  }

  resetPassword(token: string, password: string) {
    return this.http.post<MessageResponse>('/api/auth/reset-password', { token, password });
  }

  updateProfile(body: { firstName: string; lastName: string; language: string }) {
    return this.http.patch<User>('/api/users/me', body).pipe(tap((u) => this.user.set(u)));
  }

  changePassword(currentPassword: string, newPassword: string) {
    return this.http.post<void>('/api/auth/password', { currentPassword, newPassword }, { withCredentials: true });
  }

  private open(res: AuthResponse): User {
    this.token.set(res.accessToken);
    this.user.set(res.user);
    return res.user;
  }

  private clear(): void {
    this.token.set(null);
    this.user.set(null);
  }
}
