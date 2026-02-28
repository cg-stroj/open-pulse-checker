# Docker Smoke Matrix Report — 2026-02-28

Environment: Linux ubuntu-4gb-nbg1-3 6.8.0-100-generic #100-Ubuntu SMP PREEMPT_DYNAMIC Tue Jan 13 16:40:06 UTC 2026 x86_64 x86_64 x86_64 GNU/Linux

## Summary
- Result: **PARTIAL / BLOCKED**
- Blocker: Docker daemon not reachable in this environment.
- Script behavior: deterministic fail-fast for all lifecycle commands when Docker is unavailable.

## Preflight
- Command: `./scripts/preflight-checks.sh `
- Exit code: 1 (FAIL)
```text
[preflight] Open Pulse Checker Docker checks
[ok] docker found
[fail] Docker installed but daemon not reachable. Start Docker and retry.
[ok] docker compose plugin available
[ok] Port 8080 appears available
[warn] Port 5173 is in use. Startup may fail unless you change .env port settings.
[ok] Port 5432 appears available
[preflight] Failed. Resolve [fail] items and retry.
```

## Install
- Command: `./scripts/install.sh `
- Exit code: 1 (FAIL)
```text
[install] Open Pulse Checker install
[fail] Docker + Compose are required for install.
[hint] Install/start Docker and retry: ./scripts/install.sh
```

## Start
- Command: `./scripts/run.sh start`
- Exit code: 1 (FAIL)
```text
[fail] Docker + Compose are required.
```

## Status
- Command: `./scripts/run.sh status`
- Exit code: 1 (FAIL)
```text
[fail] Docker + Compose are required.
```

## Health
- Command: `./scripts/run.sh health`
- Exit code: 1 (FAIL)
```text
[fail] Docker + Compose are required.
```

## Logs
- Command: `./scripts/run.sh logs`
- Exit code: 1 (FAIL)
```text
[fail] Docker + Compose are required.
```

## Stop
- Command: `./scripts/run.sh stop`
- Exit code: 1 (FAIL)
```text
[fail] Docker + Compose are required.
```

## Reset
- Command: `./scripts/run.sh reset`
- Exit code: 1 (FAIL)
```text
[fail] Docker + Compose are required.
```

