# Fantacalcio Intelligence — Production Deployment Runbook

This runbook is the provider-neutral input for D02. It assumes the certified strategy: one Linux VM/VPS for the Spring Boot JAR and reverse proxy, a PostgreSQL 17 instance (managed where available), and static frontend assets served by the reverse proxy. No production secrets belong in Git.

## 1. Infrastructure prerequisites

- A Linux VM/VPS with Java 21, a dedicated non-root service user, firewall access only for SSH, HTTP and HTTPS, and enough disk for two backend releases plus logs.
- PostgreSQL 17, preferably managed, with encrypted connections, automated backups and point-in-time recovery when available.
- A DNS name for the application and a TLS certificate issued by a trusted CA.
- Nginx, Caddy or an equivalent reverse proxy capable of static hosting, SPA fallback, TLS termination and proxying `/api/v1/` and `/actuator/`.
- Build workstation/CI with Node 22, npm 10+ and the repository Maven Wrapper. Build artifacts must be traceable to a release commit.
- Store secrets in the provider secret manager or a root/service-readable environment file outside the release directory.

Create release directories such as:

```text
/opt/fantacalcio/releases/<release-id>/backend.jar
/opt/fantacalcio/releases/<release-id>/frontend/
/opt/fantacalcio/current -> /opt/fantacalcio/releases/<release-id>
/etc/fantacalcio/backend.env
```

## 2. PostgreSQL creation

Create a dedicated database and least-privilege application role. Do not use the PostgreSQL superuser at runtime.

```sql
CREATE ROLE fantacalcio_app LOGIN PASSWORD '<generated-secret>';
CREATE DATABASE fantacalcio_intelligence OWNER fantacalcio_app ENCODING 'UTF8';
```

Restrict network access to the application host, require TLS when the database is remote, and keep administrative credentials separate. The application role must own or be permitted to migrate its application schema because Flyway runs during startup.

## 3. Database credentials

Place the JDBC URL, username and password in the deployment secret store. Use the real variables from `backend/.env.production.example`:

```text
DATABASE_URL=jdbc:postgresql://<db-host>:5432/fantacalcio_intelligence
DATABASE_USERNAME=<application-role>
DATABASE_PASSWORD=<secret>
```

Never pass secrets on a process command line, place them in frontend `VITE_*` variables, commit them, or include them in deployment logs.

## 4. Backend environment

Required:

```text
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
ADMIN_USERNAME
ADMIN_PASSWORD
APP_USER_USERNAME
APP_USER_PASSWORD
CORS_ALLOWED_ORIGINS=https://<app-domain>
```

Optional settings and certified defaults are documented in `backend/.env.production.example`. Keep source ingestion, listone sync, Transfermarkt discovery, identity automation and the data-refresh scheduler disabled for the first deployment. Enable each only after monitoring and source policies are approved.

For a same-origin reverse proxy, browser CORS is normally not exercised, but `CORS_ALLOWED_ORIGINS` is still required by backend startup. Set it to the exact public HTTPS application origin, never `*` and never a trailing path.

## 5. Backend artifact

Build from a clean release checkout using only the wrapper:

```powershell
Set-Location "C:\path\to\fantacalcio-intelligence\backend"
.\mvnw.cmd clean test
.\mvnw.cmd clean package
```

On the managed Codex shell, if Maven resolves `user.home` incorrectly, invoke this same wrapper through `cmd.exe` and pass an explicit local repository. This is a build-machine accommodation, not a production runtime requirement. Do not substitute a globally installed Maven binary.

Publish `backend/target/backend-0.0.1-SNAPSHOT.jar` to the new release directory. Record its SHA-256 checksum and retain the prior JAR for rollback.

## 6. Flyway migration

The JAR contains migrations from `classpath:db/migration`, currently through V69 (including V66.1). Startup uses `spring.jpa.hibernate.ddl-auto=validate`, Flyway validation, forward migration, `clean-disabled=true` and `baseline-on-migrate=false`.

Before first production startup:

1. take and verify a PostgreSQL backup;
2. restore that backup into an isolated rehearsal database when possible;
3. start the candidate JAR against rehearsal with `SPRING_PROFILES_ACTIVE=prod`;
4. confirm Flyway validates all migrations and reaches V69;
5. confirm legacy synthetic records are absent after V34/V59 cleanup;
6. stop rehearsal and only then approve production migration.

