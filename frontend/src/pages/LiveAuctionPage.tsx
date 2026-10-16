import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AppShell } from '@/layouts/AppShell';
import { BackButton } from '@/components/navigation/BackButton';
import { useAuth } from '@/auth/useAuth';
import { featureApi, type AuctionDecision } from '@/features/core/api';
import { queryKeys } from '@/api/queryKeys';
import styles from '@/features/core/FeaturePage.module.css';

function positiveInteger(value: string): number | null {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function errorMessage(error: unknown): string | null {
  return error instanceof Error ? error.message : error ? 'Operazione non riuscita.' : null;
}

function newIdempotencyKey(sessionId: string): string {
  const random =
    typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `live-auction-${sessionId}-${random}`;
}

export function LiveAuctionPage() {
  const auth = useAuth();
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const [manualId, setManualId] = useState('');
  const [nominationPlayerId, setNominationPlayerId] = useState('');
  const [nominationPrice, setNominationPrice] = useState('1');
  const [proposedBid, setProposedBid] = useState('1');
  const [buyerType, setBuyerType] = useState<'USER' | 'OTHER'>('USER');
  const [paidPrice, setPaidPrice] = useState('1');
  const [decision, setDecision] = useState<AuctionDecision>();
  const [feedback, setFeedback] = useState<string>();
  const [operationError, setOperationError] = useState<unknown>();
  const [idempotencyKey, setIdempotencyKey] = useState(() => newIdempotencyKey('new'));

  const squad = useQuery({
    queryKey: queryKeys.squad,
    queryFn: ({ signal }) => featureApi.squad(auth.getAuthorization, signal),
    enabled: !sessionId,
  });
  const create = useMutation({
    mutationFn: () => featureApi.createSession(auth.getAuthorization, squad.data!.id),
    onSuccess: (session) => void navigate(`/auction/live/${session.id}`),
  });
  const state = useQuery({
    queryKey: queryKeys.auctionSession(sessionId),
    queryFn: ({ signal }) => featureApi.sessionState(auth.getAuthorization, sessionId!, signal),
    enabled: Boolean(sessionId),
    refetchInterval: () => (document.visibilityState === 'visible' ? 10000 : false),
  });

  const refresh = async (message: string) => {
    setFeedback(message);
    await state.refetch();
  };
  const activate = useMutation({
    mutationFn: () => featureApi.activateSession(auth.getAuthorization, sessionId!),
    onMutate: () => setOperationError(undefined),
    onError: (error) => setOperationError(error),
    onSuccess: () => {
      setOperationError(undefined);
      return refresh('Sessione attivata.');
    },
  });
  const nominate = useMutation({
    mutationFn: ({ playerId, price }: { playerId: number; price: number }) =>
      featureApi.nominatePlayer(auth.getAuthorization, sessionId!, playerId, price),
    onMutate: () => setOperationError(undefined),
    onError: (error) => setOperationError(error),
    onSuccess: (_, variables) => {
      setOperationError(undefined);
      setDecision(undefined);
      setProposedBid(String(variables.price));
      setPaidPrice(String(variables.price));
      return refresh('Nomination registrata.');
    },
  });
  const evaluate = useMutation({
    mutationFn: (bid: number) => featureApi.sessionDecision(auth.getAuthorization, sessionId!, bid),
    onMutate: () => setOperationError(undefined),
    onError: (error) => setOperationError(error),
    onSuccess: (result) => {
      setOperationError(undefined);
      setDecision(result);
      setFeedback('Offerta valutata sul contesto reale della sessione.');
    },
  });
  const purchase = useMutation({
    mutationFn: ({ playerId, price }: { playerId: number; price: number | null }) =>
      featureApi.purchasePlayer(auth.getAuthorization, sessionId!, {
        playerId,
        buyerType,
        paidPrice: price,
        idempotencyKey,
      }),
    onMutate: () => setOperationError(undefined),
    onError: (error) => setOperationError(error),
    onSuccess: () => {
      setOperationError(undefined);
      setDecision(undefined);
      setIdempotencyKey(newIdempotencyKey(sessionId!));
      return refresh(
        buyerType === 'USER'
          ? 'Aggiudicazione registrata e rosa aggiornata.'
          : 'Giocatore registrato come acquistato da un altro partecipante.',
      );
    },
  });
  const complete = useMutation({
    mutationFn: () => featureApi.completeSession(auth.getAuthorization, sessionId!),
    onMutate: () => setOperationError(undefined),
    onError: (error) => setOperationError(error),
    onSuccess: () => {
      setOperationError(undefined);
      return refresh('Sessione completata.');
    },
  });

  const availablePlayers =
    state.data?.players.filter((player) => player.availability === 'AVAILABLE') ?? [];
  const selectedNominationId = nominationPlayerId || String(availablePlayers[0]?.playerId ?? '');
  const currentPlayer = state.data?.players.find(
    (player) => player.playerId === state.data?.session.currentPlayerId,
  );
  const mutationPending =
    activate.isPending ||
    nominate.isPending ||
    evaluate.isPending ||
    purchase.isPending ||
    complete.isPending;
  const currentPurchasePrice = positiveInteger(paidPrice);
  const purchasePriceValid = buyerType === 'OTHER' || currentPurchasePrice !== null;

  return (
    <AppShell>
      <div className={styles.page}>
        {sessionId && <BackButton fallbackTo="/auction/live" />}
        <header className={styles.hero}>
          <div>
            <span className={styles.eyebrow}>Protected workspace</span>
            <h1>Live Auction</h1>
            <p className={styles.muted}>
              Aggiornamento prudente ogni 10 secondi, sospeso a scheda nascosta.
            </p>
          </div>
        </header>
        {!sessionId ? (
          <section className={styles.panel}>
            <h2>Avvia o recupera una sessione</h2>
            <p>La sessione è associata alla rosa e accessibile solo a owner o admin.</p>
            {squad.isPending ? (
              <p className={styles.muted}>Caricamento rosa personale…</p>
            ) : squad.isError ? (
              <div className={styles.status}>
                <p>Crea o compila prima una rosa personale con giocatori reali del catalogo.</p>
                <Link className={styles.button} to="/squad">
                  Configura la rosa
                </Link>
              </div>
            ) : squad.data ? (
              <div className={styles.grid}>
                <div className={styles.metric}>
                  <span>Rosa</span>
                  <strong>{squad.data.name}</strong>
                </div>
                <div className={styles.metric}>
                  <span>Giocatori</span>
                  <strong>
                    {squad.data.totalPlayers}/{squad.data.maximumPlayers}
                  </strong>
                </div>
                <div className={styles.metric}>
                  <span>Budget residuo</span>
                  <strong>{squad.data.remainingBudget}</strong>
                </div>
              </div>
            ) : null}
            {squad.data && squad.data.totalPlayers === 0 && (
              <p className={styles.muted}>
                La rosa è vuota: la sessione partirà senza acquisti e userà esclusivamente il
                catalogo reale.
              </p>
            )}
            {squad.data &&
              squad.data.totalPlayers > 0 &&
              squad.data.totalPlayers < squad.data.maximumPlayers && (
                <p className={styles.muted}>
                  Rosa in costruzione: mancano {squad.data.maximumPlayers - squad.data.totalPlayers}{' '}
                  giocatori.
                </p>
              )}
            <div className={styles.actions}>
              <button
                className={styles.button}
                disabled={!squad.data || create.isPending}
                onClick={() => create.mutate()}
              >
                {create.isPending ? 'Creazione…' : 'Crea sessione'}
              </button>
              <input
                aria-label="ID sessione"
                inputMode="numeric"
                value={manualId}
                onChange={(event) => setManualId(event.target.value)}
                placeholder="ID sessione esistente"
              />
              <button
                className={`${styles.button} ${styles.secondary}`}
                disabled={!positiveInteger(manualId)}
                onClick={() => void navigate(`/auction/live/${manualId}`)}
              >
                Recupera
              </button>
            </div>
            {create.isError && (
              <p className={styles.error} role="alert">
                {errorMessage(create.error)}
              </p>
            )}
          </section>
        ) : state.isPending ? (
          <section className={styles.panel}>Recupero sessione…</section>
        ) : state.isError ? (
          <section className={`${styles.panel} ${styles.error}`} role="alert">
            {errorMessage(state.error)} <Link to="/auction/live">Torna al launcher</Link>
          </section>
        ) : (
          <>
            <section className={styles.grid}>
              <div className={styles.metric}>
                <span>Stato</span>
                <strong>{state.data.session.status}</strong>
              </div>
              <div className={styles.metric}>
                <span>Budget residuo</span>
                <strong>{state.data.remainingBudget}</strong>
              </div>
              <div className={styles.metric}>
                <span>Prezzo corrente</span>
                <strong>{state.data.session.currentPrice ?? '—'}</strong>
              </div>
            </section>

            {feedback && <p role="status">{feedback}</p>}
            {operationError && (
              <p className={styles.error} role="alert">
                {errorMessage(operationError)}
              </p>
            )}

            {state.data.session.status === 'DRAFT' && (
              <section className={styles.panel}>
                <h2>Sessione pronta</h2>
                <p>Attivala per inizializzare il catalogo dei giocatori eleggibili.</p>
                <button
                  className={styles.button}
                  disabled={mutationPending}
                  onClick={() => activate.mutate()}
                >
                  {activate.isPending ? 'Attivazione…' : 'Attiva sessione'}
                </button>
              </section>
            )}

            {state.data.session.status === 'ACTIVE' && (
              <>
                <section className={styles.panel}>
                  <h2>Nomina giocatore</h2>
                  <div className={styles.actions}>
                    <select
                      aria-label="Giocatore da nominare"
                      value={selectedNominationId}
                      onChange={(event) => setNominationPlayerId(event.target.value)}
                    >
                      {availablePlayers.map((player) => (
                        <option key={player.playerId} value={player.playerId}>
                          {player.playerName} · {player.role}
                        </option>
                      ))}
                    </select>
                    <input
                      aria-label="Prezzo corrente nomination"
                      type="number"
                      min="1"
                      step="1"
                      value={nominationPrice}
                      onChange={(event) => setNominationPrice(event.target.value)}
                    />
                    <button
                      className={styles.button}
                      disabled={
                        mutationPending ||
                        !positiveInteger(selectedNominationId) ||
                        !positiveInteger(nominationPrice)
                      }
                      onClick={() =>
                        nominate.mutate({
                          playerId: positiveInteger(selectedNominationId)!,
                          price: positiveInteger(nominationPrice)!,
                        })
                      }
                    >
                      {nominate.isPending ? 'Nomination…' : 'Registra nomination'}
                    </button>
                  </div>
                  {availablePlayers.length === 0 && (
                    <p className={styles.muted}>Nessun giocatore disponibile nella sessione.</p>
                  )}
                </section>

                <section className={styles.panel}>
                  <h2>Giocatore corrente</h2>
                  {currentPlayer ? (
                    <>
                      <p>
                        <Link to={`/players/${currentPlayer.playerId}`}>
                          {currentPlayer.playerName}
                        </Link>{' '}
                        · ruolo {currentPlayer.role}
                      </p>
                      <div className={styles.actions}>
                        <input
                          aria-label="Offerta proposta"
                          type="number"
                          min="1"
                          step="1"
                          value={proposedBid}
                          onChange={(event) => setProposedBid(event.target.value)}
                        />
                        <button
                          className={styles.button}
                          disabled={mutationPending || !positiveInteger(proposedBid)}
                          onClick={() => evaluate.mutate(positiveInteger(proposedBid)!)}
                        >
                          {evaluate.isPending ? 'Valutazione…' : 'Valuta offerta'}
                        </button>
                      </div>
                      {decision && (
                        <div className={styles.status}>
                          <strong>{decision.action}</strong>
                          <p>
                            Offerta {decision.evaluatedBid}; budget dopo offerta{' '}
                            {decision.budgetAfterBid}.
                          </p>
                          {decision.reasons.length > 0 && <p>{decision.reasons.join(' · ')}</p>}
                          {decision.warnings.length > 0 && (
                            <p className={styles.error}>{decision.warnings.join(' · ')}</p>
                          )}
                        </div>
                      )}
                      <div className={styles.actions}>
                        <select
                          aria-label="Tipo aggiudicatario"
                          value={buyerType}
                          onChange={(event) => setBuyerType(event.target.value as 'USER' | 'OTHER')}
                        >
                          <option value="USER">La mia rosa</option>
                          <option value="OTHER">Altro partecipante</option>
                        </select>
                        <input
                          aria-label="Prezzo aggiudicazione"
                          type="number"
                          min="1"
                          step="1"
                          value={paidPrice}
                          onChange={(event) => setPaidPrice(event.target.value)}
                          placeholder={
                            buyerType === 'OTHER' ? 'Prezzo facoltativo' : 'Prezzo pagato'
                          }
                        />
                        <button
                          className={styles.button}
                          disabled={mutationPending || !purchasePriceValid}
                          onClick={() =>
                            purchase.mutate({
                              playerId: currentPlayer.playerId,
                              price: currentPurchasePrice,
                            })
                          }
                        >
                          {purchase.isPending ? 'Registrazione…' : 'Registra aggiudicazione'}
                        </button>
                      </div>
                    </>
                  ) : (
                    <p>Nessuna nomination attiva.</p>
                  )}
                  <p className={styles.muted}>
                    Versione stato {state.data.session.version}. Nessun realtime simulato.
                  </p>
                </section>

                <section className={styles.panel}>
                  <h2>Controlli sessione</h2>
                  <p>
                    {state.data.purchases.length} aggiudicazioni registrate;{' '}
                    {availablePlayers.length} giocatori ancora disponibili.
                  </p>
                  <button
                    className={`${styles.button} ${styles.secondary}`}
                    disabled={mutationPending}
                    onClick={() => complete.mutate()}
                  >
                    {complete.isPending ? 'Chiusura…' : 'Completa sessione'}
                  </button>
                </section>
              </>
            )}

            {state.data.session.status === 'COMPLETED' && (
              <section className={styles.panel}>
                <h2>Asta completata</h2>
                <p>
                  La sessione è chiusa. Le {state.data.purchases.length} aggiudicazioni restano
                  nello storico e non sono più modificabili.
                </p>
              </section>
            )}
          </>
        )}
      </div>
    </AppShell>
  );
}
