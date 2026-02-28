# Docker-only Smoke Matrix (Ticket #79)

## Scope
Validate the canonical Docker lifecycle path:

`install -> start -> status -> health -> logs -> reset`

## Checklist

| Step | Command | Expected |
|---|---|---|
| Preflight | `./scripts/preflight-checks.sh` | Docker daemon + compose reachable; ports checked |
| Install | `./scripts/install.sh` | `.env`/`frontend/.env` bootstrapped, postgres image pulled |
| Start | `./scripts/run.sh start` | Compose stack up, backend/frontend health green |
| Status | `./scripts/run.sh status` | Services listed as running |
| Health | `./scripts/run.sh health` | postgres + backend + frontend checks pass |
| Logs | `./scripts/run.sh logs` | Recent compose logs printed |
| Stop | `./scripts/run.sh stop` | Compose stack stopped |
| Reset | `./scripts/run.sh reset` | Full docker reset with volumes removed |
| Reset + env purge (optional) | `./scripts/run.sh reset --purge-env` | Same as reset + env files removed |

## Latest run evidence

See: `docs/docker-smoke-matrix-report-2026-02-28.md`
