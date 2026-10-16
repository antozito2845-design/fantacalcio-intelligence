import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from '@/app/bootstrap/App';
import { environment } from '@/app/bootstrap/env';
import { FatalErrorBoundary } from '@/components/feedback/FatalErrorBoundary';
import { configureFatalTelemetry } from '@/observability/fatalTelemetry';
import '@/styles/global.css';

configureFatalTelemetry({
  endpoint: environment.VITE_FATAL_TELEMETRY_ENDPOINT,
  production: import.meta.env.PROD,
});

const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('Elemento root non trovato');
}

createRoot(rootElement).render(
  <StrictMode>
    <FatalErrorBoundary>
      <App />
    </FatalErrorBoundary>
  </StrictMode>,
);
