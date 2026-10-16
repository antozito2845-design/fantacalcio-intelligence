import { lazy, Suspense, useState } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { RealDashboardPage } from '@/pages/RealDashboardPage';
import { LoginPage } from '@/pages/LoginPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { AdminRoute, ProtectedRoute, PublicOnlyRoute } from '@/auth/RouteGuards';
import { LoadingState } from '@/components/ui';

const DesignSystemPage = lazy(async () => ({
  default: (await import('@/pages/DesignSystemPage')).DesignSystemPage,
}));
const AdminPage = lazy(async () => ({ default: (await import('@/pages/AdminPage')).AdminPage }));
const StatusPage = lazy(async () => ({
  default: (await import('@/pages/StatusPage')).StatusPage,
}));
const PlayersPage = lazy(async () => ({
  default: (await import('@/pages/PlayersPage')).PlayersPage,
}));

const HistoricalPlayersPage = lazy(async () => ({
  default: (await import('@/pages/HistoricalPlayersPage')).HistoricalPlayersPage,
}));

const HistoricalPlayerDetailPage = lazy(async () => ({
  default: (await import('@/pages/HistoricalPlayerDetailPage')).HistoricalPlayerDetailPage,
}));

const PlayerDetailPage = lazy(async () => ({
  default: (await import('@/pages/PlayerDetailPage')).PlayerDetailPage,
}));
const AuctionPage = lazy(async () => ({
  default: (await import('@/pages/AuctionPage')).AuctionPage,
}));
const LiveAuctionPage = lazy(async () => ({
  default: (await import('@/pages/LiveAuctionPage')).LiveAuctionPage,
}));
const SquadPage = lazy(async () => ({ default: (await import('@/pages/SquadPage')).SquadPage }));
const LineupPage = lazy(async () => ({
  default: (await import('@/pages/LineupPage')).LineupPage,
}));
const CalendarPage = lazy(async () => ({
  default: (await import('@/pages/CalendarPage')).CalendarPage,
}));
const ScoutingPage = lazy(async () => ({
  default: (await import('@/pages/ScoutingPage')).ScoutingPage,
}));
const ComingSoonPage = lazy(async () => ({
  default: (await import('@/pages/ComingSoonPage')).ComingSoonPage,
}));
function createAppRouter() {
  return createBrowserRouter([
    { path: '/', element: <RealDashboardPage /> },
    { path: '/players', element: <PlayersPage /> },
    { path: '/players/history', element: <HistoricalPlayersPage /> },
    {
      path: '/players/history/:id',
      element: <HistoricalPlayerDetailPage />,
    },
    { path: '/players/:id', element: <PlayerDetailPage /> },
    { path: '/auction', element: <AuctionPage /> },
    { path: '/lineup', element: <LineupPage /> },
    { path: '/calendar', element: <CalendarPage /> },
    { path: '/scouting', element: <ScoutingPage /> },
    { path: '/analysis', element: <ComingSoonPage title="Analisi" /> },
    { path: '/statistics', element: <ComingSoonPage title="Statistiche" /> },
    { path: '/comparator', element: <ComingSoonPage title="Comparatore" /> },
    { path: '/market', element: <ComingSoonPage title="Market" /> },
    { path: '/settings', element: <ComingSoonPage title="Impostazioni" /> },
    { path: '/design-system', element: <DesignSystemPage /> },
    { element: <PublicOnlyRoute />, children: [{ path: '/login', element: <LoginPage /> }] },
    {
      element: <ProtectedRoute />,
      children: [
        { path: '/profile', element: <StatusPage title="Profilo autenticato" /> },
        { path: '/auction/live', element: <LiveAuctionPage /> },
        { path: '/auction/live/:sessionId', element: <LiveAuctionPage /> },
        { path: '/squad', element: <SquadPage /> },
      ],
    },
    {
      element: <AdminRoute />,
      children: [
        { path: '/admin', element: <AdminPage /> },
        { path: '/admin/status', element: <StatusPage title="Stato amministrativo" /> },
      ],
    },
    { path: '/access-denied', element: <StatusPage title="Accesso negato" /> },
    { path: '*', element: <NotFoundPage /> },
  ]);
}
export function AppRouter() {
  const [router] = useState(createAppRouter);
  return (
    <Suspense fallback={<LoadingState />}>
      <RouterProvider router={router} />
    </Suspense>
  );
}
