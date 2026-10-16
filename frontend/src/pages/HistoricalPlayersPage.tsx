import { useEffect, useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { dashboardApi } from '@/api/dashboardApi';
import { featureApi } from '@/features/core/api';
import { AppShell } from '@/layouts/AppShell';
import styles from '@/features/core/FeaturePage.module.css';

export function HistoricalPlayersPage() {
  const [url, setUrl] = useSearchParams();
  const [search, setSearch] = useState(url.get('search') ?? '');

  const role = url.get('role') ?? '';
  const page = Number(url.get('page') ?? 0);

  const seasons = useQuery({
    queryKey: ['historical-player-seasons'],
    queryFn: dashboardApi.seasons,
  });

  const historicalSeasons = useMemo(
    () =>
      [...(seasons.data ?? [])]
        .filter((season) => !season.current)
        .sort((a, b) => b.code.localeCompare(a.code)),
    [seasons.data],
  );

  const selectedSeason = url.get('season') ?? historicalSeasons[0]?.code ?? '';

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setUrl(
        (current) => {
          const next = new URLSearchParams(current);
          const currentSearch = current.get('search') ?? '';

          if (search === currentSearch) return current;

          if (search) next.set('search', search);
          else next.delete('search');

          next.set('page', '0');
          return next;
        },
        { replace: true },
      );
    }, 350);

    return () => window.clearTimeout(timer);
  }, [search, setUrl]);

  const query = useQuery({
    queryKey: [
      'historical-players',
      {
        season: selectedSeason,
        search: url.get('search') ?? '',
        role,
        page,
      },
    ],
    queryFn: ({ signal }) =>
      featureApi.historicalPlayers(
        {
          season: selectedSeason,
          search: url.get('search') ?? undefined,
          role: role || undefined,
          page,
          size: 25,
        },
        signal,
      ),
    enabled: Boolean(selectedSeason),
    placeholderData: keepPreviousData,
  });

  const update = (key: string, value: string) => {
    const next = new URLSearchParams(url);

    if (value) next.set(key, value);
    else next.delete(key);

    if (key !== 'page') next.set('page', '0');

    setUrl(next);
  };

  return (
    <AppShell>
      <div className={styles.page}>
        <header className={styles.hero}>
          <div>
            <span className={styles.eyebrow}>Archivio stagionale</span>
            <h1>Catalogo storico</h1>

            <p className={styles.muted}>
              {query.data && selectedSeason
                ? `${query.data.totalElements} giocatori ? ${selectedSeason}`
                : 'Giocatori e statistiche delle stagioni precedenti'}
            </p>
          </div>
        </header>

        <section
          className={`${styles.panel} ${styles.filters}`}
          aria-label="Filtri catalogo storico"
        >
          <label>
            Stagione
            <select
              aria-label="Stagione"
              value={selectedSeason}
              disabled={seasons.isPending || historicalSeasons.length === 0}
              onChange={(event) => update('season', event.target.value)}
            >
              {historicalSeasons.map((season) => (
                <option key={season.code} value={season.code}>
                  {season.code}
                </option>
              ))}
            </select>
          </label>

          <label>
            Cerca
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Nome giocatore"
            />
          </label>

          <label>
            Ruolo
            <select
              aria-label="Ruolo"
              value={role}
              onChange={(event) => update('role', event.target.value)}
            >
              <option value="">Tutti</option>

              {['P', 'D', 'C', 'A'].map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </label>

          <button
            className={`${styles.button} ${styles.secondary}`}
            onClick={() => {
              setSearch('');
              setUrl({});
            }}
          >
            Azzera
          </button>
        </section>

        <section className={styles.panel} aria-live="polite">
          {seasons.isPending ? (
            <div className={styles.status}>Caricamento stagioni?</div>
          ) : seasons.isError ? (
            <div className={`${styles.status} ${styles.error}`}>Stagioni non disponibili.</div>
          ) : historicalSeasons.length === 0 ? (
            <div className={styles.status}>Nessuna stagione storica disponibile.</div>
          ) : query.isPending ? (
            <div className={styles.status}>Caricamento catalogo storico?</div>
          ) : query.isError ? (
            <div className={`${styles.status} ${styles.error}`}>
              Catalogo storico non disponibile.{' '}
              <button className={styles.button} onClick={() => void query.refetch()}>
                Riprova
              </button>
            </div>
          ) : query.data.content.length === 0 ? (
            <div className={styles.status}>Nessun giocatore corrisponde ai filtri.</div>
          ) : (
            <>
              <div className={`${styles.tableWrap} ${styles.desktopTable}`}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Giocatore</th>
                      <th>Stato</th>
                      <th>Ruolo</th>
                      <th>Club</th>
                      <th>Presenze</th>
                      <th>Fantamedia</th>
                      <th>Gol</th>
                      <th>Assist</th>
                    </tr>
                  </thead>

                  <tbody>
                    {query.data.content.map((player) => (
                      <tr key={player.id}>
                        <td>
                          <Link
                            to={`/players/history/${player.id}?season=${encodeURIComponent(
                              selectedSeason,
                            )}`}
                          >
                            <strong>{player.name}</strong>
                          </Link>
                        </td>

                        <td>{player.active ? 'Attivo' : 'Storico'}</td>

                        <td>
                          <span className={styles.role}>{player.fantacalcioRole}</span>
                        </td>

                        <td>{player.club ?? 'Dato non disponibile'}</td>

                        <td>{player.appearances ?? 'Dato non disponibile'}</td>

                        <td>{player.fantasyAverage ?? 'Dato non disponibile'}</td>

                        <td>{player.goals ?? 'Dato non disponibile'}</td>

                        <td>{player.assists ?? 'Dato non disponibile'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className={styles.cards}>
                {query.data.content.map((player) => (
                  <article className={styles.card} key={player.id}>
                    <Link
                      to={`/players/history/${player.id}?season=${encodeURIComponent(
                        selectedSeason,
                      )}`}
                    >
                      <strong>{player.name}</strong>
                    </Link>

                    <span>
                      <span className={styles.role}>{player.fantacalcioRole}</span> ?{' '}
                      {player.club ?? 'Club non disponibile'}
                    </span>

                    <span>
                      {player.active ? 'Attivo' : 'Storico'} ? {player.appearances ?? '?'} presenze
                      ? FM {player.fantasyAverage ?? '?'}
                    </span>

                    <span>
                      Gol {player.goals ?? '?'} ? Assist {player.assists ?? '?'}
                    </span>
                  </article>
                ))}
              </div>

              <div className={styles.pager}>
                <button
                  className={`${styles.button} ${styles.secondary}`}
                  disabled={page === 0}
                  onClick={() => update('page', String(page - 1))}
                >
                  Precedente
                </button>

                <span>
                  {page + 1} / {Math.max(query.data.totalPages, 1)}
                </span>

                <button
                  className={`${styles.button} ${styles.secondary}`}
                  disabled={page + 1 >= query.data.totalPages}
                  onClick={() => update('page', String(page + 1))}
                >
                  Successiva
                </button>
              </div>
            </>
          )}
        </section>
      </div>
    </AppShell>
  );
}
