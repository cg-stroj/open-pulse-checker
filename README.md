# Open Pulse Checker

Open Pulse Checker is a **security-first, self-host-first OSS monitoring platform** in active development.

## Phase 1 (started)
This repository now includes the first vertical slice:
- Monitor domain + persistence (JPA + Flyway)
- HTTP check execution MVP
- Incident open/resolve transition flow
- API v1 monitor CRUD + manual check trigger

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

### API examples
Create a monitor:
```bash
curl -s -X POST http://localhost:8080/api/v1/monitors \
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
curl -s http://localhost:8080/api/v1/monitors
```

Get monitor by id:
```bash
curl -s http://localhost:8080/api/v1/monitors/{id}
```

Enable/disable monitor:
```bash
curl -s -X PATCH http://localhost:8080/api/v1/monitors/{id}/enabled \
  -H 'Content-Type: application/json' \
  -d '{"enabled": false}'
```

Trigger manual check:
```bash
curl -s -X POST http://localhost:8080/api/v1/monitors/{id}/run-check
```

Health endpoint:
```bash
curl -s http://localhost:8080/api/v1/health
```

## Security defaults
- No credentials or secrets embedded in source
- Strict input validation for API requests
- Only HTTP/HTTPS monitor targets allowed
- Flyway-managed schema (no runtime auto-DDL generation)
- `open-in-view` disabled to reduce accidental lazy-loading leaks

## License
AGPL-3.0-only. See [LICENSE](LICENSE).
