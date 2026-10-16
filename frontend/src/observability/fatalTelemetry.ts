export interface FatalErrorEvent {
  eventType: 'frontend_fatal_error';
  source: 'react-error-boundary';
  errorName: string;
  timestamp: string;
  routePathname: string;
}

export interface FatalTelemetryProvider {
  capture(event: FatalErrorEvent): void;
}

type TelemetryTransport = typeof fetch;

interface TelemetryConfiguration {
  endpoint?: string;
  production: boolean;
  transport?: TelemetryTransport;
  timeoutMs?: number;
}

export class NoopFatalTelemetryProvider implements FatalTelemetryProvider {
  capture(): void {}
}

export class HttpFatalTelemetryProvider implements FatalTelemetryProvider {
  constructor(
    private readonly endpoint: string,
    private readonly transport: TelemetryTransport = fetch,
    private readonly timeoutMs = 3_000,
  ) {}

  capture(event: FatalErrorEvent): void {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), this.timeoutMs);

    // AUTHORIZED_OBSERVABILITY_TRANSPORT: isolated from the authenticated business API client.
    try {
      void this.transport(this.endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(event),
        signal: controller.signal,
        credentials: 'omit',
        keepalive: true,
      })
        .catch(() => undefined)
        .finally(() => window.clearTimeout(timeout));
    } catch {
      window.clearTimeout(timeout);
    }
  }
}

const noopProvider = new NoopFatalTelemetryProvider();

let provider: FatalTelemetryProvider = noopProvider;

function validEndpoint(endpoint: string | undefined, production: boolean): string | undefined {
  if (!endpoint || !URL.canParse(endpoint)) return undefined;
  const url = new URL(endpoint);
  if (url.username || url.password || url.search || url.hash) return undefined;
  if (url.protocol === 'https:') return url.href;
  const localDevelopmentEndpoint =
    !production &&
    url.protocol === 'http:' &&
    ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  return localDevelopmentEndpoint ? url.href : undefined;
}

export function createFatalTelemetryProvider({
  endpoint,
  production,
  transport,
  timeoutMs,
}: TelemetryConfiguration): FatalTelemetryProvider {
  const validatedEndpoint = validEndpoint(endpoint, production);
  return validatedEndpoint
    ? new HttpFatalTelemetryProvider(validatedEndpoint, transport, timeoutMs)
    : noopProvider;
}

export function configureFatalTelemetry(configuration: TelemetryConfiguration): void {
  provider = createFatalTelemetryProvider(configuration);
}

export function captureFatalError(error: Error): void {
  try {
    provider.capture({
      eventType: 'frontend_fatal_error',
      source: 'react-error-boundary',
      errorName: error.name || 'Error',
      timestamp: new Date().toISOString(),
      routePathname: window.location.pathname,
    });
  } catch {
    // Observability must never interfere with the fatal recovery UI.
  }
}

export function setFatalTelemetryProvider(nextProvider?: FatalTelemetryProvider): void {
  provider = nextProvider ?? noopProvider;
}
