import { z } from 'zod';

export const apiErrorResponseSchema = z.object({
  timestamp: z.string(),
  status: z.number().int(),
  error: z.string(),
  message: z.string(),
  path: z.string(),
});

export type ApiErrorResponse = z.infer<typeof apiErrorResponseSchema>;
