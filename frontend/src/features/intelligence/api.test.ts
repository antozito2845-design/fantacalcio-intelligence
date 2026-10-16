import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { AppError } from '@/api/errors/AppError';
import { intelligenceApi, lineupSchema } from './api';

const lineup = {
  season: '2026-2027',
  matchday: 1,
  squadId: 1,
  formation: '4-3-3',
  complete: true,
  selectedPlayers: 11,
  missingPlayers: 0,
  formationScore: 80,
  roles: {
    P: { required: 1, selected: 1, missing: 0 },
    D: { required: 4, selected: 4, missing: 0 },
    C: { required: 3, selected: 3, missing: 0 },
    A: { required: 3, selected: 3, missing: 0 },
  },
  starters: [
    {
      playerId: 1,
      playerName: 'Titolare reale',
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
  bench: [],
  maxSubstitutions: 3,
  usedSubstitutions: 0,
  remainingSubstitutions: 3,
  substitutions: [],
  warnings: [],
};

describe('intelligenceApi lineups', () => {
  it('inoltra stagione, giornata, rosa e Basic Auth al motore', async () => {
    let target: URL | undefined;
    let authorization: string | null = null;
    server.use(
      http.get('/api/v1/lineups/recommended', ({ request }) => {
        target = new URL(request.url);
        authorization = request.headers.get('authorization');
        return HttpResponse.json(lineup);
      }),
    );
    const data = await intelligenceApi.lineup(3, '2026/2027', 42, () => 'Basic test-only');
    expect(data.season).toBe('2026-2027');
    expect(target?.searchParams.get('season')).toBe('2026/2027');
    expect(target?.searchParams.get('matchday')).toBe('3');
    expect(target?.searchParams.get('squadId')).toBe('42');
    expect(authorization).toBe('Basic test-only');
  });

  it('normalizza a null i segnali reali omessi', async () => {
    const partialPlayer = { ...lineup.starters[0] } as Record<string, unknown>;
    ['fixtureDifficulty', 'selectionScore', 'availabilityStatus'].forEach((field) => {
      delete partialPlayer[field];
    });
    server.use(
      http.get('/api/v1/lineups/recommended', () =>
        HttpResponse.json({ ...lineup, starters: [partialPlayer] }),
      ),
    );
    const data = await intelligenceApi.lineup(1, '2026/2027', 1);
    const starter = data.starters[0];
    expect(starter?.selectionScore).toBeNull();
    expect(starter?.fixtureDifficulty).toBeNull();
    expect(starter?.availabilityStatus).toBeNull();
  });

  it('classifica come invalid-response una risposta non conforme al contratto', async () => {
    server.use(
      http.get('/api/v1/lineups/recommended', () => HttpResponse.json({ season: '2026-2027' })),
    );
    await expect(intelligenceApi.lineup(1)).rejects.toBeInstanceOf(AppError);
    await expect(intelligenceApi.lineup(1)).rejects.toMatchObject({
      kind: 'invalid-response',
    });
  });

  it('rifiuta un contratto lineup malformato', () => {
    expect(() => lineupSchema.parse({ season: '2026-2027' })).toThrow();
  });
});
