import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { featureApi } from '@/features/core/api';
import { HistoricalPlayerDetailPage } from '@/pages/HistoricalPlayerDetailPage';

vi.mock('@/layouts/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function renderPage(path = '/players/history/653?season=2024%2F2025') {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/players/history/:id" element={<HistoricalPlayerDetailPage />} />
          <Route path="/players/history" element={<output>Catalogo storico 2024/2025</output>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('dettaglio storico giocatore', () => {
  it('carica il profilo identity-first della stagione selezionata', async () => {
    const detailSpy = vi.spyOn(featureApi, 'historicalPlayer').mockResolvedValue({
      id: 653,
      name: 'Andrea Belotti',
      active: false,
      season: '2024/2025',
      club: 'Como',
      clubSlug: 'como',
      roles: {
        FANTACALCIO: 'A',
      },
      statistics: [
        {
          season: '2024/2025',
          source: 'FANTACALCIO',
          sourceExternalId: null,
          club: 'Como',
          historicalClubs: [],
          appearances: 13,
          starts: 5,
          minutesPlayed: 520,
          averageRating: null,
          fantasyAverage: 6.31,
          goals: 2,
          assists: 0,
        },
      ],
    });

    renderPage();

    expect(
      await screen.findByRole('heading', {
        name: 'Andrea Belotti',
      }),
    ).toBeVisible();

    expect(screen.getByText(/2024\/2025/)).toBeVisible();
    expect(screen.getByText(/Non attivo/)).toBeVisible();
    expect(screen.getByText('6.31')).toBeVisible();

    expect(screen.getByRole('columnheader', { name: 'Gol' })).toBeVisible();

    expect(screen.getByRole('columnheader', { name: 'Assist' })).toBeVisible();

    expect(detailSpy).toHaveBeenCalledWith('653', '2024/2025', expect.anything());

    const backButton = screen.getByRole('button', { name: 'Indietro' });
    expect(backButton).toBeVisible();
    expect(screen.queryByText('Intelligence asta')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /acquist|rosa/i })).not.toBeInTheDocument();

    await userEvent.click(backButton);
    expect(screen.getByText('Catalogo storico 2024/2025')).toBeVisible();
  });

  it('non trasforma metriche goalkeeper mancanti in zero', async () => {
    vi.spyOn(featureApi, 'historicalPlayer').mockResolvedValue({
      id: 900,
      name: 'Portiere storico',
      active: false,
      season: '2024/2025',
      club: null,
      clubSlug: null,
      roles: {
        FANTACALCIO: 'P',
      },
      statistics: [
        {
          season: '2024/2025',
          source: 'FANTACALCIO',
          sourceExternalId: null,
          club: null,
          historicalClubs: [],
          appearances: null,
          starts: null,
          minutesPlayed: null,
          averageRating: null,
          fantasyAverage: null,
          goals: null,
          assists: null,
          goalsConceded: null,
          saves: null,
          penaltiesSaved: null,
          cleanSheets: null,
        },
      ],
    });

    renderPage('/players/history/900?season=2024%2F2025');

    expect(
      await screen.findByRole('heading', {
        name: 'Portiere storico',
      }),
    ).toBeVisible();

    expect(
      screen.getByRole('columnheader', {
        name: 'Gol subiti',
      }),
    ).toBeVisible();

    expect(
      screen.getByRole('columnheader', {
        name: 'Parate',
      }),
    ).toBeVisible();

    expect(screen.getAllByText('Dato non disponibile').length).toBeGreaterThanOrEqual(6);
  });
});
