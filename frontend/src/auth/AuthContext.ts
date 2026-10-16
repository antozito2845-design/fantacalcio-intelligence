import { createContext } from 'react';

export type AuthStatus = 'anonymous' | 'authenticating' | 'authenticated';

export interface AuthValue {
  status: AuthStatus;
  username?: string;
  isAdmin: boolean;
  getAuthorization: () => string | undefined;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthValue | undefined>(undefined);
