import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/auth/useAuth';
import { AppError } from '@/api/errors/AppError';
import { queryKeys } from '@/api/queryKeys';
import { featureApi, type Squad } from '@/features/core/api';
import styles from '@/features/core/FeaturePage.module.css';
import { AppShell } from '@/layouts/AppShell';

function message(error: unknown) {
  return error instanceof AppError ? error.message : 'Operazione non riuscita.';
}

export function SquadPage() {
  const auth = useAuth();
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const [playerId, setPlayerId] = useState('');
  const [price, setPrice] = useState('1');
  const [squadName, setSquadName] = useState('');
  const [editingPlayerId, setEditingPlayerId] = useState<number | null>(null);
  const [editingPrice, setEditingPrice] = useState('');

  const squad = useQuery({
    queryKey: queryKeys.squad,
    queryFn: ({ signal }) => featureApi.squad(auth.getAuthorization, signal),
  });
  const missingSquad = squad.error instanceof AppError && squad.error.status === 404;
  const analysis = useQuery({
    queryKey: queryKeys.squadAnalysis,
    queryFn: ({ signal }) => featureApi.analysis(auth.getAuthorization, signal),
    enabled: squad.isSuccess,
  });
  const league = useQuery({
    queryKey: queryKeys.leagueConfiguration,
    queryFn: ({ signal }) => featureApi.leagueConfiguration(signal),
    enabled: missingSquad,
  });

  const acceptSquad = (next: Squad) => {
    qc.setQueryData(queryKeys.squad, next);
    void qc.invalidateQueries({ queryKey: queryKeys.squadAnalysis });
    void qc.invalidateQueries({ queryKey: ['lineup'] });
  };
  const createSquad = useMutation({
    mutationFn: () =>
      featureApi.createSquad(auth.getAuthorization, league.data!.id, squadName.trim()),
    onSuccess: acceptSquad,
  });
  const candidates = useQuery({
    queryKey: queryKeys.squadCandidates(search),
    queryFn: ({ signal }) =>
      featureApi.players({ search: search.trim(), page: 0, size: 10 }, signal),
    enabled: squad.isSuccess && search.trim().length >= 2,
  });
  const add = useMutation({
    mutationFn: () => featureApi.addPlayer(auth.getAuthorization, Number(playerId), Number(price)),
    onSuccess: (next) => {
      acceptSquad(next);
      setPlayerId('');
      setSearch('');
      setPrice('1');
    },
  });
  const remove = useMutation({
    mutationFn: (id: number) => featureApi.removePlayer(auth.getAuthorization, id),
    onSuccess: acceptSquad,
  });
  const updatePrice = useMutation({
    mutationFn: ({ id, value }: { id: number; value: number }) =>
      featureApi.updatePlayerPrice(auth.getAuthorization, id, value),
    onSuccess: (next) => {
      acceptSquad(next);
      setEditingPlayerId(null);
    },
  });

  const current = squad.data;
  const selected = candidates.data?.content.find((candidate) => candidate.id === Number(playerId));
  const roleSummary = selected ? current?.roles[selected.fantacalcioRole] : undefined;
  const duplicate = current?.players.some((entry) => entry.playerId === selected?.id) ?? false;
  const roleFull = roleSummary ? roleSummary.current >= roleSummary.maximum : false;
  const emptySlotsAfterAdd = current
    ? Math.max(0, current.maximumPlayers - current.totalPlayers - 1)
    : 0;
  const maxAffordable = current ? current.remainingBudget - emptySlotsAfterAdd : 0;
  const invalidPrice = Number(price) < 1 || Number(price) > maxAffordable;

  return (
    <AppShell>
      <div className={styles.page}>
        <header className={styles.hero}>
          <div>
            <span className={styles.eyebrow}>Gestione rosa reale</span>
            <h1>La tua rosa</h1>
            <p className={styles.muted}>
              Giocatori del catalogo, budget e limiti definiti dalla lega.
            </p>
          </div>
          <Link className={styles.button} to="/players">
            Apri il catalogo
          </Link>
        </header>

        {squad.isPending ? (
          <section className={styles.panel}>Caricamento rosa…</section>
        ) : missingSquad ? (
          <section className={styles.panel}>
            <h2>Crea la tua rosa personale</h2>
            <p>Partirà vuota: nessun giocatore o prezzo verrà generato automaticamente.</p>
            {league.isPending ? (
              <p>Caricamento configurazione lega…</p>
            ) : league.isError ? (
              <p className={styles.error}>Configurazione lega non disponibile.</p>
            ) : (
              <>
                <div className={styles.grid}>
                  <div className={styles.metric}>
                    <span>Squadre</span>
                    <strong>{league.data.teamsCount}</strong>
                  </div>
                  <div className={styles.metric}>
                    <span>Budget iniziale</span>
                    <strong>{league.data.totalBudget}</strong>
                  </div>
                  {Object.entries({
                    P: league.data.goalkeepersCount,
                    D: league.data.defendersCount,
                    C: league.data.midfieldersCount,
                    A: league.data.forwardsCount,
                  }).map(([role, maximum]) => (
                    <div className={styles.metric} key={role}>
                      <span>{role}</span>
                      <strong>{maximum}</strong>
                    </div>
                  ))}
                </div>
                <p className={styles.muted}>
                  Difesa modificata: {league.data.defenseModifier ? 'sì' : 'no'} · Capitano:{' '}
                  {league.data.captainBonus ? 'sì' : 'no'}
                </p>
                <div className={styles.filters} aria-label="Crea rosa">
                  <label>
                    Nome rosa
                    <input
                      value={squadName}
                      maxLength={100}
                      onChange={(event) => setSquadName(event.target.value)}
                    />
                  </label>
                  <button
                    className={styles.button}
                    disabled={!squadName.trim() || createSquad.isPending}
                    onClick={() => createSquad.mutate()}
                  >
                    Crea rosa vuota
                  </button>
                </div>
              </>
            )}
            {createSquad.isError && (
              <p className={styles.error} role="alert">
                {message(createSquad.error)}
              </p>
            )}
          </section>
        ) : squad.isError ? (
          <section className={styles.panel}>
            <h2>Rosa non disponibile</h2>
            <p className={styles.error} role="alert">
              {message(squad.error)}
            </p>
          </section>
        ) : current ? (
          <>
            <section className={styles.grid}>
              <div className={styles.metric}>
                <span>Budget iniziale</span>
                <strong>{current.initialBudget}</strong>
              </div>
              <div className={styles.metric}>
                <span>Speso</span>
                <strong>{current.spentBudget}</strong>
              </div>
              <div className={styles.metric}>
                <span>Residuo</span>
                <strong>{current.remainingBudget}</strong>
              </div>
              <div className={styles.metric}>
                <span>Giocatori</span>
                <strong>
                  {current.totalPlayers}/{current.maximumPlayers}
                </strong>
              </div>
            </section>
            <section className={styles.panel}>
              <h2>{current.name}</h2>
              <div className={styles.grid}>
                {Object.entries(current.roles).map(([role, counts]) => (
                  <div className={styles.metric} key={role}>
                    <span>
                      {role} ·{' '}
                      {counts.current >= counts.maximum
                        ? 'completo'
                        : `mancano ${counts.remaining}`}
                    </span>
                    <strong>
                      {counts.current}/{counts.maximum}
                    </strong>
                  </div>
                ))}
              </div>
            </section>
            <section className={styles.panel}>
              <h2>Aggiungi un giocatore reale</h2>
              <div className={styles.filters} aria-label="Aggiungi giocatore">
                <label>
                  Cerca nel catalogo
                  <input
                    value={search}
                    onChange={(event) => {
                      setSearch(event.target.value);
                      setPlayerId('');
                    }}
                  />
                </label>
                <label>
                  Giocatore
                  <select value={playerId} onChange={(event) => setPlayerId(event.target.value)}>
                    <option value="">Seleziona</option>
                    {candidates.data?.content.map((player) => (
                      <option key={player.id} value={player.id}>
                        {player.name} · {player.club} · {player.fantacalcioRole}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Prezzo acquisto
                  <input
                    type="number"
                    min="1"
                    max={Math.max(1, maxAffordable)}
                    value={price}
                    onChange={(event) => setPrice(event.target.value)}
                  />
                </label>
                <button
                  className={styles.button}
                  disabled={!selected || duplicate || roleFull || invalidPrice || add.isPending}
                  onClick={() => add.mutate()}
                >
                  Aggiungi alla rosa
                </button>
              </div>
              {search.trim().length > 0 && search.trim().length < 2 && (
                <p className={styles.muted}>Inserisci almeno due caratteri.</p>
              )}
              {duplicate && <p className={styles.error}>Il giocatore è già presente nella rosa.</p>}
              {roleFull && (
                <p className={styles.error}>Il reparto {selected?.fantacalcioRole} è completo.</p>
              )}
              {selected && invalidPrice && (
                <p className={styles.error}>
                  Prezzo non valido: puoi spendere al massimo {Math.max(0, maxAffordable)}{' '}
                  mantenendo un credito per ogni posto libero.
                </p>
              )}
              {add.isError && (
                <p className={styles.error} role="alert">
                  {message(add.error)}
                </p>
              )}
              {current.players.length === 0 ? (
                <p>Nessun giocatore ancora inserito.</p>
              ) : (
                <div className={styles.tableWrap}>
                  <table className={styles.table}>
                    <thead>
                      <tr>
                        <th>Giocatore</th>
                        <th>Ruolo</th>
                        <th>Club</th>
                        <th>Prezzo</th>
                        <th>Azioni</th>
                      </tr>
                    </thead>
                    <tbody>
                      {current.players.map((entry) => (
                        <tr key={entry.id}>
                          <td>
                            <Link to={`/players/${entry.playerId}`} state={{ from: '/squad' }}>
                              {entry.playerName}
                            </Link>
                          </td>
                          <td>
                            <span className={styles.role}>{entry.role}</span>
                          </td>
                          <td>{entry.club}</td>
                          <td>
                            {editingPlayerId === entry.playerId ? (
                              <input
                                aria-label={`Prezzo ${entry.playerName}`}
                                type="number"
                                min="1"
                                value={editingPrice}
                                onChange={(event) => setEditingPrice(event.target.value)}
                              />
                            ) : (
                              entry.purchasePrice
                            )}
                          </td>
                          <td>
                            {editingPlayerId === entry.playerId ? (
                              <>
                                <button
                                  className={styles.button}
                                  disabled={Number(editingPrice) < 1 || updatePrice.isPending}
                                  onClick={() =>
                                    updatePrice.mutate({
                                      id: entry.playerId,
                                      value: Number(editingPrice),
                                    })
                                  }
                                >
                                  Salva
                                </button>{' '}
                                <button
                                  className={`${styles.button} ${styles.secondary}`}
                                  onClick={() => setEditingPlayerId(null)}
                                >
                                  Annulla
                                </button>
                              </>
                            ) : (
                              <>
                                <button
                                  className={`${styles.button} ${styles.secondary}`}
                                  onClick={() => {
                                    setEditingPlayerId(entry.playerId);
                                    setEditingPrice(String(entry.purchasePrice));
                                  }}
                                >
                                  Modifica prezzo
                                </button>{' '}
                                <button
                                  className={`${styles.button} ${styles.secondary}`}
                                  disabled={remove.isPending}
                                  onClick={() => remove.mutate(entry.playerId)}
                                >
                                  Rimuovi
                                </button>
                              </>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {(remove.isError || updatePrice.isError) && (
                <p className={styles.error} role="alert">
                  {message(remove.error ?? updatePrice.error)}
                </p>
              )}
            </section>
          </>
        ) : null}

        {analysis.data && (
          <section className={styles.panel}>
            <h2>Analisi rosa</h2>
            <p>{analysis.data.summary}</p>
            {analysis.data.globalWarnings.length > 0 && (
              <ul className={styles.list}>
                {analysis.data.globalWarnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            )}
          </section>
        )}
      </div>
    </AppShell>
  );
}
