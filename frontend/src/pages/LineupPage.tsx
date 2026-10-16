import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { AppShell } from '@/layouts/AppShell';
import { useAuth } from '@/auth/useAuth';
import { intelligenceApi, type LineupPlayer } from '@/features/intelligence/api';
import { featureApi } from '@/features/core/api';
import { queryKeys } from '@/api/queryKeys';
import { AppError } from '@/api/errors/AppError';
import shared from '@/features/core/FeaturePage.module.css';
import styles from '@/features/intelligence/Intelligence.module.css';
function Player({ player }: { player: LineupPlayer }) {
  return (
    <article className={styles.player}>
      <strong>{player.playerName}</strong>
      <small>
        {player.club} · {player.role}
      </small>
      <small>
        {player.home ? 'Casa' : 'Trasferta'} vs {player.opponent}
      </small>
      <span className={styles.badge}>{player.selectionScore ?? 'N/D'}</span>
    </article>
  );
}

function arrayOrEmpty<T>(value: T[] | null | undefined): T[] {
  return Array.isArray(value) ? value : [];
}

function recordOrEmpty<T>(value: Record<string, T> | null | undefined): Record<string, T> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

const missingLineupResponse = new AppError('Risposta lineup assente', {
  kind: 'invalid-response',
});

function lineupErrorContent(error: unknown): { title: string; detail: string } {
  if (!(error instanceof AppError)) {
    return {
      title: 'Formazione non disponibile',
      detail: 'La risposta del backend non è conforme al contratto della formazione.',
    };
  }
  switch (error.kind) {
    case 'not-found':
      return {
        title: 'Formazione non disponibile',
        detail: `Il motore non riconosce la rosa per la stagione richiesta: ${error.message}.`,
      };
    case 'invalid-response':
      return {
        title: 'Formazione non disponibile',
        detail: 'La risposta del backend non è conforme al contratto della formazione.',
      };
    case 'network':
    case 'timeout':
      return {
        title: 'Formazione non disponibile',
        detail: 'Il servizio di calcolo non è raggiungibile da questo browser.',
      };
    case 'server':
      return {
        title: 'Formazione non disponibile',
        detail: 'Errore interno del motore di calcolo.',
      };
    default:
      return {
        title: 'Formazione non disponibile',
        detail: error.status
          ? `Richiesta non riuscita (HTTP ${error.status}): ${error.message}`
          : error.message,
      };
  }
}

