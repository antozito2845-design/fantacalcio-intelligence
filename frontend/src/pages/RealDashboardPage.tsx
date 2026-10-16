import { useQuery } from '@tanstack/react-query';
import { Moon, Server, Sun } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { dashboardApi } from '@/api/dashboardApi';
import { queryKeys } from '@/api/queryKeys';
import { useAuth } from '@/auth/useAuth';
import { featureApi } from '@/features/core/api';
import {
  ConfidenceScore,
  DifficultyIndicator,
  Metric,
  PriceRange,
  RoleBadge,
  type Difficulty,
  type PlayerRole,
} from '@/components/data-display';
import { Badge, Card, IconButton, Panel, StatusBadge } from '@/components/ui';
import { AppShell } from '@/layouts/AppShell';
import { AppError } from '@/api/errors/AppError';
import styles from './DesignSystemPage.module.css';

function WidgetState({
  loading,
  error,
  errorText,
  empty,
  children,
}: {
  loading: boolean;
  error: boolean;
  errorText?: string;
  empty?: boolean;
  children: ReactNode;
}) {
  if (loading) return <p className={styles.widgetState}>Caricamento dati reali…</p>;
  if (error)
    return (
      <p className={`${styles.widgetState} ${styles.widgetError}`}>
        {errorText ?? 'Dati temporaneamente non disponibili.'}
      </p>
    );
  if (empty) return <p className={styles.widgetState}>Nessun dato disponibile.</p>;
  return <>{children}</>;
}

function lineupErrorText(error: unknown): string {
  if (!(error instanceof AppError)) return 'Dati temporaneamente non disponibili.';
  switch (error.kind) {
    case 'not-found':
      return `Rosa non riconosciuta dal motore: ${error.message}`;
    case 'invalid-response':
      return 'Il servizio ha restituito dati non conformi al contratto della formazione.';
    case 'server':
      return 'Errore interno del motore di calcolo.';
    case 'network':
    case 'timeout':
      return 'Il servizio di calcolo non è raggiungibile.';
    default:
      return error.status ? `Richiesta non riuscita (HTTP ${error.status}).` : error.message;
  }
}

function difficulty(value: string): Difficulty {
  const normalized = value.toUpperCase();
  if (normalized.includes('EASY') || normalized.includes('FACILE')) return 'EASY';
  if (normalized.includes('HARD') || normalized.includes('DIFFIC')) return 'HARD';
  if (normalized.includes('MEDIUM') || normalized.includes('MEDIA')) return 'MEDIUM';
  return 'UNKNOWN';
}

function role(value: string): PlayerRole {
  return value === 'P' || value === 'D' || value === 'C' || value === 'A' ? value : 'C';
}

