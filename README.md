# Open Pulse Checker

Open Pulse Checker is a **security-first, self-host-first OSS monitoring platform** in active development.

## Phase 1 (started)
This repository now includes the first vertical slice plus Phase 1.1 baseline:
- Monitor domain + persistence (JPA + Flyway)
- HTTP check execution MVP
- Incident open/resolve transition flow
- API v1 monitor CRUD + manual check trigger
- Periodic scheduler with bounded worker pool and due-time logic
- Alert dispatch abstraction + webhook notifier baseline
- Spring Security authn/authz baseline (ADMIN/VIEWER)

## Quick Start
### Prerequisites
- Java 21
- Maven 3.9+

### Run tests
```bash
mvn -q test
```

### Run locally
```bash
mvn spring-boot:run
```

### Security/auth configuration (local)
Default local credentials are intentionally low-trust placeholders and should be overridden:
- `admin` / `admin-change-me` (role `ADMIN`)
- `viewer` / `viewer-change-me` (role `VIEWER`)

Override with env vars:
```bash
export OPENPULSE_SECURITY_ADMIN_USERNAME=admin
export OPENPULSE_SECURITY_ADMIN_PASSWORD='replace-me'
export OPENPULSE_SECURITY_VIEWER_USERNAME=viewer
export OPENPULSE_SECURITY_VIEWER_PASSWORD='replace-me-too'
```

All monitor read endpoints require authentication (`ADMIN` or `VIEWER`).
Write endpoints require `ADMIN`:
- `POST /api/v1/monitors`
- `PATCH /api/v1/monitors/{id}/enabled`
- `POST /api/v1/monitors/{id}/run-check`

### API examples
Create a monitor:
```bash
curl -s -X POST http://localhost:8080/api/v1/monitors \
  -u admin:admin-change-me \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Example",
    "type": "HTTP",
    "targetUrl": "https://example.com",
    "intervalSec": 60,
    "enabled": true,
    "timeoutMs": 1500
  }'
```

List monitors:
```bash
curl -s -u viewer:viewer-change-me http://localhost:8080/api/v1/monitors
```

Get monitor by id:
```bash
curl -s -u viewer:viewer-change-me http://localhost:8080/api/v1/monitors/{id}
```

Enable/disable monitor:
```bash
curl -s -X PATCH http://localhost:8080/api/v1/monitors/{id}/enabled \
  -u admin:admin-change-me \
  -H 'Content-Type: application/json' \
  -d '{"enabled": false}'
```

Trigger manual check:
```bash
curl -s -X POST -u admin:admin-change-me http://localhost:8080/api/v1/monitors/{id}/run-check
```

Health endpoint:
```bash
curl -s http://localhost:8080/api/v1/health
```

## Scheduler behavior
- Poll loop runs every `openpulse.scheduler.poll-interval-ms` (default `5000`)
- Only enabled monitors are considered
- A monitor is due when `lastCheckedAt + intervalSec <= now`
- Execution is bounded by a fixed worker pool (`openpulse.scheduler.worker-pool-size`, default `4`)
- Duplicate concurrent execution for the same monitor is prevented via in-flight guard

## Alerting baseline
- Alert dispatch abstraction: `AlertNotifier`
- Webhook notifier is config-driven and opt-in:
```yaml
openpulse:
  alerting:
    webhook:
      enabled: true
      url: https://your-webhook-endpoint
```
- Incident OPEN emits `INCIDENT_OPENED`
- Incident recovery emits `INCIDENT_RESOLVED`
- Notifier failures are logged and isolated so check execution/scheduler keep running

## Security defaults
- No credentials or secrets embedded in source
- Strict input validation for API requests
- Only HTTP/HTTPS monitor targets allowed
- Flyway-managed schema (no runtime auto-DDL generation)
- `open-in-view` disabled to reduce accidental lazy-loading leaks

## License
AGPL-3.0-only. See [LICENSE](LICENSE).
