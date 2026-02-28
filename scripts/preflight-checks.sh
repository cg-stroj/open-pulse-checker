#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-auto}"

echo "[preflight] Open Pulse Checker checks (mode: $MODE)"

FAILED=0
WARNED=0

ok() { echo "[ok] $1"; }
warn() { echo "[warn] $1"; WARNED=1; }
fail() { echo "[fail] $1"; FAILED=1; }

check_cmd() {
  local cmd="$1"
  local message="${2:-Install '$cmd' and retry.}"
  if command -v "$cmd" >/dev/null 2>&1; then
    ok "$cmd found"
  else
    fail "$cmd not found. $message"
  fi
}

check_port_free() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1 && lsof -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    warn "Port $port is in use. Startup may fail unless you change .env port settings."
  else
    ok "Port $port appears available"
  fi
}

docker_ready() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1
}

if docker_ready; then
  ok "Docker daemon + compose ready"
elif [[ "$MODE" == "docker" ]]; then
  fail "Docker mode requested but Docker/Compose is not ready."
else
  warn "Docker not ready; installer/run can use local PostgreSQL mode."
  check_cmd java "Install Java 21+."
  check_cmd mvn "Install Maven 3.9+."
  check_cmd node "Install Node.js 20+ (22 recommended)."
  check_cmd npm "Install npm with Node.js."
  check_cmd psql "Install PostgreSQL client."
  check_cmd pg_isready "Install PostgreSQL tools."
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
