import { apiErrorResponseSchema, type ApiErrorResponse } from '@/api/contracts/apiError';
import { AppError, type AppErrorKind } from '@/api/errors/AppError';
import { environment } from '@/app/bootstrap/env';

const DEFAULT_TIMEOUT_MS = 10_000;
let unauthorizedHandler: (() => void) | undefined;

export function setUnauthorizedHandler(handler: (() => void) | undefined) {
  unauthorizedHandler = handler;
}

export interface HttpRequestOptions extends Omit<RequestInit, 'signal'> {
  timeoutMs?: number;
  signal?: AbortSignal;
  getAuthorization?: () => string | undefined;
}

function errorKindForStatus(status: number): AppErrorKind {
  const kinds: Partial<Record<number, AppErrorKind>> = {
    400: 'bad-request',
    401: 'unauthorized',
    403: 'forbidden',
    404: 'not-found',
    409: 'conflict',
    422: 'unprocessable',
    429: 'rate-limit',
  };
  return kinds[status] ?? (status >= 500 ? 'server' : 'http');
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204 || response.headers.get('content-length') === '0') return undefined;
  const text = await response.text();
  if (!text) return undefined;
  if (!response.headers.get('content-type')?.toLowerCase().includes('application/json'))
    return text;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

function toAppError(response: Response, body: unknown): AppError {
  const parsed = apiErrorResponseSchema.safeParse(body);
  const details: ApiErrorResponse | undefined = parsed.success ? parsed.data : undefined;
  const message = details?.message ?? `Richiesta HTTP non riuscita (${response.status})`;
  return new AppError(message, {
    kind: errorKindForStatus(response.status),
    status: response.status,
    details,
  });
}

function isAppError(error: unknown): error is AppError {
  return (
    error instanceof AppError ||
    (typeof error === 'object' && error !== null && 'kind' in error && 'message' in error)
  );
}

export async function httpRequest<T>(
  path: string,
  options: HttpRequestOptions = {},
): Promise<T | undefined> {
  const { timeoutMs = DEFAULT_TIMEOUT_MS, getAuthorization, headers, signal, ...init } = options;
  const timeoutController = new AbortController();
  const timeoutId = window.setTimeout(() => timeoutController.abort('timeout'), timeoutMs);
  const requestHeaders = new Headers(headers);
  requestHeaders.set('Accept', 'application/json');
  const authorization = getAuthorization?.();
  if (authorization) requestHeaders.set('Authorization', authorization);

  const abortFromCaller = () => timeoutController.abort(signal?.reason);
  signal?.addEventListener('abort', abortFromCaller, { once: true });

  try {
    const response = await fetch(`${environment.VITE_API_BASE_URL}${path}`, {
      ...init,
      headers: requestHeaders,
      signal: timeoutController.signal,
    });
    const body = await readBody(response);
    if (!response.ok) {
      if (response.status === 401) unauthorizedHandler?.();
      throw toAppError(response, body);
    }
    return body as T | undefined;
  } catch (error) {
    if (isAppError(error)) throw error;
    if (timeoutController.signal.aborted) {
      throw new AppError('Richiesta annullata o scaduta', { kind: 'timeout', cause: error });
    }
    throw new AppError('Impossibile raggiungere il servizio', { kind: 'network', cause: error });
  } finally {
    window.clearTimeout(timeoutId);
    signal?.removeEventListener('abort', abortFromCaller);
  }
}
