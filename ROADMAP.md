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

## Next
- notification policy customization
- paging/filtering and richer audit querying
- multi-node operational tooling and metrics
