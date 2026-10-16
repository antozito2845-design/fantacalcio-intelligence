import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/server';
import { AppError } from '@/api/errors/AppError';
import { dashboardApi } from './dashboardApi';

describe('dashboardApi lineup contract', () => {
  it('accetta i segnali reali assenti (null) e inoltra Basic Auth', async () => {
    let authorization: string | null = null;
    server.use(
      http.get('/api/v1/lineups/recommended', ({ request }) => {
        authorization = request.headers.get('authorization');
        return HttpResponse.json({
          season: '2026-2027',
          matchday: 1,
          squadId: 1,
          formation: '4-3-3',
          complete: true,
          selectedPlayers: 11,
          missingPlayers: 0,
          formationScore: 80,
          starters: [
            {
              playerId: 1,
              playerName: 'Titolare reale',
              club: 'Club test',
              role: 'P',
              opponent: 'Avversario test',
              home: true,
              fixtureDifficulty: null,
              selectionScore: null,
              availabilityStatus: 'UNKNOWN',
              reason: 'Evidenza reale dal backend.',
            },
          ],
          warnings: [],
        });
      }),
    );
    const data = await dashboardApi.lineup('2026-2027', 1, 1, () => 'Basic test-only');
    const starter = data.starters[0];
    expect(starter?.selectionScore).toBeNull();
    expect(starter?.fixtureDifficulty).toBeNull();
    expect(authorization).toBe('Basic test-only');
  });

  it('classifica come invalid-response una risposta non conforme al contratto', async () => {
    server.use(
      http.get('/api/v1/lineups/recommended', () => HttpResponse.json({ season: '2026-2027' })),
    );
    await expect(dashboardApi.lineup('2026-2027', 1, 1)).rejects.toBeInstanceOf(AppError);
    await expect(dashboardApi.lineup('2026-2027', 1, 1)).rejects.toMatchObject({
      kind: 'invalid-response',
    });
  });
});
