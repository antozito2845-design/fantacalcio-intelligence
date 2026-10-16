import { z } from 'zod';
import { httpRequest, type HttpRequestOptions } from '@/api/client/httpClient';
import { AppError } from '@/api/errors/AppError';

const nullableDate = z.string().nullable();
const importBatchSchema = z.object({
  id: z.number(),
  source: z.string(),
  season: z.string(),
  filename: z.string(),
  status: z.string(),
  totalRows: z.number(),
  validRows: z.number(),
  invalidRows: z.number(),
  reconciledRows: z.number(),
  startedAt: z.string(),
  completedAt: nullableDate,
  reconciliationStartedAt: nullableDate,
  reconciliationCompletedAt: nullableDate,
  errorMessage: z.string().nullable(),
});
const importPageSchema = z.object({
  content: z.array(importBatchSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});
const importRowErrorSchema = z.object({
  id: z.number(),
  rowNumber: z.number(),
  externalId: z.string().nullable(),
  externalName: z.string().nullable(),
  clubName: z.string().nullable(),
  fantasyRole: z.string().nullable(),
  validationError: z.string(),
  rawData: z.string().nullable(),
});
const importDetailSchema = z.object({
  batch: importBatchSchema,
  invalidRows: z.array(importRowErrorSchema),
  errorPage: z.number(),
  errorSize: z.number(),
  totalInvalidRows: z.number(),
  totalErrorPages: z.number(),
});
const sourceFreshnessSchema = z.object({
  source: z.string(),
  batchId: z.number().nullable(),
  observedAt: nullableDate,
  ageHours: z.number().nullable(),
  freshMaxHours: z.number(),
  staleAfterHours: z.number(),
  requiredForRefresh: z.boolean(),
  status: z.string(),
});
const freshnessSchema = z.object({
  season: z.string(),
  evaluatedAt: z.string(),
  overallStatus: z.string(),
  sources: z.array(sourceFreshnessSchema),
  blockers: z.array(z.string()),
  warnings: z.array(z.string()),
});
const refreshSchema = z.object({
  id: z.number(),
  season: z.string(),
  triggerType: z.string(),
  status: z.string(),
  sourceFreshnessStatus: z.string().nullable(),
  sourceFreshnessCheckedAt: nullableDate,
  reconciliationMatchedPlayers: z.number(),
  generatedEvaluations: z.number(),
  generatedClubRatings: z.number(),
  startedAt: z.string(),
  completedAt: nullableDate,
  errorMessage: z.string().nullable(),
});
const automationSchema = z.object({
  id: z.number(),
  season: z.string(),
  triggerType: z.string(),
  status: z.string(),
  stage: z.string(),
  listoneRunId: z.number().nullable(),
  ingestionRunCount: z.number(),
  refreshRunId: z.number().nullable(),
  startedAt: z.string(),
  lastHeartbeatAt: nullableDate,
  completedAt: nullableDate,
  errorMessage: z.string().nullable(),
});
const catalogIssueSchema = z.object({
  severity: z.string(),
  code: z.string(),
  message: z.string(),
  playerId: z.number().nullable(),
  fantacalcioExternalId: z.string().nullable(),
  playerName: z.string().nullable(),
  club: z.string().nullable(),
  details: z.record(z.string(), z.unknown()),
});
const catalogReadinessSchema = z.object({
  season: z.string(),
  fantacalcioBatchId: z.number(),
  gazzettaBatchId: z.number(),
  fantamarktBatchId: z.number().nullable(),
  totalFantacalcioPlayers: z.number(),
  confirmedPlayers: z.number(),
  unresolvedPlayers: z.number(),
  activeCatalogPlayers: z.number(),
  playersWithoutFantacalcioRole: z.number(),
  playersWithoutActiveRegistration: z.number(),
  pendingTransferClaims: z.number(),
  officialSerieAExits: z.number(),
  missingFromFantacalcio: z.number(),
  qualityPercentage: z.number(),
  dataFreshness: freshnessSchema,
  readinessStatus: z.string(),
  blockers: z.array(z.string()),
  warnings: z.array(z.string()),
  status: z.string(),
  totalOperationalPlayers: z.number(),
  totalClubs: z.number(),
  playersByRole: z.object({ P: z.number(), D: z.number(), C: z.number(), A: z.number() }),
  errorCount: z.number(),
  warningCount: z.number(),
  issues: z.array(catalogIssueSchema),
  clubSummaries: z.array(
    z.object({
      clubId: z.number(),
      club: z.string(),
      totalPlayers: z.number(),
      P: z.number(),
      D: z.number(),
      C: z.number(),
      A: z.number(),
    }),
  ),
});

export type ImportBatch = z.infer<typeof importBatchSchema>;
export type Freshness = z.infer<typeof freshnessSchema>;
export type DataRefresh = z.infer<typeof refreshSchema>;
export type AutomationRun = z.infer<typeof automationSchema>;
export type ImportDetail = z.infer<typeof importDetailSchema>;
export type CatalogReadiness = z.infer<typeof catalogReadinessSchema>;

function path(base: string, season?: string) {
  return season ? `${base}?season=${encodeURIComponent(season)}` : base;
}
async function validated<T>(
  url: string,
  schema: z.ZodType<T>,
  auth: () => string | undefined,
  init?: HttpRequestOptions,
) {
  const body = await httpRequest<unknown>(url, { ...init, getAuthorization: auth });
  const parsed = schema.safeParse(body);
  if (!parsed.success)
    throw new AppError('Il servizio ha restituito dati non conformi al contratto', {
      kind: 'invalid-response',
    });
  return parsed.data;
}
export const adminApi = {
  imports: (auth: () => string | undefined, season?: string, signal?: AbortSignal) =>
    validated(path('/admin/imports', season), importPageSchema, auth, { signal }),
  importDetail: (auth: () => string | undefined, batchId: number, signal?: AbortSignal) =>
    validated(`/admin/imports/${batchId}`, importDetailSchema, auth, { signal }),
  catalogReadiness: (auth: () => string | undefined, season?: string, signal?: AbortSignal) =>
    validated(path('/admin/catalog/readiness', season), catalogReadinessSchema, auth, { signal }),
  freshness: (auth: () => string | undefined, season?: string, signal?: AbortSignal) =>
    validated(path('/admin/data-freshness', season), freshnessSchema, auth, { signal }),
  latestRefresh: (auth: () => string | undefined, season?: string, signal?: AbortSignal) =>
    validated(path('/admin/data-refresh/latest', season), refreshSchema, auth, { signal }),
  latestAutomation: (auth: () => string | undefined, season?: string, signal?: AbortSignal) =>
    validated(path('/admin/automation/latest', season), automationSchema, auth, { signal }),
  runRefresh: (auth: () => string | undefined, season?: string) =>
    validated(path('/admin/data-refresh/run', season), refreshSchema, auth, { method: 'POST' }),
};
