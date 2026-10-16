import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import { server } from '@/test/server';
import { LiveAuctionPage } from './LiveAuctionPage';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    status: 'authenticated',
    username: 'owner-a',
    isAdmin: false,
    getAuthorization: () => 'Basic test',
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));
vi.mock('@/layouts/AppShell', () => ({
  AppShell: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

const session = {
  id: 7,
  squadId: 5,
  season: '2026/2027',
  leagueTeams: 8,
  totalBudget: 1000,
  defenseModifier: true,
  status: 'DRAFT',
  playerStateInitialized: false,
  currentPlayerId: null as number | null,
  currentPrice: null as number | null,
  version: 0,
  createdAt: '2026-08-31T10:00:00Z',
  updatedAt: '2026-08-31T10:00:00Z',
};
const players = [
  { playerId: 11, playerName: 'Giocatore reale', role: 'A', availability: 'AVAILABLE' },
  { playerId: 12, playerName: 'Alternativa reale', role: 'D', availability: 'AVAILABLE' },
];

interface TestPurchase {
  id: number;
  sessionId: number;
  playerId: number;
  buyerType: string;
  paidPrice: number | null;
  idempotencyKey: string;
  createdAt: string;
}

interface TestSnapshot {
  session: typeof session;
  spentBudget: number;
  remainingBudget: number;
  purchases: TestPurchase[];
  players: Array<(typeof players)[number]>;
  availabilityComplete: boolean;
}

function snapshot(overrides: Partial<typeof session> = {}): TestSnapshot {
  return {
    session: { ...session, ...overrides },
    spentBudget: 879,
    remainingBudget: 121,
    purchases: [],
    players,
    availabilityComplete: true,
  };
}

afterEach(cleanup);

function renderSession() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/auction/live/7']}>
        <Routes>
          <Route path="/auction/live/:sessionId" element={<LiveAuctionPage />} />
          <Route path="/auction/live" element={<div>Launcher</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function apiError(status: number, message: string, path: string) {
  return HttpResponse.json(
    {
      timestamp: '2026-08-31T10:00:00Z',
      status,
      error: status === 409 ? 'Conflict' : 'Bad Request',
      message,
      path,
    },
    { status },
  );
}

describe('LiveAuctionPage lifecycle reale', () => {
  it('attiva una sessione DRAFT e impedisce il doppio submit', async () => {
    let current = snapshot();
    let activationCalls = 0;
    server.use(
      http.get('/api/v1/auction/sessions/7/state', () => HttpResponse.json(current)),
      http.post('/api/v1/auction/sessions/7/activate', ({ request }) => {
        expect(request.headers.get('authorization')).toBe('Basic test');
        activationCalls += 1;
        current = snapshot({ status: 'ACTIVE', playerStateInitialized: true, version: 1 });
        return HttpResponse.json(current.session);
      }),
    );
    renderSession();

    const activate = await screen.findByRole('button', { name: 'Attiva sessione' });
    await userEvent.dblClick(activate);

    expect(await screen.findByRole('heading', { name: 'Nomina giocatore' })).toBeVisible();
    expect(activationCalls).toBe(1);
  });

  it('nomina, valuta e aggiudica aggiornando budget e storico', async () => {
    let current = snapshot({ status: 'ACTIVE', playerStateInitialized: true, version: 1 });
    server.use(
      http.get('/api/v1/auction/sessions/7/state', () => HttpResponse.json(current)),
      http.post('/api/v1/auction/sessions/7/nomination', async ({ request }) => {
        expect(await request.json()).toEqual({ playerId: 11, currentPrice: 2 });
        current = {
          ...current,
          session: { ...current.session, currentPlayerId: 11, currentPrice: 2, version: 2 },
        };
        return HttpResponse.json(current.session);
      }),
      http.post('/api/v1/auction/sessions/7/decision', async ({ request }) => {
        expect(await request.json()).toEqual({ proposedBid: 3 });
        return HttpResponse.json({
          action: 'RILANCIA',
          currentPlayerId: 11,
          currentPlayerName: 'Giocatore reale',
          currentRole: 'A',
          currentPrice: 2,
          proposedBid: 3,
          evaluatedBid: 3,
          remainingBudget: 121,
          budgetAfterBid: 118,
          priceZone: 'VALUE_ZONE',
          reasons: ['BUDGET_SAFE'],
          warnings: [],
        });
      }),
      http.post('/api/v1/auction/sessions/7/purchases', async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>;
        expect(body).toMatchObject({ playerId: 11, buyerType: 'USER', paidPrice: 3 });
        expect(body.idempotencyKey).toMatch(/^live-auction-/);
        const key = String(body.idempotencyKey);
        current = {
          ...current,
          session: { ...current.session, currentPlayerId: null, currentPrice: null, version: 3 },
          spentBudget: 882,
          remainingBudget: 118,
          purchases: [
            {
              id: 31,
              sessionId: 7,
              playerId: 11,
              buyerType: 'USER',
              paidPrice: 3,
              idempotencyKey: key,
              createdAt: '2026-08-31T10:03:00Z',
            },
          ],
          players: players.map((player) =>
            player.playerId === 11 ? { ...player, availability: 'ACQUIRED_BY_USER' } : player,
          ),
        };
        return HttpResponse.json(current.purchases[0]);
      }),
    );
    renderSession();

    await userEvent.clear(await screen.findByLabelText('Prezzo corrente nomination'));
    await userEvent.type(screen.getByLabelText('Prezzo corrente nomination'), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Registra nomination' }));
    expect(await screen.findByRole('link', { name: 'Giocatore reale' })).toBeVisible();

    await userEvent.clear(screen.getByLabelText('Offerta proposta'));
    await userEvent.type(screen.getByLabelText('Offerta proposta'), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Valuta offerta' }));
    expect(await screen.findByText('RILANCIA')).toBeVisible();
    expect(screen.getByText(/budget dopo offerta 118/)).toBeVisible();

    await userEvent.clear(screen.getByLabelText('Prezzo aggiudicazione'));
    await userEvent.type(screen.getByLabelText('Prezzo aggiudicazione'), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Registra aggiudicazione' }));
    expect(await screen.findByText('Aggiudicazione registrata e rosa aggiornata.')).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(await screen.findByText(/1 aggiudicazioni registrate/)).toBeVisible();
    expect(screen.getByText('118')).toBeVisible();
  });

  it('mostra il rifiuto backend per offerta troppo bassa o oltre budget', async () => {
    const current = snapshot({
      status: 'ACTIVE',
      playerStateInitialized: true,
      currentPlayerId: 11,
      currentPrice: 5,
      version: 2,
    });
    let attempt = 0;
    server.use(
      http.get('/api/v1/auction/sessions/7/state', () => HttpResponse.json(current)),
      http.post('/api/v1/auction/sessions/7/decision', () => {
        attempt += 1;
        if (attempt === 1) {
          return apiError(400, "proposedBid non puo' essere inferiore a currentPrice", '/decision');
        }
        if (attempt === 2) {
          return apiError(400, 'proposedBid supera il budget residuo', '/decision');
        }
        return HttpResponse.json({
          action: 'OSSERVA',
          currentPlayerId: 11,
          currentPlayerName: 'Giocatore reale',
          currentRole: 'A',
          currentPrice: 5,
          proposedBid: 5,
          evaluatedBid: 5,
          remainingBudget: 121,
          budgetAfterBid: 116,
          priceZone: 'VALUE_ZONE',
          reasons: ['BUDGET_SAFE'],
          warnings: [],
        });
      }),
    );
    renderSession();

    const input = await screen.findByLabelText('Offerta proposta');
    await userEvent.clear(input);
    await userEvent.type(input, '4');
    await userEvent.click(screen.getByRole('button', { name: 'Valuta offerta' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('inferiore a currentPrice');

    await userEvent.clear(input);
    await userEvent.type(input, '999');
    await userEvent.click(screen.getByRole('button', { name: 'Valuta offerta' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('supera il budget residuo');

    await userEvent.clear(input);
    await userEvent.type(input, '5');
    await userEvent.click(screen.getByRole('button', { name: 'Valuta offerta' }));
    expect(await screen.findByText('OSSERVA')).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('spiega sessione assente, non autorizzata o backend irraggiungibile', async () => {
    server.use(
      http.get('/api/v1/auction/sessions/7/state', () =>
        apiError(404, 'Sessione asta non trovata', '/api/v1/auction/sessions/7/state'),
      ),
    );
    renderSession();

    expect(await screen.findByRole('alert')).toHaveTextContent('Sessione asta non trovata');
    expect(screen.getByRole('link', { name: 'Torna al launcher' })).toBeVisible();
  });

  it('chiude una sessione ACTIVE e rende lo stato non modificabile', async () => {
    let current = snapshot({ status: 'ACTIVE', playerStateInitialized: true, version: 1 });
    server.use(
      http.get('/api/v1/auction/sessions/7/state', () => HttpResponse.json(current)),
      http.post('/api/v1/auction/sessions/7/complete', () => {
        current = snapshot({
          status: 'COMPLETED',
          playerStateInitialized: true,
          version: 2,
        });
        return HttpResponse.json(current.session);
      }),
    );
    renderSession();

    await userEvent.click(await screen.findByRole('button', { name: 'Completa sessione' }));
    expect(await screen.findByRole('heading', { name: 'Asta completata' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Registra nomination' })).not.toBeInTheDocument();
  });
});
