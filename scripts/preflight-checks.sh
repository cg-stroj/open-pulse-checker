#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-docker}"

echo "[preflight] Open Pulse Checker checks (mode: $MODE)"

if [[ "$MODE" == "auto" || "$MODE" == "local" ]]; then
  echo "[fail] Runtime mode '$MODE' is not supported. Open Pulse Checker is Docker-only."
  echo "[hint] Use: ./scripts/preflight-checks.sh docker"
  exit 1
fi

if [[ "$MODE" != "docker" ]]; then
  echo "[fail] Unsupported mode '$MODE'."
  echo "Usage: ./scripts/preflight-checks.sh [docker]"
  exit 1
fi

FAILED=0
WARNED=0

ok() { echo "[ok] $1"; }
warn() { echo "[warn] $1"; WARNED=1; }
fail() { echo "[fail] $1"; FAILED=1; }

check_port_free() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1 && lsof -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    warn "Port $port is in use. Startup may fail unless you change .env port settings."
  else
    ok "Port $port appears available"
  fi
}

if ! command -v docker >/dev/null 2>&1; then
  fail "Docker not found. Install Docker Engine/Desktop."
else
  ok "docker found"
fi

if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then
    ok "Docker daemon reachable"
  else
    fail "Docker installed but daemon not reachable. Start Docker and retry."
  fi

  if docker compose version >/dev/null 2>&1; then
    ok "docker compose plugin available"
  else
    fail "docker compose plugin not available. Install Docker Compose v2."
  fi
fi

check_port_free "8080"
check_port_free "5173"
check_port_free "5432"

if [[ "$FAILED" -eq 1 ]]; then
  echo "[preflight] Failed. Resolve [fail] items and retry."
  exit 1
fi

if [[ "$WARNED" -eq 1 ]]; then
  echo "[preflight] Completed with warnings."
else
  echo "[preflight] Completed successfully."
fi
