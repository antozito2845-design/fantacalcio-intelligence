import { ArrowDownRight, ArrowRight, ArrowUpRight, ShieldCheck } from 'lucide-react';
import type { PropsWithChildren, ReactNode } from 'react';
import styles from './data-display.module.css';
export type PlayerRole = 'P' | 'D' | 'C' | 'A';
const roles: Record<PlayerRole, string> = {
  P: 'Portiere',
  D: 'Difensore',
  C: 'Centrocampista',
  A: 'Attaccante',
};
export function RoleBadge({ playerRole }: { playerRole: PlayerRole }) {
  return (
    <span
      className={`${styles.role} ${styles[playerRole]}`}
      role="img"
      aria-label={`Ruolo: ${roles[playerRole]}`}
    >
      {playerRole}
    </span>
  );
}
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'UNKNOWN';
const difficulties: Record<Difficulty, string> = {
  EASY: 'Facile',
  MEDIUM: 'Media',
  HARD: 'Difficile',
  UNKNOWN: 'Non disponibile',
};
export function DifficultyIndicator({ level }: { level: Difficulty }) {
  return (
    <span
      className={`${styles.difficulty} ${styles[level]}`}
      aria-label={`Difficoltà: ${difficulties[level]}`}
    >
      <span className={styles.dot} aria-hidden="true" />
      {difficulties[level]}
    </span>
  );
}
export function ConfidenceScore({ value }: { value: number }) {
  const safe = Math.min(100, Math.max(0, value));
  const level = safe >= 80 ? 'high' : safe >= 60 ? 'medium' : 'low';
  const label = level === 'high' ? 'alta' : level === 'medium' ? 'media' : 'bassa';
  return (
    <span
      className={`${styles.confidence} ${styles[level]}`}
      aria-label={`Confidenza ${label}: ${safe}%`}
    >
      <ShieldCheck size={14} aria-hidden="true" />
      {safe}%
    </span>
  );
}
export type Trend = 'positive' | 'negative' | 'neutral';
export function TrendIndicator({ trend, value }: { trend: Trend; value: string }) {
  const Icon =
    trend === 'positive' ? ArrowUpRight : trend === 'negative' ? ArrowDownRight : ArrowRight;
  const label = trend === 'positive' ? 'positivo' : trend === 'negative' ? 'negativo' : 'stabile';
  return (
    <span className={`${styles.trend} ${styles[trend]}`} aria-label={`Trend ${label}: ${value}`}>
      <Icon size={15} aria-hidden="true" />
      {value}
    </span>
  );
}
export function PriceRange({
  min,
  max,
  unit = 'crediti',
}: {
  min: number;
  max: number;
  unit?: string;
}) {
  return (
    <span className={styles.price}>
      {min}–{max} {unit}
    </span>
  );
}
export function Metric({
  label,
  value,
  helper,
  trend,
}: PropsWithChildren<{ label: string; value: ReactNode; helper?: string; trend?: ReactNode }>) {
  return (
    <div className={styles.metric}>
      <span className={styles.metricLabel}>{label}</span>
      <strong className={styles.metricValue}>{value}</strong>
      {trend}
      {helper && <span className={styles.metricHelper}>{helper}</span>}
    </div>
  );
}
