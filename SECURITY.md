# Security Policy

## Phase 1.2 controls
- DB-backed identity (`app_users`, `user_roles`) with bcrypt password hashing
- Role-based authorization (`ADMIN`/`VIEWER`) enforced at endpoint level
- Guarded bootstrap admin initializer for initial access provisioning
- Persistent audit logging (`audit_events`) for auth success/failure and write actions
- Distributed scheduler safety via DB lock leases (`scheduler_locks`) with expiry/steal
- Alert delivery resilience with bounded retry/backoff and idempotency dedupe (`dispatched_alerts`)

## Credential handling
- Do not commit credentials or bootstrap passwords
- Use environment variables/secrets manager for bootstrap admin values
- Disable bootstrap admin after initial provisioning
- Rotate credentials after suspected disclosure

## Lock safety assumptions
- Lease duration bounds duplicate execution windows across instances
- Expired lease steal supports worker crash recovery
- Clock skew between nodes should be kept minimal (NTP recommended)

## Audit
- Auth events: login success + failure
- Write/check actions: monitor create/update-enabled/run-check
- Audit trail persisted with actor, action, target, result, timestamp

## Reporting
Please use GitHub private security advisories for vulnerabilities.
