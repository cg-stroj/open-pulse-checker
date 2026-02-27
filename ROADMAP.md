# Roadmap

This roadmap is a chronological delivery record for Open Pulse Checker.

## Timeline of delivered milestones

### Phase 1.0–1.1 · Foundation (initial baseline)
- Core monitor/check/incident domain and scheduler baseline.
- API surface for monitor operations and health endpoints.
- Initial RBAC/security baseline and alerting abstraction.

### Phase 1.2 · Reliability + identity + audit
- Distributed scheduler lock/lease model (`scheduler_locks`) with safe renewal/steal semantics.
- Webhook retry/backoff with deterministic idempotency keys and duplicate suppression (`dispatched_alerts`).
- Database-backed identity (`app_users`, `user_roles`) with bcrypt password storage.
- Guarded bootstrap-admin initialization path.
- Persistent audit logging for auth and admin write actions (`audit_events`).

### Phase 1.3 · Security hardening
- Sensitive endpoint rate limiting with retry hints.
- Service/API key authentication (`X-API-Key`) with hashed secret persistence.
- Dead-letter queue for failed webhook deliveries and admin replay endpoint.
- Actuator observability baseline (`health`, `readiness`, `metrics`) with role controls.

### Phase 1.4 · Production-readiness slice
- Scheduler lock hardening with explicit acquire outcomes and contention telemetry.
- Idempotent scheduler dispatch revalidation before check execution.
- Production profile with PostgreSQL wiring and safe Flyway defaults.
- Release-operability assets: runbook, changelog, and release templates.

### Phase 2.0 · Status pages
- Public status pages by slug with monitor health summary and bounded incident timeline.
- Admin APIs/UI for status page create/list and monitor attach/reorder/remove.
- Public/private visibility controls for externally shared pages.

### Phase 2.1 · Policy-driven operations
- Notification policy system with scope precedence (`MONITOR` > `STATUS_PAGE` > `GLOBAL`).
- Severity route rules, cooldown/de-dup behavior, and escalation-step metadata.
- Maintenance windows (`ONE_TIME` / `RECURRING`, `SUPPRESS` / `ANNOTATE`) with timezone-aware evaluation.
- Admin CRUD APIs and migrations for policy + maintenance window persistence.

### Phase 2.2 · Incident operations lifecycle
- Admin incident lifecycle controls: acknowledge, annotate, resolve, reopen.
- Manual-event persistence for actor/action/reason/state transitions.
- Audit integration for every manual incident operation.

### Phase 2.3 · Audit explorer and exports
- Admin audit query APIs with filterable retrieval and bounded CSV/JSON export.
- Frontend Audit Explorer module for operational investigation workflows.

### Phase 2.4 · List scalability and query ergonomics
- Paging/filtering/sorting support for monitors, incidents, and status pages.
- Backward-compatible response mode (`paged=false` legacy arrays, `paged=true` metadata envelope).

### Frontend/admin UX expansion (current delivery wave)
- Admin session UX with route guards and consistent 401/403 handling.
- Delivered modules: dashboard, monitors, incidents, maintenance windows, notification policies, status pages, audit explorer.
- Accessibility/responsiveness baseline and deterministic smoke E2E quality gate.

## Concise future direction

- Deepen incident history and timeline read APIs for richer RCA workflows.
- Expand multi-node operational tooling and alert tuning on observability signals.
- Continue release hardening for repeatable upgrades and lower-risk rollbacks.