export function RealDashboardPage() {
  const [theme, setTheme] = useState<'dark' | 'light'>('dark');
  const auth = useAuth();
  const system = useQuery({ queryKey: queryKeys.system, queryFn: dashboardApi.system });
  const seasons = useQuery({ queryKey: queryKeys.seasons, queryFn: dashboardApi.seasons });
  const clubs = useQuery({ queryKey: queryKeys.clubs, queryFn: dashboardApi.clubs });
  const currentSeason = seasons.data?.find((season) => season.current)?.code;
  const players = useQuery({
    queryKey: queryKeys.players(currentSeason),
    queryFn: () => dashboardApi.players(currentSeason),
    enabled: seasons.isSuccess,
  });
  const auction = useQuery({
    queryKey: queryKeys.auction(currentSeason),
    queryFn: () => dashboardApi.auction(currentSeason),
    enabled: Boolean(currentSeason),
  });
  const fixtures = useQuery({
    queryKey: queryKeys.fixtures(currentSeason, 1),
    queryFn: () => dashboardApi.fixtures(currentSeason, 1),
    enabled: Boolean(currentSeason),
  });
  const squad = useQuery({
    queryKey: queryKeys.squad,
    queryFn: ({ signal }) => featureApi.squad(auth.getAuthorization, signal),
    enabled: auth.status === 'authenticated',
    retry: false,
  });
  const lineup = useQuery({
    queryKey: queryKeys.lineup(squad.data?.season ?? currentSeason, 1, squad.data?.id),
    queryFn: () =>
      dashboardApi.lineup(
        squad.data?.season ?? currentSeason,
        1,
        squad.data?.id,
        auth.getAuthorization,
      ),
    enabled: Boolean(currentSeason) && squad.isSuccess && squad.data.totalPlayers >= 11,
  });
  const scouting = useQuery({
    queryKey: queryKeys.scouting(currentSeason),
    queryFn: () => dashboardApi.scouting(currentSeason),
    enabled: Boolean(currentSeason),
  });
  const readiness = useQuery({
    queryKey: queryKeys.admin(currentSeason),
    queryFn: () => dashboardApi.readiness(currentSeason, auth.getAuthorization),
    enabled: auth.isAdmin && Boolean(currentSeason),
  });

  function toggleTheme() {
    const next = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
    document.documentElement.dataset.theme = next;
  }

  return (
    <AppShell>
      <div id="dashboard">
        <header className={styles.pageHeader}>
          <div>
            <p className={styles.eyebrow}>Dashboard live · F06</p>
            <h1>Intelligence overview</h1>
            <p>
              {currentSeason
                ? `Stagione corrente ${currentSeason}`
                : 'Sincronizzazione stagione in corso'}
            </p>
          </div>
          <div className={styles.headerActions}>
            <span className={styles.systemPill}>
              <Server size={14} />
              {system.isSuccess
                ? `${system.data.application} ${system.data.version} · online`
                : system.isError
                  ? 'Backend offline'
                  : 'Connessione…'}
            </span>
            <IconButton
              label={`Attiva tema ${theme === 'dark' ? 'chiaro' : 'scuro'}`}
              onClick={toggleTheme}
            >
              {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
            </IconButton>
          </div>
        </header>
        <section className={styles.kpis} aria-label="Metriche reali">
          <Card className={styles.kpi}>
            <Metric
              label="Giocatori catalogati"
              value={players.data?.totalElements ?? '—'}
              helper={players.isError ? 'non disponibile' : 'totale backend'}
            />
          </Card>
          <Card className={styles.kpi}>
            <Metric
              label="Club attivi"
              value={clubs.data?.length ?? '—'}
              helper={clubs.isError ? 'non disponibile' : 'catalogo corrente'}
            />
          </Card>
          <Card className={styles.kpi}>
            <Metric
              label="Stagione corrente"
              value={currentSeason ?? '—'}
              helper={seasons.isError ? 'non disponibile' : 'fonte backend'}
            />
          </Card>
          <Card className={styles.kpi}>
            <Metric
              label="Stato sistema"
              value={system.data?.status ?? '—'}
              helper={
                system.isError
                  ? 'backend offline'
                  : (system.data?.environment ?? 'verifica in corso')
              }
            />
          </Card>
        </section>
        <section className={styles.dashboard} aria-label="Dashboard dati reali">
          <Panel>
            <div className={styles.panelHead}>
              <h2>Auction intelligence</h2>
              <Badge>Dati reali</Badge>
            </div>
            <WidgetState
              loading={auction.isLoading}
              error={auction.isError}
              empty={auction.data?.players.length === 0}
            >
              <div className={styles.scoutList}>
                {auction.data?.players.slice(0, 3).map((player) => (
                  <div className={styles.scout} key={player.playerId}>
                    <RoleBadge playerRole={role(player.fantasyRole)} />
                    <span className={styles.scoutMeta}>
                      {player.playerName}
                      <span>
                        {player.club} ·{' '}
                        <PriceRange min={player.minimumPrice} max={player.maximumPrice} />
                      </span>
                    </span>
                    <ConfidenceScore value={Math.round(player.reliabilityScore)} />
                  </div>
                ))}
              </div>
            </WidgetState>
          </Panel>
          <Panel>
            <div className={styles.panelHead}>
              <h2>Formazione consigliata</h2>
              <StatusBadge tone="info">{lineup.data?.formation ?? 'Live'}</StatusBadge>
            </div>
            {auth.status !== 'authenticated' ? (
              <p className={styles.widgetState}>
                <Link to="/login" state={{ from: '/lineup' }}>
                  Accedi
                </Link>{' '}
                e configura una rosa personale per ottenere la formazione.
              </p>
            ) : squad.isPending ? (
              <p className={styles.widgetState}>Caricamento rosa personale…</p>
            ) : squad.isError ? (
              <p className={styles.widgetState}>
                <Link to="/squad">Crea o compila prima la tua rosa</Link> con giocatori reali.
              </p>
            ) : squad.data.totalPlayers < 11 ? (
              <p className={styles.widgetState}>
                Rosa incompleta ({squad.data.totalPlayers}/11):{' '}
                <Link to="/squad">aggiungi giocatori reali</Link> prima di calcolare la formazione.
              </p>
            ) : (
              <WidgetState
                loading={lineup.isLoading}
                error={lineup.isError}
                errorText={lineupErrorText(lineup.error)}
                empty={lineup.data?.starters.length === 0}
              >
                <div className={styles.lineup} aria-label="Formazione consigliata reale">
                  <div className={styles.pitchGrid}>
                    {lineup.data?.starters.slice(0, 9).map((player) => (
                      <span className={styles.player} key={player.playerId}>
                        {player.role} · {player.playerName}
                      </span>
                    ))}
                  </div>
                </div>
              </WidgetState>
            )}
          </Panel>
          <Panel>
            <div className={styles.panelHead}>
              <h2>Calendario & matchup</h2>
              <Badge>Giornata 1</Badge>
            </div>
            <WidgetState
              loading={fixtures.isLoading}
              error={fixtures.isError}
              empty={fixtures.data?.length === 0}
            >
              {fixtures.data?.slice(0, 3).map((fixture) => (
                <div key={fixture.fixtureId}>
                  <div className={styles.match}>
                    <strong>{fixture.homeClub}</strong>
                    <span className={styles.vs}>VS</span>
                    <strong>{fixture.awayClub}</strong>
                  </div>
                  <DifficultyIndicator level={difficulty(fixture.homeDifficulty)} />
                </div>
              ))}
            </WidgetState>
          </Panel>
          <Panel>
            <div className={styles.panelHead}>
              <h2>Scouting nuovi arrivi</h2>
              <Badge>Live</Badge>
            </div>
            <WidgetState
              loading={scouting.isLoading}
              error={scouting.isError}
              empty={scouting.data?.length === 0}
            >
              <div className={styles.scoutList}>
                {scouting.data?.slice(0, 3).map((player) => (
                  <div className={styles.scout} key={player.playerId}>
                    <RoleBadge playerRole={role(player.role)} />
                    <span className={styles.scoutMeta}>
                      {player.playerName}
                      <span>
                        {player.club} · {player.recommendation}
                      </span>
                    </span>
                    <ConfidenceScore value={Math.round(player.confidenceScore)} />
                  </div>
                ))}
              </div>
            </WidgetState>
          </Panel>
          <Panel className={styles.span2}>
            <div className={styles.panelHead}>
              <h2>Catalogo giocatori</h2>
              <Badge>{players.data ? `${players.data.totalElements} totali` : 'Live'}</Badge>
            </div>
            <WidgetState
              loading={players.isLoading}
              error={players.isError}
              empty={players.data?.content.length === 0}
            >
              <div className={styles.tableWrap}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Profilo</th>
                      <th>Ruolo</th>
                      <th>Club</th>
                      <th>Fonti</th>
                    </tr>
                  </thead>
                  <tbody>
                    {players.data?.content.map((player) => (
                      <tr key={player.id}>
                        <td>{player.name}</td>
                        <td>
                          {player.fantacalcioRole ? (
                            <RoleBadge playerRole={role(player.fantacalcioRole)} />
                          ) : (
                            '—'
                          )}
                        </td>
                        <td>{player.club}</td>
                        <td>{player.sourcesCount}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </WidgetState>
          </Panel>
          {auth.isAdmin && (
            <Panel className={styles.span2}>
              <div className={styles.panelHead}>
                <h2>Catalog readiness</h2>
                <Badge>Admin · sola lettura</Badge>
              </div>
              <WidgetState loading={readiness.isLoading} error={readiness.isError}>
                {readiness.data && (
                  <div className={styles.adminMetrics}>
                    <Metric label="Stato" value={readiness.data.readinessStatus} />
                    <Metric label="Qualità" value={`${readiness.data.qualityPercentage}%`} />
                    <Metric label="Operativi" value={readiness.data.totalOperationalPlayers} />
                    <Metric label="Avvisi" value={readiness.data.warningCount} />
                  </div>
                )}
              </WidgetState>
            </Panel>
          )}
        </section>
      </div>
    </AppShell>
  );
}
