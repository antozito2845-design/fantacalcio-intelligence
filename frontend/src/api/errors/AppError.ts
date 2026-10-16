import type { ApiErrorResponse } from '@/api/contracts/apiError';

export type AppErrorKind =
  | 'bad-request'
  | 'unauthorized'
  | 'forbidden'
  | 'not-found'
  | 'conflict'
  | 'unprocessable'
  | 'rate-limit'
  | 'server'
  | 'http'
  | 'network'
  | 'timeout'
  | 'invalid-response';

interface AppErrorOptions {
  kind: AppErrorKind;
  status?: number;
  cause?: unknown;
  details?: ApiErrorResponse;
}

export class AppError extends Error {
  readonly kind: AppErrorKind;
  readonly status?: number;
  readonly details?: ApiErrorResponse;

  constructor(message: string, options: AppErrorOptions) {
    super(message, { cause: options.cause });
    this.name = 'AppError';
    this.kind = options.kind;
    this.status = options.status;
    this.details = options.details;
  }
}
