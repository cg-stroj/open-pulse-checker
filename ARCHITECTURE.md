# Architecture

## Phase 1.4 additions
- `schedulerlock`: explicit acquire outcomes for lock contention/stale recovery visibility
- `service`: idempotent scheduler dispatch revalidation before check execution
- `actuator`: `/actuator/info` build metadata for release visibility
- `config`: `application-prod.yml` profile for PostgreSQL + safe Flyway defaults
- `operations`: release template/changelog/runbook documentation for deploy safety

## Phase 1.2 modules
- `api`: monitor + health endpoints
- `service`: monitor lifecycle, check execution, scheduler dispatch
- `schedulerlock`: DB-backed distributed lease lock (`scheduler_locks`)
- `alerting`: alert dispatch + webhook notifier with retry/idempotency dedupe
- `auth`: DB user/role persistence + DB `UserDetailsService` + bootstrap admin initializer
- `audit`: persistent auth and write-action audit trail
- `persistence`: monitor/check/incident JPA model + Flyway migrations

## Key runtime flow
1. Scheduler finds due monitors
2. For each monitor, attempts DB lease lock (`monitor-check:{id}`)
3. If acquired, check executes and result persists
4. Incident transition emits alert event
5. Webhook notifier sends with deterministic idempotency key + bounded retries
6. Successful deliveries are deduped via `dispatched_alerts`
7. Writes/auth events emit persistent audit records

## Delivered Phase 1.2 schema additions
- `scheduler_locks`
- `dispatched_alerts`
- `app_users`
- `user_roles`
- `audit_events`

## Phase 1.3 additions

- **Rate Limiting:** `RateLimitFilter` + in-memory token bucket keyed by principal/API key/IP.
- **Service Accounts:** `service_api_keys` table + `ApiKeyAuthenticationFilter` integrated into Spring Security roles.
- **Notifier DLQ:** exhausted webhook retries are persisted to `alert_dead_letters` and replayed through admin API.
- **Observability:** Spring Boot Actuator health/readiness/metrics plus counters for checks, alerts, auth failures, and rate-limit hits.
