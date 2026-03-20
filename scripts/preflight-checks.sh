#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[preflight] Open Pulse Checker Docker checks"

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  echo "Usage: ./scripts/preflight-checks.sh"
  exit 0
fi

if [[ $# -ne 0 ]]; then
  echo "Usage: ./scripts/preflight-checks.sh"
  exit 1
fi

FAILED=0
WARNED=0

ok() { echo "[ok] $1"; }
warn() { echo "[warn] $1"; WARNED=1; }
fail() { echo "[fail] $1"; FAILED=1; }

env_value() {
  local key="$1" default="$2"
  local file="$ROOT_DIR/.env"
  if [[ ! -f "$file" ]]; then
    file="$ROOT_DIR/.env.example"
  fi
  local line
  line="$(grep -E "^${key}=" "$file" | tail -n1 || true)"
  if [[ -n "$line" ]]; then
    echo "${line#*=}"
  else
    echo "$default"
  fi
}

check_port_free() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    if lsof -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      warn "Port $port is in use. Update .env (OPENPULSE_PORT/OPENPULSE_FRONTEND_PORT/OPENPULSE_DB_PORT) before start."
    else
      ok "Port $port appears available"
    fi
  elif command -v ss >/dev/null 2>&1; then
    if ss -ltn "sport = :$port" | grep -q LISTEN; then
      warn "Port $port is in use. Update .env (OPENPULSE_PORT/OPENPULSE_FRONTEND_PORT/OPENPULSE_DB_PORT) before start."
    else
      ok "Port $port appears available"
    fi
  else
    warn "Cannot check port $port (missing lsof/ss)."
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

if command -v curl >/dev/null 2>&1; then
  ok "curl found (required for ./scripts/run.sh health)"
else
  fail "curl not found. Install curl for deterministic health checks."
fi

BACKEND_PORT="$(env_value OPENPULSE_PORT 8888)"
FRONTEND_PORT="$(env_value OPENPULSE_FRONTEND_PORT 5173)"
DB_PORT="$(env_value OPENPULSE_DB_PORT 5432)"

ok "Planned ports -> backend:${BACKEND_PORT} frontend:${FRONTEND_PORT} postgres:${DB_PORT}"
check_port_free "$BACKEND_PORT"
check_port_free "$FRONTEND_PORT"
check_port_free "$DB_PORT"

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  if docker compose -f "$ROOT_DIR/docker-compose.full.yml" --env-file "$ROOT_DIR/.env.example" config >/dev/null 2>&1; then
    ok "docker-compose.full.yml resolves with .env.example"
  else
    fail "docker-compose.full.yml validation failed against .env.example"
  fi
fi

if [[ "$FAILED" -eq 1 ]]; then
  echo "[preflight] Failed. Resolve [fail] items and retry."
  exit 1
fi

if [[ "$WARNED" -eq 1 ]]; then
  echo "[preflight] Completed with warnings."
else
  echo "[preflight] Completed successfully."
fi
