import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';

import { httpRequest } from '@/api/client/httpClient';
import { AppError } from '@/api/errors/AppError';
import { server } from '@/test/server';

describe('httpRequest', () => {
  it('restituisce JSON nelle risposte riuscite', async () => {
    server.use(http.get('/api/v1/example', () => HttpResponse.json({ ready: true })));
    await expect(httpRequest<{ ready: boolean }>('/example')).resolves.toEqual({ ready: true });
  });

  it('mappa ApiErrorResponse preservando i dettagli', async () => {
    server.use(
      http.get('/api/v1/missing', () =>
        HttpResponse.json(
          {
            timestamp: '2026-08-22T10:00:00Z',
            status: 404,
            error: 'Not Found',
            message: 'Giocatore non trovato',
            path: '/api/v1/missing',
          },
          { status: 404 },
        ),
      ),
    );
    const error = await httpRequest('/missing').catch((reason: unknown) => reason);
    expect(error).toBeInstanceOf(AppError);
    expect(error).toMatchObject({
      kind: 'not-found',
      status: 404,
      message: 'Giocatore non trovato',
    });
  });

  it('gestisce errori non JSON con fallback sicuro', async () => {
    server.use(http.get('/api/v1/forbidden', () => new HttpResponse('Denied', { status: 403 })));
    await expect(httpRequest('/forbidden')).rejects.toMatchObject({
      kind: 'forbidden',
      status: 403,
      message: 'Richiesta HTTP non riuscita (403)',
    });
  });

  it.each([
    [400, 'bad-request'],
    [401, 'unauthorized'],
    [403, 'forbidden'],
    [404, 'not-found'],
    [409, 'conflict'],
    [422, 'unprocessable'],
    [429, 'rate-limit'],
    [500, 'server'],
  ] as const)('mappa lo status %s come %s', async (status, kind) => {
    server.use(http.get('/api/v1/status-case', () => new HttpResponse(null, { status })));
    await expect(httpRequest('/status-case')).rejects.toMatchObject({ kind, status });
  });

  it('invia Authorization dinamico nelle richieste protette', async () => {
    server.use(
      http.get('/api/v1/protected', ({ request }) =>
        HttpResponse.json({ authorization: request.headers.get('authorization') }),
      ),
    );
    await expect(
      httpRequest('/protected', { getAuthorization: () => 'Basic dXNlcjpwYXNz' }),
    ).resolves.toEqual({ authorization: 'Basic dXNlcjpwYXNz' });
  });

  it('supporta body JSON e risposta no-content', async () => {
    server.use(
      http.post('/api/v1/resource', async ({ request }) => {
        expect(await request.json()).toEqual({ name: 'test' });
        return new HttpResponse(null, { status: 204 });
      }),
    );
    await expect(
      httpRequest('/resource', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: 'test' }),
      }),
    ).resolves.toBeUndefined();
  });

  it('distingue gli errori di rete', async () => {
    server.use(http.get('/api/v1/network', () => HttpResponse.error()));
    await expect(httpRequest('/network')).rejects.toMatchObject({ kind: 'network' });
  });

  it('distingue il timeout', async () => {
    server.use(
      http.get('/api/v1/slow', async () => {
        await new Promise((resolve) => setTimeout(resolve, 50));
        return HttpResponse.json({ ready: true });
      }),
    );
    await expect(httpRequest('/slow', { timeoutMs: 5 })).rejects.toMatchObject({ kind: 'timeout' });
  });
});
