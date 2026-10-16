import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Activity, Database, RefreshCw, ShieldCheck, TriangleAlert } from 'lucide-react';
import { dashboardApi } from '@/api/dashboardApi';
import { queryKeys } from '@/api/queryKeys';
import { useAuth } from '@/auth/useAuth';
import { Button, StatusBadge } from '@/components/ui';
import { adminApi } from '@/features/admin/api';
import { AppShell } from '@/layouts/AppShell';
import shared from '@/features/core/FeaturePage.module.css';
import styles from '@/features/admin/AdminPage.module.css';

const formatDate = (value: string | null) =>
  value
    ? new Intl.DateTimeFormat('it-IT', { dateStyle: 'short', timeStyle: 'short' }).format(
        new Date(value),
      )
    : '—';
const tone = (status?: string): 'success' | 'warning' | 'danger' | 'info' => {
  if (!status) return 'info';
  if (['READY', 'FRESH', 'COMPLETED', 'SUCCESS'].includes(status)) return 'success';
  if (['FAILED', 'BLOCKED', 'STALE', 'MISSING'].includes(status)) return 'danger';
  return 'warning';
};

export function AdminPage() {
  const auth = useAuth();
  const client = useQueryClient();
  const [confirming, setConfirming] = useState(false);
  const [selectedBatchId, setSelectedBatchId] = useState<number>();
  const seasons = useQuery({ queryKey: queryKeys.seasons, queryFn: dashboardApi.seasons });
  const season = seasons.data?.find((item) => item.current)?.code;
  const freshness = useQuery({
    queryKey: ['admin', 'freshness', season],
    queryFn: ({ signal }) => adminApi.freshness(auth.getAuthorization, season, signal),
    enabled: Boolean(season),
  });
  const imports = useQuery({
    queryKey: ['admin', 'imports', season],
    queryFn: ({ signal }) => adminApi.imports(auth.getAuthorization, season, signal),
    enabled: Boolean(season),
  });
  const catalog = useQuery({
    queryKey: ['admin', 'catalog-readiness', season],
    queryFn: ({ signal }) => adminApi.catalogReadiness(auth.getAuthorization, season, signal),
    enabled: Boolean(season),
  });
  const importDetail = useQuery({
    queryKey: ['admin', 'import-detail', selectedBatchId],
    queryFn: ({ signal }) => adminApi.importDetail(auth.getAuthorization, selectedBatchId!, signal),
    enabled: selectedBatchId !== undefined,
    retry: false,
  });
  const refresh = useQuery({
    queryKey: ['admin', 'refresh', season],
    queryFn: ({ signal }) => adminApi.latestRefresh(auth.getAuthorization, season, signal),
    enabled: Boolean(season),
    retry: false,
  });
  const automation = useQuery({
    queryKey: ['admin', 'automation', season],
    queryFn: ({ signal }) => adminApi.latestAutomation(auth.getAuthorization, season, signal),
    enabled: Boolean(season),
    retry: false,
  });
  const runRefresh = useMutation({
    mutationFn: () => adminApi.runRefresh(auth.getAuthorization, season),
    onSuccess: async () => {
      setConfirming(false);
      await client.invalidateQueries({ queryKey: ['admin'] });
    },
  });
  const loading =
    seasons.isLoading || freshness.isLoading || imports.isLoading || catalog.isLoading;
  const criticalError = seasons.error ?? freshness.error ?? imports.error ?? catalog.error;
  return (
    <AppShell>
      <div className={shared.page}>
        <header className={shared.hero}>
          <div>
            <p className={shared.eyebrow}>Operations console · F14</p>
            <h1>Data operations</h1>
            <p className={shared.muted}>
              Stato operativo e processi reali per {season ?? 'la stagione corrente'}.
            </p>
          </div>
          <StatusBadge tone={tone(freshness.data?.overallStatus)}>
            {freshness.data?.overallStatus ?? 'Verifica'}
          </StatusBadge>
        </header>
        {loading && (
          <section className={shared.panel} aria-live="polite">
            Caricamento stato operativo…
          </section>
        )}
        {criticalError && (
          <section className={`${shared.panel} ${shared.error}`} role="alert">
            Console non disponibile: {criticalError.message}
          </section>
        )}
        {!loading && !criticalError && (
          <>
            <section className={styles.summary} aria-label="Riepilogo operativo">
              <article className={shared.metric}>
                <Database aria-hidden="true" />
                <span>Ultimo refresh</span>
                <strong>{refresh.data?.status ?? 'Non disponibile'}</strong>
              </article>
              <article className={shared.metric}>
                <Activity aria-hidden="true" />
                <span>Automazione</span>
                <strong>{automation.data?.status ?? 'Non disponibile'}</strong>
              </article>
              <article className={shared.metric}>
                <ShieldCheck aria-hidden="true" />
                <span>Fonti monitorate</span>
                <strong>{freshness.data?.sources.length ?? 0}</strong>
              </article>
              <article className={shared.metric}>
                <TriangleAlert aria-hidden="true" />
                <span>Blocchi reali</span>
                <strong>{freshness.data?.blockers.length ?? 0}</strong>
              </article>
            </section>
            <section className={shared.panel}>
              <div className={styles.heading}>
                <div>
                  <h2>Catalog readiness</h2>
                  <p className={shared.muted}>
                    Copertura e qualità calcolate dal backend sul catalogo operativo.
                  </p>
                </div>
                <StatusBadge tone={tone(catalog.data?.readinessStatus)}>
                  {catalog.data?.readinessStatus ?? 'Non disponibile'}
                </StatusBadge>
              </div>
              {catalog.data && (
                <>
                  <dl className={styles.details}>
                    <div>
                      <dt>Qualità catalogo</dt>
                      <dd>{catalog.data.qualityPercentage}%</dd>
                    </div>
                    <div>
                      <dt>Confermati</dt>
                      <dd>
                        {catalog.data.confirmedPlayers}/{catalog.data.totalFantacalcioPlayers}
                      </dd>
                    </div>
                    <div>
                      <dt>Non risolti</dt>
                      <dd>{catalog.data.unresolvedPlayers}</dd>
                    </div>
                    <div>
                      <dt>Issue</dt>
                      <dd>
                        {catalog.data.errorCount} errori · {catalog.data.warningCount} avvisi
                      </dd>
                    </div>
                  </dl>
                  {[...catalog.data.blockers, ...catalog.data.warnings].length > 0 && (
                    <ul className={shared.list}>
                      {[...catalog.data.blockers, ...catalog.data.warnings].map(
                        (message, index) => (
                          <li key={`${index}-${message}`}>{message}</li>
                        ),
                      )}
                    </ul>
                  )}
                  {catalog.data.issues.length === 0 ? (
                    <p className={shared.muted}>Nessuna issue di qualità rilevata.</p>
                  ) : (
                    <div className={styles.issueList} aria-label="Issue qualità catalogo">
                      {catalog.data.issues.map((issue, index) => (
                        <article
                          className={styles.issue}
                          key={`${index}-${issue.code}-${issue.playerId ?? 'catalog'}`}
                        >
                          <div className={styles.heading}>
                            <strong>{issue.code}</strong>
                            <StatusBadge
                              tone={tone(issue.severity === 'ERROR' ? 'BLOCKED' : 'WARNING')}
                            >
                              {issue.severity}
                            </StatusBadge>
                          </div>
                          <p>{issue.message}</p>
                          {(issue.playerName || issue.club) && (
                            <small>
                              {[issue.playerName, issue.club].filter(Boolean).join(' · ')}
                            </small>
                          )}
                        </article>
                      ))}
                    </div>
                  )}
                </>
              )}
            </section>
            <section className={shared.panel}>
              <div className={styles.heading}>
                <div>
                  <h2>Freshness delle fonti</h2>
                  <p className={shared.muted}>
                    Valutata {formatDate(freshness.data?.evaluatedAt ?? null)}
                  </p>
                </div>
              </div>
              <div className={styles.sourceGrid}>
                {freshness.data?.sources.map((source) => (
                  <article className={styles.source} key={source.source}>
                    <div className={styles.heading}>
                      <strong>{source.source}</strong>
                      <StatusBadge tone={tone(source.status)}>{source.status}</StatusBadge>
                    </div>
                    <span>Ultimo batch: {source.batchId ?? '—'}</span>
                    <span>Osservato: {formatDate(source.observedAt)}</span>
                    <span>Età: {source.ageHours === null ? '—' : `${source.ageHours} ore`}</span>
                  </article>
                ))}
              </div>
              {[...(freshness.data?.blockers ?? []), ...(freshness.data?.warnings ?? [])].length >
                0 && (
                <ul className={shared.list}>
                  {[...(freshness.data?.blockers ?? []), ...(freshness.data?.warnings ?? [])].map(
                    (message, index) => (
                      <li key={`${index}-${message}`}>{message}</li>
                    ),
                  )}
                </ul>
              )}
            </section>
            <section className={shared.panel}>
              <div className={styles.heading}>
                <div>
                  <h2>Refresh dati</h2>
                  <p className={shared.muted}>
                    Riconciliazione, valutazioni e rating club con pipeline backend.
                  </p>
                </div>
                <Button onClick={() => setConfirming(true)} disabled={runRefresh.isPending}>
                  <RefreshCw size={16} aria-hidden="true" />
                  Avvia refresh
                </Button>
              </div>
              {confirming && (
                <div
                  className={styles.confirm}
                  role="alertdialog"
                  aria-modal="true"
                  aria-labelledby="refresh-title"
                >
                  <div>
                    <strong id="refresh-title">Conferma refresh manuale</strong>
                    <p>Avviare la pipeline per {season}? L’operazione può richiedere tempo.</p>
                  </div>
                  <div className={shared.actions}>
                    <Button
                      variant="secondary"
                      onClick={() => setConfirming(false)}
                      disabled={runRefresh.isPending}
                    >
                      Annulla
                    </Button>
                    <Button loading={runRefresh.isPending} onClick={() => runRefresh.mutate()}>
                      Conferma
                    </Button>
                  </div>
                </div>
              )}
              {runRefresh.isSuccess && (
                <p className={styles.success} role="status">
                  Refresh completato con stato {runRefresh.data.status}.
                </p>
              )}
              {runRefresh.isError && (
                <p className={shared.error} role="alert">
                  Refresh non riuscito: {runRefresh.error.message}
                </p>
              )}
              {refresh.data && (
                <dl className={styles.details}>
                  <div>
                    <dt>Stato</dt>
                    <dd>{refresh.data.status}</dd>
                  </div>
                  <div>
                    <dt>Avvio</dt>
                    <dd>{formatDate(refresh.data.startedAt)}</dd>
                  </div>
                  <div>
                    <dt>Riconciliati</dt>
                    <dd>{refresh.data.reconciliationMatchedPlayers}</dd>
                  </div>
                  <div>
                    <dt>Valutazioni</dt>
                    <dd>{refresh.data.generatedEvaluations}</dd>
                  </div>
                </dl>
              )}
            </section>
            <section className={shared.panel}>
              <div className={styles.heading}>
                <div>
                  <h2>Storico import</h2>
                  <p className={shared.muted}>
                    {imports.data?.totalElements ?? 0} batch restituiti dal backend.
                  </p>
                </div>
              </div>
              {imports.data?.content.length === 0 ? (
                <p>Nessun import disponibile per questa stagione.</p>
              ) : (
                <div className={shared.tableWrap}>
                  <table className={shared.table}>
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Fonte</th>
                        <th>Stato</th>
                        <th>Righe</th>
                        <th>Valide</th>
                        <th>Invalide</th>
                        <th>Riconciliate</th>
                        <th>Avvio</th>
                        <th>
                          <span className={styles.visuallyHidden}>Azioni</span>
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {imports.data?.content.map((batch) => (
                        <tr key={batch.id}>
                          <td>{batch.id}</td>
                          <td>{batch.source}</td>
                          <td>
                            <StatusBadge tone={tone(batch.status)}>{batch.status}</StatusBadge>
                          </td>
                          <td>{batch.totalRows}</td>
                          <td>{batch.validRows}</td>
                          <td>{batch.invalidRows}</td>
                          <td>{batch.reconciledRows}</td>
                          <td>{formatDate(batch.startedAt)}</td>
                          <td>
                            <Button
                              variant="secondary"
                              size="sm"
                              aria-expanded={selectedBatchId === batch.id}
                              onClick={() =>
                                setSelectedBatchId((current) =>
                                  current === batch.id ? undefined : batch.id,
                                )
                              }
                            >
                              {selectedBatchId === batch.id ? 'Chiudi' : 'Dettagli'}
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {selectedBatchId !== undefined && (
                <div className={styles.importDetail} aria-live="polite">
                  {importDetail.isLoading && <p>Caricamento dettaglio batch…</p>}
                  {importDetail.isError && (
                    <p className={shared.error} role="alert">
                      Dettaglio batch non disponibile: {importDetail.error.message}
                    </p>
                  )}
                  {importDetail.data && (
                    <>
                      <div className={styles.heading}>
                        <h3>Diagnostica batch #{importDetail.data.batch.id}</h3>
                        <span>{importDetail.data.totalInvalidRows} righe invalide</span>
                      </div>
                      {importDetail.data.invalidRows.length === 0 ? (
                        <p>Nessun errore di riga registrato.</p>
                      ) : (
                        <ul className={styles.errorRows}>
                          {importDetail.data.invalidRows.map((row) => (
                            <li key={row.id}>
                              <strong>Riga {row.rowNumber}</strong>
                              <span>{row.validationError}</span>
                              <small>
                                {[row.externalName, row.clubName, row.fantasyRole]
                                  .filter(Boolean)
                                  .join(' · ')}
                              </small>
                            </li>
                          ))}
                        </ul>
                      )}
                    </>
                  )}
                </div>
              )}
            </section>
          </>
        )}
      </div>
    </AppShell>
  );
}
