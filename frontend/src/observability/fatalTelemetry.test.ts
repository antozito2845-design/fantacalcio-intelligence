import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  createFatalTelemetryProvider,
  HttpFatalTelemetryProvider,
  NoopFatalTelemetryProvider,
  type FatalErrorEvent,
} from './fatalTelemetry';

const event: FatalErrorEvent = {
  eventType: 'frontend_fatal_error',
  source: 'react-error-boundary',
  errorName: 'TypeError',
  timestamp: '2026-08-22T20:00:00.000Z',
  routePathname: '/safe-route',
};

afterEach(() => vi.restoreAllMocks());

describe('fatal telemetry provider selection', () => {
  it('seleziona Noop quando endpoint è assente', () => {
    expect(createFatalTelemetryProvider({ production: true })).toBeInstanceOf(
      NoopFatalTelemetryProvider,
    );
  });

  it('seleziona il provider HTTP per un endpoint HTTPS valido', () => {
    expect(
      createFatalTelemetryProvider({
        endpoint: 'https://telemetry.example.test/fatal-events',
        production: true,
      }),
    ).toBeInstanceOf(HttpFatalTelemetryProvider);
  });

  it('usa Noop per URL invalidi o HTTP non valido in produzione', () => {
    expect(
      createFatalTelemetryProvider({ endpoint: 'non-un-url', production: true }),
    ).toBeInstanceOf(NoopFatalTelemetryProvider);
    expect(
      createFatalTelemetryProvider({
        endpoint: 'http://telemetry.example.test/fatal-events',
        production: true,
      }),
    ).toBeInstanceOf(NoopFatalTelemetryProvider);
  });
});

describe('HttpFatalTelemetryProvider', () => {
  it('invia un POST JSON privacy-safe senza Authorization né credenziali', async () => {
    const transport = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 202 }));
    const selected = createFatalTelemetryProvider({
      endpoint: 'https://telemetry.example.test/fatal-events',
      production: true,
      transport,
    });

    selected.capture(event);
    await vi.waitFor(() => expect(transport).toHaveBeenCalledOnce());

    const [endpoint, request] = transport.mock.calls[0] ?? [];
    expect(endpoint).toBe('https://telemetry.example.test/fatal-events');
    expect(request).toMatchObject({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'omit',
      keepalive: true,
    });
    if (typeof request?.body !== 'string') throw new Error('Expected serialized JSON body');
    const serialized = request.body;
    expect(JSON.parse(serialized)).toEqual(event);
    for (const forbidden of [
      'Basic',
      'Authorization',
      'password',
      'stack',
      'message',
      'username',
      'player',
      'squad',
      'auction',
    ]) {
      expect(serialized).not.toContain(forbidden);
    }
  });

  it('assorbe un errore di rete senza rejection non gestite', async () => {
    const transport = vi.fn<typeof fetch>().mockRejectedValue(new Error('offline'));
    const selected = createFatalTelemetryProvider({
      endpoint: 'http://localhost:4318/fatal-events',
      production: false,
      transport,
    });
    expect(() => selected.capture(event)).not.toThrow();
    await vi.waitFor(() => expect(transport).toHaveBeenCalledOnce());
  });
});
