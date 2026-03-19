# Open Pulse Checker — Technical Documentation

This document is the **behavior/API source of truth**.
For runtime module boundaries see `ARCHITECTURE.md`. For procedures see `OPERATIONS_RUNBOOK.md`.

## 1) System behavior baseline

- Monitor types: `HTTP`, `TCP`, `PING`
- Monitor write interval policy: `60`, `120`, `180`, `240`, `300` seconds only
- Notifications: minimal-release channel scope is **EMAIL-only**
- Retention: fixed **30-day** history retention (check results + resolved incidents)
- Dashboard: **top live monitor grid + bottom incident timeline**

## 2) API overview

Default local base URL: `http://localhost:8080`

### Health/platform
- `GET /api/v1/health`
- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/metrics` (ADMIN)

### Setup/bootstrap
- `GET /api/v1/setup/status`
- `POST /api/v1/setup/first-admin`

### Monitor APIs
- `GET /api/v1/monitors`
- `POST /api/v1/monitors`
- `PUT /api/v1/monitors/{id}`
- `PATCH /api/v1/monitors/{id}/enabled`
- `DELETE /api/v1/monitors/{id}`
- `POST /api/v1/monitors/{id}/run-check`

### Incident/admin APIs
- `GET /api/v1/admin/incidents`
- `POST /api/v1/admin/incidents/{id}/acknowledge`
- `POST /api/v1/admin/incidents/{id}/annotations`
- `POST /api/v1/admin/incidents/{id}/resolve`
- `POST /api/v1/admin/incidents/{id}/reopen`

### Policy/maintenance/status/audit
- Notification policies: `GET/POST/PUT /api/v1/admin/notification-policies...`
- Maintenance windows: `GET/POST/PUT/DELETE /api/v1/admin/maintenance-windows...`
- Status pages admin: `GET/POST /api/v1/status-pages`, monitor attach/remove endpoints
- Status pages public: `GET /api/v1/public/status-pages/{slug}`
- Audit v1/v2: `/api/v1/admin/audit-events...`, `/api/v2/admin/audit-events...`

## 3) Monitor model and validation rules

### Shared fields
- `name` (required)
- `type` (`HTTP` | `TCP` | `PING`)
- `targetUrl` (required)
- `intervalSec` (must be one of 60/120/180/240/300)
- `enabled`
- `timeoutMs`
- `emailAlertOnDown`
- `emailAlertOnRecovery`

### HTTP-only fields
- `httpMethod` (optional, defaults to `GET`)
- `expectedResponseKeyword` (optional substring check)

### Target rules by type
- `HTTP`: `http://` or `https://` URL with non-empty host
- `TCP`: `host:port`
- `PING`: hostname or IPv4 only; URL scheme/path/port rejected

### Type-specific behavior
- For `TCP`/`PING`, `httpMethod` and `expectedResponseKeyword` are reset/ignored.
- If HTTP keyword is configured and not found, check status is `DOWN` with explicit keyword-mismatch message.

## 4) Authentication, setup hardening, and rate limiting

### Auth model
- HTTP Basic user auth (`ADMIN`, `VIEWER`)
- Service API key auth via `X-API-Key`

### Setup hardening
- First-admin setup is one-time and becomes locked after completion.
- Optional bootstrap protection supports header secret and/or CIDR allowlist.

### Sensitive-route rate limiting (current scope)
- `GET /api/v1/admin/auth/login`
- `GET /api/v1/setup/status`
- `POST /api/v1/setup/first-admin`
- `POST /api/v1/monitors`
- `PATCH /api/v1/monitors/{id}/enabled`
- `POST /api/v1/monitors/{id}/run-check`

Responses use HTTP `429` with `Retry-After` on limit breach.

## 5) Notifications (minimal release scope)

- `NotificationChannel` enum contains multiple channels for future rollout.
- Runtime and policy validation are constrained by `NotificationChannelScope` to active channels.
- Current active set is **EMAIL only**.
- Non-email channels are rejected/filtered for minimal release flows.

## 6) Incident lifecycle

States:
- `OPEN`
- `ACKNOWLEDGED`
- `RESOLVED`

Automatic transitions:
- DOWN opens incident (if no active one exists).
- UP resolves active incident.

Manual controls:
- Acknowledge: `OPEN -> ACKNOWLEDGED`
- Resolve: `OPEN|ACKNOWLEDGED -> RESOLVED`
- Reopen: `RESOLVED -> OPEN`
- Annotate: no state change

## 7) Retention behavior

Implemented by `MonitorHistoryRetentionService`:
- Fixed retention constant: 30 days
- Purge targets:
  - `check_results.checked_at < cutoff`
  - `incidents.resolved_at < cutoff`
- Open incidents are retained.
- Cleanup runs on schedule and is distributed-lock protected.

## 8) Frontend layout expectations (current)

### App shell navigation
- Dashboard
- Monitors
- Incidents
- Audit Explorer
- Maintenance Windows
- Notification Policies
- Status Pages
- Settings

### Dashboard page
- Header summary badges (`DOWN`, `UP`, incident count)
- **Top section:** live monitor status grid
- **Bottom section:** incident timeline (list + selected incident details)

## 9) CI gate reality

GitHub Actions workflow (`.github/workflows/ci.yml`) currently runs:
1. `mvn clean verify`
2. `npm ci` (frontend)
3. `npx playwright install --with-deps chromium`
4. `npm run lint`
5. `npm run build`
6. `npm run test:e2e:smoke`

## 10) Drift-control references

When updating behavior docs, keep these files in sync in the same PR:
- `README.md` (operator summary)
- `ARCHITECTURE.md` (module/runtime view)
- `OPERATIONS_RUNBOOK.md` (operational procedures)
- `ROADMAP.md` (timeline + forward plan)
