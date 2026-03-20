# Open Pulse Checker

Open Pulse Checker is a self-hosted uptime and incident platform.

This README is the **operator-facing entry point**. For implementation details, use the source-of-truth links in [Documentation map](#documentation-map-and-source-of-truth).

## Current release reality (minimal scope)

- Monitor types: `HTTP`, `TCP`, `PING`
- Interval policy for create/update: **60/120/180/240/300 seconds** (1–5 minutes)
- Notifications: **email-only active channel** in runtime and policy validation
- Retention: fixed **30-day** purge window for check results and resolved incidents
- Dashboard layout: **top live monitor grid + bottom incident timeline**
- Auth/setup hardening: setup bootstrap guard, setup lock after first admin, route-level rate limiting on sensitive endpoints
- CI gates (GitHub Actions): backend `mvn clean verify`, frontend `lint`, `build`, Playwright `test:e2e:smoke`

## Monitor model (quick reference)

Shared fields:
- `name`
- `type` (`HTTP` | `TCP` | `PING`)
- `targetUrl`
- `intervalSec` (must be 60/120/180/240/300)
- `timeoutMs`
- `enabled`
- `emailAlertOnDown`
- `emailAlertOnRecovery`

HTTP-only fields:
- `httpMethod` (defaults to `GET` when omitted)
- `expectedResponseKeyword` (optional response-body substring)

Target rules:
- `HTTP`: must be `http://` or `https://` URL with host
- `TCP`: must be `host:port`
- `PING`: hostname or IPv4 target (URL scheme/path/port rejected)

## Quickstart (Docker-only)

### 1) Clone

```bash
git clone https://github.com/cg-stroj/open-pulse-checker.git
cd open-pulse-checker
```

### 2) Preflight (optional, recommended)

Linux/macOS:
```bash
./scripts/preflight-checks.sh
```

Windows (PowerShell):
```powershell
./scripts/preflight-checks.ps1
```

### 3) Install

Linux/macOS:
```bash
./scripts/install.sh
```

Windows (PowerShell):
```powershell
./scripts/install.ps1
```

### 4) Start

Linux/macOS:
```bash
./scripts/run.sh start
```

Windows (PowerShell):
```powershell
./scripts/run.ps1 -Command start
```

### 5) Verify health + login path

Linux/macOS:
```bash
./scripts/run.sh health
curl -i http://localhost:5173/api/v1/admin/auth/login
```

Windows (PowerShell):
```powershell
./scripts/run.ps1 -Command health
curl.exe -i http://localhost:5173/api/v1/admin/auth/login
```

Expected login-path result before auth is configured: `401 Unauthorized` (this confirms frontend proxy -> backend auth route).

Default endpoints (from `.env`):
- Frontend (UI + API via same-origin proxy): `http://localhost:5173`
- API via frontend origin (recommended): `http://localhost:5173/api/v1`
- Direct backend API (ops/debug): `http://localhost:8888/api/v1`

For standard Docker install/start there are **no manual API base or CORS edits required**.

## Deterministic lifecycle commands

Linux/macOS:
- `./scripts/run.sh start`
- `./scripts/run.sh stop`
- `./scripts/run.sh restart`
- `./scripts/run.sh status`
- `./scripts/run.sh health`
- `./scripts/run.sh logs`
- `./scripts/run.sh reset` (full Docker reset: containers + network + volumes)
- `./scripts/run.sh reset --purge-env` (also removes generated `.env` files)

Windows (PowerShell):
- `./scripts/run.ps1 -Command start`
- `./scripts/run.ps1 -Command stop`
- `./scripts/run.ps1 -Command restart`
- `./scripts/run.ps1 -Command status`
- `./scripts/run.ps1 -Command health`
- `./scripts/run.ps1 -Command logs`
- `./scripts/run.ps1 -Command reset`
- `./scripts/run.ps1 -Command reset -PurgeEnv`

## Development/CI reality

Local backend tests use deterministic H2 (PostgreSQL compatibility mode); Docker/Testcontainers are not required for `mvn test`/`mvn clean verify`.

GitHub Actions CI (`.github/workflows/ci.yml`) currently runs:
1. `mvn --batch-mode --no-transfer-progress clean verify`
2. `npm ci` (frontend)
3. `npx playwright install --with-deps chromium`
4. `npm run lint`
5. `npm run build`
6. `npm run test:e2e:smoke`

## Documentation map and source of truth

- **Product/operator quickstart (entry point):** `README.md` (this file)
- **System behavior and module boundaries:** `ARCHITECTURE.md`
- **API/feature behavior details:** `DOCUMENTATION.md`
- **Operational procedures and rollback:** `OPERATIONS_RUNBOOK.md`
- **Delivered timeline and next phases:** `ROADMAP.md`

Drift rule:
- If behavior-level statements change in this README, update `DOCUMENTATION.md` and `ARCHITECTURE.md` in the same PR.

## License

Licensed under [GNU AGPL-3.0](./LICENSE).
