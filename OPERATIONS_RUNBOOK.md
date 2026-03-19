# Operations Runbook

## QA-first delivery protocol (MANDATORY)

For this project, all work follows this gate:

1. **Implement on feature branch**
   - Never develop directly on `main`.
2. **Local technical gate (agent)**
   - Backend tests pass.
   - Frontend lint/build/e2e smoke pass.
   - Runtime health (`./scripts/run.sh health`) is green.
3. **Manual UI acceptance (BOS)**
   - BOS verifies behavior in UI on running instance.
   - No push to GitHub before BOS says explicit "OK to push".
4. **Push/merge gate**
   - Only after BOS acceptance and clean working tree.
   - Ticket moves to `Ready`; BOS decides `Done`.

Release command baseline before BOS acceptance:
```bash
mvn test
cd frontend && npm run lint && npm run build && npm run test:e2e:smoke && cd ..
./scripts/run.sh health
```

Backend test prerequisite (local + CI parity):
- Java 21 + Maven 3.9+.
- Backend tests run against in-memory H2 (PostgreSQL compatibility mode) for deterministic execution.
- Docker/Testcontainers are **not** required for `mvn test` / `mvn clean verify`.

CI parity (enforced on push/PR via `.github/workflows/ci.yml`):
- Backend verify with H2-only test runtime (`mvn clean verify`)
- Frontend lint (`npm run lint`)
- Frontend production build (`npm run build`)
- Frontend Playwright smoke (`npm run test:e2e:smoke`)

## Assistant runtime access model (Docker control)

Goal: assistant can run `status/logs/restart/health` directly for faster diagnostics.

Recommended host setup:
1. Add runtime user to docker group (or equivalent secure socket access policy).
2. Re-login shell/session to apply group membership.
3. Verify access:
```bash
docker ps
docker compose -f docker-compose.full.yml ps
```

Security note:
- Docker socket access is privileged. Use only on trusted operator host.
- If group access is not allowed, use operator-executed commands from this runbook as fallback.

## One-command Docker lifecycle

Primary bootstrap path:
```bash
./scripts/install.sh
./scripts/run.sh start
./scripts/run.sh health
```

Deterministic lifecycle controls:
```bash
./scripts/run.sh status
./scripts/run.sh logs
./scripts/run.sh restart
./scripts/run.sh stop
./scripts/run.sh reset
```

Optional full env cleanup:
```bash
./scripts/run.sh reset --purge-env
```

## Admin onboarding and emergency bootstrap fallback

Default path (recommended):
1. Use onboarding endpoints (`GET /api/v1/setup/status`, `POST /api/v1/setup/first-admin`) to create the first admin.
2. Keep bootstrap admin fallback flags disabled.

Emergency-only fallback (break-glass):
1. Set both flags to `true` before startup:
   - `OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_ENABLED=true`
   - `OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_EMERGENCY_FALLBACK_ENABLED=true`
2. Provide temporary credentials via:
   - `OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_USERNAME`
   - `OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_PASSWORD`
3. Restart service and perform recovery login.
4. Immediately set both flags back to `false` and restart.

Guardrails:
- Fallback is blocked after setup completion (`setup_state.setup_locked=true`).
- Fallback is blocked if any `ADMIN` role already exists.

## Backup before deploy/migration
1. Put deployment in maintenance window.
2. Verify target image/tag and migration scripts to be applied.
3. Take a PostgreSQL logical backup:
   ```bash
   pg_dump --format=custom --file=openpulse-predeploy.dump "$OPENPULSE_DB_URL"
   ```
4. Store backup in encrypted storage with retention policy.

## Restore procedure
1. Stop app instances writing to the database.
2. Restore backup to a clean DB or target DB:
   ```bash
   pg_restore --clean --if-exists --no-owner --dbname="$OPENPULSE_DB_URL" openpulse-predeploy.dump
   ```
3. Start one app instance and verify `/actuator/health` + key API checks.
4. Scale remaining instances once checks pass.

