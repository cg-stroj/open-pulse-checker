# Open Pulse Checker

Open Pulse Checker is a security-first, self-hosted monitoring platform for uptime checks, incident tracking, alert routing, and public status communication.

## Why teams use it

- **Single operational surface:** monitors, incidents, alert policy, maintenance windows, and status pages.
- **Security by default:** role-based access, API keys, persistent audit trail, and safe public/private boundaries.
- **Operator-focused workflows:** manual incident controls, notification suppression/annotation rules, and audit exports.
- **Production-minded runtime:** PostgreSQL support, distributed scheduler locking, and observability metrics.

## Quickstart (5 minutes)

### 1) Clone

```bash
git clone https://github.com/<your-org>/open-pulse-checker.git
cd open-pulse-checker
```

### 2) Install

Linux/macOS:
```bash
./scripts/install.sh
```

Windows (PowerShell):
```powershell
./scripts/install.ps1
```

### 3) Start the stack

Linux/macOS:
```bash
./scripts/run.sh start
```

Windows (PowerShell):
```powershell
./scripts/run.ps1 start
```

### 4) Verify health

Linux/macOS:
```bash
./scripts/run.sh health
```

Windows (PowerShell):
```powershell
./scripts/run.ps1 health
```

Default endpoints (from `.env`):
- API: `http://localhost:8888/api/v1`
- Frontend: `http://localhost:5173`

Important (local mode): frontend port is deterministic. By default it is `5173` (or `OPENPULSE_FRONTEND_PORT` if explicitly set), and `run.sh` will **not** auto-increment or rewrite `.env` at runtime.
If the configured frontend port is already in use, startup fails fast with a clear message.

Override example (explicit env only):

```bash
OPENPULSE_FRONTEND_PORT=5180 ./scripts/run.sh start local
```

## Install and run modes

### Prerequisites (what is required)

PostgreSQL is always required (no H2 fallback), regardless of runtime mode.

### Option A: Docker runtime (recommended)

Required:
- Docker Engine (daemon running)
- Docker Compose v2 (`docker compose`)

### Option B: Local runtime (without Docker)

Required:
- Java 21+
- Maven 3.9+
- Node.js 20+ (22 recommended) + npm
- PostgreSQL server + client tools (`psql`, `pg_isready`)

### Optional but useful

- `curl` (used by health checks)
- `lsof` (port collision checks)

### CI note (GitHub Actions)

Integration tests require PostgreSQL. The CI workflow provisions a `postgres` service container and passes:
- `OPENPULSE_DB_URL=jdbc:postgresql://localhost:5432/openpulse`
- `OPENPULSE_DB_USERNAME=openpulse`
- `OPENPULSE_DB_PASSWORD=openpulse`

Run a preflight check anytime:

Linux/macOS:
```bash
./scripts/preflight-checks.sh [auto|docker|local]
```

Windows (PowerShell):
```powershell
./scripts/preflight-checks.ps1 [auto|docker|local]
```
(`local` intentionally fails fast on Windows because local runtime provisioning is not yet supported there.)

### Installation options

### 1) One-command install (recommended)

Linux/macOS:
```bash
./scripts/install.sh [auto|docker|local]
```

Windows (PowerShell):
```powershell
./scripts/install.ps1 [auto|docker|local]
```

Interactive setup wizard (writes `.env` safely):

Linux/macOS:
```bash
./scripts/install.sh --wizard
# or force default selection baseline:
./scripts/install.sh docker --wizard
```

Windows (PowerShell):
```powershell
./scripts/install.ps1 -Wizard
# or:
./scripts/install.ps1 -Mode docker -Wizard
```

Behavior:
- **Linux/macOS**
  - `auto` (default) chooses Docker when ready, otherwise local mode
  - `docker` forces Docker stack setup
  - `local` forces native backend/frontend + local PostgreSQL setup (installer provisions local PostgreSQL role + database)
- **Windows (PowerShell scripts)**
  - `auto` resolves to Docker-only flow
  - `docker` forces Docker stack setup
  - `local` is currently **unsupported** and fails fast with an explicit error message

Installer also bootstraps `.env` + `frontend/.env` from examples when missing.

Wizard prompts cover:
- runtime mode (`auto|docker|local`)
- backend/frontend/database ports (validated 1..65535)
- database name/user/password (with secure password generation option)
- optional bootstrap admin username/password (with secure password generation option)

Windows still fails fast for `local` runtime selection (non-interactive and wizard mode) to match current platform policy.

### 2) Manual setup

Backend:
```bash
mvn spring-boot:run
```

Frontend:
```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

### 3) Production profile (manual backend)

```bash
export SPRING_PROFILES_ACTIVE=prod
export OPENPULSE_DB_URL='jdbc:postgresql://postgres:5432/openpulse'
export OPENPULSE_DB_USERNAME='openpulse'
export OPENPULSE_DB_PASSWORD='strong-secret-from-vault'
mvn spring-boot:run
```

## Uninstall / reset

Linux/macOS:
```bash
./scripts/uninstall.sh
```

Windows (PowerShell):
```powershell
./scripts/uninstall.ps1
```

Options:
- remove Docker DB volume too: `--purge-data` (PowerShell: `-PurgeData`)
- remove generated `.env` files: `--purge-env` (PowerShell: `-PurgeEnv`)
- non-interactive: `--yes` (PowerShell: `-Yes`)

Example full reset (including DB data + env files):
```bash
./scripts/uninstall.sh --purge-data --purge-env --yes
```

## Basic operations

- **Bootstrap first admin** (fresh DB): enable `OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_*` env vars.
- **Create and manage monitors** via `/api/v1/monitors` and admin UI modules.
- **Operate incidents** with acknowledge/annotate/resolve/reopen controls.
- **Configure alert behavior** with notification policies and maintenance windows.
- **Publish service health** using public status pages by slug.

## Documentation index

- Product + setup overview: **this README**
- Delivery timeline: [`ROADMAP.md`](./ROADMAP.md)
- Technical documentation: [`DOCUMENTATION.md`](./DOCUMENTATION.md)
- Architecture snapshot: [`ARCHITECTURE.md`](./ARCHITECTURE.md)
- Operations and rollback procedures: [`OPERATIONS_RUNBOOK.md`](./OPERATIONS_RUNBOOK.md)
- Release gate checklist: [`docs/v2.1-release-readiness-checklist.md`](./docs/v2.1-release-readiness-checklist.md)

## License

Licensed under [GNU AGPL-3.0](./LICENSE).
