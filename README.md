# Open Pulse Checker

Open Pulse Checker is a security-first, self-host-first OSS monitoring platform.

## Frontend quality gate (ticket #58)
- Accessibility/responsiveness baseline closure was applied for major admin routes (focus visibility, keyboard-first affordances, labeling, responsive shell polish).
- Smoke E2E coverage is available in `frontend/e2e/smoke.spec.ts` (major navigation + key admin action flow).
- Run frontend quality checks:
  - `cd frontend && npm run lint && npm run build`
  - `cd frontend && npm run test:e2e:smoke`
- Known limitation: smoke tests are API-mocked UI smoke (deterministic FE gate), not full backend integration tests.

## Release readiness gate (ticket #46, v2.1)
- Canonical release gate checklist: `docs/v2.1-release-readiness-checklist.md`
- Includes: backend tests, frontend lint/build/smoke, migration verification, backup/restore sanity, security checks, changelog/release notes, tag cut, and rollback validation.
- EPIC #48 (Frontend v1) closure evidence is tracked in the same checklist.

## Phase 2.4 delivered (ops list scalability: paging/filtering/sorting)
- Enhanced list endpoints (backward compatible):
  - `GET /api/v1/monitors`
  - `GET /api/v1/admin/incidents`
  - `GET /api/v1/status-pages`
- Additive query params:
  - `paged` (`true|false`, default `false` for legacy array response)
  - `page` (default `0`), `size` (default `25`, max `200`)
  - `sortBy`, `sortDir` (`asc|desc`)
  - endpoint-specific filters (`q`, plus resource fields like `enabled`, `type`, `state`, `monitorId`, `isPublic`)
- When `paged=true`, response shape includes metadata:
  - `items`, `page`, `size`, `total`, `totalPages`, `hasNext`, `hasPrevious`

## Phase 2.3 delivered (audit explorer + export UX)
- ADMIN audit API:
  - `GET /api/v1/admin/audit-events` (search/filter + pagination)
  - `GET /api/v1/admin/audit-events/export?format=csv|json` (filtered export, bounded result set)
- Baseline filter fields: `q`, `actor`, `action`, `resource`, `outcome`, `fromAt`, `toAt`
- Frontend module `Audit Explorer` (`/audit-explorer`) for operational troubleshooting with pagination + CSV/JSON export feedback

## Phase 2.2 delivered (incident manual lifecycle + annotations)
- ADMIN incident operations:
  - `POST /api/v1/admin/incidents/{id}/acknowledge`
  - `POST /api/v1/admin/incidents/{id}/annotations`
  - `POST /api/v1/admin/incidents/{id}/resolve`
  - `POST /api/v1/admin/incidents/{id}/reopen`
- Manual lifecycle state includes `ACKNOWLEDGED` (between `OPEN` and `RESOLVED`)
- Automatic lifecycle remains backward compatible:
  - DOWN opens incident only when no active (`OPEN`/`ACKNOWLEDGED`) incident exists
  - UP resolves active (`OPEN`/`ACKNOWLEDGED`) incidents
- Manual operation persistence:
  - `incident_manual_events` stores actor, action, reason, from/to states, timestamp
  - `audit_events` records admin write action trail for each manual operation

## Phase 2.1 delivered (notification policy + maintenance windows)
- Policy scopes: `GLOBAL`, `MONITOR`, `STATUS_PAGE` with override precedence monitor > status page > global
- Severity-aware route rules (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`)
- Channel toggles (currently `WEBHOOK`) and ordered escalation-step metadata
- Cooldown + de-dup suppression integrated in `AlertDispatchService`
- ADMIN API:
  - `GET /api/v1/admin/notification-policies`
  - `GET /api/v1/admin/notification-policies/{id}`
  - `POST /api/v1/admin/notification-policies`
  - `PUT /api/v1/admin/notification-policies/{id}`
- Schema additions: `notification_policies`, `notification_route_rules`, `notification_escalation_steps`, plus dispatch metadata columns on `dispatched_alerts`
- Maintenance windows domain:
  - one-time (`ONE_TIME`) windows using absolute UTC timestamps (`startAt`/`endAt`)
  - recurring (`RECURRING`) weekly windows using `timezone`, `recurringDays`, `recurringStartTime`, `recurringEndTime`
  - scope support: `GLOBAL` and `MONITOR`
  - policy support: `SUPPRESS` and `ANNOTATE`
- ADMIN API for maintenance windows:
  - `GET /api/v1/admin/maintenance-windows`
  - `GET /api/v1/admin/maintenance-windows/{id}`
  - `POST /api/v1/admin/maintenance-windows`
  - `PUT /api/v1/admin/maintenance-windows/{id}`
  - `DELETE /api/v1/admin/maintenance-windows/{id}`
- Incident/alert semantics during active maintenance:
  - `SUPPRESS`: new DOWN incidents are not created, therefore no incident-opened alert is emitted.
  - `ANNOTATE`: incidents/alerts continue, and event reason is annotated with active maintenance context.
  - Existing open incidents still resolve on recovery for deterministic lifecycle behavior.
- Additional schema: `maintenance_windows` (`V6__phase2_1_maintenance_windows.sql`)

### Notification policy create example (ADMIN)
```bash
curl -u admin:change-me -X POST http://localhost:8080/api/v1/admin/notification-policies \
  -H 'Content-Type: application/json' \
  -d '{
    "scopeType":"GLOBAL",
    "enabled":true,
    "cooldownSeconds":120,
    "dedupSeconds":60,
    "routes":[
      {"severity":"CRITICAL","webhookEnabled":true},
      {"severity":"HIGH","webhookEnabled":true},
      {"severity":"MEDIUM","webhookEnabled":true},
      {"severity":"LOW","webhookEnabled":false},
      {"severity":"INFO","webhookEnabled":false}
    ],
    "escalationSteps":[
      {"stepOrder":1,"afterSeconds":0,"minSeverity":"CRITICAL","webhookEnabled":true}
    ]
  }'
