# Roadmap

## Phase 1
### 1.0/1.1 (done)
- Core monitor/check/incident model
- API + scheduler baseline
- Security baseline and role policies
- Alerting abstraction baseline

### 1.2 (done)
- ✅ Distributed scheduler lock/lease (DB-backed, expiry steal)
- ✅ Webhook retry/backoff + idempotency key + duplicate suppression
- ✅ DB-backed identity (users/roles, bcrypt, DB UserDetailsService)
- ✅ Bootstrap admin initializer (config/env guarded)
- ✅ Persistent auth + write-action audit logging
- ✅ Test coverage for lock semantics, notifier retry/idempotency, DB auth role behavior, audit insertions

## Phase 1.3 delivered
- [x] Sensitive endpoint rate limiting with retry hint headers
- [x] Service account API keys with hashed-secret storage and role mapping
- [x] Webhook dead-letter queue + replay endpoint
- [x] Actuator health/readiness/metrics baseline and security posture docs

## Phase 1.4 (done)
- ✅ Multi-instance scheduler lock hardening (stale recovery clarity, contention telemetry)
- ✅ Scheduler dispatch idempotency check before execution
- ✅ Production profile and compose stack for PostgreSQL deployment
- ✅ Build/version metadata exposure via actuator info
- ✅ Release hardening templates and operational runbook (backup/restore/rollback)

## Phase 2.0 delivered
- [x] Public status pages by slug with monitor health summary and incident timeline
- [x] ADMIN management API for page create/list and monitor attach/reorder/remove
- [x] Status page schema + FK/indexed monitor mapping table
- [x] Endpoint/security tests for public/private behavior and admin authz

## Phase 2.1 delivered
- [x] Notification policy customization model (global, per-monitor, per-status-page scopes)
- [x] Severity routing rules + channel toggles (webhook)
- [x] Cooldown and de-dup windows enforced in alert dispatch path
- [x] Escalation step model (ordered, delay/min-severity metadata)
- [x] ADMIN CRUD API for notification policies
- [x] Flyway migration for policy persistence + dispatch metadata extensions

## Next
- maintenance windows
- paging/filtering and richer audit querying
- multi-node operational tooling and metrics (dashboarding/alerts on new lock counters)
