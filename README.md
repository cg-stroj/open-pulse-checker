# Pulseguard

Pulseguard is a **security-first, self-host-first OSS monitoring platform** in active development.
Phase 0 establishes a hardened baseline for backend services, delivery pipelines, and contributor workflows.

## Vision
- Self-hosted by default
- Least-privilege architecture and secure defaults
- Transparent OSS workflows with built-in quality and security gates

## Quick Start
### Prerequisites
- Java 21
- Maven 3.9+
- Docker + Docker Compose

### Run locally (Maven)
```bash
mvn clean test
mvn spring-boot:run
curl http://localhost:8080/api/v1/health
```

### Run with Docker
```bash
docker compose up --build
curl http://localhost:8080/api/v1/health
```

## Current API
- `GET /api/v1/health` → service liveness and timestamp

## Installer skeleton
- `scripts/install.sh`
- `scripts/install.ps1`

Both scripts currently run preflight checks for Java, Docker, and port availability.

## License
AGPL-3.0-only. See [LICENSE](LICENSE).
