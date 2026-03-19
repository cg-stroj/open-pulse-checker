# Open Pulse Checker — Technical Documentation

## Table of contents

1. [System architecture](#system-architecture)
2. [API overview](#api-overview)
3. [Authentication and authorization model](#authentication-and-authorization-model)
4. [Observability](#observability)
5. [Incident lifecycle and manual controls](#incident-lifecycle-and-manual-controls)
6. [Maintenance windows](#maintenance-windows)
7. [Notification policy system](#notification-policy-system)
8. [Status pages](#status-pages)
9. [Audit API v2](#audit-api-v2)
10. [Frontend modules](#frontend-modules)
11. [Deployment and runbook links](#deployment-and-runbook-links)

---

## System architecture

Open Pulse Checker is a backend + frontend platform with persistent storage and Docker-based runtime.

### Core runtime components

- **Backend API/service (Spring Boot)**
  - Monitor management and check execution
  - Incident lifecycle management
  - Alert routing/dispatch
  - Admin control endpoints
- **Frontend (React + TypeScript + Vite)**
  - Operational/admin UX for monitors, incidents, policies, and status pages
- **Database (PostgreSQL)**
  - Monitor/check/incident state
  - Identity, roles, API keys
  - Alert dispatch history, maintenance windows, audit events

### Architectural modules

- `api`: REST controllers for monitor, incident, status-page, policy, and audit operations
- `service`: business rules (check scheduling, incident transitions, policy resolution)
- `schedulerlock`: DB-backed lease locking for multi-instance safe scheduling
- `alerting`: webhook channel dispatch, retry/backoff, idempotency, DLQ replay
- `auth`: user/role auth + API key auth + emergency-only bootstrap admin fallback init
- `audit`: persistent event trail for auth and privileged writes
- `persistence`: JPA model + Flyway migrations

### High-level flow

1. Scheduler discovers due monitors.
2. Each due monitor attempts lock acquisition (`monitor-check:{monitorId}`).
3. On lock acquisition, check executes and result is persisted.
4. State transitions open/resolve incidents.
5. Alert dispatch evaluates policy + maintenance behavior.
6. Deliveries are retried (bounded), deduplicated, and auditable.

## API overview

All examples assume default base URL: `http://localhost:8080`.

### Health and platform

- `GET /api/v1/health`
- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/metrics`

### First-run setup APIs

- `GET /api/v1/setup/status` (public; returns setup lock state and one-time setup token while setup is still open)
- `POST /api/v1/setup/first-admin` (public; requires setup token, creates initial `ADMIN`, then hard-locks setup)

### Monitor APIs

- `GET /api/v1/monitors`
- `POST /api/v1/monitors`
- `PUT /api/v1/monitors/{id}`
- `PATCH /api/v1/monitors/{id}/enabled`
- `DELETE /api/v1/monitors/{id}`
- `POST /api/v1/monitors/{id}/run-check`

Delete policy (Ticket #112):
- `DELETE /api/v1/monitors/{id}` requires `ADMIN` role.
- Status page bindings are detached automatically via FK cascade (`status_page_monitors.monitor_id -> monitors.id ON DELETE CASCADE`).
- Deletion is explicitly blocked with `409 Conflict` when monitor history exists in `check_results` or `incidents`.
- Conflict response is deterministic and actionable, for example:
  - `Monitor deletion blocked: historical references exist (checkResults=3, incidents=1). Remove related history first or archive the monitor by disabling it.`
- Successful deletions write an audit event with action `MONITOR_DELETE`.

List APIs support optional query ergonomics:
- `paged=true|false` (default `false` for compatibility)
- `page`, `size`, `sortBy`, `sortDir`, plus endpoint-specific filters

#### Advanced monitor capabilities (Tickets #97/#98/#99)

Supported monitor `type` values:
- `HTTP`
- `TCP`
- `PING`

Request/response fields relevant to advanced checks:
- `httpMethod` (HTTP monitors only): optional, defaults to `GET` when omitted.
- `expectedResponseKeyword` (HTTP monitors only): optional substring match against response body.

Behavior rules:
- `HTTP` monitor target must be `http://` or `https://` URL.
- `PING` monitor target uses URL validation as HTTP (`http/https` + host required).
- `TCP` monitor target must use `host:port` format (for example `localhost:5432`).
- For non-HTTP monitors (`TCP`, `PING`), `httpMethod` and `expectedResponseKeyword` are ignored/reset.
- Keyword matching is a direct substring check (`contains`) against response body.
- If the keyword is configured and not found, check result is `DOWN` with error message:
  - `Expected response keyword not found: <keyword>`

### Incident/admin APIs

- `GET /api/v1/admin/incidents`
- `POST /api/v1/admin/incidents/{id}/acknowledge`
- `POST /api/v1/admin/incidents/{id}/annotations`
- `POST /api/v1/admin/incidents/{id}/resolve`
- `POST /api/v1/admin/incidents/{id}/reopen`

### Notification policy APIs

- `GET /api/v1/admin/notification-policies`
- `GET /api/v1/admin/notification-policies/{id}`
- `POST /api/v1/admin/notification-policies`
- `PUT /api/v1/admin/notification-policies/{id}`

### Maintenance window APIs

- `GET /api/v1/admin/maintenance-windows`
- `GET /api/v1/admin/maintenance-windows/{id}`
- `POST /api/v1/admin/maintenance-windows`
- `PUT /api/v1/admin/maintenance-windows/{id}`
- `DELETE /api/v1/admin/maintenance-windows/{id}`

### Status page APIs

- Admin:
  - `POST /api/v1/status-pages`
  - `GET /api/v1/status-pages`
  - `POST /api/v1/status-pages/{id}/monitors`
  - `DELETE /api/v1/status-pages/{id}/monitors/{monitorId}`
- Public:
  - `GET /api/v1/public/status-pages/{slug}`

### Audit APIs

- v1 baseline:
  - `GET /api/v1/admin/audit-events`
  - `GET /api/v1/admin/audit-events/export?format=csv|json`
- v2 extended:
  - `GET /api/v2/admin/audit-events`
  - `GET /api/v2/admin/audit-events/export?format=csv|json`

## Authentication and authorization model

### Auth methods

- **HTTP Basic** for user-based sessions (admin/viewer).
- **API key auth** for service accounts using `X-API-Key: <keyId>.<secret>`.

### Roles

- `ADMIN`: full admin operations and write privileges.
- `VIEWER`: read-oriented access where explicitly allowed.

### Security model notes

- Admin endpoints are restricted under admin routes.
- API key secrets are not stored in plaintext; hashed secret values are persisted.
- First-run setup token values are issued as one-time secrets, stored only as hashes, and enforced with expiration.
- Setup is hard-locked once an initial `ADMIN` exists.
- Auth success/failure and privileged writes are captured in audit logs.
- Sensitive endpoints include rate limiting with `429` + retry hints.

## Observability

### Metrics and telemetry

Platform exposes Micrometer metrics through Actuator. Key families include:

- Scheduler lock outcomes and skip reasons:
  - `openpulse.scheduler.lock.acquire.success`
  - `openpulse.scheduler.lock.acquire.fail`
  - `openpulse.scheduler.lock.acquire.steal`
  - `openpulse.scheduler.lock.renew.fail`
  - `openpulse.scheduler.execution.skip.lock`
  - `openpulse.scheduler.execution.skip.local_inflight`
- Alert dispatch/reliability:
  - `openpulse.alerts.dispatch.attempts`
  - `openpulse.alerts.dispatch.latency`
  - `openpulse.alerts.delivery.delay`
  - `openpulse.alerts.dlq.backlog`
  - `openpulse.alerts.dlq.oldest.age.seconds`
  - `openpulse.alerts.dlq.replay`

### Ops usage

- Use the frontend dashboard module for real-time operational visibility.
- Use the runbook for SLO thresholds, alert tuning, and triage flows.
- Treat DLQ backlog/age growth as high-priority delivery incidents.

## Incident lifecycle and manual controls

### States

- `OPEN`
- `ACKNOWLEDGED`
- `RESOLVED`

### Automatic behavior

- DOWN transitions open an incident when no active (`OPEN`/`ACKNOWLEDGED`) incident exists.
- UP transitions resolve active (`OPEN`/`ACKNOWLEDGED`) incidents.

### Manual controls (admin)

- Acknowledge: `OPEN -> ACKNOWLEDGED`
- Resolve: `OPEN|ACKNOWLEDGED -> RESOLVED`
- Reopen: `RESOLVED -> OPEN`
- Annotate: add context without state transition

Manual actions require reason input and are persisted in:
- `incident_manual_events` (domain-level incident trail)
- `audit_events` (security/compliance trail)

## Monitor history retention (Ticket #116)

Fixed retention policy:
- Monitor check history (`check_results`) is retained for exactly 30 days.
- Resolved incidents (`incidents` with non-null `resolved_at`) are retained for exactly 30 days based on `resolved_at`.
- Open incidents are not purged by retention while still active.
- Incident manual events are cleaned up automatically through FK cascade when their parent incident is purged.

Cleanup execution:
- A scheduled cleanup job runs periodically and purges records older than the 30-day cutoff.
- Cleanup is lock-protected using `scheduler_locks` so only one node performs retention at a time.
- APIs naturally reflect the retained window because data is removed at persistence level.

## Maintenance windows

Maintenance windows are policy-aware schedule overlays for planned operational work.

### Types

- `ONE_TIME`: absolute UTC `startAt`/`endAt`
- `RECURRING`: timezone + day/time recurrence (`timezone`, `recurringDays`, `recurringStartTime`, `recurringEndTime`)

### Scopes

- `GLOBAL`
- `MONITOR`

### Policies

- `SUPPRESS`: suppresses new DOWN-triggered incident openings and open alerts during active window.
- `ANNOTATE`: keeps normal transitions active but annotates reasons with maintenance context.

Recovery behavior remains deterministic: existing incidents resolve on UP transitions.

## Notification policy system

Notification policies control how alerts are routed and suppressed.

### Scope precedence

1. `MONITOR`
2. `STATUS_PAGE`
3. `GLOBAL`

Most specific matching scope wins.

### Core features

- Severity route rules (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`)
- Channel toggles per severity/escalation step (WEBHOOK, EMAIL, TELEGRAM, SLACK, DISCORD, TEAMS)
- Cooldown and dedup windows integrated in dispatch path
- Ordered escalation-step metadata (`stepOrder`, `afterSeconds`, `minSeverity`)

### Persistence model

- `notification_policies`
- `notification_route_rules`
- `notification_escalation_steps`
- dispatch metadata extensions in `dispatched_alerts`

### Channel setup + troubleshooting

Configuration prefixes:
- `openpulse.alerting.webhook.*`
- `openpulse.alerting.email.*`
- `openpulse.alerting.telegram.*`
- `openpulse.alerting.slack.*`
- `openpulse.alerting.discord.*`
- `openpulse.alerting.teams.*`

Test trigger endpoint:
- `POST /api/v1/admin/notification-policies/{id}/test`

Troubleshooting checklist:
- Verify channel config is enabled and URL/token/chat/email target is set.
- Confirm channel is selected in route/escalation rules for target severity.
- Check retries/DLQ counters (`openpulse.alerts.failed`, `openpulse.alerts.dlq`) in metrics.
- Inspect DLQ entries and replay with `POST /api/v1/admin/dlq/{id}/replay`.
- Secrets are redacted in errors and DLQ payload/failure reason fields by default.

## Status pages

Status pages provide externally shareable service health views.

### Public model

- Public endpoint is unauthenticated only when page is configured public (`is_public=true`).
- Non-public slugs return `404` to reduce information leakage.

### Data composition

Public payload includes:
- Monitor health summary
- Derived page status (e.g., operational/degraded/outage)
- Bounded recent incident timeline

### Admin operations

- Create/list status pages
- Attach/reorder/remove monitors on a page
- Control public visibility

## Audit API v2

Audit API v2 is additive and preserves v1 compatibility.

### Endpoints

- `GET /api/v2/admin/audit-events`
- `GET /api/v2/admin/audit-events/export?format=csv|json`

### Query model

Common filters:
- `q`, `actor`, `action`, `resource`, `outcome`, `fromAt`, `toAt`

Pagination modes:
- Page mode (default): `page`, `size`
- Cursor mode: `cursorMode=true&size=...&cursor=...` with `nextCursor`

### Export guardrails

- Export default limit is bounded.
- Hard maximum export cap is enforced (`5000`).
- Unsupported export formats return `400`.

## Frontend modules

Delivered frontend modules include:

- **Admin auth/session UX** (`/login`, protected routes, 401/403 handling)
- **Ops Dashboard** (`/dashboard`) for scheduler/alerting telemetry
- **Monitors** (`/monitors`) for list/detail/create/edit/toggle/run-check/delete with explicit impact confirmation
- **Incidents Console** (`/incidents`) for lifecycle controls and annotations
- **Maintenance Windows** (`/maintenance-windows`) CRUD and schedule configuration
- **Notification Policies** (`/notification-policies`) scope/routing/escalation controls
- **Status Pages** (`/status-pages`) page management + public preview
- **Audit Explorer** (`/audit-explorer`) filtered query + CSV/JSON export

Shared UX foundations:
- query/cache provider
- reusable UI primitives
- resilient loading/error/empty states
- smoke-tested route navigation and key admin flow coverage

## Deployment and runbook links

- Operational runbook: [`OPERATIONS_RUNBOOK.md`](./OPERATIONS_RUNBOOK.md)
- Release readiness checklist: [`docs/v2.1-release-readiness-checklist.md`](./docs/v2.1-release-readiness-checklist.md)
- Architecture snapshot: [`ARCHITECTURE.md`](./ARCHITECTURE.md)
- User-facing quickstart and usage: [`README.md`](./README.md)
- Delivery timeline: [`ROADMAP.md`](./ROADMAP.md)
