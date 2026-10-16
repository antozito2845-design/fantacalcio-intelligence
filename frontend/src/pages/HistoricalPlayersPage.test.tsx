import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { dashboardApi } from '@/api/dashboardApi';
import { featureApi } from '@/features/core/api';
import { HistoricalPlayersPage } from '@/pages/HistoricalPlayersPage';

vi.mock('@/layouts/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

function renderPage(path = '/players/history') {
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
        <HistoricalPlayersPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('catalogo storico giocatori', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('mostra giocatori storici reali senza trasformarli nel catalogo operativo', async () => {
    vi.spyOn(dashboardApi, 'seasons').mockResolvedValue([
      {
        code: '2025/2026',
        current: false,
      },
      {
        code: '2024/2025',
        current: false,
      },
      {
        code: '2026/2027',
        current: true,
      },
    ] as Awaited<ReturnType<typeof dashboardApi.seasons>>);

    const historicalSpy = vi.spyOn(featureApi, 'historicalPlayers').mockResolvedValue({
      content: [
        {
          id: 653,
          name: 'Andrea Belotti',
          active: false,
          season: '2025/2026',
          club: 'Cagliari',
          clubSlug: 'cagliari',
          fantacalcioRole: 'A',
          appearances: 28,
          fantasyAverage: 6.4,
          goals: 7,
          assists: 2,
        },
      ],
      page: 0,
      size: 25,
      totalElements: 663,
      totalPages: 27,
    });

    renderPage();

    expect(
      await screen.findByRole('heading', {
        name: 'Catalogo storico',
      }),
    ).toBeVisible();

    expect((await screen.findAllByText('Andrea Belotti')).length).toBeGreaterThan(0);

    expect(screen.getAllByText('Storico').length).toBeGreaterThan(0);

    expect(screen.getAllByText('Cagliari').length).toBeGreaterThan(0);

    expect(screen.getByText('663 giocatori ? 2025/2026')).toBeVisible();

    expect(historicalSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        season: '2025/2026',
        page: 0,
        size: 25,
      }),
      expect.anything(),
    );

    expect(
      screen.queryByRole('option', {
        name: '2026/2027',
      }),
    ).not.toBeInTheDocument();
  });

  it('propaga filtro ruolo e paginazione al contratto API', async () => {
    vi.spyOn(dashboardApi, 'seasons').mockResolvedValue([
      {
        code: '2025/2026',
        current: false,
      },
      {
        code: '2024/2025',
        current: false,
      },
    ] as Awaited<ReturnType<typeof dashboardApi.seasons>>);

    const historicalSpy = vi.spyOn(featureApi, 'historicalPlayers').mockImplementation((filters) =>
      Promise.resolve({
        content: [
          {
            id: filters.page === 0 ? 1 : 2,
            name: filters.page === 0 ? 'Prima pagina' : 'Seconda pagina',
            active: false,
            season: filters.season,
            club: null,
            clubSlug: null,
            fantacalcioRole: filters.role ?? 'D',
            appearances: 10,
            fantasyAverage: 6,
            goals: 0,
            assists: 0,
          },
        ],
        page: filters.page,
        size: 25,
        totalElements: 30,
        totalPages: 2,
      }),
    );

    renderPage();

    expect((await screen.findAllByText('Prima pagina')).length).toBeGreaterThan(0);

    await userEvent.selectOptions(screen.getByLabelText('Ruolo'), 'A');

    await waitFor(() => {
      expect(historicalSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          season: '2025/2026',
          role: 'A',
          page: 0,
          size: 25,
        }),
        expect.anything(),
      );
    });

    await userEvent.click(
      screen.getByRole('button', {
        name: 'Successiva',
      }),
    );

    await waitFor(() => {
      expect(historicalSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          season: '2025/2026',
          role: 'A',
          page: 1,
          size: 25,
        }),
        expect.anything(),
      );
    });

    expect((await screen.findAllByText('Seconda pagina')).length).toBeGreaterThan(0);

    expect(screen.getByText('2 / 2')).toBeVisible();
  });
});
