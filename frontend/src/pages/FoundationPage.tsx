import styles from './FoundationPage.module.css';

export function FoundationPage() {
  return (
    <main className={styles.page}>
      <section className={styles.panel} aria-labelledby="foundation-title">
        <p className={styles.eyebrow}>Foundation</p>
        <h1 id="foundation-title">Fantacalcio Intelligence</h1>
        <p>Frontend foundation ready</p>
      </section>
    </main>
  );
}
