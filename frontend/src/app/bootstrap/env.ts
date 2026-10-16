import { z } from 'zod';

const environmentSchema = z.object({
  VITE_API_BASE_URL: z
    .string()
    .trim()
    .min(1, 'VITE_API_BASE_URL obbligatoria')
    .refine(
      (value) => value.startsWith('/') || URL.canParse(value),
      'VITE_API_BASE_URL deve essere un path assoluto o un URL valido',
    ),
  VITE_FATAL_TELEMETRY_ENDPOINT: z.preprocess(
    (value) => (typeof value === 'string' && value.trim() === '' ? undefined : value),
    z.string().trim().optional(),
  ),
});

export type AppEnvironment = z.infer<typeof environmentSchema>;

export function parseEnvironment(source: Record<string, unknown>): AppEnvironment {
  return environmentSchema.parse(source);
}

/*
 * IMPORTANT:
 * Vite replaces import.meta.env variables statically at build time.
 * Access custom VITE_* variables explicitly so they are included
 * in the production bundle.
 */
export const environment = parseEnvironment({
  VITE_API_BASE_URL: import.meta.env.VITE_API_BASE_URL,
  VITE_FATAL_TELEMETRY_ENDPOINT: import.meta.env.VITE_FATAL_TELEMETRY_ENDPOINT,
});
