import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { AppShell } from '@/layouts/AppShell';
import { featureApi } from '@/features/core/api';
import styles from '@/features/core/FeaturePage.module.css';
export function AuctionPage() {
  const [url, setUrl] = useSearchParams();
  const role = url.get('role') ?? '';
  const ranking = useQuery({
    queryKey: ['auction-ranking', role],
    queryFn: ({ signal }) => featureApi.ranking(role, signal),
  });
  const strategy = useQuery({
    queryKey: ['auction-strategy'],
    queryFn: ({ signal }) => featureApi.strategy(signal),
  });
  return (
    <AppShell>
      <div className={styles.page}>
        <header className={styles.hero}>
          <div>
            <span className={styles.eyebrow}>Decision engine</span>
            <h1>Auction Intelligence</h1>
            <p className={styles.muted}>
              Prezzi e priorità calcolati sul contesto reale della lega.
            </p>
          </div>
          <Link className={styles.button} to="/auction/live">
            Apri Live Auction
          </Link>
        </header>
        <section className={`${styles.panel} ${styles.filters}`}>
          <label>
            Ruolo
            <select
              value={role}
              onChange={(e) => setUrl(e.target.value ? { role: e.target.value } : {})}
            >
              <option value="">Tutti</option>
              {['P', 'D', 'C', 'A'].map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </label>
        </section>
        <section className={styles.panel} aria-live="polite">
          <h2>Ranking</h2>
          {ranking.isPending ? (
            <p>Calcolo ranking…</p>
          ) : ranking.isError ? (
            <p className={styles.error}>
              Ranking non disponibile.{' '}
              <button className={styles.button} onClick={() => void ranking.refetch()}>
                Riprova
              </button>
            </p>
          ) : ranking.data.players.length === 0 ? (
            <p>Nessun profilo disponibile.</p>
          ) : (
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Giocatore</th>
                    <th>Ruolo</th>
                    <th>Club</th>
                    <th>Range</th>
                    <th>Consigliato</th>
                    <th>Score</th>
                  </tr>
                </thead>
                <tbody>
                  {ranking.data.players.map((p) => (
                    <tr key={p.playerId}>
                      <td>{p.position}</td>
                      <td>
                        <Link to={`/players/${p.playerId}`} state={{ from: '/auction' }}>
                          {p.playerName}
                        </Link>
                      </td>
                      <td>
                        <span className={styles.role}>{p.fantasyRole}</span>
                      </td>
                      <td>{p.club}</td>
                      <td>
                        {p.minimumPrice}–{p.maximumPrice}
                      </td>
                      <td className={styles.score}>{p.recommendedPrice}</td>
                      <td>{p.intelligenceScore}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
        <section className={styles.panel}>
          <h2>Strategia</h2>
          {strategy.isPending ? (
            <p>Elaborazione strategia…</p>
          ) : strategy.isError ? (
            <p className={styles.error}>
              Strategia non disponibile per la configurazione corrente.
            </p>
          ) : (
            <>
              <p>{strategy.data.strategySummary}</p>
              <div className={styles.grid}>
                {strategy.data.roles.map((r) => (
                  <article className={styles.metric} key={r.role}>
                    <span>
                      Ruolo {r.role} · {r.requiredSlots} slot
                    </span>
                    <strong>{r.allocatedBudget} crediti</strong>
                    <small>{r.budgetPercentage}% del budget</small>
                  </article>
                ))}
              </div>
              {strategy.data.globalWarnings.length > 0 && (
                <ul className={styles.list}>
                  {strategy.data.globalWarnings.map((w) => (
                    <li key={w}>{w}</li>
                  ))}
                </ul>
              )}
            </>
          )}
        </section>
      </div>
    </AppShell>
  );
}
