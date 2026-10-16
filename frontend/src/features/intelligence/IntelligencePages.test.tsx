import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/bootstrap/App';
import { server } from '@/test/server';

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

const player = (id: number, name: string, role: string) => ({
  playerId: id,
  playerName: name,
  club: 'Club test',
  role,
  opponent: 'Avversario test',
  home: true,
  fixtureDifficulty: 42,
  selectionScore: 80,
  availabilityStatus: 'AVAILABLE',
  reason: 'Motivazione reale dal backend.',
});
const lineup = {
  season: '2026-2027',
  matchday: 1,
  squadId: 1,
  formation: '4-3-3',
  complete: true,
  selectedPlayers: 2,
  missingPlayers: 9,
  formationScore: 80,
  roles: { P: { required: 1, selected: 1, missing: 0 } },
  starters: [player(1, 'Titolare reale', 'P'), player(2, 'Attaccante reale', 'A')],
  bench: [player(3, 'Riserva reale', 'P')],
  maxSubstitutions: 3,
  usedSubstitutions: 0,
  remainingSubstitutions: 3,
  substitutions: [],
  warnings: ['Rosa incompleta'],
};
const arrival = (id: number, name: string, potential: number) => ({
  playerId: id,
  playerName: name,
  club: 'Club test',
  role: 'C',
  season: '2026-2027',
  previousClub: 'Club estero',
  previousLeague: 'Liga',
  previousCountryCode: 'ES',
  transferType: 'PERMANENT',
  adaptationScore: 70,
  potentialScore: potential,
  riskScore: 30,
  confidenceScore: 80,
  recommendation: 'MONITORARE',
  scoutingSummary: 'Analisi reale.',
  eligibilityStatus: 'ELIGIBLE',
  updatedAt: '2026-08-22T10:00:00Z',
});
beforeEach(() => {
  server.use(
    http.get('/api/v1/squad', () =>
      HttpResponse.json({
        id: 1,
        name: 'Rosa reale',
        season: '2026-2027',
        initialBudget: 500,
        spentBudget: 100,
        remainingBudget: 400,
        totalPlayers: 11,
        maximumPlayers: 25,
        roles: {},
        players: [],
      }),
    ),
    http.get('/api/v1/lineups/recommended', () => HttpResponse.json(lineup)),
    http.get('/api/v1/fixtures/matchday/:matchday', () =>
      HttpResponse.json([
        {
          fixtureId: 1,
          matchday: 1,
          homeClub: 'Casa reale',
          awayClub: 'Trasferta reale',
          homeDifficulty: 'FACILE',
          awayDifficulty: 'DIFFICILE',
        },
      ]),
    ),
    http.get('/api/v1/fixtures/club/:club/matchups', () =>
      HttpResponse.json([
        {
          fixtureId: 1,
          matchday: 1,
          kickoffAt: null,
          clubId: 1,
          club: 'Casa reale',
          opponentClubId: 2,
          opponent: 'Trasferta reale',
          home: true,
          status: 'SCHEDULED',
          roles: [
            {
              role: 'A',
              difficultyScore: 35,
              difficultyLevel: 'FACILE',
              evidenceTier: 'HIGH',
              evidenceCoverage: 90,
            },
            {
              role: 'C',
              difficultyScore: 60,
              difficultyLevel: 'MEDIA',
              evidenceTier: 'MEDIUM',
              evidenceCoverage: 75,
            },
            {
              role: 'P',
              difficultyScore: 78,
              difficultyLevel: 'DIFFICILE',
              evidenceTier: 'LOW',
              evidenceCoverage: 50,
            },
          ],
        },
      ]),
    ),
    http.get('/api/v1/seasons', () =>
      HttpResponse.json([
        { id: 1, code: '2026-2027', startYear: 2026, endYear: 2027, current: true },
      ]),
    ),
    http.get('/api/v1/clubs', () =>
      HttpResponse.json([
        {
          id: 1,
          name: 'Casa reale',
          shortName: 'CAS',
          slug: 'casa-reale',
          countryCode: 'IT',
          active: true,
        },
      ]),
    ),
    http.get('/api/v1/scouting/arrivals', () =>
      HttpResponse.json([arrival(1, 'Talento reale', 91), arrival(2, 'Prospetto reale', 75)]),
    ),
  );
});
afterEach(() => {
  cleanup();
  window.history.replaceState({}, '', '/');
});
describe('F11 Lineup', () => {
  it('renderizza titolari, panchina, modulo e reasoning reali', async () => {
    window.history.replaceState({}, '', '/lineup');
    render(<App />);
    expect((await screen.findAllByText('Titolare reale')).length).toBeGreaterThan(0);
    expect(screen.getByText('Riserva reale')).toBeVisible();
    expect(screen.getByText('4-3-3')).toBeVisible();
    expect(screen.getAllByText(/Motivazione reale/).length).toBeGreaterThan(0);
  });
  it('inoltra stagione, rosa e Basic Auth reali al motore', async () => {
    let target: URL | undefined;
    let authorization: string | null = null;
    server.use(
      http.get('/api/v1/lineups/recommended', ({ request }) => {
        target = new URL(request.url);
        authorization = request.headers.get('authorization');
        return HttpResponse.json(lineup);
      }),
    );
    window.history.replaceState({}, '', '/lineup');
    render(<App />);
    expect((await screen.findAllByText('Titolare reale')).length).toBeGreaterThan(0);
    expect(target?.searchParams.get('season')).toBe('2026-2027');
    expect(target?.searchParams.get('matchday')).toBe('1');
    expect(target?.searchParams.get('squadId')).toBe('1');
    expect(authorization).toBe('Basic test-only');
  });
  it('non va in crash quando le collezioni della risposta sono omesse', async () => {
    const partialLineup = { ...lineup } as Record<string, unknown>;
    ['roles', 'starters', 'bench', 'substitutions', 'warnings'].forEach((field) => {
      delete partialLineup[field];
    });
    server.use(http.get('/api/v1/lineups/recommended', () => HttpResponse.json(partialLineup)));
    window.history.replaceState({}, '', '/lineup');
    render(<App />);
    expect(
      await screen.findByRole('heading', { name: 'Nessuna formazione disponibile' }),
    ).toBeVisible();
    expect(screen.getByText(/Il motore non ha prodotto titolari/)).toBeVisible();
  });
  it('renderizza titolari senza crash con panchina e metadati omessi', async () => {
    const partialLineup = { ...lineup } as Record<string, unknown>;
    ['roles', 'bench', 'substitutions', 'warnings'].forEach((field) => {
      delete partialLineup[field];
    });
    server.use(http.get('/api/v1/lineups/recommended', () => HttpResponse.json(partialLineup)));
    window.history.replaceState({}, '', '/lineup');
    render(<App />);
    expect((await screen.findAllByText('Titolare reale')).length).toBeGreaterThan(0);
    expect(screen.getByText('Nessuna riserva disponibile.')).toBeVisible();
  });
  it('mostra empty state con motivi reali del backend', async () => {
    server.use(
      http.get('/api/v1/lineups/recommended', () =>
        HttpResponse.json({
          ...lineup,
          complete: false,
          starters: [],
          bench: [],
          selectedPlayers: 0,
          missingPlayers: 11,
          roles: {
            P: { required: 1, selected: 0, missing: 1 },
            D: { required: 4, selected: 0, missing: 4 },
            C: { required: 3, selected: 0, missing: 3 },
            A: { required: 3, selected: 0, missing: 3 },
          },
          warnings: ['Evidenza reale insufficiente'],
        }),
      ),
    );
    window.history.replaceState({}, '', '/lineup');
    render(<App />);
    expect(
      await screen.findByRole('heading', { name: 'Nessuna formazione disponibile' }),
    ).toBeVisible();
    expect(screen.getByText(/mancano 11 giocatori classificabili/)).toBeVisible();
    expect(
      screen.getByText(/Reparti scoperti: P \(1\) · D \(4\) · C \(3\) · A \(3\)/),
    ).toBeVisible();
    expect(screen.getByText('Evidenza reale insufficiente')).toBeVisible();
    expect(screen.getByRole('link', { name: /Completa la rosa/ })).toBeVisible();
  });
  it('distingue la rosa non riconosciuta dal motore', async () => {
    server.use(
      http.get('/api/v1/lineups/recommended', () =>
        HttpResponse.json(
          {
            timestamp: '2026-08-26T10:00:00Z',
            status: 404,
            error: 'Not Found',
            message: 'Rosa attiva non trovata: 1 per la stagione 2026/2027',
            path: '/api/v1/lineups/recommended',
          },
          { status: 404 },
        ),
      ),
    );
    window.history.replaceState({}, '', '/lineup');
    render(<App />);
    expect(await screen.findByRole('link', { name: 'Verifica la rosa' })).toBeVisible();
    expect(screen.getByText(/non riconosce la rosa per la stagione/)).toBeVisible();
  });
  it('distingue una risposta non conforme al contratto', async () => {
    server.use(
      http.get('/api/v1/lineups/recommended', () =>
        HttpResponse.json({ ...lineup, formationScore: 'alto' }),
      ),
    );
    window.history.replaceState({}, '', '/lineup');
    render(<App />);
    expect(
      await screen.findByText(/La risposta del backend non è conforme al contratto/),
    ).toBeVisible();
    expect(screen.getByRole('button', { name: 'Riprova' })).toBeEnabled();
  });
  it('mostra errore e retry', async () => {
    server.use(
      http.get('/api/v1/lineups/recommended', () => new HttpResponse(null, { status: 500 })),
    );
    window.history.replaceState({}, '', '/lineup');
    render(<App />);
    expect(await screen.findByText(/Errore interno del motore di calcolo/)).toBeVisible();
    expect(screen.getByRole('button', { name: 'Riprova' })).toBeEnabled();
  });
  it('non invoca il motore e invita a completare una rosa sotto gli undici', async () => {
    server.use(
      http.get('/api/v1/squad', () =>
        HttpResponse.json({
          id: 1,
          name: 'Rosa incompleta',
          season: '2026-2027',
          initialBudget: 500,
          spentBudget: 20,
          remainingBudget: 480,
          totalPlayers: 2,
          maximumPlayers: 25,
          roles: {},
          players: [],
        }),
      ),
    );
    window.history.replaceState({}, '', '/lineup');
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'Rosa incompleta' })).toBeVisible();
    expect(screen.getByText(/2\/11 inseriti/)).toBeVisible();
  });
});
describe('F12 Calendar', () => {
  it('renderizza fixture, difficoltà e naviga giornata', async () => {
    window.history.replaceState({}, '', '/calendar');
    render(<App />);
    expect((await screen.findAllByText('Casa reale')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('FACILE').length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Successiva' }));
    expect(screen.getByRole('combobox', { name: 'Giornata' })).toHaveValue('2');
  });
  it('carica matchup reale scegliendo il club', async () => {
    window.history.replaceState({}, '', '/calendar');
    render(<App />);
    const clubSelect = await screen.findByRole('combobox', { name: 'Analisi club' });
    await screen.findByRole('option', { name: 'Casa reale' });
    await userEvent.selectOptions(clubSelect, 'casa-reale');
    expect(await screen.findByText(/Evidenza HIGH/)).toBeVisible();
    expect(screen.getByText(/A · FACILE/)).toBeVisible();
    expect(screen.getByText(/C · MEDIA/)).toBeVisible();
    expect(screen.getByText(/P · DIFFICILE/)).toBeVisible();
    expect(screen.getByText('35/100')).toBeVisible();
    await userEvent.click(screen.getByText('Come leggere la difficoltà'));
    expect(screen.getByText(/media pesata diversa per ruolo/)).toBeVisible();
    expect(screen.getByText(/non è una motivazione generata/)).toBeVisible();
  });
  it('gestisce calendario vuoto', async () => {
    server.use(http.get('/api/v1/fixtures/matchday/:matchday', () => HttpResponse.json([])));
    window.history.replaceState({}, '', '/calendar');
    render(<App />);
    expect(await screen.findByText(/Nessuna fixture/)).toBeVisible();
  });
});
describe('F13 Scouting', () => {
  it('renderizza e ordina opportunità reali', async () => {
    window.history.replaceState({}, '', '/scouting');
    render(<App />);
    expect(await screen.findByText('Talento reale')).toBeVisible();
    expect(screen.getByText('Prospetto reale')).toBeVisible();
  });
  it('filtra per ricerca e mostra zero results', async () => {
    window.history.replaceState({}, '', '/scouting');
    render(<App />);
    const input = await screen.findByPlaceholderText('Nome giocatore');
    await userEvent.type(input, 'inesistente');
    expect(screen.getByText(/Nessuna opportunità/)).toBeVisible();
  });
  it('gestisce errore backend', async () => {
    server.use(
      http.get('/api/v1/scouting/arrivals', () => new HttpResponse(null, { status: 500 })),
    );
    window.history.replaceState({}, '', '/scouting');
    render(<App />);
    expect(await screen.findByText(/Scouting non disponibile/)).toBeVisible();
  });
});
