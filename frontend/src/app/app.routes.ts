import { Routes } from '@angular/router';

import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guards';
import { NAV } from './core/nav';

const moduleRoutes: Routes = NAV.flatMap((group) => group.items).map((item) => ({
  path: item.path,
  title: `${item.label} · RSB CRM`,
  data: { title: item.label, description: item.description },
  loadComponent: () => import('./shared/coming-soon.page').then((m) => m.ComingSoonPage),
}));

export const routes: Routes = [
  // Pages publiques
  {
    path: 'login',
    title: 'Connexion · RSB CRM',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: 'Créer un compte · RSB CRM',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: 'forgot-password',
    title: 'Mot de passe oublié · RSB CRM',
    loadComponent: () => import('./features/auth/forgot-password.page').then((m) => m.ForgotPasswordPage),
  },
  {
    path: 'reset-password',
    title: 'Nouveau mot de passe · RSB CRM',
    loadComponent: () => import('./features/auth/reset-password.page').then((m) => m.ResetPasswordPage),
  },
  {
    path: 'verify-email',
    title: "Vérification de l'e-mail · RSB CRM",
    loadComponent: () => import('./features/auth/verify-email.page').then((m) => m.VerifyEmailPage),
  },

  // Application connectée
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      ...moduleRoutes,
      {
        path: 'profile',
        title: 'Mon profil · RSB CRM',
        loadComponent: () => import('./features/profile/profile.page').then((m) => m.ProfilePage),
      },
      {
        path: 'admin',
        title: 'Administration · RSB CRM',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./features/admin/admin-users.page').then((m) => m.AdminUsersPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