```

### Maintenance window create example (ADMIN)
```bash
curl -u admin:change-me -X POST http://localhost:8080/api/v1/admin/maintenance-windows \
  -H 'Content-Type: application/json' \
  -d '{
    "name":"Weekly patching",
    "scopeType":"GLOBAL",
    "type":"RECURRING",
    "policy":"SUPPRESS",
    "enabled":true,
    "timezone":"Europe/Berlin",
    "recurringDays":["SUNDAY"],
    "recurringStartTime":"01:00",
    "recurringEndTime":"03:00"
  }'
```

## Phase 2.0 delivered (status pages + public timeline API)
- Public status page endpoint: `GET /api/v1/public/status-pages/{slug}`
- Admin status page management endpoints:
  - `POST /api/v1/status-pages`
  - `GET /api/v1/status-pages`
  - `POST /api/v1/status-pages/{id}/monitors`
  - `DELETE /api/v1/status-pages/{id}/monitors/{monitorId}`
- New schema: `status_pages`, `status_page_monitors`
- Public response composes current monitor summary + bounded incident timeline (latest 20)
- Public access is only for `is_public=true`; non-public slugs return `404` to avoid information leakage

### Status page API examples
Create status page (ADMIN):
```bash
curl -u admin:change-me -X POST http://localhost:8080/api/v1/status-pages \
  -H 'Content-Type: application/json' \
  -d '{"name":"Main Status","slug":"main-status","isPublic":true}'
```

Attach/reorder monitors (ADMIN):
```bash
curl -u admin:change-me -X POST http://localhost:8080/api/v1/status-pages/{pageId}/monitors \
  -H 'Content-Type: application/json' \
  -d '{"monitorIds":["<monitor-uuid-1>","<monitor-uuid-2>"]}'
```

Fetch public page (no auth when page is public):
```bash
curl http://localhost:8080/api/v1/public/status-pages/main-status
```

## Phase 1.4 delivered (production-readiness slice)
- Hardened distributed scheduler locking with explicit acquire outcomes (`ACQUIRED`/`STOLEN`/`CONTENDED`) and stale-lock recovery visibility
- Scheduler lock telemetry counters for acquire success/fail/steal and execution skips on lock contention
- Idempotent scheduler dispatch re-checks due state before execution to reduce race-driven double runs
- Production profile (`application-prod.yml`) with PostgreSQL datasource and secure env-var based config
- Production compose stack (`docker-compose.prod.yml`) wiring app + Postgres
- Safe operational visibility via `/actuator/info` build metadata exposure
- Release hardening assets: `.github/release-template.md`, `CHANGELOG.md`, and `OPERATIONS_RUNBOOK.md`

## Phase 1.2 delivered
- Distributed scheduler lock/lease (`scheduler_locks`) with acquire/renew/release + expiry steal semantics
- Webhook reliability hardening: bounded retry/backoff + deterministic idempotency key + DB duplicate suppression (`dispatched_alerts`)
- DB-backed identity: `app_users` + `user_roles`, bcrypt password hashes, DB `UserDetailsService`
- Guarded bootstrap admin initializer (`openpulse.security.bootstrap-admin.*`)
- Persistent audit logging (`audit_events`) for auth and write/check-trigger actions

## 5-minute quickstart (clean machine)

### 1) Clone
```bash
git clone https://github.com/<your-org>/open-pulse-checker.git
cd open-pulse-checker
```

### 2) One-command install

Linux/macOS:
```bash
./scripts/install.sh
```

Windows (PowerShell):
```powershell
./scripts/install.ps1
```

Installer behavior:
- runs preflight checks with clear failures/warnings
- bootstraps `.env` and `frontend/.env` from templates
- prefers Docker setup; falls back to local dependency install when Docker is unavailable

### 3) One-command run (backend + frontend + db)

Linux/macOS:
```bash
./scripts/run.sh start
```

Windows (PowerShell):
```powershell
./scripts/run.ps1 start
```

Default mode is `auto`:
- Docker available -> starts full stack via `docker-compose.full.yml` (`postgres + backend + frontend`)
- Docker unavailable -> starts scripted local fallback (`backend + frontend`, backend uses embedded H2)

### 4) Verify health
```bash
./scripts/run.sh health
```

Checks include:
- DB readiness (`pg_isready`) in Docker mode
- API reachability (`http://localhost:8080/api/v1/health`)
- frontend reachability (`http://localhost:5173`)

