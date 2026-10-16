import { QueryClient } from '@tanstack/react-query';

import { AppError } from '@/api/errors/AppError';

export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= 2) return false;
  if (!(error instanceof AppError)) return false;
  return error.kind === 'network' || error.kind === 'timeout' || error.kind === 'server';
}

export function createAppQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: shouldRetry,
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        refetchOnReconnect: true,
      },
      mutations: { retry: false },
    },
  });
}
