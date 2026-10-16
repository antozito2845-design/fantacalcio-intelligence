import { describe, expect, it } from 'vitest';

import { parseEnvironment } from '@/app/bootstrap/env';

describe('parseEnvironment', () => {
  it('accetta un base path API assoluto', () => {
    expect(parseEnvironment({ VITE_API_BASE_URL: '/api/v1' })).toEqual({
      VITE_API_BASE_URL: '/api/v1',
    });
  });

  it('rifiuta configurazioni mancanti o relative', () => {
    expect(() => parseEnvironment({})).toThrow();
    expect(() => parseEnvironment({ VITE_API_BASE_URL: 'api/v1' })).toThrow();
  });

  it('tratta un endpoint telemetry vuoto come configurazione assente', () => {
    expect(
      parseEnvironment({
        VITE_API_BASE_URL: '/api/v1',
        VITE_FATAL_TELEMETRY_ENDPOINT: '   ',
      }),
    ).toEqual({ VITE_API_BASE_URL: '/api/v1', VITE_FATAL_TELEMETRY_ENDPOINT: undefined });
  });
});
