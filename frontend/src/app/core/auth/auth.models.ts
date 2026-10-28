export type Role = 'ADMIN' | 'MANAGER' | 'SALES';
export type UserStatus = 'PENDING' | 'PENDING_APPROVAL' | 'ACTIVE' | 'DISABLED';

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  initials: string;
  role: Role;
  status: UserStatus;
  language: string;
  emailVerifiedAt: string | null;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  expiresAt: string;
  user: User;
}

export interface MessageResponse {
  message: string;
}

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateur',
  MANAGER: 'Manager',
  SALES: 'Commercial',
};

export const STATUS_LABELS: Record<UserStatus, string> = {
  PENDING: 'E-mail non vérifié',
  PENDING_APPROVAL: 'À valider',
  ACTIVE: 'Actif',
  DISABLED: 'Désactivé',
};