## Migration safety
- `spring.jpa.hibernate.ddl-auto=validate` is required in production.
- Flyway runs at startup with `validate-on-migrate=true` and `clean-disabled=true`.
- Never use Flyway clean in production.
- Apply schema changes with reviewed SQL migrations committed under `src/main/resources/db/migration`.
- `V10__setup_state_id_integer.sql` automatically upgrades legacy `setup_state.id` from `SMALLINT` to `INTEGER` at startup (no manual SQL required).
- Phase 2.1 adds `V5__phase2_1_notification_policy.sql` and `V6__phase2_1_maintenance_windows.sql`.
- Post-deploy checks:
  - verify policy scope uniqueness and dispatch metadata indexes
  - verify `maintenance_windows` constraints for scope/type integrity
  - run a deterministic maintenance-window smoke test (one active `SUPPRESS` window + one `ANNOTATE` window)

## Maintenance window behavior (Ticket #41)
- `SUPPRESS`: while active, new DOWN transitions do not open incidents and no opened-alert is dispatched.
- `ANNOTATE`: transitions continue, but incident/alert reason text includes maintenance context.
- Recovery (`UP`) continues to resolve existing incidents, including during maintenance, to keep lifecycle deterministic.

## Ops API list querying (Ticket #43)
For larger operational datasets, list endpoints support DB-backed paging/filtering/sorting.

Examples:
- `GET /api/v1/monitors?paged=true&page=0&size=50&sortBy=updatedAt&sortDir=desc&enabled=true&q=api`
- `GET /api/v1/admin/incidents?paged=true&page=0&size=50&sortBy=openedAt&sortDir=desc&state=OPEN`
- `GET /api/v1/status-pages?paged=true&page=0&size=50&sortBy=name&sortDir=asc&isPublic=true`

Behavior:
- Default (`paged=false`) keeps legacy array responses for backward compatibility.
- `paged=true` returns metadata: `items`, `page`, `size`, `total`, `totalPages`, `hasNext`, `hasPrevious`.
- Guardrails: negative page coerced to `0`; invalid/non-positive size defaults to `25`; max size `200`.

## Monitor deletion operations (Ticket #112)

Endpoint:
- `DELETE /api/v1/monitors/{id}` (`ADMIN` only)

Deterministic delete policy:
- Status page bindings are removed automatically by DB cascade.
- Monitor deletion is blocked (`409 Conflict`) if monitor has any rows in `check_results` or `incidents`.
- API error message includes blocking counters (`checkResults`, `incidents`) for operator triage.
- Each successful delete writes `MONITOR_DELETE` audit event.

Recovery and rollback notes:
- Deletion is destructive for monitor configuration and status-page binding links.
- Monitor deletion remains blocked while history exists; retention cleanup (30-day window) removes old history automatically.
- If a monitor was deleted by mistake, recreate monitor with same target/config and reattach to status pages.
- For incident timeline continuity, prefer disabling monitors instead of deleting when history exists.

## Monitor history retention operations (Ticket #116)

Policy:
- Fixed 30-day retention for monitor history.
- Purged datasets:
  - `check_results.checked_at < now-30d`
  - `incidents.resolved_at < now-30d` (resolved incidents only)
- `incident_manual_events` are removed automatically via `ON DELETE CASCADE` when parent incidents are purged.

Execution model:
- Scheduled cleanup job runs periodically (`openpulse.retention.cleanup-interval-ms`, default `3600000` ms).
- Job is protected by distributed scheduler lock (`scheduler_locks`) to avoid multi-node double execution.

Operator notes:
- Open incidents are intentionally retained until resolved.
- Dashboard/admin/status APIs automatically show only retained history because old rows are physically purged.
- If cleanup appears stalled, inspect scheduler lock health and application logs for `Monitor history retention cleanup failed`.

## Incident manual lifecycle operations (Ticket #42)

