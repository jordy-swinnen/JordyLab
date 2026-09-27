export type UserStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface AppUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  createdAt: string;
  enabled: boolean;
  realmRoles: string[];
  status: UserStatus;
}
