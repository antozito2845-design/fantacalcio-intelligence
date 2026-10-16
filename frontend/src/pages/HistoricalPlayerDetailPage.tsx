import { useQuery } from '@tanstack/react-query';
import { useParams, useSearchParams } from 'react-router-dom';
import { BackButton } from '@/components/navigation/BackButton';
import { AppShell } from '@/layouts/AppShell';
import { featureApi, type PlayerSeasonStatistics } from '@/features/core/api';
import styles from '@/features/core/FeaturePage.module.css';

const value = (current: number | null | undefined) =>
  current == null ? 'Dato non disponibile' : String(current);

const sourceLabel = (statistic: PlayerSeasonStatistics) =>
  statistic.sourceExternalId
    ? `${statistic.source} ? ${statistic.sourceExternalId}`
    : statistic.source;

function isGoalkeeper(roles: Record<string, string>) {
  return (
    Object.entries(roles)
      .find(([source]) => source.toUpperCase() === 'FANTACALCIO')?.[1]
      ?.toUpperCase() === 'P'
  );
}

export function HistoricalPlayerDetailPage() {
  const { id = '' } = useParams();
  const [searchParams] = useSearchParams();
  const season = searchParams.get('season') ?? '';

  const query = useQuery({
    queryKey: ['historical-player', id, season],
    queryFn: ({ signal }) => featureApi.historicalPlayer(id, season, signal),
    enabled: Boolean(id && season),
    retry: false,
  });

  const backTo = season
    ? `/players/history?season=${encodeURIComponent(season)}`
    : '/players/history';

  const goalkeeper = query.data ? isGoalkeeper(query.data.roles) : false;

  return (
    <AppShell>
      <div className={styles.page}>
        <BackButton fallbackTo={backTo} />

        {!season ? (
          <section className={`${styles.panel} ${styles.error}`}>
            <h1>Stagione storica mancante</h1>
            <p>Seleziona il giocatore dal catalogo storico.</p>
          </section>
        ) : query.isPending ? (
          <section className={styles.panel}>Caricamento profilo storico?</section>
        ) : query.isError ? (
          <section className={`${styles.panel} ${styles.error}`}>
            <h1>Giocatore storico non trovato</h1>
          </section>
        ) : (
          <>
            <header className={styles.hero}>
              <div>
                <span className={styles.eyebrow}>
                  {Object.values(query.data.roles).join(' ? ') || 'Ruolo non disponibile'}
                </span>

                <h1>{query.data.name}</h1>

                <p className={styles.muted}>
                  {query.data.season} ? {query.data.club ?? 'Club non disponibile'} ?{' '}
                  {query.data.active ? 'Attivo' : 'Non attivo'}
                </p>
              </div>
            </header>

            <section className={styles.panel}>
              <h2>Statistiche della stagione</h2>

              {query.data.statistics.length === 0 ? (
                <p className={styles.muted}>Nessuna statistica disponibile.</p>
              ) : (
                <div className={styles.tableWrap}>
                  <table className={styles.table}>
                    <thead>
                      <tr>
                        <th>Fonte</th>
                        <th>Club</th>
                        <th>Presenze</th>
                        <th>Minuti</th>

                        {goalkeeper ? (
                          <>
                            <th>Gol subiti</th>
                            <th>Parate</th>
                            <th>Rigori parati</th>
                            <th>Clean sheet</th>
                          </>
                        ) : (
                          <>
                            <th>Fantamedia</th>
                            <th>Gol</th>
                            <th>Assist</th>
                          </>
                        )}
                      </tr>
                    </thead>

                    <tbody>
                      {query.data.statistics.map((statistic, index) => (
                        <tr key={`${statistic.source}-${statistic.sourceExternalId ?? index}`}>
                          <td>{sourceLabel(statistic)}</td>
                          <td>{statistic.club ?? query.data.club ?? 'Dato non disponibile'}</td>
                          <td>{value(statistic.appearances)}</td>
                          <td>{value(statistic.minutesPlayed)}</td>

                          {goalkeeper ? (
                            <>
                              <td>{value(statistic.goalsConceded)}</td>
                              <td>{value(statistic.saves)}</td>
                              <td>{value(statistic.penaltiesSaved)}</td>
                              <td>{value(statistic.cleanSheets)}</td>
                            </>
                          ) : (
                            <>
                              <td>{value(statistic.fantasyAverage)}</td>
                              <td>{value(statistic.goals)}</td>
                              <td>{value(statistic.assists)}</td>
                            </>
                          )}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className={styles.panel}>
              <p className={styles.muted}>
                Profilo storico informativo: nessuna operazione di rosa o asta ? disponibile da
                questa pagina.
              </p>
            </section>
          </>
        )}
      </div>
    </AppShell>
  );
}
