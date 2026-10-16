import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { createAppQueryClient } from '@/app/providers/queryClient';
import { RealDashboardPage } from './RealDashboardPage';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    status: 'authenticated',
    username: 'utente-test',
    isAdmin: false,
    getAuthorization: () => 'Basic test-only',
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

const squad = {
  id: 7,
  name: 'Rosa reale dashboard',
  season: '2026-2027',
  initialBudget: 1000,
  spentBudget: 100,
  remainingBudget: 900,
  totalPlayers: 25,
  maximumPlayers: 25,
  roles: {},
  players: [],
};

const lineup = {
  season: '2026-2027',
  matchday: 1,
  squadId: 7,
  formation: '4-3-3',
  complete: true,
  selectedPlayers: 11,
  missingPlayers: 0,
  formationScore: 80,
  starters: [
    {
      playerId: 1,
      playerName: 'Titolare reale dashboard',
      club: 'Club test',
      role: 'P',
      opponent: 'Avversario test',
      home: true,
      fixtureDifficulty: 42,
      selectionScore: 80,
      availabilityStatus: 'AVAILABLE',
      reason: 'Motivazione reale dal backend.',
    },
  ],
  warnings: [],
};

function renderPage() {
  const queryClient = createAppQueryClient();
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RealDashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  server.use(
    http.get('/api/v1/system/status', () =>
      HttpResponse.json({
        status: 'UP',
        application: 'FI',
        version: '1.0',
        environment: 'test',
        checkedAt: '2026-08-26T10:00:00Z',
      }),
    ),
    http.get('/api/v1/seasons', () =>
      HttpResponse.json([
        { id: 1, code: '2026-2027', startYear: 2026, endYear: 2027, current: true },
      ]),
    ),
    http.get('/api/v1/clubs', () => HttpResponse.json([])),
    http.get('/api/v1/players', () =>
      HttpResponse.json({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 }),
    ),
    http.get('/api/v1/auction/intelligence-rankings', () =>
      HttpResponse.json({
        season: '2026-2027',
        budget: 500,
        teams: 8,
        defenseModifier: true,
        role: null,
        club: null,
        maximumPrice: null,
        recommendation: null,
        excludeUnavailable: true,
        totalPlayers: 0,
        players: [],
      }),
    ),
    http.get('/api/v1/fixtures/matchday/1', () => HttpResponse.json([])),
    http.get('/api/v1/scouting/arrivals', () => HttpResponse.json([])),
    http.get('/api/v1/squad', () => HttpResponse.json(squad)),
  );
});

afterEach(() => {
  cleanup();
});

describe('Dashboard lineup widget D02.25', () => {
  it('inoltra stagione della rosa e Basic Auth al motore', async () => {
    let target: URL | undefined;
    let authorization: string | null = null;
    server.use(
      http.get('/api/v1/lineups/recommended', ({ request }) => {
        target = new URL(request.url);
        authorization = request.headers.get('authorization');
        return HttpResponse.json(lineup);
      }),
    );
    renderPage();
    expect(await screen.findByText(/Titolare reale dashboard/)).toBeVisible();
    expect(target?.searchParams.get('season')).toBe('2026-2027');
    expect(target?.searchParams.get('matchday')).toBe('1');
    expect(target?.searchParams.get('squadId')).toBe('7');
    expect(authorization).toBe('Basic test-only');
  });

  it('mostra la causa reale quando il motore fallisce', async () => {
    server.use(
      http.get('/api/v1/lineups/recommended', () => new HttpResponse(null, { status: 500 })),
    );
    renderPage();
    expect(
      // The app retries server errors twice (default backoff), so the error
      // state appears after the retries complete.
      await screen.findByText('Errore interno del motore di calcolo.', {}, { timeout: 6000 }),
    ).toBeVisible();
  });
});
