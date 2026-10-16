import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/auth/useAuth';
import { featureApi } from '@/features/core/api';
import { AppShell } from '@/layouts/AppShell';
import base from '@/features/core/FeaturePage.module.css';
import styles from './AuctionCopilot.module.css';

type StrategyMode = 'CONSERVATIVE' | 'BALANCED' | 'AGGRESSIVE';

type RankingPlayer = Awaited<ReturnType<typeof featureApi.ranking>>['players'][number];

const roleSlots: Record<string, number> = {
  P: 3,
  D: 8,
  C: 8,
  A: 6,
};

const modeLabels: Record<StrategyMode, string> = {
  CONSERVATIVE: 'Conservativa',
  BALANCED: 'Equilibrata',
  AGGRESSIVE: 'Aggressiva',
};

function clampScore(value: number): number {
  return Math.max(0, Math.min(100, Math.round(value)));
}

function tier(score: number): 'S' | 'A' | 'B' | 'C' | 'D' {
  if (score >= 90) return 'S';
  if (score >= 80) return 'A';
  if (score >= 70) return 'B';
  if (score >= 60) return 'C';
  return 'D';
}

function pricePlan(player: RankingPlayer, mode: StrategyMode) {
  const recommended = player.recommendedPrice;
  const ceiling = Math.max(recommended, player.maximumPrice);

  if (mode === 'CONSERVATIVE') {
    const target = Math.max(player.minimumPrice, Math.round(recommended * 0.9));
    const maximum = Math.max(target, Math.round(recommended + (ceiling - recommended) * 0.45));

    return { target, maximum: Math.min(ceiling, maximum) };
  }

  if (mode === 'AGGRESSIVE') {
    return {
      target: Math.min(ceiling, Math.max(recommended, Math.round(recommended * 1.08))),
      maximum: ceiling,
    };
  }

  return {
    target: recommended,
    maximum: Math.max(recommended, Math.round(recommended + (ceiling - recommended) * 0.72)),
  };
}

function estimatedSlot(player: RankingPlayer, players: RankingPlayer[]): number {
  const sameRole = players.filter((candidate) => candidate.fantasyRole === player.fantasyRole);
  const rolePosition =
    sameRole.findIndex((candidate) => candidate.playerId === player.playerId) + 1;

  if (rolePosition <= 0) return 1;

  const slots = roleSlots[player.fantasyRole] ?? 1;
  const groupSize = Math.max(1, Math.ceil(sameRole.length / slots));

  return Math.min(slots, Math.max(1, Math.ceil(rolePosition / groupSize)));
}

function confidence(player: RankingPlayer): number | null {
  return player.reliabilityScore == null ? null : clampScore(player.reliabilityScore);
}

function unavailable(player: RankingPlayer): boolean {
  const status = player.availabilityStatus?.toUpperCase();
  return status === 'INJURED' || status === 'UNAVAILABLE' || status === 'SUSPENDED';
}

