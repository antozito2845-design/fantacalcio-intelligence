import {
  ChartNoAxesCombined,
  ShieldCheck,
  Sparkles,
  Sun,
  Moon,
  UsersRound,
  Wallet,
} from 'lucide-react';
import { useState } from 'react';
import {
  ConfidenceScore,
  DifficultyIndicator,
  Metric,
  PriceRange,
  RoleBadge,
  TrendIndicator,
} from '@/components/data-display';
import {
  Badge,
  Button,
  Card,
  CircularMetric,
  Divider,
  EmptyState,
  ErrorState,
  IconButton,
  Input,
  LoadingState,
  Panel,
  ProgressBar,
  SearchInput,
  Select,
  Skeleton,
  StatusBadge,
  Tabs,
} from '@/components/ui';
import { AppShell } from '@/layouts/AppShell';
import styles from './DesignSystemPage.module.css';
const rows: Array<{
  name: string;
  role: 'A' | 'C' | 'D';
  score: string;
  price: readonly [number, number];
  confidence: number;
  difficulty: 'HARD' | 'MEDIUM' | 'EASY';
}> = [
  {
    name: 'Profilo dimostrativo A',
    role: 'A' as const,
    score: '7,48',
    price: [28, 34],
    confidence: 94,
    difficulty: 'HARD' as const,
  },
  {
    name: 'Profilo dimostrativo B',
    role: 'C' as const,
    score: '7,15',
    price: [22, 27],
    confidence: 89,
    difficulty: 'MEDIUM' as const,
  },
  {
    name: 'Profilo dimostrativo C',
    role: 'D' as const,
    score: '6,98',
    price: [20, 25],
    confidence: 82,
    difficulty: 'EASY' as const,
  },
];
export function DesignSystemPage() {
  const [theme, setTheme] = useState<'dark' | 'light'>('dark');
  function toggleTheme() {
    const next = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
    document.documentElement.dataset.theme = next;
  }
  return (
    <AppShell>
      <div id="preview">
        <header className={styles.pageHeader}>
          <div>
            <p className={styles.eyebrow}>Dashboard foundation · F03</p>
            <h1>Intelligence overview</h1>
            <p>Dati statici limitati alla validazione del sistema visivo.</p>
          </div>
          <IconButton
            label={`Attiva tema ${theme === 'dark' ? 'chiaro' : 'scuro'}`}
            onClick={toggleTheme}
          >
            {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          </IconButton>
        </header>
        <section className={styles.kpis} aria-label="Metriche dimostrative">
          <Card className={styles.kpi}>
            <span className={`${styles.kpiIcon} ${styles.cyan}`} aria-hidden="true">
              <Wallet size={20} />
            </span>
            <Metric
              label="Budget disponibile"
              value="72"
              helper="crediti"
              trend={<TrendIndicator trend="positive" value="+12" />}
            />
          </Card>
          <Card className={styles.kpi}>
            <span className={`${styles.kpiIcon} ${styles.blue}`} aria-hidden="true">
              <UsersRound size={20} />
            </span>
            <Metric label="Valore rosa" value="498" helper="crediti stimati" />
          </Card>
          <Card className={styles.kpi}>
            <span className={`${styles.kpiIcon} ${styles.violet}`} aria-hidden="true">
              <ChartNoAxesCombined size={20} />
            </span>
            <Metric
              label="Punti previsti"
              value="1.783"
              helper="scenario dimostrativo"
              trend={<TrendIndicator trend="positive" value="+4,8%" />}
            />
          </Card>
          <Card className={styles.kpi}>
            <span className={`${styles.kpiIcon} ${styles.green}`} aria-hidden="true">
              <ShieldCheck size={20} />
            </span>
            <Metric label="Affidabilità rosa" value="82%" helper="confidenza alta" />
          </Card>
        </section>
        <section className={styles.dashboard} aria-label="Composizione dashboard dimostrativa">
          <Panel>
            <div className={styles.panelHead}>
              <h2>Quadro asta</h2>
              <StatusBadge tone="success">Ottimale</StatusBadge>
            </div>
            <div className={styles.score}>
              <CircularMetric value={84} label="Punteggio strategia" />
              <p className={styles.scoreText}>
                <strong>Strategia bilanciata</strong>
                <br />
                La foundation rende leggibili priorità, rischio e confidenza.
              </p>
            </div>
            <Divider />
            <div className={styles.strategyMeta} aria-label="Sintesi fabbisogno rosa">
              <span>
                <b>2</b> Difensori
              </span>
              <span>
                <b>1</b> Centrocampista
              </span>
              <span>
                <b>1</b> Attaccante
              </span>
            </div>
            <ProgressBar value={78} label="Preparazione asta" />
          </Panel>
          <Panel>
            <div className={styles.panelHead}>
              <h2>Formazione consigliata</h2>
              <Badge>3–4–3</Badge>
            </div>
            <div className={styles.lineup} aria-label="Placeholder formazione">
              <span className={styles.pitchLabel}>Intesa formazione · 92%</span>
              <div className={styles.pitchGrid}>
                {[
                  'A · Profilo',
                  'A · Profilo',
                  'A · Profilo',
                  'C · Profilo',
                  'C · Profilo',
                  'C · Profilo',
                  'D · Profilo',
                  'D · Profilo',
                  'P · Profilo',
                ].map((x, i) => (
                  <span className={styles.player} key={i}>
                    {x}
                  </span>
                ))}
              </div>
            </div>
          </Panel>
          <Panel>
            <div className={styles.panelHead}>
              <h2>Calendario & matchup</h2>
              <Badge>Giornata demo</Badge>
            </div>
            <div className={styles.match}>
              <strong>Squadra A</strong>
              <span className={styles.vs}>VS</span>
              <strong>Squadra B</strong>
            </div>
            <DifficultyIndicator level="HARD" />
            <div
              className={styles.formDots}
              aria-label="Forma recente: tre risultati positivi, due negativi"
            >
              <i />
              <i />
              <i />
              <i className={styles.badDot} />
              <i className={styles.badDot} />
            </div>
            <div className={styles.match}>
              <strong>Squadra C</strong>
              <span className={styles.vs}>VS</span>
              <strong>Squadra D</strong>
            </div>
            <DifficultyIndicator level="MEDIUM" />
            <div
              className={styles.formDots}
              aria-label="Forma recente: tre risultati positivi, uno neutro, uno negativo"
            >
              <i />
              <i />
              <i />
              <i className={styles.neutralDot} />
              <i className={styles.badDot} />
            </div>
          </Panel>
          <Panel>
            <div className={styles.panelHead}>
              <h2>Scouting & market</h2>
              <Tabs labels={['Opportunità', 'Osservati']} />
            </div>
            <div className={styles.scoutList}>
              {rows.map((row) => (
                <div className={styles.scout} key={row.name}>
                  <RoleBadge playerRole={row.role} />
                  <span className={styles.scoutMeta}>
                    {row.name}
                    <span>
                      <PriceRange min={row.price[0]} max={row.price[1]} />
                    </span>
                  </span>
                  <ConfidenceScore value={row.confidence} />
                  <Sparkles className={styles.scoutSpark} size={15} aria-hidden="true" />
                </div>
              ))}
            </div>
          </Panel>
          <Panel className={styles.span2}>
            <div className={styles.panelHead}>
              <h2>Profili in evidenza</h2>
              <SearchInput label="Cerca profilo" placeholder="Cerca nella tabella…" />
            </div>
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Profilo</th>
                    <th>Ruolo</th>
                    <th>Media</th>
                    <th>Fascia prezzo</th>
                    <th>Confidenza</th>
                    <th>Difficoltà</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row, index) => (
                    <tr key={row.name}>
                      <td className={styles.rank}>{index + 1}</td>
                      <td>{row.name}</td>
                      <td>
                        <RoleBadge playerRole={row.role} />
                      </td>
                      <td>{row.score}</td>
                      <td>
                        <PriceRange min={row.price[0]} max={row.price[1]} />
                      </td>
                      <td>
                        <ConfidenceScore value={row.confidence} />
                      </td>
                      <td>
                        <DifficultyIndicator level={row.difficulty} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Panel>
        </section>
        <section className={styles.showcase} aria-labelledby="system-title">
          <h2 id="system-title" className={styles.sectionTitle}>
            Design system preview
          </h2>
          <div className={styles.showcaseGrid}>
            <Panel className={styles.stack}>
              <h3>Azioni & campi</h3>
              <div className={styles.row}>
                <Button>Primario</Button>
                <Button variant="secondary">Secondario</Button>
                <Button variant="ghost">Ghost</Button>
                <Button variant="danger">Pericolo</Button>
                <Button loading>Carica</Button>
              </div>
              <Input label="Nome lega" placeholder="Inserisci un nome" />
              <SearchInput placeholder="Cerca…" />
              <Select label="Scenario">
                <option>Predefinito</option>
              </Select>
            </Panel>
            <Panel className={styles.stack}>
              <h3>Segnali analitici</h3>
              <div className={styles.row}>
                <RoleBadge playerRole="P" />
                <RoleBadge playerRole="D" />
                <RoleBadge playerRole="C" />
                <RoleBadge playerRole="A" />
              </div>
              <div className={styles.row}>
                <DifficultyIndicator level="EASY" />
                <DifficultyIndicator level="MEDIUM" />
                <DifficultyIndicator level="HARD" />
                <DifficultyIndicator level="UNKNOWN" />
              </div>
              <div className={styles.row}>
                <ConfidenceScore value={82} />
                <TrendIndicator trend="positive" value="+8%" />
                <TrendIndicator trend="negative" value="−3%" />
                <TrendIndicator trend="neutral" value="0%" />
              </div>
              <div className={styles.row}>
                <StatusBadge tone="success">Pronto</StatusBadge>
                <StatusBadge tone="warning">Attenzione</StatusBadge>
                <StatusBadge tone="danger">Rischio</StatusBadge>
                <StatusBadge tone="info">Info</StatusBadge>
              </div>
            </Panel>
            <Panel className={styles.stack}>
              <h3>Superfici & caricamento</h3>
              <Tabs labels={['Panoramica', 'Dettaglio', 'Storico']} />
              <Skeleton height="2.5rem" />
              <Skeleton />
              <ProgressBar value={64} label="Avanzamento dimostrativo" />
            </Panel>
          </div>
          <div className={styles.states}>
            <Panel>
              <EmptyState />
            </Panel>
            <Panel>
              <ErrorState />
            </Panel>
            <Panel>
              <LoadingState />
            </Panel>
          </div>
        </section>
      </div>
    </AppShell>
  );
}
