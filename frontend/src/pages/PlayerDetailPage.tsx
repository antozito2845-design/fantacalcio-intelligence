import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { AppError } from '@/api/errors/AppError';
import { AppShell } from '@/layouts/AppShell';
import { BackButton } from '@/components/navigation/BackButton';
import { featureApi, type PlayerSeasonStatistics } from '@/features/core/api';
import styles from '@/features/core/FeaturePage.module.css';

const value = (v: number | null | undefined, suffix = '') =>
  v == null ? 'Dato non disponibile' : `${v}${suffix}`;

type StatisticColumn = {
  label: string;
  value: (statistic: PlayerSeasonStatistics) => string;
};

function isFantasyGoalkeeper(roles: Record<string, string>): boolean {
  const fantasyRole = Object.entries(roles).find(
    ([source]) => source.toUpperCase() === 'FANTACALCIO',
  )?.[1];
  return fantasyRole?.toUpperCase() === 'P';
}

function statisticColumns(goalkeeper: boolean): StatisticColumn[] {
  if (goalkeeper) {
    return [
      { label: 'Presenze', value: (statistic) => value(statistic.appearances) },
      { label: 'Minuti giocati', value: (statistic) => value(statistic.minutesPlayed) },
      { label: 'Gol subiti', value: (statistic) => value(statistic.goalsConceded) },
      { label: 'Parate effettuate', value: (statistic) => value(statistic.saves) },
      { label: 'Rigori parati', value: (statistic) => value(statistic.penaltiesSaved) },
      {
        label: 'Porte inviolate / Clean sheet',
        value: (statistic) => value(statistic.cleanSheets),
      },
    ];
  }
  return [
    { label: 'Presenze', value: (statistic) => value(statistic.appearances) },
    { label: 'Gol', value: (statistic) => value(statistic.goals) },
    { label: 'Assist', value: (statistic) => value(statistic.assists) },
    { label: 'Fantamedia', value: (statistic) => value(statistic.fantasyAverage) },
  ];
}

function sourceLabel(statistic: PlayerSeasonStatistics): string {
  return statistic.sourceExternalId
    ? `${statistic.source} · ${statistic.sourceExternalId}`
    : statistic.source;
}
export function PlayerDetailPage() {
  const { id = '' } = useParams();
  const q = useQuery({
    queryKey: ['player', id],
    queryFn: ({ signal }) => featureApi.player(id, signal),
    retry: false,
  });
  const goalkeeper = q.data ? isFantasyGoalkeeper(q.data.roles) : false;
  const columns = statisticColumns(goalkeeper);
  const notFound = q.error instanceof AppError && q.error.status === 404;
  const roles = q.data
    ? Object.entries(q.data.roles)
        .map(([source, role]) => `${source}: ${role}`)
        .join(' · ')
    : '';
  return (
    <AppShell>
      <div className={styles.page}>
        <BackButton fallbackTo="/players" />
        {q.isPending ? (
          <section className={styles.panel}>Caricamento profilo…</section>
        ) : q.isError ? (
          <section className={`${styles.panel} ${styles.error}`} role="alert">
            <h1>{notFound ? 'Giocatore non trovato' : 'Profilo non disponibile'}</h1>
            <p>
              {notFound
                ? 'Il giocatore richiesto non esiste nel catalogo corrente.'
                : 'Il servizio non ha potuto caricare il profilo. Riprova.'}
            </p>
            {!notFound && (
              <button className={styles.button} onClick={() => void q.refetch()}>
                Riprova
              </button>
            )}
            <Link to="/players">Torna al catalogo</Link>
          </section>
        ) : (
          <>
            <header className={styles.hero}>
              <div>
                <span className={styles.eyebrow}>{roles || 'Ruolo non disponibile'}</span>
                <h1>{q.data.name}</h1>
                <p className={styles.muted}>{q.data.club}</p>
              </div>
              <Link className={styles.button} to={`/auction?player=${q.data.id}`}>
                Intelligence asta
              </Link>
            </header>
            {q.data.evaluation && (
              <section className={styles.panel}>
                <h2>Valutazione</h2>
                <div className={styles.grid}>
                  {[
                    ['Media prevista', value(q.data.evaluation.projectedFantasyAverage)],
                    ['Affidabilità', value(q.data.evaluation.reliabilityScore)],
                    ['Potenziale', value(q.data.evaluation.potentialScore)],
                    ['Rischio', value(q.data.evaluation.riskScore)],
                  ].map(([a, b]) => (
                    <div className={styles.metric} key={a}>
                      <span>{a}</span>
                      <strong>{b}</strong>
                    </div>
                  ))}
                </div>
                {q.data.evaluation.summary && <p>{q.data.evaluation.summary}</p>}
              </section>
            )}
            {q.data.tacticalProfile && (
              <section className={styles.panel}>
                <h2>Profilo tattico</h2>
                <div className={styles.grid}>
                  <div className={styles.metric}>
                    <span>Posizione reale</span>
                    <strong>{q.data.tacticalProfile.realPosition ?? 'Non disponibile'}</strong>
                  </div>
                  <div className={styles.metric}>
                    <span>Probabilità titolare</span>
                    <strong>{value(q.data.tacticalProfile.starterProbability, '%')}</strong>
                  </div>
                </div>
                {q.data.tacticalProfile.tacticalNotes && (
                  <p>{q.data.tacticalProfile.tacticalNotes}</p>
                )}
              </section>
            )}
            <section className={styles.panel}>
              <h2>Storico disponibile</h2>
              {q.data.statistics.length === 0 ? (
                <p className={styles.muted}>Nessuna statistica disponibile.</p>
              ) : (
                <div className={styles.tableWrap}>
                  <table className={styles.table}>
                    <thead>
                      <tr>
                        <th>Stagione</th>
                        <th>Fonte</th>
                        {columns.map((column) => (
                          <th key={column.label}>{column.label}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {q.data.statistics.map((statistic, index) => (
                        <tr key={`${statistic.season}-${statistic.source}-${index}`}>
                          <td>{statistic.season}</td>
                          <td>{sourceLabel(statistic)}</td>
                          {columns.map((column) => (
                            <td key={column.label}>{column.value(statistic)}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </>
        )}
      </div>
    </AppShell>
  );
}
