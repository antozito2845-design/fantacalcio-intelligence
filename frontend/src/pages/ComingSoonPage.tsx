import { Link } from 'react-router-dom';
import { AppShell } from '@/layouts/AppShell';
import styles from '@/features/core/FeaturePage.module.css';

export function ComingSoonPage({ title }: { title: string }) {
  return (
    <AppShell>
      <div className={styles.page}>
        <section className={`${styles.panel} ${styles.status}`}>
          <span className={styles.eyebrow}>In roadmap</span>
          <h1>{title}</h1>
          <p className={styles.muted}>Questa sezione non è ancora disponibile.</p>
          <Link className={styles.button} to="/">
            Torna alla dashboard
          </Link>
        </section>
      </div>
    </AppShell>
  );
}
