# Fantacalcio Intelligence - Backend Production Runbook

## Purpose

This runbook describes the minimum safe production configuration for the
Fantacalcio Intelligence Spring Boot backend.

The production profile is `prod`.

## Required environment variables

The production profile intentionally has no fallback for credentials or
database connection values.

Required:

- `SPRING_PROFILES_ACTIVE=prod`
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `ADMIN_USERNAME`
- `ADMIN_PASSWORD`
- `APP_USER_USERNAME`
- `APP_USER_PASSWORD`
- `CORS_ALLOWED_ORIGINS`

Optional:

- `SERVER_PORT` - default `8080`
- `DATA_REFRESH_SCHEDULER_ENABLED` - default `false`
- `DATA_REFRESH_SCHEDULER_CRON` - default `0 0 4 * * *`
- `DATA_REFRESH_SCHEDULER_SEASON` - default `2026/2027`

`CORS_ALLOWED_ORIGINS` is a comma-separated allow-list. Never use `*`.

Example:

`https://app.example.com,https://admin.example.com`

## Secret rules

Never commit production passwords.

Use long random values for `ADMIN_PASSWORD`, `APP_USER_PASSWORD` and
`DATABASE_PASSWORD`.

Development defaults from `application.properties` must never be reused in
production.

## Database

Production uses Flyway as the schema authority.

Hibernate remains:

`spring.jpa.hibernate.ddl-auto=validate`

Startup must fail if the database schema does not match the application.

Before deploying a new migration:

1. back up PostgreSQL;
2. verify the migration on the test database;
3. deploy the application;
4. verify Flyway startup logs;
5. verify `/actuator/health/readiness`.

## Health checks

Public health endpoints:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

Detailed health information is hidden in production.

Recommended platform checks:

- liveness: `/actuator/health/liveness`
- readiness: `/actuator/health/readiness`

Do not use application business endpoints as infrastructure health probes.

## Security

The backend is stateless.

Admin endpoints under `/api/v1/admin/**` require the `ADMIN` role.

Basic Auth passwords are encoded before they are stored in the in-memory
Spring Security user store.

CORS uses an explicit allow-list supplied by `CORS_ALLOWED_ORIGINS`.

## Data refresh scheduler

The scheduler is disabled by default, including in production.

Enable it only after:

1. source import operations are stable;
2. source freshness is being monitored;
3. the expected season is configured;
4. scheduled refresh failures are monitored.

The freshness gate prevents a refresh from silently using stale required
sources.

## Deployment verification

After each deployment verify:

1. application startup is successful;
2. Flyway reports the expected schema version;
3. `/actuator/health` returns `UP`;
4. `/actuator/health/liveness` returns `UP`;
5. `/actuator/health/readiness` returns `UP`;
6. an admin endpoint rejects unauthenticated requests;
7. an admin endpoint accepts valid admin credentials;
8. `/api/v1/admin/data-freshness` returns the expected source state;
9. `/api/v1/admin/catalog/readiness` has no unexpected blocker;
10. Git working tree and release commit are clean.

## Shutdown

Spring Boot graceful shutdown is enabled.

The process should receive a normal termination signal and be allowed to
finish active requests before the platform force-kills it.