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

## Next
- notification policy customization
- paging/filtering and richer audit querying
- multi-node operational tooling and metrics (dashboarding/alerts on new lock counters)
