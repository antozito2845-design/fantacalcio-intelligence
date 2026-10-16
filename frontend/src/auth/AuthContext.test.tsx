import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthProvider } from './AuthProvider';
import { useAuth } from './useAuth';
import { server } from '@/test/server';

function Harness() {
  const auth = useAuth();
  return (
    <div>
      <span data-testid="status">{auth.status}</span>
      <span data-testid="admin">{String(auth.isAdmin)}</span>
      <button onClick={() => void auth.login('utente', 'segreto').catch(() => undefined)}>
        Login
      </button>
      <button onClick={auth.logout}>Logout</button>
    </div>
  );
}

function renderAuth() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <Harness />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

afterEach(cleanup);

describe('AuthContext Basic stateless', () => {
  it('autentica un utente e rileva capability non-admin', async () => {
    server.use(
      http.get('/api/v1/squads', ({ request }) =>
        request.headers.get('authorization')
          ? HttpResponse.json([])
          : new HttpResponse(null, { status: 401 }),
      ),
      http.get('/api/v1/admin/catalog/readiness', () => new HttpResponse(null, { status: 403 })),
    );
    renderAuth();
    await userEvent.click(screen.getByRole('button', { name: 'Login' }));
    expect(await screen.findByText('authenticated')).toBeVisible();
    expect(screen.getByTestId('admin')).toHaveTextContent('false');
  });

  it('rileva la capability admin con probe read-only', async () => {
    server.use(
      http.get('/api/v1/squads', () => HttpResponse.json([])),
      http.get('/api/v1/admin/catalog/readiness', () =>
        HttpResponse.json({
          season: '2026-2027',
          activeCatalogPlayers: 500,
          qualityPercentage: 98,
          readinessStatus: 'READY',
          totalOperationalPlayers: 500,
          totalClubs: 20,
          playersByRole: { P: 40, D: 160, C: 180, A: 120 },
          errorCount: 0,
          warningCount: 0,
        }),
      ),
    );
    renderAuth();
    await userEvent.click(screen.getByRole('button', { name: 'Login' }));
    expect(await screen.findByText('authenticated')).toBeVisible();
    expect(screen.getByTestId('admin')).toHaveTextContent('true');
  });

  it('rifiuta credenziali non valide e mantiene sessione anonima', async () => {
    server.use(http.get('/api/v1/squads', () => new HttpResponse(null, { status: 401 })));
    renderAuth();
    await userEvent.click(screen.getByRole('button', { name: 'Login' }));
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
  });

  it('logout elimina la sessione mantenuta in memoria', async () => {
    server.use(
      http.get('/api/v1/squads', () => HttpResponse.json([])),
      http.get('/api/v1/admin/catalog/readiness', () => new HttpResponse(null, { status: 403 })),
    );
    renderAuth();
    await userEvent.click(screen.getByRole('button', { name: 'Login' }));
    expect(await screen.findByText('authenticated')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Logout' }));
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
  });

  it('una 401 successiva invalida globalmente la sessione', async () => {
    server.use(
      http.get('/api/v1/squads', () => HttpResponse.json([])),
      http.get('/api/v1/admin/catalog/readiness', () => new HttpResponse(null, { status: 403 })),
    );
    renderAuth();
    await userEvent.click(screen.getByRole('button', { name: 'Login' }));
    expect(await screen.findByText('authenticated')).toBeVisible();
    server.use(
      http.get('/api/v1/protected-expired', () => new HttpResponse(null, { status: 401 })),
    );
    const { httpRequest } = await import('@/api/client/httpClient');
    await act(async () => {
      await httpRequest('/protected-expired').catch(() => undefined);
    });
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
  });
});
