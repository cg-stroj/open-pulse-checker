# Operations Runbook (Phase 1.4)

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
- Phase 2.1 adds `V5__phase2_1_notification_policy.sql`; verify policy scope uniqueness and dispatch metadata indexes post-deploy.

## Rollback guidance (failed migration/deploy)
1. Stop newly deployed app version.
2. Inspect Flyway history table and app logs to determine if migration partially applied.
3. If migration failed after changing schema/data, restore from predeploy backup.
4. Redeploy last known good app version.
5. Run smoke checks and compare monitor/check counts with predeploy baselines.
6. Open postmortem issue with migration ID, failure mode, and remediation.
