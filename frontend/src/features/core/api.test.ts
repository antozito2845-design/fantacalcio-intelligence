import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { featureApi, playerPageSchema } from './api';

const emptyPage = { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 };

describe('featureApi F07-F10', () => {
  it('invia ricerca, ruolo, club e paginazione al catalogo', async () => {
    server.use(
      http.get('/api/v1/players', ({ request }) => {
        const url = new URL(request.url);
        expect(Object.fromEntries(url.searchParams)).toMatchObject({
          search: 'lautaro',
          role: 'A',
          club: 'inter',
          page: '2',
          size: '25',
        });
        return HttpResponse.json(emptyPage);
      }),
    );
    expect(
      (await featureApi.players({ search: 'lautaro', role: 'A', club: 'inter', page: 2, size: 25 }))
        .content,
    ).toEqual([]);
  });

  it('rifiuta un contratto catalogo malformato', () => {
    expect(() => playerPageSchema.parse({ content: 'fake' })).toThrow();
  });

  it('legge un dettaglio reale con sezioni nullable', async () => {
    server.use(
      http.get('/api/v1/players/7', () =>
        HttpResponse.json({
          id: 7,
          name: 'Profilo reale',
          club: 'Inter',
          clubSlug: 'inter',
          roles: { FANTACALCIO: 'A' },
          tacticalProfile: null,
          clubTactics: null,
          evaluation: null,
          statistics: [],
        }),
      ),
    );
    expect((await featureApi.player('7')).name).toBe('Profilo reale');
  });

  it('applica il filtro ruolo al ranking asta', async () => {
    server.use(
      http.get('/api/v1/auction/intelligence-rankings', ({ request }) => {
        expect(new URL(request.url).searchParams.get('role')).toBe('D');
        return HttpResponse.json({
          season: '2026-2027',
          budget: 1000,
          teams: 8,
          defenseModifier: true,
          role: 'D',
          club: null,
          maximumPrice: null,
          recommendation: null,
          excludeUnavailable: true,
          totalPlayers: 0,
          players: [],
        });
      }),
    );
    expect((await featureApi.ranking('D')).role).toBe('D');
  });

  it('carica strategia con warning reali', async () => {
    server.use(
      http.get('/api/v1/auction/strategy', () =>
        HttpResponse.json({
          season: '2026-2027',
          totalBudget: 1000,
          teams: 8,
          defenseModifier: true,
          totalRequiredPlayers: 25,
          totalPlannedSpending: 900,
          expectedRemainingBudget: 100,
          withinBudget: true,
          strategySummary: 'Bilanciata',
          roles: [],
          globalWarnings: ['Vincolo reale'],
        }),
      ),
    );
    expect((await featureApi.strategy()).globalWarnings).toEqual(['Vincolo reale']);
  });

  it('invia Basic Auth nelle letture rosa', async () => {
    server.use(
      http.get('/api/v1/squad', ({ request }) => {
        expect(request.headers.get('authorization')).toBe('Basic test');
        return HttpResponse.json({
          id: 1,
          name: 'Rosa',
          season: '2026-2027',
          initialBudget: 500,
          spentBudget: 0,
          remainingBudget: 500,
          totalPlayers: 0,
          maximumPlayers: 25,
          roles: {},
          players: [],
        });
      }),
    );
    expect((await featureApi.squad(() => 'Basic test')).id).toBe(1);
  });

  it('crea una sessione live con la rosa reale', async () => {
    server.use(
      http.post('/api/v1/auction/sessions', async ({ request }) => {
        expect(await request.json()).toEqual({ squadId: 9 });
        return HttpResponse.json({
          id: 4,
          squadId: 9,
          season: '2026-2027',
          leagueTeams: 8,
          totalBudget: 500,
          defenseModifier: true,
          status: 'CREATED',
          playerStateInitialized: false,
          currentPlayerId: null,
          currentPrice: null,
          version: 0,
          createdAt: '2026-08-22T10:00:00Z',
          updatedAt: '2026-08-22T10:00:00Z',
        });
      }),
    );
    expect((await featureApi.createSession(() => 'Basic test', 9)).id).toBe(4);
  });

  it('propaga auth e payload nel lifecycle completo della sessione live', async () => {
    const session = {
      id: 4,
      squadId: 9,
      season: '2026-2027',
      leagueTeams: 8,
      totalBudget: 500,
      defenseModifier: true,
      status: 'ACTIVE',
      playerStateInitialized: true,
      currentPlayerId: 7,
      currentPrice: 3,
      version: 2,
      createdAt: '2026-08-22T10:00:00Z',
      updatedAt: '2026-08-22T10:01:00Z',
    };
    server.use(
      http.post('/api/v1/auction/sessions/4/activate', ({ request }) => {
        expect(request.headers.get('authorization')).toBe('Basic test');
        return HttpResponse.json(session);
      }),
      http.post('/api/v1/auction/sessions/4/nomination', async ({ request }) => {
        expect(await request.json()).toEqual({ playerId: 7, currentPrice: 3 });
        return HttpResponse.json(session);
      }),
      http.post('/api/v1/auction/sessions/4/decision', async ({ request }) => {
        expect(await request.json()).toEqual({ proposedBid: 4 });
        return HttpResponse.json({
          action: 'RILANCIA',
          currentPlayerId: 7,
          currentPlayerName: 'Giocatore reale',
          currentRole: 'A',
          currentPrice: 3,
          proposedBid: 4,
          evaluatedBid: 4,
          remainingBudget: 100,
          budgetAfterBid: 96,
          priceZone: 'VALUE_ZONE',
          reasons: ['BUDGET_SAFE'],
          warnings: [],
        });
      }),
      http.post('/api/v1/auction/sessions/4/purchases', async ({ request }) => {
        expect(await request.json()).toEqual({
          playerId: 7,
          buyerType: 'USER',
          paidPrice: 4,
          idempotencyKey: 'test-key',
        });
        return HttpResponse.json({
          id: 12,
          sessionId: 4,
          playerId: 7,
          buyerType: 'USER',
          paidPrice: 4,
          idempotencyKey: 'test-key',
          createdAt: '2026-08-22T10:02:00Z',
        });
      }),
      http.post('/api/v1/auction/sessions/4/complete', () =>
        HttpResponse.json({
          ...session,
          status: 'COMPLETED',
          currentPlayerId: null,
          currentPrice: null,
        }),
      ),
    );

    expect((await featureApi.activateSession(() => 'Basic test', '4')).status).toBe('ACTIVE');
    expect((await featureApi.nominatePlayer(() => 'Basic test', '4', 7, 3)).currentPrice).toBe(3);
    expect((await featureApi.sessionDecision(() => 'Basic test', '4', 4)).action).toBe('RILANCIA');
    expect(
      (
        await featureApi.purchasePlayer(() => 'Basic test', '4', {
          playerId: 7,
          buyerType: 'USER',
          paidPrice: 4,
          idempotencyKey: 'test-key',
        })
      ).paidPrice,
    ).toBe(4);
    expect((await featureApi.completeSession(() => 'Basic test', '4')).status).toBe('COMPLETED');
  });

  it('crea una rosa vuota usando la configurazione lega reale', async () => {
    server.use(
      http.post('/api/v1/squads', async ({ request }) => {
        expect(request.headers.get('authorization')).toBe('Basic test');
        expect(await request.json()).toEqual({
          leagueConfigurationId: 3,
          name: 'Rosa personale',
          active: true,
        });
        return HttpResponse.json({
          id: 12,
          name: 'Rosa personale',
          season: '2026-2027',
          initialBudget: 500,
          spentBudget: 0,
          remainingBudget: 500,
          totalPlayers: 0,
          maximumPlayers: 25,
          roles: {},
          players: [],
        });
      }),
    );
    expect((await featureApi.createSquad(() => 'Basic test', 3, 'Rosa personale')).id).toBe(12);
  });

  it('non maschera un conflitto su mutazione rosa', async () => {
    server.use(
      http.post('/api/v1/squad/players', () =>
        HttpResponse.json({ message: 'Vincolo ruolo' }, { status: 409 }),
      ),
    );
    await expect(featureApi.addPlayer(() => 'Basic test', 7, 10)).rejects.toMatchObject({
      kind: 'conflict',
    });
  });

  it('modifica il prezzo di acquisto sulla rosa corrente', async () => {
    server.use(
      http.patch('/api/v1/squad/players/7', async ({ request }) => {
        expect(request.headers.get('authorization')).toBe('Basic test');
        expect(await request.json()).toEqual({ purchasePrice: 35 });
        return HttpResponse.json({
          id: 1,
          name: 'Rosa',
          season: '2026-2027',
          initialBudget: 500,
          spentBudget: 35,
          remainingBudget: 465,
          totalPlayers: 1,
          maximumPlayers: 25,
          roles: { A: { current: 1, maximum: 6, remaining: 5 } },
          players: [
            {
              id: 2,
              playerId: 7,
              playerName: 'Attaccante reale',
              club: 'Club',
              role: 'A',
              purchasePrice: 35,
            },
          ],
        });
      }),
    );
    expect((await featureApi.updatePlayerPrice(() => 'Basic test', 7, 35)).spentBudget).toBe(35);
  });
});