Admin endpoints:
- `POST /api/v1/admin/incidents/{id}/acknowledge`
- `POST /api/v1/admin/incidents/{id}/annotations`
- `POST /api/v1/admin/incidents/{id}/resolve`
- `POST /api/v1/admin/incidents/{id}/reopen`

Operational rules:
- `OPEN -> ACKNOWLEDGED` (manual acknowledge)
- `OPEN|ACKNOWLEDGED -> RESOLVED` (manual resolve)
- `RESOLVED -> OPEN` (manual reopen)
- Annotation does not change state.
- All manual actions require a non-blank reason and are written to:
  - `incident_manual_events` (domain trail)
  - `audit_events` (security/audit trail)

Validation failures return `400`; wrong role returns `403`; unauthenticated returns `401`.

## Audit API v2 operations (Ticket #44)

Examples:
- `GET /api/v2/admin/audit-events?page=0&size=50&actor=admin&action=INCIDENT_RESOLVE&outcome=SUCCESS&fromAt=2026-02-01T00:00:00Z&toAt=2026-02-28T00:00:00Z`
- `GET /api/v2/admin/audit-events?cursorMode=true&size=100&q=incident&cursor=<nextCursor>`
- `GET /api/v2/admin/audit-events/export?format=csv&limit=2000&actor=admin`
- `GET /api/v2/admin/audit-events/export?format=json&q=AUTH_LOGIN&outcome=FAILURE`

Behavior:
- API is additive (`/api/v1/admin/audit-events` remains unchanged for compatibility).
- Query/export filters are parity-aligned to avoid mismatched export sets.
- Export limit defaults to `1000` and is capped at `5000` rows for operational safety.
- Cursor mode is keyset-style based on `occurredAt` descending for efficient deep pagination.

## Multi-node observability (Ticket #45)

### SLO-aligned thresholds
- **Lock contention ratio**: target <10%; warning if >20% for 10m.
- **Scheduler lock skips**: warning if `skip.lock` sustained >0.10/s for 15m.
- **Notifier failure ratio**: target <1%; critical if >5% for 10m.
- **Alert delivery p95 delay**: warning if >120s for 15m.
- **DLQ backlog**: critical if unreplayed backlog >25 for 15m.
- **DLQ oldest age**: critical if oldest unreplayed item age >900s (15m) for 10m.

### Key metrics (via `/actuator/metrics`)
- `openpulse.scheduler.lock.acquire.success`
- `openpulse.scheduler.lock.acquire.fail`
- `openpulse.scheduler.lock.acquire.steal`
- `openpulse.scheduler.lock.renew.fail`
- `openpulse.scheduler.execution.skip.lock`
- `openpulse.scheduler.execution.skip.local_inflight`
- `openpulse.alerts.dispatch.attempts`
- `openpulse.alerts.dispatch.latency`
- `openpulse.alerts.delivery.delay`
- `openpulse.alerts.dlq.backlog`
- `openpulse.alerts.dlq.oldest.age.seconds`
- `openpulse.alerts.dlq`
- `openpulse.alerts.dlq.replay`

### Dashboard observability troubleshooting (Ticket #111)
1. Verify actuator exposure and auth (expect `401` without auth, `200` with ADMIN):
   ```bash
   curl -i http://localhost:8080/actuator/metrics
   curl -i -u admin:*** http://localhost:8080/actuator/metrics
   ```
2. Verify required metrics are present in catalog:
   ```bash
   curl -s -u admin:*** http://localhost:8080/actuator/metrics | jq -r '.names[]' | grep '^openpulse\.'
   ```
3. Verify one concrete metric payload:
   ```bash
   curl -s -u admin:*** "http://localhost:8080/actuator/metrics/openpulse.alerts.dispatch.latency?tag=outcome:success"
   ```
4. If UI is served behind a path prefix (example `/openpulse`), ensure frontend API base URL includes that prefix so dashboard derives actuator base URL correctly:
   - `VITE_API_BASE_URL=https://<host>/openpulse/api/v1`
   - Dashboard actuator calls must resolve to `https://<host>/openpulse/actuator/...`
