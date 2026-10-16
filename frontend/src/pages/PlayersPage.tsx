import { useEffect, useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { AppShell } from '@/layouts/AppShell';
import { featureApi } from '@/features/core/api';
import { useAuth } from '@/auth/useAuth';
import { AppError } from '@/api/errors/AppError';
import { queryKeys } from '@/api/queryKeys';
import styles from '@/features/core/FeaturePage.module.css';

export function PlayersPage() {
  const auth = useAuth();
  const qc = useQueryClient();
  const [url, setUrl] = useSearchParams();
  const [search, setSearch] = useState(url.get('search') ?? '');
  const [selected, setSelected] = useState<{ id: number; name: string } | null>(null);
  const [purchasePrice, setPurchasePrice] = useState('1');
  const role = url.get('role') ?? '';
  const club = url.get('club') ?? '';
  const page = Number(url.get('page') ?? 0);
  useEffect(() => {
    const id = window.setTimeout(() => {
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
    return () => window.clearTimeout(id);
  }, [search, setUrl]);
  const query = useQuery({
    queryKey: ['players', { search: url.get('search') ?? '', role, club, page }],
    queryFn: ({ signal }) =>
      featureApi.players(
        {
          search: url.get('search') ?? undefined,
          role: role || undefined,
          club: club || undefined,
          page,
          size: 25,
        },
        signal,
      ),
    placeholderData: keepPreviousData,
  });
  const squad = useQuery({
    queryKey: queryKeys.squad,
    queryFn: ({ signal }) => featureApi.squad(auth.getAuthorization, signal),
    enabled: auth.status === 'authenticated',
    retry: false,
  });
  const add = useMutation({
    mutationFn: () =>
      featureApi.addPlayer(auth.getAuthorization, selected!.id, Number(purchasePrice)),
    onSuccess: (next) => {
      qc.setQueryData(queryKeys.squad, next);
      void qc.invalidateQueries({ queryKey: queryKeys.squadAnalysis });
      void qc.invalidateQueries({ queryKey: ['lineup'] });
      setSelected(null);
      setPurchasePrice('1');
    },
  });
  const duplicate = squad.data?.players.some((entry) => entry.playerId === selected?.id) ?? false;
  const missingSquad = squad.error instanceof AppError && squad.error.status === 404;
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
            <span className={styles.eyebrow}>Player intelligence</span>
            <h1>Giocatori</h1>
            <p className={styles.muted}>
              {query.data ? `${query.data.totalElements} profili reali` : 'Catalogo Serie A'}
            </p>
            <Link className={`${styles.button} ${styles.secondary}`} to="/players/history">
              Apri catalogo storico
            </Link>
          </div>
        </header>
        <section className={`${styles.panel} ${styles.filters}`} aria-label="Filtri giocatori">
          <label>
            Cerca
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Nome giocatore"
            />
          </label>
          <label>
            Ruolo
            <select value={role} onChange={(e) => update('role', e.target.value)}>
              <option value="">Tutti</option>
              {['P', 'D', 'C', 'A'].map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </label>
          <label>
            Club
            <input
              value={club}
              onChange={(e) => update('club', e.target.value)}
              placeholder="Club esatto"
            />
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
          {auth.status === 'authenticated' && missingSquad && (
            <p>
              Per acquistare dal catalogo, <Link to="/squad">crea prima la tua rosa</Link>.
            </p>
          )}
          {selected && squad.data && (
            <div className={styles.filters} aria-label="Acquista dal catalogo">
              <div className={styles.metric}>
                <span>Giocatore selezionato</span>
                <strong>{selected.name}</strong>
              </div>
              <label>
                Prezzo acquisto
                <input
                  type="number"
                  min="1"
                  value={purchasePrice}
                  onChange={(event) => setPurchasePrice(event.target.value)}
                />
              </label>
              <button
                className={styles.button}
                disabled={duplicate || Number(purchasePrice) < 1 || add.isPending}
                onClick={() => add.mutate()}
              >
                Conferma acquisto
              </button>
              <button
                className={`${styles.button} ${styles.secondary}`}
                onClick={() => setSelected(null)}
              >
                Annulla
              </button>
              {duplicate && <p className={styles.error}>Il giocatore è già nella rosa.</p>}
              {add.isError && (
                <p className={styles.error} role="alert">
                  {add.error instanceof AppError ? add.error.message : 'Acquisto rifiutato.'}
                </p>
              )}
            </div>
          )}
          {query.isPending ? (
            <div className={styles.status}>Caricamento catalogo…</div>
          ) : query.isError ? (
            <div className={`${styles.status} ${styles.error}`}>
              Contratto giocatori non disponibile.{' '}
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
                      <th>Ruolo</th>
                      <th>Club</th>
                      <th>Fonti</th>
                      {squad.data && <th>Rosa</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {query.data.content.map((p) => (
                      <tr key={p.id}>
                        <td>
                          <Link to={`/players/${p.id}`} state={{ from: '/players' }}>
                            {p.name}
                          </Link>
                        </td>
                        <td>
                          <span className={styles.role}>{p.fantacalcioRole}</span>
                        </td>
                        <td>{p.club}</td>
                        <td>{p.sourcesCount}</td>
                        {squad.data && (
                          <td>
                            <button
                              className={`${styles.button} ${styles.secondary}`}
                              disabled={squad.data.players.some((entry) => entry.playerId === p.id)}
                              onClick={() => setSelected({ id: p.id, name: p.name })}
                            >
                              {squad.data.players.some((entry) => entry.playerId === p.id)
                                ? 'Già in rosa'
                                : 'Aggiungi'}
                            </button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className={styles.cards}>
                {query.data.content.map((p) => (
                  <article className={styles.card} key={p.id}>
                    <Link to={`/players/${p.id}`} state={{ from: '/players' }}>
                      <strong>{p.name}</strong>
                    </Link>
                    <span>
                      <span className={styles.role}>{p.fantacalcioRole}</span> · {p.club}
                    </span>
                    {squad.data && (
                      <button
                        className={`${styles.button} ${styles.secondary}`}
                        disabled={squad.data.players.some((entry) => entry.playerId === p.id)}
                        onClick={() => setSelected({ id: p.id, name: p.name })}
                      >
                        {squad.data.players.some((entry) => entry.playerId === p.id)
                          ? 'Già in rosa'
                          : 'Aggiungi alla rosa'}
                      </button>
                    )}
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