Never edit an already-applied migration or run `flyway clean`. Flyway Community does not provide automatic down migrations; database rollback is backup/restore.

## 7. Backend startup

Use a process supervisor (systemd or provider equivalent) and load secrets from `/etc/fantacalcio/backend.env`:

```ini
[Unit]
Description=Fantacalcio Intelligence backend
After=network-online.target

[Service]
User=fantacalcio
WorkingDirectory=/opt/fantacalcio/current
EnvironmentFile=/etc/fantacalcio/backend.env
ExecStart=/usr/bin/java -jar /opt/fantacalcio/current/backend.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
TimeoutStopSec=30
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

Keep backend port 8080 private to the host/network. Allow graceful shutdown to complete before forcing termination.

## 8. Backend health

Infrastructure probes already exist:

```text
GET /actuator/health/liveness
GET /actuator/health/readiness
```

Production hides health details. Readiness includes application/database health through Spring Boot Actuator. Also verify the public application endpoint `GET /api/v1/system/status`, which executes a database query and should return HTTP 200 with `status: UP`.

Do not route traffic until readiness is `UP`. An unauthenticated admin request must be rejected, while valid admin credentials must succeed over HTTPS.

## 9. Frontend environment and build

Use `frontend/.env.production.example` as the source of public build-time values:

```text
VITE_API_BASE_URL=/api/v1
VITE_FATAL_TELEMETRY_ENDPOINT=
```

`VITE_DEV_PROXY_TARGET` is development-only and must not be set for production. Build with Node 22/npm 10+:

```powershell
Set-Location "C:\path\to\fantacalcio-intelligence\frontend"
npm ci
npm run typecheck
npm run lint
npm run test:run
npm run build
npm run format:check
```

Verify `dist/index.html`, hashed JS/CSS assets, lazy chunks, no `.map`, no MSW worker, no localhost telemetry endpoint and no secret. Publish only `dist/`.

## 10. Frontend publishing

Copy `dist/` into `/opt/fantacalcio/releases/<release-id>/frontend/`. Do not overwrite the active release in place. Switch the `current` symlink only after backend readiness and proxy configuration validation.

Cache hashed `/assets/*` as immutable for one year. Serve `index.html` with `no-cache` or `must-revalidate` so releases can switch safely.

## 11. Reverse proxy

The required topology is same-origin:

```text
Internet -> HTTPS reverse proxy
                    +-> /api/v1/*  -> 127.0.0.1:8080
                    +-> /actuator/* -> 127.0.0.1:8080 (restrict externally if provider probes internally)
                    +-> /*          -> frontend dist
```

Provider-neutral Nginx-shaped example (validate syntax on the selected host):

```nginx
server {
    listen 80;
    server_name APP_DOMAIN;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name APP_DOMAIN;
    root /opt/fantacalcio/current/frontend;

    location /api/v1/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header Authorization $http_authorization;
        proxy_set_header Content-Type $content_type;
        proxy_read_timeout 60s;
    }

    location /actuator/health/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /assets/ {
        try_files $uri =404;
        add_header Cache-Control "public, max-age=31536000, immutable" always;
    }

    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache" always;
    }
}
```

The `proxy_pass` form intentionally preserves `/api/v1/`. Verify this behavior on the selected reverse proxy before release.

## 12. HTTPS

- Terminate TLS at the reverse proxy with a trusted certificate.
- Redirect all HTTP to HTTPS before enabling Basic Auth traffic.
- Enable automatic certificate renewal and monitor expiry.
- After HTTPS is verified, set `Strict-Transport-Security: max-age=31536000; includeSubDomains`. Add `preload` only after every subdomain is permanently HTTPS-ready.
- Never expose port 8080 publicly.

## 13. CORS

Set `CORS_ALLOWED_ORIGINS` to the exact public application origin, for example `https://app.example.com`. Multiple approved origins are comma-separated. Wildcard origins are rejected by startup. The backend allows the required methods and `Authorization`, `Content-Type`, `Accept`, and `Cache-Control` request headers with credentials enabled.

Same-origin `/api/v1` is preferred and avoids cross-origin browser traffic. CORS remains defense-in-depth for direct backend access and alternate approved origins.

## 14. Security headers

Apply at the reverse proxy after testing the candidate release:

```text
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self' <telemetry-origin-if-enabled>; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

Do not add `'unsafe-inline'` or a telemetry origin unless runtime verification proves it necessary. If telemetry is enabled, add only its HTTPS origin to `connect-src`. `frame-ancestors 'none'` is the primary framing control; `X-Frame-Options: DENY` may be added for legacy clients.

## 15. Telemetry

Telemetry is optional for the first deployment. If no endpoint is configured, the frontend selects `NoopFatalTelemetryProvider` and deployment remains valid.

To activate real telemetry, set `VITE_FATAL_TELEMETRY_ENDPOINT` before `npm run build`. The endpoint must be HTTPS, accept CORS POST requests, and be privacy-approved. It receives only `eventType`, `source`, `errorName`, `timestamp`, and route pathname; never error message, stack, Basic credentials or business data. Add the telemetry origin to CSP `connect-src`.

## 16. Smoke test

After switching the candidate release:

1. verify HTTP redirects to HTTPS;
2. verify liveness, readiness and `/api/v1/system/status` return HTTP 200/`UP`;
3. test `/`, `/players`, a real player detail, `/auction`, `/lineup`, `/calendar`, `/scouting` and a frontend 404;
4. test login, `/squad`, `/admin`, `/profile`, logout and refresh losing in-memory auth;
5. verify direct navigation to every SPA route returns the app, not proxy 404;
6. verify dark/light and 390/768/1440 layouts;
7. verify browser console has zero errors, React warnings and uncaught exceptions;
8. verify unauthenticated admin/API mutation access is rejected;
9. if telemetry is enabled, use a controlled synthetic fatal test only in an approved non-production/rehearsal environment and inspect the privacy-safe payload.

## 17. Backup

Take a logical backup immediately before every migration-bearing deployment. PostgreSQL 17 example:

```bash
PGPASSWORD="$DATABASE_PASSWORD" pg_dump \
  --host="$DB_HOST" --port=5432 --username="$DATABASE_USERNAME" \
  --format=custom --compress=9 --file="fantacalcio-predeploy-${RELEASE_ID}.dump" \
  fantacalcio_intelligence
```

Store backups encrypted outside the VM, record checksum/size/tool version, and test restore regularly. Prefer provider snapshots/PITR in addition to `pg_dump`, not instead of it.

## 18. Restore

Restore into a new empty database first; do not overwrite production until the rollback decision is approved:

```bash
createdb --host="$DB_HOST" --username="$DB_ADMIN_USER" fantacalcio_restore
PGPASSWORD="$DB_ADMIN_PASSWORD" pg_restore \
  --host="$DB_HOST" --username="$DB_ADMIN_USER" \
  --dbname=fantacalcio_restore --clean --if-exists --no-owner \
  "fantacalcio-predeploy-${RELEASE_ID}.dump"
```

Validate Flyway history, row counts, health and representative reads. For an actual rollback, stop application writes, restore/swap the database under a documented maintenance window, then start the compatible prior JAR.

## 19. Rollback

Frontend rollback:

1. repoint `current/frontend` to the prior immutable release;
2. reload the reverse proxy;
3. purge only `index.html`/HTML cache, not hashed assets;
4. repeat public/direct-route smoke tests.

Backend rollback without schema incompatibility:

1. stop the candidate gracefully;
2. repoint `current/backend.jar` to the prior JAR;
3. restore the prior environment/config version;
4. start and verify readiness/system status.

Database rollback:

- Never assume Flyway can downgrade automatically.
- If the new migration is backward-compatible, the prior JAR may run against the migrated schema after explicit verification.
- If incompatible or data was transformed, stop writes and restore the pre-deploy backup/PITR into a new database, switch `DATABASE_URL`, then start the prior JAR.
- Preserve the failed database and logs for investigation.

Configuration rollback:

Keep versioned, secret-free config templates with each release and versioned secret-manager revisions. Restore the prior values atomically; never copy secrets into Git or command history.

## D02 approval inputs

D02 must not start until the owner supplies or approves: cloud/provider and region, VM sizing, managed PostgreSQL plan, domain/DNS control, TLS method, production database identifiers/credentials through a secret channel, admin/user usernames and passwords through a secret channel, backup retention/RPO/RTO, monitoring/log retention, and the optional telemetry decision/endpoint.
