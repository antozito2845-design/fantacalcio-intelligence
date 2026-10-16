import { describe, expect, it } from 'vitest';
import { AppError } from '@/api/errors/AppError';
import { shouldRetry } from './queryClient';

describe('query retry policy', () => {
  it('non riprova gli errori 4xx', () => {
    expect(shouldRetry(0, new AppError('no', { kind: 'unauthorized', status: 401 }))).toBe(false);
  });
  it('riprova una volta network e 5xx ma non oltre il limite', () => {
    expect(shouldRetry(0, new AppError('offline', { kind: 'network' }))).toBe(true);
    expect(shouldRetry(1, new AppError('server', { kind: 'server', status: 500 }))).toBe(true);
    expect(shouldRetry(2, new AppError('server', { kind: 'server', status: 500 }))).toBe(false);
  });
});
