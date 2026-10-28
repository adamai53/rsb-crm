import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { ToastService } from './toast.service';

/** Message lisible d'une erreur d'API (format problem+json, messages en français). */
export function errorMessage(err: unknown, fallback = 'Une erreur est survenue.'): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Serveur injoignable. Vérifiez votre connexion.';
    }
    const detail = (err.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail) {
      return detail;
    }
  }
  return fallback;
}

/** Erreurs de validation par champ renvoyées par l'API (propriété "errors"). */
export function fieldErrors(err: unknown): Record<string, string> {
  if (err instanceof HttpErrorResponse && err.status === 400) {
    const errors = (err.error as { errors?: Record<string, string> } | null)?.errors;
    return errors ?? {};
  }
  return {};
}

/**
 * Erreurs globales : réseau, serveur et droits insuffisants donnent un toast.
 * Les autres (validation, identifiants…) sont affichées par l'écran concerné.
 */
export const errorToastInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && !req.url.startsWith('/api/public/')) {
        if (err.status === 0 || err.status >= 500 || err.status === 403) {
          if (!req.url.startsWith('/api/auth/')) {
            toast.show(errorMessage(err));
          }
        }
      }
      return throwError(() => err);
    }),
  );
};
