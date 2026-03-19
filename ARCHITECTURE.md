# Architecture

This file describes runtime architecture and module responsibilities.
For API/feature behavior details, use `DOCUMENTATION.md`.

## Source-of-truth boundaries

- Runtime/module architecture: **this file**
- API contracts and behavioral rules: `DOCUMENTATION.md`
- Operational execution/rollback procedures: `OPERATIONS_RUNBOOK.md`
- Delivery timeline and planned direction: `ROADMAP.md`

## Current architecture snapshot

### Runtime components
- **Backend API/service (Spring Boot)**
  - monitor lifecycle, check execution, incidents, policies, setup/auth hardening, audit
- **Frontend (React + TypeScript + Vite)**
  - admin/operator UX and public status-page preview
- **Database (PostgreSQL)**
  - monitors/check results/incidents, identity/roles, policies, maintenance windows, audit

### Core modules
- `api`: REST controllers (monitor, incident, setup, status page, maintenance, policies, audit)
- `service`: domain logic (monitor validation, check scheduling, incident transitions, retention)
- `schedulerlock`: DB-backed lease lock for multi-instance safe scheduling
- `alerting`: notifier dispatch + dedup/cooldown + DLQ support; **minimal release routing is email-only**
- `notificationpolicy`: policy model + scope precedence + active-channel enforcement
- `auth` + `setup` + `apikey`: user auth, first-admin bootstrap, service key auth
- `audit`: persistent auth and privileged-write event trail
- `persistence`: JPA entities + Flyway migrations

## Key runtime flows

### Monitor check flow
1. Scheduler finds due monitors.
2. Node attempts distributed lock (`monitor-check:{id}`).
3. On lock acquisition, check executes and result persists.
4. Incident transitions are evaluated.
5. Alert dispatch resolves policy and sends via active channels (email-only in current release).
6. Dispatch history is deduped/audited.

### Setup/auth hardening flow
1. Setup is exposed via `/api/v1/setup/*` until first admin is created.
2. Optional bootstrap protection gate can restrict setup endpoints (header secret and/or CIDR allowlist).
3. Successful first-admin creation hard-locks setup.
4. Sensitive setup/auth routes are rate-limited.

## Data model highlights

- `monitors` supports `HTTP`, `TCP`, `PING` with shared fields plus HTTP-specific method/keyword fields.
- `check_results` stores per-check status, latency, status code, error context.
- `incidents` tracks lifecycle (`OPEN`, `ACKNOWLEDGED`, `RESOLVED`).
- `notification_*` tables store scope, route rules, escalation metadata (active channel scope currently locked to `EMAIL`).
- `scheduler_locks` enables safe multi-node scheduling and retention cleanup locking.
- `audit_events` records auth and privileged write actions.

## Retention model

- Fixed `30` day retention window (implemented in `MonitorHistoryRetentionService`).
- Purges:
  - old `check_results` by `checked_at`
  - old **resolved** incidents by `resolved_at`
- Open incidents are intentionally retained.

## CI gate architecture reality

GitHub Actions (`.github/workflows/ci.yml`) executes:
- backend verify: `mvn clean verify`
- frontend gates: `npm run lint`, `npm run build`, `npm run test:e2e:smoke`

If pipeline scope changes, update this section and `README.md` + `OPERATIONS_RUNBOOK.md` together.
