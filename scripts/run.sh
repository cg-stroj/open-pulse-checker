#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMMAND="${1:-start}"
MODE_ARG="${2:-docker}"

usage() {
  cat <<'EOF'
Usage: ./scripts/run.sh {start|stop|restart|status|health|logs} [docker]

Docker-only runtime.
Any non-docker mode (auto/local) is not supported.
EOF
}

case "$COMMAND" in
  start|stop|restart|status|health|logs) ;;
  -h|--help) usage; exit 0 ;;
  *) usage; exit 1 ;;
esac

case "$MODE_ARG" in
  docker) ;;
  auto|local)
    echo "[fail] Runtime mode '$MODE_ARG' is not supported. Open Pulse Checker is Docker-only."
    echo "[hint] Use: ./scripts/run.sh $COMMAND docker"
    exit 1
    ;;
  *)
    usage
    exit 1
    ;;
esac

ensure_env_file() {
  [[ -f "$ROOT_DIR/.env" ]] || cp "$ROOT_DIR/.env.example" "$ROOT_DIR/.env"
}

set_env_value() {
  local file="$1" key="$2" value="$3" tmp
  tmp="$(mktemp)"
  awk -F= -v k="$key" -v v="$value" 'BEGIN{u=0} $1==k {print k"="v; u=1; next} {print} END{if(!u) print k"="v}' "$file" > "$tmp"
  mv "$tmp" "$file"
}

docker_ready() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1
}

compose_cmd() {
  docker compose -f "$ROOT_DIR/docker-compose.full.yml" --env-file "$ROOT_DIR/.env" "$@"
}

wait_http() {
  local name="$1" url="$2" timeout="${3:-120}" elapsed=0
  until curl -fsS "$url" >/dev/null 2>&1; do
    sleep 2
    elapsed=$((elapsed+2))
    [[ $elapsed -lt $timeout ]] || { echo "[fail] $name healthcheck timed out: $url"; return 1; }
  done
  echo "[ok] $name reachable: $url"
}

health_docker() {
  set -a; source "$ROOT_DIR/.env"; set +a
  OPENPULSE_PORT="${OPENPULSE_PORT:-8080}"
  OPENPULSE_FRONTEND_PORT="${OPENPULSE_FRONTEND_PORT:-5173}"
  OPENPULSE_DB_USERNAME="${OPENPULSE_DB_USERNAME:-openpulse}"
  OPENPULSE_DB_NAME="${OPENPULSE_DB_NAME:-openpulse}"

  compose_cmd exec -T postgres pg_isready -U "$OPENPULSE_DB_USERNAME" -d "$OPENPULSE_DB_NAME" >/dev/null
  wait_http backend "http://localhost:${OPENPULSE_PORT}/api/v1/health" 120
  wait_http frontend "http://localhost:${OPENPULSE_FRONTEND_PORT}" 120
}

ensure_env_file
set_env_value "$ROOT_DIR/.env" OPENPULSE_RUNTIME_MODE docker

echo "[run] command=$COMMAND mode=docker"

if ! docker_ready; then
  echo "[fail] Docker + Compose are required for docker runtime."
  exit 1
fi

case "$COMMAND" in
  start) compose_cmd up -d --build; health_docker ;;
  stop) compose_cmd down ;;
  restart) compose_cmd down; compose_cmd up -d --build; health_docker ;;
  status) compose_cmd ps ;;
  health) health_docker ;;
  logs) compose_cmd logs --tail=200 ;;
esac
