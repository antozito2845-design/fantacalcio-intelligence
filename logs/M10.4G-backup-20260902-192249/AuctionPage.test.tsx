import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthProvider } from '@/auth/AuthProvider';
import { server } from '@/test/server';
import { AuctionPage } from './AuctionPage';

const ranking = {
  season: '2026/2027',
  budget: 1000,
  teams: 8,
  defenseModifier: true,
  role: null,
  club: null,
  maximumPrice: null,
  recommendation: null,
  excludeUnavailable: true,
  totalPlayers: 3,
  players: [
    {
      position: 1,
      playerId: 10,
      playerName: 'Attaccante Alpha',
      club: 'Club A',
      fantasyRole: 'A',
      intelligenceScore: 92,
      minimumPrice: 20,
      recommendedPrice: 40,
      maximumPrice: 55,
      budgetPercentage: 4,
      reliabilityScore: 88,
      potentialScore: 90,
      riskScore: 25,
      starterProbability: 90,
      availabilityStatus: 'AVAILABLE',
      availabilityMatchday: 1,
      strengths: ['Titolarità alta'],
      risks: ['Nessun rischio critico'],
      finalRecommendation: 'OBIETTIVO PRIORITARIO',
    },
    {
      position: 2,
      playerId: 11,
      playerName: 'Attaccante Beta',
      club: 'Club B',
      fantasyRole: 'A',
      intelligenceScore: 82,
      minimumPrice: 10,
      recommendedPrice: 28,
      maximumPrice: 40,
      budgetPercentage: 2.8,
      reliabilityScore: 75,
      potentialScore: 80,
      riskScore: 35,
      starterProbability: 79,
      availabilityStatus: 'AVAILABLE',
      availabilityMatchday: 1,
      strengths: [],
      risks: [],
      finalRecommendation: 'ACQUISTO CONSIGLIATO',
    },
    {
      position: 3,
      playerId: 12,
      playerName: 'Difensore Gamma',
      club: 'Club C',
      fantasyRole: 'D',
      intelligenceScore: 72,
      minimumPrice: 4,
      recommendedPrice: 10,
      maximumPrice: 15,
      budgetPercentage: 1,
      reliabilityScore: null,
      potentialScore: 70,
      riskScore: null,
      starterProbability: null,
      availabilityStatus: null,
      availabilityMatchday: null,
      strengths: [],
      risks: [],
      finalRecommendation: 'ACQUISTARE SOLO AL PREZZO GIUSTO',
    },
  ],
};

const strategy = {
  season: '2026/2027',
  totalBudget: 1000,
  teams: 8,
  defenseModifier: true,
  totalRequiredPlayers: 25,
  totalPlannedSpending: 930,
  expectedRemainingBudget: 70,
  withinBudget: true,
  strategySummary: 'Piano reale.',
  roles: [
    {
      role: 'P',
      requiredSlots: 3,
      budgetPercentage: 8,
      allocatedBudget: 80,
      plannedSpending: 70,
      remainingRoleBudget: 10,
      warnings: [],
    },
    {
      role: 'D',
      requiredSlots: 8,
      budgetPercentage: 22,
      allocatedBudget: 220,
      plannedSpending: 200,
      remainingRoleBudget: 20,
      warnings: [],
    },
    {
      role: 'C',
      requiredSlots: 8,
      budgetPercentage: 27,
      allocatedBudget: 270,
      plannedSpending: 250,
      remainingRoleBudget: 20,
      warnings: [],
    },
    {
      role: 'A',
      requiredSlots: 6,
      budgetPercentage: 43,
      allocatedBudget: 430,
      plannedSpending: 410,
      remainingRoleBudget: 20,
      warnings: [],
    },
  ],
  globalWarnings: [],
};

function renderPage() {
  server.use(
    http.get('/api/v1/auction/intelligence-rankings', () => HttpResponse.json(ranking)),
    http.get('/api/v1/auction/strategy', () => HttpResponse.json(strategy)),
  );

  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <QueryClientProvider client={client}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/auction']}>
          <AuctionPage />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

afterEach(cleanup);

describe('M10 Auction Copilot', () => {
  it('mostra FantaScore, tier, target, tetto e decisione operativa', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Auction Copilot' })).toBeVisible();

    await userEvent.click(await screen.findByRole('button', { name: /Attaccante Alpha/ }));

    expect(screen.getByText('92/100')).toBeVisible();
    expect(screen.getByText('S')).toBeVisible();
    expect(screen.getAllByText('40').length).toBeGreaterThan(0);

    const bid = screen.getByRole('spinbutton', { name: 'Prezzo corrente asta' });
    await userEvent.clear(bid);
    await userEvent.type(bid, '30');

    expect(await screen.findByText('COMPRA')).toBeVisible();
  });

  it('passa a STOP quando il prezzo supera il tetto massimo', async () => {
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: /Attaccante Alpha/ }));

    const bid = screen.getByRole('spinbutton', { name: 'Prezzo corrente asta' });
    await userEvent.clear(bid);
    await userEvent.type(bid, '99');

    expect(await screen.findByText('STOP')).toBeVisible();
  });

  it('cerca giocatori e non espone la vecchia CTA Live Auction', async () => {
    renderPage();

    await screen.findByText('Attaccante Alpha');

    await userEvent.type(
      screen.getByRole('searchbox', { name: /Cerca giocatore o club/ }),
      'Gamma',
    );

    expect(screen.getByText('Difensore Gamma')).toBeVisible();
    expect(screen.queryByText('Attaccante Alpha')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Live Auction/i })).not.toBeInTheDocument();
  });

  it('mantiene i nullable come dato non disponibile', async () => {
    renderPage();

    await userEvent.type(
      await screen.findByRole('searchbox', { name: /Cerca giocatore o club/ }),
      'Gamma',
    );

    await userEvent.click(screen.getByRole('button', { name: /Difensore Gamma/ }));

    expect(screen.getAllByText('Dato non disponibile').length).toBeGreaterThanOrEqual(2);
  });
});
