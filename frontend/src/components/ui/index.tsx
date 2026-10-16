import { AlertTriangle, Inbox, LoaderCircle, Search } from 'lucide-react';
import {
  forwardRef,
  useId,
  useState,
  type ButtonHTMLAttributes,
  type CSSProperties,
  type InputHTMLAttributes,
  type PropsWithChildren,
  type ReactNode,
  type SelectHTMLAttributes,
} from 'react';
import styles from './ui.module.css';
export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';
export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
}) {
  return (
    <button
      className={`${styles.button} ${styles[variant]} ${size === 'md' ? '' : styles[size]}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <LoaderCircle size={16} className={styles.spinner} aria-hidden="true" />}
      {children}
    </button>
  );
}
export const IconButton = forwardRef<
  HTMLButtonElement,
  ButtonHTMLAttributes<HTMLButtonElement> & { label: string }
>(function IconButton({ label, children, ...props }, ref) {
  return (
    <button ref={ref} className={styles.iconButton} aria-label={label} {...props}>
      {children}
    </button>
  );
});
export function Input({
  label,
  id,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  const uid = useId();
  const inputId = id ?? uid;
  return (
    <label className={styles.field} htmlFor={inputId}>
      {label}
      <input id={inputId} className={styles.input} {...props} />
    </label>
  );
}
export function SearchInput({
  label = 'Cerca',
  id,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label?: string }) {
  const uid = useId();
  const inputId = id ?? uid;
  return (
    <label className={styles.field} htmlFor={inputId}>
      <span className={styles.visuallyHidden}>{label}</span>
      <span className={styles.inputWrap}>
        <Search className={styles.searchIcon} size={17} aria-hidden="true" />
        <input
          id={inputId}
          type="search"
          className={`${styles.input} ${styles.searchInput}`}
          {...props}
        />
      </span>
    </label>
  );
}
export function Select({
  label,
  id,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string }) {
  const uid = useId();
  const selectId = id ?? uid;
  return (
    <label className={styles.field} htmlFor={selectId}>
      {label}
      <select id={selectId} className={styles.select} {...props}>
        {children}
      </select>
    </label>
  );
}
export function Badge({ children }: PropsWithChildren) {
  return <span className={styles.badge}>{children}</span>;
}
export function StatusBadge({
  tone,
  children,
}: PropsWithChildren<{ tone: 'success' | 'warning' | 'danger' | 'info' }>) {
  return (
    <span className={`${styles.badge} ${tone === 'danger' ? styles.dangerTone : styles[tone]}`}>
      {children}
    </span>
  );
}
export function Card({ children, className = '' }: PropsWithChildren<{ className?: string }>) {
  return <section className={`${styles.card} ${className}`}>{children}</section>;
}
export function Panel({
  children,
  className = '',
  ...props
}: PropsWithChildren<{ className?: string } & React.HTMLAttributes<HTMLElement>>) {
  return (
    <section className={`${styles.panel} ${className}`} {...props}>
      {children}
    </section>
  );
}
export function Divider() {
  return <hr className={styles.divider} />;
}
export function Tabs({ labels, defaultTab = 0 }: { labels: string[]; defaultTab?: number }) {
  const [active, setActive] = useState(defaultTab);
  return (
    <div className={styles.tabs} role="tablist" aria-label="Sezioni">
      {labels.map((label, index) => (
        <button
          key={label}
          role="tab"
          aria-selected={active === index}
          className={styles.tab}
          onClick={() => setActive(index)}
        >
          {label}
        </button>
      ))}
    </div>
  );
}
export function ProgressBar({ value, label }: { value: number; label: string }) {
  const safe = Math.min(100, Math.max(0, value));
  return (
    <div
      className={styles.progress}
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={safe}
    >
      <div className={styles.progressFill} style={{ inlineSize: `${safe}%` }} />
    </div>
  );
}
export function CircularMetric({ value, label }: { value: number; label: string }) {
  const safe = Math.min(100, Math.max(0, value));
  return (
    <div
      className={styles.circular}
      style={{ '--metric': safe } as CSSProperties}
      role="img"
      aria-label={`${label}: ${safe}%`}
    >
      <span className={styles.circularInner}>{safe}%</span>
    </div>
  );
}
export function Skeleton({ height = '1rem' }: { height?: string }) {
  return <div className={styles.skeleton} style={{ blockSize: height }} aria-hidden="true" />;
}
function State({
  icon,
  title,
  children,
  live,
}: PropsWithChildren<{ icon: ReactNode; title: string; live?: 'polite' | 'assertive' }>) {
  return (
    <div className={styles.state} aria-live={live}>
      {icon}
      <h3>{title}</h3>
      <div>{children}</div>
    </div>
  );
}
export function EmptyState() {
  return (
    <State icon={<Inbox aria-hidden="true" />} title="Nessun dato">
      I contenuti appariranno qui quando disponibili.
    </State>
  );
}
export function ErrorState() {
  return (
    <State
      icon={<AlertTriangle aria-hidden="true" />}
      title="Qualcosa non ha funzionato"
      live="assertive"
    >
      Riprova tra poco.
    </State>
  );
}
export function LoadingState() {
  return (
    <State
      icon={<LoaderCircle className={styles.spinner} aria-hidden="true" />}
      title="Caricamento"
      live="polite"
    >
      Preparazione dei dati in corso.
    </State>
  );
}
export function VisuallyHidden({ children }: PropsWithChildren) {
  return <span className={styles.visuallyHidden}>{children}</span>;
}
