# Security Policy

## Phase 2.0 controls
- Public status page endpoint only exposes data for pages explicitly flagged `is_public=true`
- Non-public status pages intentionally return `404` on public slug access to avoid page enumeration/data leakage
- Status page management endpoints remain `ADMIN`-only
- Incident timeline responses are bounded (latest 20) to reduce abuse/query amplification risk

## Phase 1.4 controls
- Distributed scheduler lock hardening: owner-scoped renew/release and explicit stale lease recovery path
- Lock contention and stale-steal telemetry for operational anomaly detection
- Production profile isolation on PostgreSQL (no in-memory DB in prod profile)
- Flyway production safety flags (`validate-on-migrate=true`, `clean-disabled=true`)
- Build metadata exposure scoped to safe actuator info fields

## Phase 1.2 controls
- DB-backed identity (`app_users`, `user_roles`) with bcrypt password hashing
- Role-based authorization (`ADMIN`/`VIEWER`) enforced at endpoint level
- Guarded bootstrap admin emergency fallback initializer (opt-in, disabled by default)
- Persistent audit logging (`audit_events`) for auth success/failure and write actions
- Distributed scheduler safety via DB lock leases (`scheduler_locks`) with expiry/steal
- Alert delivery resilience with bounded retry/backoff and idempotency dedupe (`dispatched_alerts`)

## Credential handling
- Do not commit credentials or bootstrap passwords
- Prefer onboarding flow (`/api/v1/setup/*`) for first admin provisioning
- Bootstrap admin env fallback is emergency-only and disabled by default
- Enabling fallback requires both `OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_ENABLED=true`
  and `OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_EMERGENCY_FALLBACK_ENABLED=true`
- Emergency fallback is blocked once setup is locked or any `ADMIN` role exists
- Use environment variables/secrets manager for fallback credentials and disable immediately after use
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

## API key and monitoring security

- API keys are service-account credentials carried in `X-API-Key` using `keyId.secret` format.
- Only hashed secrets are stored in `service_api_keys.secret_hash`.
- Use bootstrap only for local initialization (`openpulse.security.bootstrap-api-key.*`) and rotate immediately.
- Rate limiting is applied to auth and monitor write surfaces; exceeded limits return HTTP 429.
- Metrics endpoint should be treated as sensitive and remains ADMIN-protected.
