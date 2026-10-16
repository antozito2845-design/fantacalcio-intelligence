import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { App } from '@/app/bootstrap/App';
import { server } from '@/test/server';

const catalogPlayer = (id: number, name: string) => ({
  id,
  name,
  club: 'Club reale',
  clubSlug: 'club-reale',
  fantacalcioRole: 'A',
  gazzettaRole: null,
  fantamarktRole: null,
  sourcesCount: 1,
});

const detailPlayer = (id: number, name: string, role: string, statistics: object[]) => ({
  id,
  name,
  club: 'Club reale',
  clubSlug: 'club-reale',
  roles: { FANTACALCIO: role },
  tacticalProfile: null,
  clubTactics: null,
  evaluation: null,
  statistics,
});

afterEach(() => {
  cleanup();
  window.history.replaceState({}, '', '/');
});

describe('catalogo giocatori', () => {
  it('naviga dalla prima alla seconda pagina e torna alla prima', async () => {
    const requestedPages: string[] = [];
    server.use(
      http.get('/api/v1/players', ({ request }) => {
        const page = new URL(request.url).searchParams.get('page') ?? '0';
        requestedPages.push(page);
        return HttpResponse.json({
          content: [
            catalogPlayer(Number(page) + 1, page === '0' ? 'Prima pagina' : 'Seconda pagina'),
          ],
          page: Number(page),
          size: 25,
          totalElements: 50,
          totalPages: 2,
        });
      }),
    );
    window.history.replaceState({}, '', '/players');
    render(<App />);

    expect((await screen.findAllByText('Prima pagina')).length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Successiva' }));
    expect((await screen.findAllByText('Seconda pagina')).length).toBeGreaterThan(0);
    expect(screen.getByText('2 / 2')).toBeVisible();
    await waitFor(() => expect(window.location.search).toContain('page=1'));

    await userEvent.click(screen.getByRole('button', { name: 'Precedente' }));
    expect((await screen.findAllByText('Prima pagina')).length).toBeGreaterThan(0);
    expect(requestedPages).toEqual(expect.arrayContaining(['0', '1']));
  });
});

describe('storico giocatore', () => {
  it('renderizza tutte le stagioni storiche restituite dal contratto backend', async () => {
    server.use(
      http.get('/api/v1/players/47', () =>
        HttpResponse.json({
          id: 47,
          name: 'Giocatore storico reale',
          club: 'Club reale',
          clubSlug: 'club-reale',
          roles: { FANTACALCIO: 'A' },
          tacticalProfile: null,
          clubTactics: null,
          evaluation: null,
          statistics: [
            {
              season: '2025/2026',
              source: 'FBREF',
              appearances: 30,
              starts: 24,
              minutesPlayed: 2200,
              averageRating: 6.4,
              fantasyAverage: null,
              goals: 8,
              assists: 4,
            },
            {
              season: '2024/2025',
              source: 'FBREF',
              appearances: 28,
              starts: 20,
              minutesPlayed: 1900,
              averageRating: 6.2,
              fantasyAverage: null,
              goals: 6,
              assists: 3,
            },
          ],
        }),
      ),
    );
    window.history.replaceState({}, '', '/players/47');
    render(<App />);

    expect(await screen.findByText('Giocatore storico reale')).toBeVisible();
    expect(screen.getByText('FANTACALCIO: A')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Indietro' })).toBeVisible();
    expect(screen.getByRole('columnheader', { name: /^Gol$/ })).toBeVisible();
    expect(screen.getByRole('columnheader', { name: /^Assist$/ })).toBeVisible();
    expect(screen.queryByRole('columnheader', { name: /^Gol subiti$/ })).not.toBeInTheDocument();
    expect(screen.getByText('2025/2026')).toBeVisible();
    expect(screen.getByText('2024/2025')).toBeVisible();
  });
});

describe('errori dettaglio giocatore corrente', () => {
  it('distingue un giocatore inesistente con stato 404', async () => {
    server.use(http.get('/api/v1/players/404', () => new HttpResponse(null, { status: 404 })));
    window.history.replaceState({}, '', '/players/404');
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Giocatore non trovato' })).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Il giocatore richiesto non esiste nel catalogo corrente.',
    );
    expect(screen.queryByRole('button', { name: 'Riprova' })).not.toBeInTheDocument();
  });

  it('mostra un errore recuperabile per un guasto del servizio', async () => {
    server.use(http.get('/api/v1/players/500', () => new HttpResponse(null, { status: 500 })));
    window.history.replaceState({}, '', '/players/500');
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Profilo non disponibile' })).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Il servizio non ha potuto caricare il profilo. Riprova.',
    );
    expect(screen.getByRole('button', { name: 'Riprova' })).toBeVisible();
  });
});

describe('statistiche ruolo portiere', () => {
  it('mostra le metriche goalkeeper reali e la fonte per ogni stagione', async () => {
    server.use(
      http.get('/api/v1/players/88', () =>
        HttpResponse.json(
          detailPlayer(88, 'Portiere reale', 'P', [
            {
              season: '2025/2026',
              source: 'FANTACALCIO',
              sourceExternalId: 'FANTACALCIO-88',
              appearances: 31,
              starts: 31,
              minutesPlayed: 2790,
              goalsConceded: 28,
              saves: 92,
              penaltiesSaved: 3,
              cleanSheets: 12,
              goals: 0,
              assists: 0,
            },
          ]),
        ),
      ),
    );
    window.history.replaceState({}, '', '/players/88');
    render(<App />);

    expect(await screen.findByText('Portiere reale')).toBeVisible();
    expect(screen.getByRole('columnheader', { name: /^Gol subiti$/ })).toBeVisible();
    expect(screen.getByRole('columnheader', { name: /^Parate effettuate$/ })).toBeVisible();
    expect(screen.getByRole('columnheader', { name: /^Rigori parati$/ })).toBeVisible();
    expect(
      screen.getByRole('columnheader', { name: /^Porte inviolate \/ Clean sheet$/ }),
    ).toBeVisible();
    expect(screen.queryByRole('columnheader', { name: /^Gol$/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: /^Assist$/ })).not.toBeInTheDocument();

    const row = screen.getByRole('row', { name: /2025\/2026/ });
    expect(row).toHaveTextContent('FANTACALCIO · FANTACALCIO-88');
    expect(row).toHaveTextContent('31');
    expect(row).toHaveTextContent('2790');
    expect(row).toHaveTextContent('28');
    expect(row).toHaveTextContent('92');
    expect(row).toHaveTextContent('3');
    expect(row).toHaveTextContent('12');
  });

  it('segnala ogni metrica goalkeeper nulla senza trasformarla in zero', async () => {
    server.use(
      http.get('/api/v1/players/89', () =>
        HttpResponse.json(
          detailPlayer(89, 'Portiere senza dati', 'P', [
            {
              season: '2025/2026',
              source: 'FANTACALCIO',
              sourceExternalId: null,
              appearances: null,
              minutesPlayed: null,
              goalsConceded: null,
              saves: null,
              penaltiesSaved: null,
              cleanSheets: null,
            },
          ]),
        ),
      ),
    );
    window.history.replaceState({}, '', '/players/89');
    render(<App />);

    expect(await screen.findByText('Portiere senza dati')).toBeVisible();
    const row = screen.getByRole('row', { name: /2025\/2026/ });
    expect(within(row).getAllByText('Dato non disponibile')).toHaveLength(6);
    expect(row).not.toHaveTextContent(/\b0\b/);
  });
});
