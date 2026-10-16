import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { adminApi } from './api';
import { server } from '@/test/server';

const auth = () => 'Basic dGVzdDp0ZXN0';
describe('adminApi', () => {
  it('legge lo storico import protetto e valida il contratto', async () => {
    server.use(
      http.get('/api/v1/admin/imports', ({ request }) => {
        expect(request.headers.get('authorization')).toBe(auth());
        return HttpResponse.json({
          content: [
            {
              id: 7,
              source: 'FANTACALCIO',
              season: '2026-2027',
              filename: 'listone.csv',
              status: 'COMPLETED',
              totalRows: 10,
              validRows: 9,
              invalidRows: 1,
              reconciledRows: 9,
              startedAt: '2026-08-22T10:00:00Z',
              completedAt: '2026-08-22T10:01:00Z',
              reconciliationStartedAt: null,
              reconciliationCompletedAt: null,
              errorMessage: null,
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        });
      }),
    );
    const result = await adminApi.imports(auth, '2026-2027');
    expect(result.content[0]?.source).toBe('FANTACALCIO');
  });
  it('esegue il refresh con POST e restituisce lo stato reale', async () => {
    server.use(
      http.post('/api/v1/admin/data-refresh/run', () =>
        HttpResponse.json({
          id: 4,
          season: '2026-2027',
          triggerType: 'MANUAL',
          status: 'COMPLETED',
          sourceFreshnessStatus: 'READY',
          sourceFreshnessCheckedAt: '2026-08-22T10:00:00Z',
          reconciliationMatchedPlayers: 500,
          generatedEvaluations: 480,
          generatedClubRatings: 20,
          startedAt: '2026-08-22T10:00:00Z',
          completedAt: '2026-08-22T10:02:00Z',
          errorMessage: null,
        }),
      ),
    );
    expect((await adminApi.runRefresh(auth, '2026-2027')).status).toBe('COMPLETED');
  });
  it('rifiuta payload non conformi', async () => {
    server.use(
      http.get('/api/v1/admin/data-freshness', () => HttpResponse.json({ overallStatus: 42 })),
    );
    await expect(adminApi.freshness(auth, '2026-2027')).rejects.toMatchObject({
      kind: 'invalid-response',
    });
  });
  it('rifiuta catalog readiness malformata', async () => {
    server.use(
      http.get('/api/v1/admin/catalog/readiness', () => HttpResponse.json({ readinessStatus: 42 })),
    );
    await expect(adminApi.catalogReadiness(auth, '2026-2027')).rejects.toMatchObject({
      kind: 'invalid-response',
    });
  });
});
