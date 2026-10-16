import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FatalErrorBoundary } from './FatalErrorBoundary';
import {
  createFatalTelemetryProvider,
  setFatalTelemetryProvider,
} from '@/observability/fatalTelemetry';

function BrokenView(): never {
  throw new Error('errore riservato');
}

describe('FatalErrorBoundary', () => {
  afterEach(() => {
    cleanup();
    setFatalTelemetryProvider();
  });

  it('notifica la telemetry e mostra una UX sicura senza dettagli tecnici', () => {
    const capture = vi.fn();
    setFatalTelemetryProvider({ capture });
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <FatalErrorBoundary>
        <BrokenView />
      </FatalErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeVisible();
    expect(
      screen.getByRole('heading', { name: 'Impossibile mostrare questa schermata' }),
    ).toBeVisible();
    expect(screen.queryByText('errore riservato')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ricarica applicazione' })).toBeEnabled();
    expect(capture).toHaveBeenCalledWith(
      expect.objectContaining({
        eventType: 'frontend_fatal_error',
        source: 'react-error-boundary',
        errorName: 'Error',
        routePathname: '/',
      }),
    );
    expect(JSON.stringify(capture.mock.calls)).not.toContain('errore riservato');
  });

  it('mantiene il fallback se il trasporto telemetry fallisce', async () => {
    const transport = vi
      .fn<typeof fetch>()
      .mockRejectedValue(new Error('provider non disponibile'));
    setFatalTelemetryProvider(
      createFatalTelemetryProvider({
        endpoint: 'https://telemetry.example.test/fatal-events',
        production: true,
        transport,
      }),
    );
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <FatalErrorBoundary>
        <BrokenView />
      </FatalErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeVisible();
    expect(screen.queryByText('provider non disponibile')).not.toBeInTheDocument();
    await vi.waitFor(() => expect(transport).toHaveBeenCalledOnce());
  });
});