function LineupErrorState({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const content = lineupErrorContent(error);
  return (
    <section className={`${shared.panel} ${shared.status} ${shared.error}`}>
      <h2>{content.title}</h2>
      <p>{content.detail}</p>
      {error instanceof AppError && error.kind === 'not-found' && (
        <Link className={shared.button} to="/squad">
          Verifica la rosa
        </Link>
      )}
      <button className={shared.button} onClick={onRetry}>
        Riprova
      </button>
    </section>
  );
}
export function LineupPage() {
  const auth = useAuth();
  const [url, setUrl] = useSearchParams();
  const matchday = Math.min(38, Math.max(1, Number(url.get('matchday') ?? 1)));
  const squad = useQuery({
    queryKey: queryKeys.squad,
    queryFn: ({ signal }) => featureApi.squad(auth.getAuthorization, signal),
    enabled: auth.status === 'authenticated',
  });
  const query = useQuery({
    queryKey: queryKeys.lineup(squad.data?.season, matchday, squad.data?.id),
    queryFn: ({ signal }) =>
      intelligenceApi.lineup(
        matchday,
        squad.data?.season,
        squad.data?.id,
        auth.getAuthorization,
        signal,
      ),
    enabled: auth.status === 'authenticated' && squad.isSuccess && squad.data.totalPlayers >= 11,
    retry: false,
  });
  // Keep rendering safe even if a cached/legacy payload bypasses the runtime contract.
  const retryLineup = () => void query.refetch();
  const lineup = query.data;
  const starters = arrayOrEmpty(lineup?.starters);
  const bench = arrayOrEmpty(lineup?.bench);
  const warnings = arrayOrEmpty(lineup?.warnings);
  const roles = recordOrEmpty(lineup?.roles);
  const roleEntries = Object.entries(roles);
  const missingRoles = roleEntries.filter(
    ([, summary]) => typeof summary?.missing === 'number' && summary.missing > 0,
  );
  const byRole = (role: string) => starters.filter((player) => player.role === role);
  return (
    <AppShell>
      <div className={shared.page}>
        <header className={shared.hero}>
          <div>
            <span className={shared.eyebrow}>Starting XI engine</span>
            <h1>Formazione</h1>
            <p className={shared.muted}>Selezione e motivazioni prodotte dal motore reale.</p>
          </div>
        </header>
        <section className={`${shared.panel} ${shared.filters}`}>
          <label>
            Giornata
            <select value={matchday} onChange={(e) => setUrl({ matchday: e.target.value })}>
              {Array.from({ length: 38 }, (_, i) => i + 1).map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </label>
          {squad.data && (
            <div className={shared.metric}>
              <span>Rosa attiva</span>
              <strong>{squad.data.name}</strong>
            </div>
          )}
        </section>
        {auth.status !== 'authenticated' ? (
          <section className={`${shared.panel} ${shared.status}`}>
            <h2>Accedi per configurare la formazione</h2>
            <p>La formazione consigliata richiede una rosa personale reale.</p>
            <Link className={shared.button} to="/login" state={{ from: '/lineup' }}>
              Vai al login
            </Link>
          </section>
        ) : squad.isPending ? (
          <section className={`${shared.panel} ${shared.status}`}>Caricamento rosa…</section>
        ) : squad.error instanceof AppError && squad.error.status === 404 ? (
          <section className={`${shared.panel} ${shared.status}`}>
            <h2>Crea o compila prima la tua rosa</h2>
            <p>Non esiste ancora una rosa personale attiva per questa stagione.</p>
            <Link className={shared.button} to="/squad">
              Configura la rosa
            </Link>
          </section>
        ) : squad.isError ? (
          <section className={`${shared.panel} ${shared.status} ${shared.error}`}>
            Rosa non disponibile. Riprova più tardi.
          </section>
        ) : squad.data.totalPlayers < 11 ? (
          <section className={`${shared.panel} ${shared.status}`}>
            <h2>Rosa incompleta</h2>
            <p>
              Servono almeno 11 giocatori reali per calcolare la formazione:{' '}
              {squad.data.totalPlayers}/11 inseriti.
            </p>
            <Link className={shared.button} to="/squad">
              Completa la rosa
            </Link>
          </section>
        ) : query.isPending ? (
          <section className={`${shared.panel} ${shared.status}`}>Calcolo formazione…</section>
        ) : query.isError ? (
          <LineupErrorState error={query.error} onRetry={() => void query.refetch()} />
        ) : !lineup ? (
          <LineupErrorState error={missingLineupResponse} onRetry={retryLineup} />
        ) : starters.length === 0 ? (
          <section className={`${shared.panel} ${shared.status}`}>
            <h2>Nessuna formazione disponibile</h2>
            <p>
              {lineup.complete
                ? 'Il motore non ha prodotto titolari per questa giornata.'
                : `Il motore ha selezionato ${lineup.selectedPlayers} su 11: mancano ${lineup.missingPlayers} giocatori classificabili con evidenza reale.`}
            </p>
            {missingRoles.length > 0 && (
              <p>
                Reparti scoperti:{' '}
                {missingRoles
                  .map(([role, summary]) => `${role} (${summary?.missing ?? 'N/D'})`)
                  .join(' · ')}
                .
              </p>
            )}
            {warnings.length > 0 && (
              <ul className={shared.list}>
                {warnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            )}
            <Link className={shared.button} to="/squad">
              Completa la rosa con giocatori reali
            </Link>
          </section>
        ) : (
          <>
            <section className={shared.grid}>
              <div className={shared.metric}>
                <span>Modulo ottimale</span>
                <strong>{lineup.formation}</strong>
              </div>
              <div className={shared.metric}>
                <span>Score formazione</span>
                <strong>{lineup.formationScore}</strong>
              </div>
              <div className={shared.metric}>
                <span>Selezionati</span>
                <strong>{lineup.selectedPlayers}/11</strong>
              </div>
            </section>
            <section className={styles.pitch} aria-label={`Campo formazione ${lineup.formation}`}>
              <div className={styles.line}>
                {byRole('A').map((p) => (
                  <Player key={p.playerId} player={p} />
                ))}
              </div>
              <div className={styles.line}>
                {byRole('C').map((p) => (
                  <Player key={p.playerId} player={p} />
                ))}
              </div>
              <div className={styles.line}>
                {byRole('D').map((p) => (
                  <Player key={p.playerId} player={p} />
                ))}
              </div>
              <div className={styles.line}>
                {byRole('P').map((p) => (
                  <Player key={p.playerId} player={p} />
                ))}
              </div>
            </section>
            <section className={shared.panel}>
              <h2>Panchina</h2>
              {bench.length === 0 ? (
                <p className={shared.muted}>Nessuna riserva disponibile.</p>
              ) : (
                <div className={shared.grid}>
                  {bench.map((player) => (
                    <Player key={player.playerId} player={player} />
                  ))}
                </div>
              )}
            </section>
            <section className={shared.panel}>
              <h2>Motivazioni</h2>
              <ul className={shared.list}>
                {starters.map((player) => (
                  <li key={player.playerId}>
                    <Link to={`/players/${player.playerId}`} state={{ from: '/lineup' }}>
                      {player.playerName}
                    </Link>
                    : {player.reason}
                  </li>
                ))}
                {warnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            </section>
          </>
        )}
      </div>
    </AppShell>
  );
}
