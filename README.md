# Open Pulse Checker

Open Pulse Checker is a security-first, self-host-first OSS monitoring platform.

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
- Lease can be renewed by owner; released on completion
- Expired leases can be stolen by another instance (crash recovery)

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
