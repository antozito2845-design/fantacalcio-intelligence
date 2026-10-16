import { useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { FormEvent, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { featureApi } from '@/features/core/api';
import { intelligenceApi } from '@/features/intelligence/api';
import styles from './AppShell.module.css';

export function GlobalSearch() {
  const [value, setValue] = useState('');
  const [submitted, setSubmitted] = useState('');
  const navigate = useNavigate();
  const location = useLocation();
  const region = useRef<HTMLDivElement>(null);
  const normalized = submitted.trim();
  const players = useQuery({
    queryKey: ['global-search', 'players', normalized],
    queryFn: ({ signal }) => featureApi.players({ search: normalized, page: 0, size: 6 }, signal),
    enabled: normalized.length > 0,
    retry: false,
  });
  const clubs = useQuery({
    queryKey: ['global-search', 'clubs'],
    queryFn: ({ signal }) => intelligenceApi.clubs(signal),
    enabled: normalized.length > 0,
    retry: false,
  });

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSubmitted('');
      setValue('');
    }, 0);

    return () => window.clearTimeout(timer);
  }, [location.pathname]);

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!region.current?.contains(event.target as Node)) setSubmitted('');
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const query = value.trim();
    if (query) setSubmitted(query);
  };
  const matchingClubs = (clubs.data ?? []).filter((club) =>
    club.name.toLocaleLowerCase('it').includes(normalized.toLocaleLowerCase('it')),
  );
  const pending = players.isPending || clubs.isPending;
  const error = players.isError || clubs.isError;
  const empty =
    !pending && !error && players.data?.content.length === 0 && matchingClubs.length === 0;

  return (
    <div className={styles.globalSearch} ref={region}>
      <form className={styles.searchForm} role="search" onSubmit={submit}>
        <label className={styles.visuallyHidden} htmlFor="global-search">
          Cerca giocatori e squadre
        </label>
        <Search className={styles.searchIcon} size={17} aria-hidden="true" />
        <input
          id="global-search"
          type="search"
          value={value}
          placeholder="Cerca giocatori e squadre…"
          autoComplete="off"
          onChange={(event) => setValue(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Escape') setSubmitted('');
          }}
        />
        <button type="submit" aria-label="Avvia ricerca" disabled={!value.trim()}>
          Cerca
        </button>
      </form>
      {normalized && (
        <div className={styles.searchResults} aria-live="polite" aria-label="Risultati ricerca">
          {pending ? (
            <p>Ricerca in corso…</p>
          ) : error ? (
            <p role="alert">Ricerca non disponibile. Riprova.</p>
          ) : empty ? (
            <p>Nessun giocatore o squadra trovato.</p>
          ) : (
            <>
              {players.data?.content.map((player) => (
                <Link
                  key={player.id}
                  to={`/players/${player.id}`}
                  state={{ from: `${location.pathname}${location.search}` }}
                >
                  <span>{player.name}</span>
                  <small>
                    {player.club} · {player.fantacalcioRole}
                  </small>
                </Link>
              ))}
              {matchingClubs.slice(0, 4).map((club) => (
                <button
                  type="button"
                  key={club.id}
                  onClick={() => {
                    void navigate(`/players?club=${encodeURIComponent(club.slug)}`);
                  }}
                >
                  <span>{club.name}</span>
                  <small>Squadra · vedi giocatori</small>
                </button>
              ))}
            </>
          )}
        </div>
      )}
    </div>
  );
}
