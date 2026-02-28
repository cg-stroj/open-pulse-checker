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

## Install and run mode

Open Pulse Checker scripts are **Docker-only** across platforms.

Required:
- Docker Engine/Desktop (daemon running)
- Docker Compose v2 (`docker compose`)

Run a preflight check anytime:

Linux/macOS:
```bash
./scripts/preflight-checks.sh docker
```

Windows (PowerShell):
```powershell
./scripts/preflight-checks.ps1 -Mode docker
```

Install:

Linux/macOS:
```bash
./scripts/install.sh docker
```

Windows (PowerShell):
```powershell
./scripts/install.ps1 -Mode docker
```

Run commands:
- `start`
- `stop`
- `restart`
- `status`
- `health`
- `logs`

Examples:

Linux/macOS:
```bash
./scripts/run.sh start docker
./scripts/run.sh status docker
```

Windows (PowerShell):
```powershell
./scripts/run.ps1 -Command start -Mode docker
./scripts/run.ps1 -Command status -Mode docker
```

Any `local`/`auto` runtime mode invocation fails fast with explicit guidance.

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
