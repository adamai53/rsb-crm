import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/** Routes qui ne portent pas d'access token et ne déclenchent pas de refresh. */
const PUBLIC_AUTH = /^\/api\/auth\/(login|register|refresh|logout|verify-email|forgot-password|reset-password)$/;

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/** Ajoute le Bearer aux appels /api ; sur 401, rafraîchit la session une fois puis rejoue la requête. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || req.url.startsWith('/api/public/') || PUBLIC_AUTH.test(req.url)) {
    return next(req);
  }
  const auth = inject(AuthService);

  return next(withToken(req, auth.accessToken())).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401) {
        return throwError(() => err);
      }
      return auth.refresh().pipe(
        switchMap((ok) => {
          if (!ok) {
            auth.sessionExpired();
            return throwError(() => err);
          }
          return next(withToken(req, auth.accessToken()));
        }),
      );
    }),
  );
};
