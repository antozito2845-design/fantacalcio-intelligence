import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/bootstrap/App';
import { server } from '@/test/server';
beforeEach(() => {
  server.use(
    http.get('/api/v1/system/status', () =>
      HttpResponse.json({
        status: 'UP',
        application: 'FI',
        version: '1.0',
        environment: 'test',
        checkedAt: '2026-08-22T10:00:00Z',
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
    http.get('/api/v1/lineups/recommended', () =>
      HttpResponse.json({
        season: '2026-2027',
        matchday: 1,
        squadId: null,
        formation: '3-4-3',
        complete: false,
        selectedPlayers: 0,
        missingPlayers: 11,
        formationScore: 0,
        starters: [],
        warnings: [],
      }),
    ),
    http.get('/api/v1/scouting/arrivals', () => HttpResponse.json([])),
  );
});
afterEach(() => {
  cleanup();
  window.history.replaceState({}, '', '/');
});
describe('App shell e router', () => {
  it('renderizza la dashboard foundation con landmark e navigazione attiva', () => {
    render(<App />);
    expect(screen.getByRole('main')).toBeVisible();
    expect(screen.getByRole('navigation', { name: 'Navigazione principale' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('heading', { name: 'Intelligence overview' })).toBeVisible();
  });
  it('renderizza metriche reali da MSW senza valori demo F03', async () => {
    server.use(
      http.get('/api/v1/clubs', () =>
        HttpResponse.json([
          {
            id: 1,
            name: 'Club reale',
            shortName: 'CR',
            slug: 'club-reale',
            countryCode: 'IT',
            active: true,
          },
        ]),
      ),
      http.get('/api/v1/players', () =>
        HttpResponse.json({ content: [], page: 0, size: 5, totalElements: 612, totalPages: 123 }),
      ),
    );
    render(<App />);
    expect(await screen.findByText('612')).toBeVisible();
    expect(screen.getByText('2026-2027')).toBeVisible();
    expect(screen.queryByText('1.783')).not.toBeInTheDocument();
    expect(screen.queryByText('498')).not.toBeInTheDocument();
  });
  it('mantiene la dashboard utilizzabile in caso di fallimento parziale', async () => {
    server.use(
      http.get('/api/v1/clubs', () => new HttpResponse(null, { status: 500 })),
      http.get('/api/v1/players', () =>
        HttpResponse.json({ content: [], page: 0, size: 5, totalElements: 321, totalPages: 65 }),
      ),
    );
    render(<App />);
    expect(await screen.findByText('321')).toBeVisible();
    expect(screen.getByRole('heading', { name: 'Intelligence overview' })).toBeVisible();
  });
  it('mostra stato backend offline senza far collassare gli altri widget', async () => {
    server.use(http.get('/api/v1/system/status', () => HttpResponse.error()));
    render(<App />);
    expect(await screen.findAllByText('Backend offline', {}, { timeout: 5_000 })).not.toHaveLength(
      0,
    );
    expect(screen.getByRole('heading', { name: 'Auction intelligence' })).toBeVisible();
  });
  it('rende navigabili le sezioni non ancora disponibili', async () => {
    render(<App />);
    for (const label of ['Analisi', 'Statistiche', 'Comparatore', 'Market', 'Impostazioni']) {
      await userEvent.click(screen.getByRole('link', { name: label }));
      expect(await screen.findByRole('heading', { name: label })).toBeVisible();
      expect(screen.getByText('Questa sezione non è ancora disponibile.')).toBeVisible();
      await userEvent.click(screen.getByRole('link', { name: 'Dashboard' }));
    }
  }, 10_000);
  it('renderizza /design-system', () => {
    window.history.replaceState({}, '', '/design-system');
    render(<App />);
    return expect(
      screen.findByRole('heading', { name: 'Design system preview' }),
    ).resolves.toBeVisible();
  });
  it('gestisce la 404 e il ritorno', async () => {
    window.history.replaceState({}, '', '/non-esiste');
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Pagina non trovata' })).toBeVisible();
    await userEvent.click(screen.getByRole('link', { name: 'Torna alla dashboard foundation' }));
    expect(screen.getByRole('heading', { name: 'Intelligence overview' })).toBeVisible();
  });
  it('usa dark come default e consente light', async () => {
    render(<App />);
    expect(document.documentElement.dataset.theme).not.toBe('light');
    await userEvent.click(screen.getByRole('button', { name: 'Attiva tema chiaro' }));
    expect(document.documentElement.dataset.theme).toBe('light');
  });
  it('protegge il profilo e reindirizza al login quando la sessione è assente', async () => {
    window.history.replaceState({}, '', '/profile');
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'Accedi al workspace' })).toBeVisible();
  });
  it('porta l’utente autenticato alla Live Auction e spiega la rosa mancante', async () => {
    server.use(
      http.get('/api/v1/squads', () => HttpResponse.json([])),
      http.get('/api/v1/admin/catalog/readiness', () => new HttpResponse(null, { status: 403 })),
      http.get('/api/v1/squad', () => new HttpResponse(null, { status: 404 })),
    );
    window.history.replaceState({}, '', '/auction/live');
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'Accedi al workspace' })).toBeVisible();
    await userEvent.type(screen.getByRole('textbox', { name: 'Username' }), 'utente');
    await userEvent.type(screen.getByLabelText('Password', { selector: 'input' }), 'password');
    await userEvent.click(screen.getByRole('button', { name: 'Accedi' }));
    expect(await screen.findByRole('heading', { name: 'Live Auction' })).toBeVisible();
    expect(await screen.findByText(/Crea o compila prima una rosa personale/)).toBeVisible();
  });
  it('protegge la route admin quando la sessione è assente', async () => {
    window.history.replaceState({}, '', '/admin/status');
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'Accedi al workspace' })).toBeVisible();
  });
  it('mostra un errore specifico per credenziali non valide', async () => {
    server.use(http.get('/api/v1/squads', () => new HttpResponse(null, { status: 401 })));
    window.history.replaceState({}, '', '/login');
    render(<App />);
    await userEvent.type(screen.getByRole('textbox', { name: 'Username' }), 'invalid');
    await userEvent.type(screen.getByLabelText('Password', { selector: 'input' }), 'invalid');
    await userEvent.click(screen.getByRole('button', { name: 'Accedi' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Credenziali non valide.');
  });
});