### 5) Control & troubleshooting
```bash
./scripts/run.sh status
./scripts/run.sh logs
./scripts/run.sh restart
./scripts/run.sh stop
```

## Legacy/manual run modes (backward compatibility)

### Backend-only dev
```bash
mvn spring-boot:run
```

### Frontend-only dev
```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

- Dev UI default: `http://localhost:5173`
- API default (configurable): `http://localhost:8080/api/v1`
- See `frontend/README.md` for architecture and component baseline details.

### Production profile (manual PostgreSQL wiring)
```bash
export SPRING_PROFILES_ACTIVE=prod
export OPENPULSE_DB_URL='jdbc:postgresql://postgres:5432/openpulse'
export OPENPULSE_DB_USERNAME='openpulse'
export OPENPULSE_DB_PASSWORD='strong-secret-from-vault'
mvn spring-boot:run
```
- Never commit DB credentials.
- Inject secrets via environment variables or secret manager.
- Flyway runs on startup in prod with validation enabled and clean disabled.
- Existing prod compose path remains available: `docker compose -f docker-compose.prod.yml up -d`.

## Bootstrap admin (required for fresh DB)
Enable one-time admin bootstrap via env/config:
```bash
export OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_ENABLED=true
export OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_USERNAME=admin
export OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_PASSWORD='change-me-now'
```
Passwords are stored bcrypt-hashed in `app_users.password_hash`.

## Auth model
- Roles: `ADMIN`, `VIEWER`
- Read monitor endpoints: `ADMIN` or `VIEWER`
- Write endpoints (`create`, `toggle`, `run-check`): `ADMIN`
- Auth successes/failures are audited to `audit_events`

## Scheduler lock semantics
- Per-monitor execution lock key: `monitor-check:{monitorId}`
- Each worker acquires a DB lease before dispatch
- Lease can be renewed by owner only while lease is still valid
- Release is owner-scoped to prevent cross-instance unlock
- Expired leases can be stolen by another instance (crash recovery)
- Scheduler rechecks monitor due/enabled state after lock renew before execution

Telemetry counters (Micrometer):
- `openpulse.scheduler.lock.acquire.success`
- `openpulse.scheduler.lock.acquire.fail`
- `openpulse.scheduler.lock.acquire.steal`
- `openpulse.scheduler.lock.renew.fail`
- `openpulse.scheduler.execution.skip.lock`
- `openpulse.scheduler.execution.skip.local_inflight`

Additional multi-node observability metrics:
- Alert dispatch attempts: `openpulse.alerts.dispatch.attempts{channel,outcome}`
- Alert dispatch latency: `openpulse.alerts.dispatch.latency{channel,outcome}`
- End-to-end alert delivery delay: `openpulse.alerts.delivery.delay{channel,outcome}`
- DLQ backlog gauge: `openpulse.alerts.dlq.backlog`
- DLQ oldest age gauge: `openpulse.alerts.dlq.oldest.age.seconds`
- DLQ replay counter: `openpulse.alerts.dlq.replay{result}`
- Check latency timer: `openpulse.checks.latency{status}`

Dashboard and alerts assets are in `monitoring/dashboard-observability.md` and `monitoring/alerts-prometheus.yml`.

## Webhook retry/idempotency
```yaml
openpulse:
  alerting:
    webhook:
      enabled: true
      url: https://your-webhook-endpoint
      max-attempts: 3
      initial-backoff-ms: 100
```
- Retries are bounded with exponential backoff
- `X-Idempotency-Key` header is deterministic per alert event
- Successfully delivered keys are stored in `dispatched_alerts` to suppress duplicates
- Notifier failures are isolated and never crash scheduler/check execution paths

## Phase 1.3 security hardening

- Sensitive endpoints are rate limited and return `429` with `Retry-After`.
- Service/API key auth is supported via `X-API-Key: <keyId>.<secret>`.
- API key secrets are never stored raw; SHA-256 hashes are persisted.
- Webhook delivery failures after retry exhaustion are persisted in DLQ and replayable via `POST /api/v1/admin/dlq/{id}/replay`.
- Actuator endpoints enabled: `/actuator/health`, `/actuator/health/readiness`, `/actuator/metrics` (metrics restricted to ADMIN).
