import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from './auth.models';
import { AuthService } from './auth.service';

/** Espace connecté : sinon, renvoi vers la connexion avec l'URL demandée. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isAuthenticated()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Pages publiques (connexion, inscription…) : un utilisateur déjà connecté va au tableau de bord. */
export const guestGuard: CanActivateFn = () =>
  inject(AuthService).isAuthenticated() ? inject(Router).createUrlTree(['/dashboard']) : true;

/** Restreint une route à certains rôles (la vérification qui fait foi reste côté API). */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => (inject(AuthService).hasRole(...roles) ? true : inject(Router).createUrlTree(['/dashboard']));
}
