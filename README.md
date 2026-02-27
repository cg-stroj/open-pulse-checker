# Open Pulse Checker

Open Pulse Checker is a security-first, self-host-first OSS monitoring platform.

## Daily update (2026-02-26)
### ✅ What we completed today
- Stabilized and delivered core platform slices from **Phase 1.x to Phase 2.0**.
- Production hardening is in place: distributed scheduler locks, rate limiting, API keys, DLQ, audit logging, and PostgreSQL prod profile.
- Public status pages are now implemented with slug-based public endpoint and incident timeline.
- Security posture was strengthened across auth/authz, webhook delivery reliability, and operational runbooks.
- Latest validation: full automated test suite green (`tests=28, failures=0, errors=0, skipped=0`).

### ⏭️ What remains (planned for tomorrow)
- Start **Phase 2.1**:
  - notification policy customization,
  - maintenance windows,
  - incident annotations / manual incident updates.
- Add paging/filtering + richer audit querying for operations.
- Extend multi-node operational tooling and dashboards for lock/queue metrics.
- Prepare next release checklist and cut the next milestone increment.

## Phase 2.1 delivered (notification policy customization)
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

## Quick start
### Prereqs
- Java 21
- Maven 3.9+

### Tests
```bash
mvn test
```

### Run
```bash
mvn spring-boot:run
```

### Production profile (PostgreSQL)
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
- Containerized prod startup: `docker compose -f docker-compose.prod.yml up -d`.

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
- `openpulse.scheduler.execution.skip.lock`

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
