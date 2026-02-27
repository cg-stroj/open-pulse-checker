# Operations Runbook

## One-command stack operations (Ticket #59)

Primary full-stack path (Docker):
```bash
./scripts/install.sh
./scripts/run.sh start
./scripts/run.sh health
```

Control helpers:
```bash
./scripts/run.sh status
./scripts/run.sh logs
./scripts/run.sh restart
./scripts/run.sh stop
```

Local fallback (when Docker is unavailable):
- `./scripts/run.sh start local`
- Starts backend + frontend with embedded H2 database.
- Keeps legacy manual run modes available (`mvn spring-boot:run`, `npm run dev`).

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
- Phase 2.1 adds `V5__phase2_1_notification_policy.sql` and `V6__phase2_1_maintenance_windows.sql`.
- Post-deploy checks:
  - verify policy scope uniqueness and dispatch metadata indexes
  - verify `maintenance_windows` constraints for scope/type integrity
  - run a deterministic maintenance-window smoke test (one active `SUPPRESS` window + one `ANNOTATE` window)

## Maintenance window behavior (Ticket #41)
- `SUPPRESS`: while active, new DOWN transitions do not open incidents and no opened-alert is dispatched.
- `ANNOTATE`: transitions continue, but incident/alert reason text includes maintenance context.
- Recovery (`UP`) continues to resolve existing incidents, including during maintenance, to keep lifecycle deterministic.

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

### Alert triage playbooks

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

## Rollback guidance (failed migration/deploy)
1. Stop newly deployed app version.
2. Inspect Flyway history table and app logs to determine if migration partially applied.
3. If migration failed after changing schema/data, restore from predeploy backup.
4. Redeploy last known good app version.
5. Run smoke checks and compare monitor/check counts with predeploy baselines.
6. Open postmortem issue with migration ID, failure mode, and remediation.