export function AuctionPage() {
  const auth = useAuth();
  const [url, setUrl] = useSearchParams();

  const role = url.get('role') ?? '';
  const [search, setSearch] = useState('');
  const [mode, setMode] = useState<StrategyMode>('BALANCED');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [currentPrice, setCurrentPrice] = useState('1');

  const ranking = useQuery({
    queryKey: ['auction-copilot-ranking', role],
    queryFn: ({ signal }) => featureApi.ranking(role, signal),
  });

  const strategy = useQuery({
    queryKey: ['auction-copilot-strategy'],
    queryFn: ({ signal }) => featureApi.strategy(signal),
  });

  const squad = useQuery({
    queryKey: ['auction-copilot-squad', auth.username],
    queryFn: ({ signal }) => featureApi.squad(auth.getAuthorization, signal),
    enabled: auth.status === 'authenticated',
    retry: false,
  });

  const filteredPlayers = useMemo(() => {
    const players = ranking.data?.players ?? [];
    const normalized = search.trim().toLocaleLowerCase('it');

    if (!normalized) return players;

    return players.filter(
      (player) =>
        player.playerName.toLocaleLowerCase('it').includes(normalized) ||
        player.club.toLocaleLowerCase('it').includes(normalized),
    );
  }, [ranking.data, search]);

  const selected =
    ranking.data?.players.find((player) => player.playerId === selectedId) ??
    filteredPlayers[0] ??
    null;

  const selectedPlan = selected ? pricePlan(selected, mode) : null;

  const bid = Number(currentPrice);
  const validBid = Number.isInteger(bid) && bid > 0;

  const roleState = selected && squad.data ? squad.data.roles[selected.fantasyRole] : undefined;

  const roleFull = Boolean(roleState && roleState.remaining <= 0);

  const remainingSlots =
    squad.data == null ? null : Math.max(0, squad.data.maximumPlayers - squad.data.totalPlayers);

  const minimumReserve = remainingSlots == null ? null : Math.max(0, remainingSlots - 1);

  const budgetAfterBid = squad.data && validBid ? squad.data.remainingBudget - bid : null;

  const budgetSafe =
    budgetAfterBid == null || minimumReserve == null || budgetAfterBid >= minimumReserve;

  const risk = selected?.riskScore == null ? null : clampScore(selected.riskScore);

  const decision = useMemo(() => {
    if (!selected || !selectedPlan || !validBid) return null;

    if (roleFull) {
      return {
        action: 'STOP',
        reason: 'Gli slot del ruolo sono già completi nella tua rosa.',
      };
    }

    if (!budgetSafe) {
      return {
        action: 'STOP',
        reason: 'Questa offerta non conserva un credito per ogni slot ancora libero.',
      };
    }

    if (unavailable(selected)) {
      return {
        action: 'ASPETTA',
        reason: 'Lo stato di disponibilità reale richiede prudenza prima di rilanciare.',
      };
    }

    if (risk != null && risk >= 75 && bid > selected.minimumPrice) {
      return {
        action: 'ASPETTA',
        reason: 'Il rischio disponibile è elevato rispetto al prezzo corrente.',
      };
    }

    if (bid <= selectedPlan.target) {
      return {
        action: 'COMPRA',
        reason: `Il prezzo è dentro il target della strategia ${modeLabels[mode]}.`,
      };
    }

    if (bid <= selectedPlan.maximum) {
      return {
        action: 'RILANCIA',
        reason: 'Il prezzo supera il target ma resta sotto il tetto massimo calcolato.',
      };
    }

    return {
      action: 'STOP',
      reason: 'Il prezzo ha superato il tetto massimo della strategia selezionata.',
    };
  }, [selected, selectedPlan, validBid, bid, roleFull, budgetSafe, risk, mode]);

  const alternatives = useMemo(() => {
    if (!selected || !selectedPlan || !ranking.data) return [];

    return ranking.data.players
      .filter(
        (candidate) =>
          candidate.playerId !== selected.playerId &&
          candidate.fantasyRole === selected.fantasyRole &&
          candidate.recommendedPrice <= selectedPlan.maximum,
      )
      .slice(0, 3);
  }, [ranking.data, selected, selectedPlan]);

  const squadFit = (() => {
    if (!selected) return '—';
    if (auth.status !== 'authenticated') return 'Accedi per valutare la tua rosa';
    if (!squad.data) return 'Rosa non disponibile';
    if (!roleState) return 'Ruolo non configurato';
    if (roleState.remaining <= 0) return 'RUOLO COMPLETO';
    if (roleState.remaining >= 3) return 'ALTA NECESSITÀ';
    return 'UTILE';
  })();

  return (
    <AppShell>
      <div className={base.page}>
        <header className={base.hero}>
          <div>
            <span className={base.eyebrow}>Assistente asta</span>
            <h1>Auction Copilot</h1>
            <p className={base.muted}>
              Cerca il giocatore chiamato all&apos;asta e ottieni subito valore, fascia, prezzo
              target, tetto massimo e indicazione operativa.
            </p>
          </div>
        </header>

        <section className={`${base.panel} ${styles.controls}`} aria-label="Configurazione asta">
          <label>
            Cerca giocatore o club
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Es. Lautaro, Inter…"
            />
          </label>

          <label>
            Ruolo
            <select
              value={role}
              onChange={(event) => setUrl(event.target.value ? { role: event.target.value } : {})}
            >
              <option value="">Tutti</option>
              <option value="P">Portieri</option>
              <option value="D">Difensori</option>
              <option value="C">Centrocampisti</option>
              <option value="A">Attaccanti</option>
            </select>
          </label>

          <label>
            Strategia
            <select
              aria-label="Strategia asta"
              value={mode}
              onChange={(event) => setMode(event.target.value as StrategyMode)}
            >
              <option value="CONSERVATIVE">Conservativa</option>
              <option value="BALANCED">Equilibrata</option>
              <option value="AGGRESSIVE">Aggressiva</option>
            </select>
          </label>
        </section>

        {strategy.data && (
          <section className={styles.budgetStrip} aria-label="Piano budget">
            <div>
              <span>Budget lega</span>
              <strong>{strategy.data.totalBudget}</strong>
            </div>
            <div>
              <span>Spesa pianificata</span>
              <strong>{strategy.data.totalPlannedSpending}</strong>
            </div>
            <div>
              <span>Residuo piano</span>
              <strong>{strategy.data.expectedRemainingBudget}</strong>
            </div>
            {squad.data && (
              <div>
                <span>Il tuo budget residuo</span>
                <strong>{squad.data.remainingBudget}</strong>
              </div>
            )}
          </section>
        )}

        <section className={base.panel} aria-live="polite">
          <div className={styles.sectionTitle}>
            <div>
              <h2>Giocatori</h2>
              <p className={base.muted}>
                Il FantaScore usa lo score asta reale già prodotto dal motore Fantafoot.
              </p>
            </div>
            {ranking.data && <strong>{filteredPlayers.length} risultati</strong>}
          </div>

          {ranking.isPending ? (
            <p>Calcolo mercato asta…</p>
          ) : ranking.isError ? (
            <p className={base.error}>
              Ranking non disponibile.{' '}
              <button className={base.button} onClick={() => void ranking.refetch()}>
                Riprova
              </button>
            </p>
          ) : filteredPlayers.length === 0 ? (
            <p>Nessun giocatore corrisponde ai filtri.</p>
          ) : (
            <div className={styles.playerGrid}>
              {filteredPlayers.slice(0, 40).map((player) => {
                const score = clampScore(player.intelligenceScore);
                const active = selected?.playerId === player.playerId;

                return (
                  <button
                    type="button"
                    key={player.playerId}
                    className={`${styles.playerCard} ${active ? styles.playerCardActive : ''}`}
                    onClick={() => {
                      setSelectedId(player.playerId);
                      setCurrentPrice(String(Math.max(1, player.minimumPrice)));
                    }}
                  >
                    <span className={styles.playerIdentity}>
                      <strong>{player.playerName}</strong>
                      <small>
                        {player.club} · {player.fantasyRole}
                      </small>
                    </span>
                    <span className={styles.scoreBadge}>{score}</span>
                    <span className={styles.tierBadge}>Tier {tier(score)}</span>
                    <span>{player.recommendedPrice} cr</span>
                  </button>
                );
              })}
            </div>
          )}
        </section>

        {selected && selectedPlan && ranking.data && (
          <section className={styles.copilotGrid}>
            <article className={`${base.panel} ${styles.focusCard}`}>
              <div className={styles.sectionTitle}>
                <div>
                  <span className={base.eyebrow}>Giocatore selezionato</span>
                  <h2>{selected.playerName}</h2>
                  <p className={base.muted}>
                    {selected.club} · ruolo {selected.fantasyRole}
                  </p>
                </div>
                <Link
                  className={base.button}
                  to={`/players/${selected.playerId}`}
                  state={{ from: '/auction' }}
                >
                  Scheda completa
                </Link>
              </div>

              <div className={styles.metrics}>
                <div>
                  <span>FantaScore</span>
                  <strong>{clampScore(selected.intelligenceScore)}/100</strong>
                </div>
                <div>
                  <span>Tier</span>
                  <strong>{tier(clampScore(selected.intelligenceScore))}</strong>
                </div>
                <div>
                  <span>Slot stimato</span>
                  <strong>
                    {estimatedSlot(selected, ranking.data.players)}/
                    {roleSlots[selected.fantasyRole] ?? 1}
                  </strong>
                </div>
                <div>
                  <span>Prezzo indicativo</span>
                  <strong>{selected.recommendedPrice}</strong>
                </div>
                <div>
                  <span>Target {modeLabels[mode]}</span>
                  <strong>{selectedPlan.target}</strong>
                </div>
                <div>
                  <span>Tetto massimo</span>
                  <strong>{selectedPlan.maximum}</strong>
                </div>
                <div>
                  <span>Rischio</span>
                  <strong>{risk == null ? 'Dato non disponibile' : `${risk}/100`}</strong>
                </div>
                <div>
                  <span>Confidence</span>
                  <strong>
                    {confidence(selected) == null
                      ? 'Dato non disponibile'
                      : `${confidence(selected)}/100`}
                  </strong>
                </div>
              </div>

              <p className={styles.explanation}>
                Range motore: <strong>{selected.minimumPrice}</strong>–
                <strong>{selected.maximumPrice}</strong> crediti. Il target e il tetto strategico
                sono derivati da questo range, senza modificare i dati sorgente.
              </p>
            </article>

            <article className={`${base.panel} ${styles.decisionCard}`}>
              <span className={base.eyebrow}>Decisione live</span>
              <h2>Quanto stanno offrendo?</h2>

              <label className={styles.bidInput}>
                Prezzo corrente
                <input
                  aria-label="Prezzo corrente asta"
                  type="number"
                  min="1"
                  step="1"
                  value={currentPrice}
                  onChange={(event) => setCurrentPrice(event.target.value)}
                />
              </label>

              {decision ? (
                <div
                  className={`${styles.decision} ${styles[`decision${decision.action}`] ?? ''}`}
                  role="status"
                >
                  <strong>{decision.action}</strong>
                  <p>{decision.reason}</p>
                </div>
              ) : (
                <p className={base.muted}>Inserisci un prezzo valido.</p>
              )}

              <dl className={styles.decisionDetails}>
                <div>
                  <dt>Fit rosa</dt>
                  <dd>{squadFit}</dd>
                </div>
                <div>
                  <dt>Budget dopo acquisto</dt>
                  <dd>{budgetAfterBid == null ? 'Accedi per calcolarlo' : budgetAfterBid}</dd>
                </div>
                <div>
                  <dt>Riserva minima</dt>
                  <dd>{minimumReserve == null ? 'Accedi per calcolarla' : minimumReserve}</dd>
                </div>
                <div>
                  <dt>Raccomandazione motore</dt>
                  <dd>{selected.finalRecommendation}</dd>
                </div>
              </dl>
            </article>
          </section>
        )}

        {selected && (
          <section className={styles.twoColumns}>
            <article className={base.panel}>
              <h2>Perché</h2>

              {selected.strengths.length > 0 ? (
                <>
                  <h3>Punti a favore</h3>
                  <ul className={base.list}>
                    {selected.strengths.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </>
              ) : (
                <p className={base.muted}>Nessun punto di forza aggiuntivo disponibile.</p>
              )}

              {selected.risks.length > 0 && (
                <>
                  <h3>Rischi</h3>
                  <ul className={base.list}>
                    {selected.risks.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </>
              )}
            </article>

            <article className={base.panel}>
              <h2>Alternative</h2>

              {alternatives.length === 0 ? (
                <p className={base.muted}>Nessuna alternativa comparabile nel range disponibile.</p>
              ) : (
                <div className={styles.alternatives}>
                  {alternatives.map((alternative) => (
                    <button
                      type="button"
                      key={alternative.playerId}
                      onClick={() => {
                        setSelectedId(alternative.playerId);
                        setCurrentPrice(String(Math.max(1, alternative.minimumPrice)));
                      }}
                    >
                      <span>
                        <strong>{alternative.playerName}</strong>
                        <small>{alternative.club}</small>
                      </span>
                      <span>
                        {clampScore(alternative.intelligenceScore)}/100 ·{' '}
                        {alternative.recommendedPrice} cr
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </article>
          </section>
        )}

        <section className={base.panel}>
          <h2>Piano budget per ruolo</h2>

          {strategy.isPending ? (
            <p>Elaborazione strategia…</p>
          ) : strategy.isError ? (
            <p className={base.error}>Strategia non disponibile.</p>
          ) : strategy.data ? (
            <>
              <p>{strategy.data.strategySummary}</p>
              <div className={styles.roleBudgetGrid}>
                {strategy.data.roles.map((item) => (
                  <article key={item.role}>
                    <span>Ruolo {item.role}</span>
                    <strong>{item.allocatedBudget} crediti</strong>
                    <small>
                      {item.requiredSlots} slot · {item.budgetPercentage}%
                    </small>
                  </article>
                ))}
              </div>

              {strategy.data.globalWarnings.length > 0 && (
                <ul className={base.list}>
                  {strategy.data.globalWarnings.map((warning) => (
                    <li key={warning}>{warning}</li>
                  ))}
                </ul>
              )}
            </>
          ) : null}
        </section>

        <section className={`${base.panel} ${styles.note}`}>
          <strong>Come leggere il Copilot</strong>
          <p>
            FantaScore, prezzi, rischio, affidabilità e disponibilità provengono dai motori reali
            Fantafoot. Tier, slot e profilo Conservativa/Equilibrata/Aggressiva sono trasformazioni
            trasparenti di quei valori. Se un dato manca viene mostrato come non disponibile:
            nessuna informazione calcistica viene inventata.
          </p>
        </section>
      </div>
    </AppShell>
  );
}
