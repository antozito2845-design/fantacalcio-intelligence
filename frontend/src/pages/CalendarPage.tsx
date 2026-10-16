import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { AppShell } from '@/layouts/AppShell';
import { intelligenceApi } from '@/features/intelligence/api';
import shared from '@/features/core/FeaturePage.module.css';
import styles from '@/features/intelligence/Intelligence.module.css';
import { Button, Select } from '@/components/ui';
export function CalendarPage() {
  const [url, setUrl] = useSearchParams();
  const matchday = Math.min(38, Math.max(1, Number(url.get('matchday') ?? 1)));
  const club = url.get('club') ?? '';
  const clubs = useQuery({
    queryKey: ['clubs'],
    queryFn: ({ signal }) => intelligenceApi.clubs(signal),
  });
  const fixtures = useQuery({
    queryKey: ['fixtures', matchday],
    queryFn: ({ signal }) => intelligenceApi.fixtures(matchday, signal),
    retry: false,
  });
  const matchups = useQuery({
    queryKey: ['matchups', club, matchday],
    queryFn: ({ signal }) => intelligenceApi.matchups(club, matchday, matchday, signal),
    enabled: Boolean(club),
    retry: false,
  });
  const update = (key: string, value: string) => {
    const next = new URLSearchParams(url);
    if (value) next.set(key, value);
    else next.delete(key);
    setUrl(next);
  };
  return (
    <AppShell>
      <div className={shared.page}>
        <header className={shared.hero}>
          <div>
            <span className={shared.eyebrow}>Fixture intelligence</span>
            <h1>Calendario & matchup</h1>
            <p className={shared.muted}>Giornate e difficoltà calcolate dal backend.</p>
          </div>
        </header>
        <section className={`${shared.panel} ${styles.toolbar}`} aria-label="Controlli calendario">
          <div className={styles.matchdayControls}>
            <Button
              variant="secondary"
              disabled={matchday === 1}
              onClick={() => update('matchday', String(matchday - 1))}
            >
              Precedente
            </Button>
            <Select
              label="Giornata"
              value={matchday}
              onChange={(e) => update('matchday', e.target.value)}
            >
              {Array.from({ length: 38 }, (_, i) => i + 1).map((x) => (
                <option key={x}>{x}</option>
              ))}
            </Select>
            <Button
              variant="secondary"
              disabled={matchday === 38}
              onClick={() => update('matchday', String(matchday + 1))}
            >
              Successiva
            </Button>
          </div>
          <div className={styles.clubControl}>
            <Select
              label="Analisi club"
              value={club}
              onChange={(e) => update('club', e.target.value)}
            >
              <option value="">Nessun club</option>
              {clubs.data?.map((c) => (
                <option key={c.id} value={c.slug}>
                  {c.name}
                </option>
              ))}
            </Select>
          </div>
        </section>
        <section className={shared.panel} aria-live="polite">
          <h2>Giornata {matchday}</h2>
          {fixtures.isPending ? (
            <p>Caricamento calendario…</p>
          ) : fixtures.isError ? (
            <p className={shared.error}>
              Calendario non disponibile.{' '}
              <button className={shared.button} onClick={() => void fixtures.refetch()}>
                Riprova
              </button>
            </p>
          ) : fixtures.data.length === 0 ? (
            <p>Nessuna fixture disponibile per questa giornata.</p>
          ) : (
            fixtures.data.map((f) => (
              <article className={styles.fixture} key={f.fixtureId}>
                <div>
                  <strong>{f.homeClub}</strong>
                  <br />
                  <span className={styles.difficulty}>{f.homeDifficulty}</span>
                </div>
                <span>vs</span>
                <div>
                  <strong>{f.awayClub}</strong>
                  <br />
                  <span className={styles.difficulty}>{f.awayDifficulty}</span>
                </div>
              </article>
            ))
          )}
        </section>
        {club && (
          <section className={shared.panel}>
            <h2>Matchup per ruolo</h2>
            <details className={styles.difficultyHelp}>
              <summary>Come leggere la difficoltà</summary>
              <p>
                Il punteggio va da 0 a 100: <strong>Facile</strong> sotto 50, <strong>Media</strong>{' '}
                da 50 a 69,99 e <strong>Difficile</strong> da 70.
              </p>
              <p>
                È una media pesata diversa per ruolo di forza complessiva, attacco e difesa
                avversari, rendimento dell’avversario in casa/trasferta e debolezza offensiva o
                difensiva del proprio club. La copertura indica quanta parte dei segnali previsti
                era disponibile: non è una motivazione generata.
              </p>
            </details>
            {matchups.isPending ? (
              <p>Analisi matchup…</p>
            ) : matchups.isError ? (
              <p className={shared.error}>Matchup non disponibile.</p>
            ) : matchups.data.length === 0 ? (
              <p>Nessun matchup disponibile.</p>
            ) : (
              matchups.data.map((m) => (
                <article key={m.fixtureId}>
                  <h3>
                    {m.club} · {m.home ? 'casa' : 'trasferta'} vs {m.opponent}
                  </h3>
                  <div className={shared.grid}>
                    {m.roles.map((r) => (
                      <div className={shared.metric} key={r.role}>
                        <span>
                          {r.role} · {r.difficultyLevel}
                        </span>
                        <strong>
                          {r.difficultyScore === null ? '—' : `${r.difficultyScore}/100`}
                        </strong>
                        <small>
                          Evidenza {r.evidenceTier} · copertura {r.evidenceCoverage}
                        </small>
                      </div>
                    ))}
                  </div>
                </article>
              ))
            )}
          </section>
        )}
      </div>
    </AppShell>
  );
}
