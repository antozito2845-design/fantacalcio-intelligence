import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { AppShell } from '@/layouts/AppShell';
import { intelligenceApi } from '@/features/intelligence/api';
import shared from '@/features/core/FeaturePage.module.css';
import styles from '@/features/intelligence/Intelligence.module.css';
type Sort = 'potential' | 'adaptation' | 'confidence' | 'name';
export function ScoutingPage() {
  const [url, setUrl] = useSearchParams();
  const [search, setSearch] = useState('');
  const role = url.get('role') ?? '';
  const club = url.get('club') ?? '';
  const recommendation = url.get('recommendation') ?? '';
  const sort = (url.get('sort') ?? 'potential') as Sort;
  const seasons = useQuery({
    queryKey: ['seasons'],
    queryFn: ({ signal }) => intelligenceApi.seasons(signal),
  });
  const current = seasons.data?.find((s) => s.current)?.code;
  const clubs = useQuery({
    queryKey: ['clubs'],
    queryFn: ({ signal }) => intelligenceApi.clubs(signal),
  });
  const query = useQuery({
    queryKey: ['scouting', current, role, club, recommendation],
    queryFn: ({ signal }) =>
      intelligenceApi.scouting(
        current!,
        {
          role: role || undefined,
          club: club || undefined,
          recommendation: recommendation || undefined,
        },
        signal,
      ),
    enabled: Boolean(current),
    retry: false,
  });
  const update = (key: string, value: string) => {
    const n = new URLSearchParams(url);
    if (value) n.set(key, value);
    else n.delete(key);
    setUrl(n);
  };
  const visible = useMemo(() => {
    const filtered = (query.data ?? []).filter((x) =>
      x.playerName.toLowerCase().includes(search.trim().toLowerCase()),
    );
    return [...filtered].sort((a, b) =>
      sort === 'name'
        ? a.playerName.localeCompare(b.playerName)
        : sort === 'adaptation'
          ? b.adaptationScore - a.adaptationScore
          : sort === 'confidence'
            ? b.confidenceScore - a.confidenceScore
            : b.potentialScore - a.potentialScore,
    );
  }, [query.data, search, sort]);
  return (
    <AppShell>
      <div className={shared.page}>
        <header className={shared.hero}>
          <div>
            <span className={shared.eyebrow}>Serie A arrivals</span>
            <h1>Scouting & market</h1>
            <p className={shared.muted}>
              Solo arrivi realmente presenti ed eleggibili nel catalogo Serie A.
            </p>
          </div>
        </header>
        <section className={`${shared.panel} ${shared.filters}`}>
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
            <select value={club} onChange={(e) => update('club', e.target.value)}>
              <option value="">Tutti</option>
              {clubs.data?.map((c) => (
                <option key={c.id} value={c.slug}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Recommendation
            <input
              value={recommendation}
              onChange={(e) => update('recommendation', e.target.value)}
              placeholder="Valore backend"
            />
          </label>
          <label>
            Ordina
            <select value={sort} onChange={(e) => update('sort', e.target.value)}>
              <option value="potential">Potenziale</option>
              <option value="adaptation">Adattamento</option>
              <option value="confidence">Confidence</option>
              <option value="name">Nome</option>
            </select>
          </label>
        </section>
        <section className={shared.panel} aria-live="polite">
          {query.isPending || seasons.isPending ? (
            <div className={shared.status}>Caricamento scouting…</div>
          ) : query.isError ? (
            <div className={`${shared.status} ${shared.error}`}>
              Scouting non disponibile.{' '}
              <button className={shared.button} onClick={() => void query.refetch()}>
                Riprova
              </button>
            </div>
          ) : visible.length === 0 ? (
            <div className={shared.status}>Nessuna opportunità corrisponde ai filtri.</div>
          ) : (
            <div className={shared.grid}>
              {visible.map((p) => (
                <article className={`${shared.card} ${styles.opportunity}`} key={p.playerId}>
                  <div>
                    <span className={shared.role}>{p.role}</span>{' '}
                    <span className={styles.badge}>{p.recommendation}</span>
                  </div>
                  <h2>
                    <Link to={`/players/${p.playerId}`} state={{ from: '/scouting' }}>
                      {p.playerName}
                    </Link>
                  </h2>
                  <p className={shared.muted}>
                    {p.club}
                    {p.previousClub ? ` · da ${p.previousClub}` : ''}
                  </p>
                  <div className={styles.scores}>
                    <span>
                      <strong>{p.potentialScore}</strong>Potenziale
                    </span>
                    <span>
                      <strong>{p.adaptationScore}</strong>Adattamento
                    </span>
                    <span>
                      <strong>{p.riskScore}</strong>Rischio
                    </span>
                    <span>
                      <strong>{p.confidenceScore}</strong>Confidence
                    </span>
                  </div>
                  <p>{p.scoutingSummary}</p>
                  <small>
                    {p.eligibilityStatus} · {p.transferType ?? 'Trasferimento non classificato'}
                  </small>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </AppShell>
  );
}
