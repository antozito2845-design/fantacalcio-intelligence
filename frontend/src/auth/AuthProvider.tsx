import { useCallback, useEffect, useMemo, useState, type PropsWithChildren } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { dashboardApi } from '@/api/dashboardApi';
import { AppError } from '@/api/errors/AppError';
import { setUnauthorizedHandler } from '@/api/client/httpClient';
import { AuthContext, type AuthStatus, type AuthValue } from './AuthContext';

interface Credentials {
  username: string;
  password: string;
}

function basicHeader(credentials: Credentials): string {
  const bytes = new TextEncoder().encode(`${credentials.username}:${credentials.password}`);
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return `Basic ${btoa(binary)}`;
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [credentials, setCredentials] = useState<Credentials>();
  const [status, setStatus] = useState<AuthStatus>('anonymous');
  const [isAdmin, setIsAdmin] = useState(false);
  const queryClient = useQueryClient();
  const getAuthorization = useCallback(
    () => (credentials ? basicHeader(credentials) : undefined),
    [credentials],
  );

  const login = useCallback(async (username: string, password: string) => {
    const candidate = { username: username.trim(), password };
    const authorization = () => basicHeader(candidate);
    setStatus('authenticating');
    try {
      await dashboardApi.squads(authorization);
      let admin = false;
      try {
        await dashboardApi.readiness(undefined, authorization);
        admin = true;
      } catch (error) {
        if (!(error instanceof AppError) || error.kind !== 'forbidden') throw error;
      }
      setCredentials(candidate);
      setIsAdmin(admin);
      setStatus('authenticated');
    } catch (error) {
      setCredentials(undefined);
      setIsAdmin(false);
      setStatus('anonymous');
      throw error;
    }
  }, []);

  const logout = useCallback(() => {
    setCredentials(undefined);
    setIsAdmin(false);
    setStatus('anonymous');
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(undefined);
  }, [logout]);

  const value = useMemo<AuthValue>(
    () => ({
      status,
      username: credentials?.username,
      isAdmin,
      getAuthorization,
      login,
      logout,
    }),
    [credentials?.username, getAuthorization, isAdmin, login, logout, status],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