5. UI diagnostics now map to failure class:
   - `401` → session invalid/expired
   - `403` → account missing ADMIN privileges
   - `404 endpoint` → `/actuator` route/exposure/proxy issue
   - `404 metric` → metric not registered/exposed
   - `5xx` → backend actuator/server failure
   - network error → routing/CORS/connectivity issue

## Alert triage playbooks

#### 1) Scheduler lock contention high (warning)
1. Check node count and scheduler cadence; verify no accidental over-scaling.
2. Inspect DB performance around `scheduler_locks` table and slow queries.
3. Compare `openpulse.scheduler.lock.acquire.steal` trend (crash recovery vs true contention).
4. If contention persists, temporarily increase monitor intervals for low-priority checks.

#### 2) Scheduler execution skip rate high (warning)
1. Compare `openpulse.scheduler.execution.skip.lock` and `openpulse.scheduler.lock.renew.fail`.
2. If renew failures spike, inspect thread pool saturation and JVM pauses.
3. Validate DB connectivity/latency between app nodes and PostgreSQL.
4. Ensure lock lease duration is adequate for current check execution profile.

#### 3) DLQ backlog/age critical
1. Verify notifier endpoint health (DNS, TLS, auth, 5xx/429 responses).
2. Review failed dispatch reason from DLQ entries (never log secrets/tokens).
3. Restore notifier availability, then replay items:
   `POST /api/v1/admin/dlq/{id}/replay`.
4. Confirm `openpulse.alerts.dlq.backlog` returns toward 0 and oldest age drops.

#### 4) Notifier failure ratio critical
1. Check upstream webhook SLO/status page and outbound network controls.
2. Confirm `openpulse.alerts.dispatch.attempts{outcome="failed"}` by channel.
3. If channel-specific, isolate failing channel and keep healthy channels active.
4. If global, activate incident communication fallback and reduce duplicate traffic.

#### 5) Alert delivery delay p95 high
1. Inspect `openpulse.alerts.dispatch.latency` vs `openpulse.alerts.delivery.delay`.
2. High dispatch latency => notifier call slowness; high delivery delay with low dispatch latency => queueing/retry pressure.
3. Verify retry settings (`max-attempts`, `initial-backoff-ms`) and upstream rate limits.
4. After mitigation, verify p95 trend returns below 120s.

## Release cut process (v2.1 gate)
1. Ensure local main is current and clean:
   ```bash
   git checkout main
   git pull --ff-only origin main
   ```
2. Run mandatory verification gates:
   ```bash
   mvn test
   cd frontend && npm run lint && npm run build && npm run test:e2e:smoke
   cd ..
   ```
3. Take pre-deploy DB backup:
   ```bash
   pg_dump --format=custom --file=openpulse-predeploy-$(date +%F-%H%M%S).dump "$OPENPULSE_DB_URL"
   ```
4. Prepare release notes from `.github/release-template.md` and update `CHANGELOG.md`.
5. Create and push annotated tag:
   ```bash
   VERSION=v2.1.0
   git tag -a "$VERSION" -m "Open Pulse Checker $VERSION"
   git push origin main
   git push origin "$VERSION"
   ```
6. Deploy tag/artifact with your environment's standard deploy path.
7. Post-deploy verification:
   ```bash
   curl -fsS http://localhost:8080/actuator/health
   curl -fsS http://localhost:8080/actuator/info
   ```

## Rollback guidance (failed migration/deploy)
1. Stop newly deployed app version.
2. Inspect Flyway history table and app logs to determine if migration partially applied.
3. If migration failed after changing schema/data, restore from predeploy backup.
4. Redeploy last known good app version/tag.
5. Run smoke checks and compare monitor/check counts with predeploy baselines:
   ```bash
   curl -fsS http://localhost:8080/actuator/health
   curl -fsS http://localhost:8080/api/v1/health
   cd frontend && npm run test:e2e:smoke
   ```
6. Open postmortem issue with migration ID, failure mode, and remediation.
