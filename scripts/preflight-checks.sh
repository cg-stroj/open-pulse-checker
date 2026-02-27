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
    return 0
  fi
  fail "$cmd not found. $message"
  return 1
}

check_port_free() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1 && lsof -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    warn "Port $port is in use. Startup may fail unless you change port settings."
  else
    ok "Port $port appears available"
  fi
}

docker_available() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

if [[ "$MODE" == "docker" || "$MODE" == "auto" ]]; then
  if command -v docker >/dev/null 2>&1; then
    if docker info >/dev/null 2>&1; then
      ok "Docker daemon reachable"
      if docker compose version >/dev/null 2>&1; then
        ok "docker compose plugin available"
      else
        fail "docker compose plugin not available. Install Docker Compose v2."
      fi
    else
      [[ "$MODE" == "docker" ]] && fail "Docker installed but daemon not reachable. Start Docker Desktop/daemon and retry." || warn "Docker daemon not reachable; will require local fallback mode."
    fi
  else
    [[ "$MODE" == "docker" ]] && fail "Docker not found." || warn "Docker not found; will require local fallback mode."
  fi
fi

DOCKER_OK=0
if docker_available; then
  DOCKER_OK=1
fi

if [[ "$MODE" == "local" || ( "$MODE" == "auto" && "$DOCKER_OK" -eq 0 ) ]]; then
  check_cmd java "Install Java 21+ and set JAVA_HOME."
  check_cmd mvn "Install Maven 3.9+ and retry."
  check_cmd node "Install Node.js 20+ (22 recommended)."
  check_cmd npm "Install npm with Node.js."
fi

check_port_free "8080"
check_port_free "5173"
check_port_free "5432"

if [[ "$FAILED" -eq 1 ]]; then
  echo "[preflight] Failed. Resolve the [fail] items and retry."
  exit 1
fi

if [[ "$WARNED" -eq 1 ]]; then
  echo "[preflight] Completed with warnings."
else
  echo "[preflight] Completed successfully."
fi
