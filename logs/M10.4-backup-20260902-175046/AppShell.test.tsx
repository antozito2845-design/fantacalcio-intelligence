import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it } from 'vitest';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { AuthProvider } from '@/auth/AuthProvider';
import { server } from '@/test/server';
import { AppShell } from './AppShell';

afterEach(cleanup);

function renderShell() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <MemoryRouter>
          <AppShell>
            <h1>Contenuto</h1>
          </AppShell>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}
describe('AppShell mobile navigation', () => {
  it('non mostra il riquadro tecnico della foundation nella sidebar', () => {
    renderShell();
    expect(screen.queryByText('Foundation F03')).not.toBeInTheDocument();
    expect(screen.queryByText('Design system preview')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Notifiche' })).not.toBeInTheDocument();
  });

  it('apre e chiude il menu accessibile', () => {
    renderShell();
    const open = screen.getByRole('button', { name: 'Apri menu', hidden: true });
    expect(open).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(open);
    expect(screen.getByRole('button', { name: 'Chiudi menu', hidden: true })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.getByRole('button', { name: 'Apri menu', hidden: true })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
  });

  it('espone route, label accessibili e tooltip per ogni voce della sidebar', () => {
    renderShell();
    const routes = {
      Dashboard: '/',
      Giocatori: '/players',
      Asta: '/auction',
      Rosa: '/squad',
      Formazione: '/lineup',
      Calendario: '/calendar',
      Scouting: '/scouting',
      Analisi: '/analysis',
      Statistiche: '/statistics',
      Comparatore: '/comparator',
      Market: '/market',
      Impostazioni: '/settings',
    };
    Object.entries(routes).forEach(([label, path]) => {
      const link = screen.getByRole('link', { name: label });
      expect(link).toHaveAttribute('href', path);
      expect(link).toHaveAttribute('aria-label', label);
      expect(link).toHaveAttribute('title', label);
    });
  });

  it('collassa la sidebar quando si entra in una sezione', async () => {
    renderShell();
    const sidebar = screen.getByRole('complementary', { name: 'Navigazione principale' });
    await waitFor(() => expect(sidebar).toHaveAttribute('data-collapsed', 'true'));
  });
});

const playerPage = (content: object[]) => ({
  content,
  page: 0,
  size: 6,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
});
const searchedPlayer = {
  id: 7,
  name: 'Giocatore reale',
  club: 'Squadra reale',
  clubSlug: 'squadra-reale',
  fantacalcioRole: 'A',
  gazzettaRole: null,
  fantamarktRole: null,
  sourcesCount: 2,
};

describe('P01 global search', () => {
  it('accetta input e invia con Enter mostrando un risultato navigabile', async () => {
    server.use(
      http.get('/api/v1/players', ({ request }) => {
        expect(new URL(request.url).searchParams.get('search')).toBe('Giocatore');
        return HttpResponse.json(playerPage([searchedPlayer]));
      }),
      http.get('/api/v1/clubs', () => HttpResponse.json([])),
    );
    renderShell();
    const input = screen.getByRole('searchbox', { name: /Cerca giocatori e squadre/ });
    await userEvent.type(input, 'Giocatore{enter}');
    expect(await screen.findByRole('link', { name: /Giocatore reale/ })).toHaveAttribute(
      'href',
      '/players/7',
    );
  });

  it('supporta il pulsante, loading e zero risultati', async () => {
    server.use(
      http.get('/api/v1/players', async () => {
        await delay(50);
        return HttpResponse.json(playerPage([]));
      }),
      http.get('/api/v1/clubs', async () => {
        await delay(50);
        return HttpResponse.json([]);
      }),
    );
    renderShell();
    await userEvent.type(screen.getByRole('searchbox'), 'Nessuno');
    await userEvent.click(screen.getByRole('button', { name: 'Avvia ricerca' }));
    expect(screen.getByText('Ricerca in corsoâ€¦')).toBeVisible();
    expect(await screen.findByText('Nessun giocatore o squadra trovato.')).toBeVisible();
  });

  it('rende percepibile un errore API e chiude i risultati con Escape', async () => {
    server.use(
      http.get('/api/v1/players', () => new HttpResponse(null, { status: 500 })),
      http.get('/api/v1/clubs', () => HttpResponse.json([])),
    );
    renderShell();
    const input = screen.getByRole('searchbox');
    await userEvent.type(input, 'Errore{enter}');
    expect(await screen.findByRole('alert')).toHaveTextContent('Ricerca non disponibile');
    await userEvent.type(input, '{escape}');
    expect(screen.queryByLabelText('Risultati ricerca')).not.toBeInTheDocument();
  });
});
