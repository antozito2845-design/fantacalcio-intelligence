import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '@/test/server';
import { AdminPage } from '@/pages/AdminPage';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    status: 'authenticated',
    username: 'admin',
    isAdmin: true,
    getAuthorization: () => 'Basic YWRtaW46dGVzdA==',
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));
afterEach(cleanup);
const refresh = {
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
};
const catalog = {
  season: '2026-2027',
  fantacalcioBatchId: 7,
  gazzettaBatchId: 8,
  fantamarktBatchId: null,
  totalFantacalcioPlayers: 10,
  confirmedPlayers: 9,
  unresolvedPlayers: 1,
  activeCatalogPlayers: 9,
  playersWithoutFantacalcioRole: 0,
  playersWithoutActiveRegistration: 0,
  pendingTransferClaims: 0,
  officialSerieAExits: 0,
  missingFromFantacalcio: 0,
  qualityPercentage: 90,
  readinessStatus: 'WARNING',
  blockers: [],
  warnings: ['Una identità da verificare'],
  status: 'READY',
  totalOperationalPlayers: 9,
  totalClubs: 1,
  playersByRole: { P: 1, D: 3, C: 3, A: 2 },
  errorCount: 0,
  warningCount: 1,
  issues: [
    {
      severity: 'WARNING',
      code: 'UNRESOLVED_PREVIEW_IDENTITIES',
      message: 'Una identità non è confermata',
      playerId: null,
      fantacalcioExternalId: null,
      playerName: null,
      club: null,
      details: { count: 1 },
    },
  ],
  clubSummaries: [{ clubId: 1, club: 'Club test', totalPlayers: 9, P: 1, D: 3, C: 3, A: 2 }],
  dataFreshness: {
    season: '2026-2027',
    evaluatedAt: '2026-08-22T10:00:00Z',
    overallStatus: 'READY',
    sources: [],
    blockers: [],
    warnings: [],
  },
};
function handlers() {
  server.use(
    http.get('/api/v1/seasons', () =>
      HttpResponse.json([
        { id: 1, code: '2026-2027', startYear: 2026, endYear: 2027, current: true },
      ]),
    ),
    http.get('/api/v1/admin/data-freshness', () =>
      HttpResponse.json({
        season: '2026-2027',
        evaluatedAt: '2026-08-22T10:00:00Z',
        overallStatus: 'READY',
        sources: [
          {
            source: 'FANTACALCIO',
            batchId: 7,
            observedAt: '2026-08-22T09:00:00Z',
            ageHours: 1,
            freshMaxHours: 12,
            staleAfterHours: 24,
            requiredForRefresh: true,
            status: 'FRESH',
          },
        ],
        blockers: [],
        warnings: [],
      }),
    ),
    http.get('/api/v1/admin/imports', () =>
      HttpResponse.json({
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
            completedAt: null,
            reconciliationStartedAt: null,
            reconciliationCompletedAt: null,
            errorMessage: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    ),
    http.get('/api/v1/admin/imports/:id', ({ params }) =>
      HttpResponse.json({
        batch: {
          id: Number(params.id),
          source: 'FANTACALCIO',
          season: '2026-2027',
          filename: 'listone.csv',
          status: 'COMPLETED',
          totalRows: 10,
          validRows: 9,
          invalidRows: 1,
          reconciledRows: 9,
          startedAt: '2026-08-22T10:00:00Z',
          completedAt: null,
          reconciliationStartedAt: null,
          reconciliationCompletedAt: null,
          errorMessage: null,
        },
        invalidRows: [
          {
            id: 1,
            rowNumber: 4,
            externalId: 'ext-4',
            externalName: 'Nome test',
            clubName: 'Club test',
            fantasyRole: 'C',
            validationError: 'Ruolo non valido',
            rawData: null,
          },
        ],
        errorPage: 0,
        errorSize: 50,
        totalInvalidRows: 1,
        totalErrorPages: 1,
      }),
    ),
    http.get('/api/v1/admin/catalog/readiness', () => HttpResponse.json(catalog)),
    http.get('/api/v1/admin/data-refresh/latest', () => HttpResponse.json(refresh)),
    http.get('/api/v1/admin/automation/latest', () =>
      HttpResponse.json({
        id: 3,
        season: '2026-2027',
        triggerType: 'SCHEDULED',
        status: 'COMPLETED',
        stage: 'DONE',
        listoneRunId: 1,
        ingestionRunCount: 3,
        refreshRunId: 4,
        startedAt: '2026-08-22T09:00:00Z',
        lastHeartbeatAt: null,
        completedAt: '2026-08-22T09:05:00Z',
        errorMessage: null,
      }),
    ),
    http.post('/api/v1/admin/data-refresh/run', () => HttpResponse.json(refresh)),
  );
}
function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AdminPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
describe('F14 admin page', () => {
  it('mostra stato, fonti e storico reali', async () => {
    handlers();
    renderPage();
    expect((await screen.findAllByText('FANTACALCIO')).length).toBe(2);
    expect(screen.getByText('1 batch restituiti dal backend.')).toBeVisible();
    expect(screen.getByRole('button', { name: /Avvia refresh/ })).toBeEnabled();
    expect(screen.getByRole('heading', { name: 'Catalog readiness' })).toBeVisible();
    expect(screen.getByText('Una identità non è confermata')).toBeVisible();
  });
  it('carica il dettaglio import solo su richiesta', async () => {
    handlers();
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Dettagli' }));
    expect(await screen.findByText('Ruolo non valido')).toBeVisible();
    expect(screen.getByText('Diagnostica batch #7')).toBeVisible();
  });
  it('rende lo stato catalogo bloccato senza reinterpretarlo', async () => {
    handlers();
    server.use(
      http.get('/api/v1/admin/catalog/readiness', () =>
        HttpResponse.json({
          ...catalog,
          readinessStatus: 'BLOCKED',
          status: 'BLOCKED',
          blockers: ['Catalogo incompleto'],
          warnings: [],
          errorCount: 1,
          warningCount: 0,
        }),
      ),
    );
    renderPage();
    expect((await screen.findAllByText('BLOCKED')).length).toBeGreaterThan(0);
    expect(screen.getByText('Catalogo incompleto')).toBeVisible();
  });
  it('conferma e completa il refresh impedendo il doppio invio', async () => {
    handlers();
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: /Avvia refresh/ }));
    const confirm = screen.getByRole('button', { name: 'Conferma' });
    await userEvent.click(confirm);
    expect(await screen.findByText(/Refresh completato/)).toBeVisible();
  });
  it('mostra un errore backend contestuale', async () => {
    handlers();
    server.use(
      http.get('/api/v1/admin/data-freshness', () =>
        HttpResponse.json({ message: 'Servizio non disponibile' }, { status: 503 }),
      ),
    );
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent('503');
  });
});
