import { Component, type PropsWithChildren, type ReactNode } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui';
import { captureFatalError } from '@/observability/fatalTelemetry';
import styles from './FatalErrorBoundary.module.css';

interface State {
  failed: boolean;
}

export class FatalErrorBoundary extends Component<PropsWithChildren, State> {
  override state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  override componentDidCatch(error: Error): void {
    captureFatalError(error);
  }

  override render(): ReactNode {
    if (!this.state.failed) return this.props.children;
    return (
      <main className={styles.page}>
        <section className={styles.panel} role="alert" aria-labelledby="fatal-error-title">
          <AlertTriangle aria-hidden="true" />
          <p className={styles.eyebrow}>Errore applicazione</p>
          <h1 id="fatal-error-title">Impossibile mostrare questa schermata</h1>
          <p>I dati non sono stati modificati. Ricarica l’applicazione per riprovare.</p>
          <Button onClick={() => window.location.reload()}>Ricarica applicazione</Button>
        </section>
      </main>
    );
  }
}
