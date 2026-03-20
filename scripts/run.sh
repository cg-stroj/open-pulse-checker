#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMMAND="${1:-start}"
PURGE_ENV=false

usage() {
  cat <<'EOF'
Usage: ./scripts/run.sh {start|stop|restart|status|health|logs|reset} [--purge-env]

Docker-only runtime lifecycle:
  start    Build + start all services
  stop     Stop and remove containers/network
  restart  Recreate stack
  status   Show compose service status
  health   Run backend/frontend/postgres health checks
  logs     Tail compose logs
  reset    Full reset (down --volumes --remove-orphans); optional --purge-env
EOF
}

case "$COMMAND" in
  start|stop|restart|status|health|logs|reset) ;;
  -h|--help) usage; exit 0 ;;
  *) usage; exit 1 ;;
esac

if [[ "$#" -gt 2 ]]; then
  usage
  exit 1
fi

if [[ "${2:-}" == "--purge-env" ]]; then
  PURGE_ENV=true
elif [[ "$#" -eq 2 ]]; then
  usage
  exit 1
fi

fail() {
  echo "[fail] $1" >&2
  exit 1
}

set_env_value() {
  local file="$1" key="$2" value="$3" tmp
  tmp="$(mktemp)"
  awk -F= -v k="$key" -v v="$value" 'BEGIN{u=0} $1==k {print k"="v; u=1; next} {print} END{if(!u) print k"="v}' "$file" > "$tmp"
  mv "$tmp" "$file"
}

get_env_value() {
  local key="$1" default="$2" file="$ROOT_DIR/.env"
  if [[ -f "$file" ]]; then
    local line
    line="$(grep -E "^${key}=" "$file" | tail -n1 || true)"
    if [[ -n "$line" ]]; then
      echo "${line#*=}"
      return
    fi
  fi
  echo "$default"
}

ensure_env_files() {
  [[ -f "$ROOT_DIR/.env" ]] || cp "$ROOT_DIR/.env.example" "$ROOT_DIR/.env"
  [[ -f "$ROOT_DIR/frontend/.env" ]] || cp "$ROOT_DIR/frontend/.env.example" "$ROOT_DIR/frontend/.env"
}

docker_ready() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1
}

require_http_client() {
  command -v curl >/dev/null 2>&1 || fail "curl is required for health checks. Install curl and retry."
}

compose_cmd() {
  docker compose -f "$ROOT_DIR/docker-compose.full.yml" --env-file "$ROOT_DIR/.env" "$@"
}

validate_compose_config() {
  compose_cmd config >/dev/null || fail "docker compose configuration is invalid. Check .env values and retry."
}

wait_http() {
  local name="$1" url="$2" timeout="${3:-120}" elapsed=0
  until curl -fsS "$url" >/dev/null 2>&1; do
    sleep 2
    elapsed=$((elapsed+2))
    [[ $elapsed -lt $timeout ]] || fail "$name healthcheck timed out after ${timeout}s: $url"
  done
  echo "[ok] $name reachable: $url"
}

print_endpoints() {
  local backend_port frontend_port
  backend_port="$(get_env_value OPENPULSE_PORT 8888)"
  frontend_port="$(get_env_value OPENPULSE_FRONTEND_PORT 5173)"

  cat <<EOF
[next] Open Pulse Checker endpoints:
  - Frontend UI: http://localhost:${frontend_port}
  - API via frontend proxy (recommended): http://localhost:${frontend_port}/api/v1
  - Direct backend API: http://localhost:${backend_port}/api/v1
  - Backend health: http://localhost:${backend_port}/api/v1/health
[next] Login path check: curl -i http://localhost:${frontend_port}/api/v1/admin/auth/login
EOF
}

health_docker() {
  local backend_port frontend_port db_user db_name
  backend_port="$(get_env_value OPENPULSE_PORT 8888)"
  frontend_port="$(get_env_value OPENPULSE_FRONTEND_PORT 5173)"
  db_user="$(get_env_value OPENPULSE_DB_USERNAME openpulse)"
  db_name="$(get_env_value OPENPULSE_DB_NAME openpulse)"

  compose_cmd exec -T postgres pg_isready -U "$db_user" -d "$db_name" >/dev/null || fail "Postgres is not ready. Run './scripts/run.sh logs' for details."
  echo "[ok] postgres reachable"

  wait_http backend "http://localhost:${backend_port}/api/v1/health" 120
  wait_http frontend "http://localhost:${frontend_port}" 120
}

reset_stack() {
  compose_cmd down --remove-orphans --volumes || true
  if [[ "$PURGE_ENV" == true ]]; then
    rm -f "$ROOT_DIR/.env" "$ROOT_DIR/frontend/.env"
    echo "[run] removed generated env files (.env, frontend/.env)"
  fi
  echo "[ok] reset complete"
}

if ! docker_ready; then
  fail "Docker + Compose are required. Install/start Docker and retry."
fi

ensure_env_files
set_env_value "$ROOT_DIR/.env" OPENPULSE_RUNTIME_MODE docker
require_http_client
validate_compose_config

echo "[run] command=$COMMAND"

case "$COMMAND" in
  start)
    compose_cmd up -d --build
    health_docker
    print_endpoints
    ;;
  stop)
    compose_cmd down --remove-orphans
    echo "[ok] stack stopped"
    ;;
  restart)
    compose_cmd down --remove-orphans
    compose_cmd up -d --build
    health_docker
    print_endpoints
    ;;
  status)
    compose_cmd ps
    ;;
  health)
    health_docker
    print_endpoints
    ;;
  logs)
    compose_cmd logs --tail=200
    ;;
  reset)
    reset_stack
    ;;
esac
