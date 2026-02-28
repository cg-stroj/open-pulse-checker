# Open Pulse Checker

Open Pulse Checker is a security-first, self-hosted monitoring platform for uptime checks, incident tracking, alert routing, and public status communication.

## Quickstart (Docker-only)

### 1) Clone

```bash
git clone https://github.com/<your-org>/open-pulse-checker.git
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

### 5) Verify health

Linux/macOS:
```bash
./scripts/run.sh health
```

Windows (PowerShell):
```powershell
./scripts/run.ps1 -Command health
```

Default endpoints (from `.env`):
- API: `http://localhost:8888/api/v1`
- Frontend: `http://localhost:5173`

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

## Troubleshooting basics

- If scripts fail with Docker checks, start Docker Desktop/Engine and retry.
- If startup fails due to ports, update `.env` (`OPENPULSE_PORT`, `OPENPULSE_FRONTEND_PORT`) and rerun `start`.
- Use `status` for service state and `logs` for diagnostics.
- Use `reset` when state is inconsistent and you need a clean Docker data reset.

## Smoke checklist

See [`docs/docker-smoke-matrix.md`](./docs/docker-smoke-matrix.md) for the Docker smoke checklist and latest execution evidence.

## Documentation index

- Product + setup overview: **this README**
- Delivery timeline: [`ROADMAP.md`](./ROADMAP.md)
- Technical documentation: [`DOCUMENTATION.md`](./DOCUMENTATION.md)
- Architecture snapshot: [`ARCHITECTURE.md`](./ARCHITECTURE.md)
- Operations and rollback procedures: [`OPERATIONS_RUNBOOK.md`](./OPERATIONS_RUNBOOK.md)
- Release gate checklist: [`docs/v2.1-release-readiness-checklist.md`](./docs/v2.1-release-readiness-checklist.md)

## License

Licensed under [GNU AGPL-3.0](./LICENSE).
