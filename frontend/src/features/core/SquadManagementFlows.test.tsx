import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { SquadPage } from '@/pages/SquadPage';
import { PlayersPage } from '@/pages/PlayersPage';
import { server } from '@/test/server';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    status: 'authenticated',
    username: 'owner',
    isAdmin: false,
    getAuthorization: () => 'Basic owner',
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

const roles = {
  P: { current: 0, maximum: 3, remaining: 3 },
  D: { current: 0, maximum: 8, remaining: 8 },
  C: { current: 0, maximum: 8, remaining: 8 },
  A: { current: 0, maximum: 6, remaining: 6 },
};
const emptySquad = {
  id: 9,
  name: 'Rosa owner',
  season: '2026-2027',
  initialBudget: 1000,
  spentBudget: 0,
  remainingBudget: 1000,
  totalPlayers: 0,
  maximumPlayers: 25,
  roles,
  players: [],
};
const realPlayer = {
  id: 77,
  name: 'Giocatore Catalogo',
  club: 'Club Reale',
  clubSlug: 'club-reale',
  fantacalcioRole: 'A',
  gazzettaRole: null,
  fantamarktRole: null,
  sourcesCount: 2,
};
const renderPage = (page: React.ReactNode, route = '/squad') => {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>{page}</MemoryRouter>
    </QueryClientProvider>,
  );
};

afterEach(cleanup);

describe('D02.23 real squad flows', () => {
  it('crea una rosa vuota con i vincoli restituiti dalla lega', async () => {
    server.use(
      http.get('/api/v1/squad', () =>
        HttpResponse.json({ message: 'Nessuna rosa' }, { status: 404 }),
      ),
      http.get('/api/v1/league/configuration', () =>
        HttpResponse.json({
          id: 3,
          name: 'Lega reale',
          season: '2026-2027',
          teamsCount: 8,
          totalBudget: 1000,
          goalkeepersCount: 3,
          defendersCount: 8,
          midfieldersCount: 8,
          forwardsCount: 6,
          maxSubstitutions: 5,
          defenseModifier: true,
          captainBonus: false,
          voteSource: 'FANTACALCIO',
          active: true,
        }),
      ),
      http.post('/api/v1/squads', async ({ request }) => {
        expect(await request.json()).toMatchObject({
          leagueConfigurationId: 3,
          name: 'Mia rosa',
          active: true,
        });
        return HttpResponse.json({ ...emptySquad, name: 'Mia rosa' });
      }),
      http.get('/api/v1/squad-analysis', () =>
        HttpResponse.json({
          squadId: 9,
          squadName: 'Mia rosa',
          season: '2026-2027',
          totalPlayers: 0,
          maximumPlayers: 25,
          remainingSlots: 25,
          constraintsValid: true,
          squadComplete: false,
          summary: 'Rosa vuota',
          roles: [],
          globalWarnings: [],
        }),
      ),
    );
    renderPage(<SquadPage />);
    expect(await screen.findByText('Budget iniziale')).toBeVisible();
    await userEvent.type(screen.getByRole('textbox', { name: 'Nome rosa' }), 'Mia rosa');
    await userEvent.click(screen.getByRole('button', { name: 'Crea rosa vuota' }));
    expect(await screen.findByRole('heading', { name: 'Mia rosa' })).toBeVisible();
    expect(screen.getByText('0/25')).toBeVisible();
  });

  it('aggiunge, modifica il prezzo e rimuove un giocatore reale', async () => {
    const acquired = {
      ...emptySquad,
      spentBudget: 10,
      remainingBudget: 990,
      totalPlayers: 1,
      roles: { ...roles, A: { current: 1, maximum: 6, remaining: 5 } },
      players: [
        {
          id: 101,
          playerId: 77,
          playerName: realPlayer.name,
          club: realPlayer.club,
          role: 'A',
          purchasePrice: 10,
        },
      ],
    };
    server.use(
      http.get('/api/v1/squad', () => HttpResponse.json(emptySquad)),
      http.get('/api/v1/squad-analysis', () =>
        HttpResponse.json({
          squadId: 9,
          squadName: 'Rosa owner',
          season: '2026-2027',
          totalPlayers: 0,
          maximumPlayers: 25,
          remainingSlots: 25,
          constraintsValid: true,
          squadComplete: false,
          summary: 'Vuota',
          roles: [],
          globalWarnings: [],
        }),
      ),
      http.get('/api/v1/players', () =>
        HttpResponse.json({
          content: [realPlayer],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        }),
      ),
      http.post('/api/v1/squad/players', async ({ request }) => {
        expect(await request.json()).toEqual({ playerId: 77, purchasePrice: 10 });
        return HttpResponse.json(acquired);
      }),
      http.patch('/api/v1/squad/players/77', async ({ request }) => {
        expect(await request.json()).toEqual({ purchasePrice: 20 });
        return HttpResponse.json({
          ...acquired,
          spentBudget: 20,
          remainingBudget: 980,
          players: [{ ...acquired.players[0], purchasePrice: 20 }],
        });
      }),
      http.delete('/api/v1/squad/players/77', () => HttpResponse.json(emptySquad)),
    );
    renderPage(<SquadPage />);
    await userEvent.type(await screen.findByRole('textbox', { name: 'Cerca nel catalogo' }), 'Gi');
    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Giocatore' }), '77');
    await userEvent.clear(screen.getByRole('spinbutton', { name: 'Prezzo acquisto' }));
    await userEvent.type(screen.getByRole('spinbutton', { name: 'Prezzo acquisto' }), '10');
    await userEvent.click(screen.getByRole('button', { name: 'Aggiungi alla rosa' }));
    expect(await screen.findByText(realPlayer.name)).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Modifica prezzo' }));
    const edit = screen.getByRole('spinbutton', { name: `Prezzo ${realPlayer.name}` });
    await userEvent.clear(edit);
    await userEvent.type(edit, '20');
    await userEvent.click(screen.getByRole('button', { name: 'Salva' }));
    expect(await screen.findByText('980')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Rimuovi' }));
    expect(await screen.findByText('Nessun giocatore ancora inserito.')).toBeVisible();
  });

  it('acquista dal catalogo nella rosa attiva senza dati fittizi', async () => {
    server.use(
      http.get('/api/v1/players', () =>
        HttpResponse.json({
          content: [realPlayer],
          page: 0,
          size: 25,
          totalElements: 1,
          totalPages: 1,
        }),
      ),
      http.get('/api/v1/squad', () => HttpResponse.json(emptySquad)),
      http.post('/api/v1/squad/players', () =>
        HttpResponse.json({
          ...emptySquad,
          totalPlayers: 1,
          spentBudget: 15,
          remainingBudget: 985,
          players: [
            {
              id: 101,
              playerId: 77,
              playerName: realPlayer.name,
              club: realPlayer.club,
              role: 'A',
              purchasePrice: 15,
            },
          ],
        }),
      ),
    );
    renderPage(<PlayersPage />, '/players');
    const addButtons = await screen.findAllByRole('button', { name: /Aggiungi/ });
    await userEvent.click(addButtons[0]!);
    const price = screen.getByRole('spinbutton', { name: 'Prezzo acquisto' });
    await userEvent.clear(price);
    await userEvent.type(price, '15');
    await userEvent.click(screen.getByRole('button', { name: 'Conferma acquisto' }));
    expect(await screen.findAllByRole('button', { name: 'Già in rosa' })).not.toHaveLength(0);
  });
});
