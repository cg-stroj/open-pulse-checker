#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.openpulse/run"
mkdir -p "$RUN_DIR"

COMMAND="${1:-start}"
MODE_ARG="${2:-auto}"

case "$MODE_ARG" in
  auto|docker|local) ;;
  *) echo "Usage: ./scripts/run.sh {start|stop|restart|status|health|logs|doctor} [auto|docker|local]"; exit 1 ;;
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

normalize_env() {
  ensure_env_file
  # migrate legacy keys
  if grep -q '^SERVER_PORT=' "$ROOT_DIR/.env" && ! grep -q '^OPENPULSE_PORT=' "$ROOT_DIR/.env"; then
    set_env_value "$ROOT_DIR/.env" "OPENPULSE_PORT" "$(grep '^SERVER_PORT=' "$ROOT_DIR/.env" | tail -n1 | cut -d= -f2-)"
  fi
  # dedupe keys (keep last)
  awk -F= '!seen[$1]++{keys[++n]=$1} {val[$1]=$0} END{for(i=1;i<=n;i++) print val[keys[i]]}' "$ROOT_DIR/.env" > "$ROOT_DIR/.env.tmp" && mv "$ROOT_DIR/.env.tmp" "$ROOT_DIR/.env"

  local env_override_frontend_port="${OPENPULSE_FRONTEND_PORT-}"
  set -a; source "$ROOT_DIR/.env"; set +a
  [[ -n "${env_override_frontend_port}" ]] && OPENPULSE_FRONTEND_PORT="$env_override_frontend_port"

  OPENPULSE_PORT="${OPENPULSE_PORT:-8080}"
  OPENPULSE_FRONTEND_PORT="${OPENPULSE_FRONTEND_PORT:-5173}"
  OPENPULSE_DB_PORT="${OPENPULSE_DB_PORT:-5432}"
  OPENPULSE_RUNTIME_MODE="${OPENPULSE_RUNTIME_MODE:-auto}"
}

docker_ready() { command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; }
compose_cmd() { docker compose -f "$ROOT_DIR/docker-compose.full.yml" --env-file "$ROOT_DIR/.env" "$@"; }

pick_mode() {
  if [[ "$MODE_ARG" == "docker" || "$MODE_ARG" == "local" ]]; then echo "$MODE_ARG"; return; fi
  if [[ "$OPENPULSE_RUNTIME_MODE" == "docker" || "$OPENPULSE_RUNTIME_MODE" == "local" ]]; then echo "$OPENPULSE_RUNTIME_MODE"; return; fi
  if docker_ready; then echo docker; else echo local; fi
}

wait_http() {
  local name="$1" url="$2" timeout="${3:-120}" elapsed=0
  until curl -fsS "$url" >/dev/null 2>&1; do
    sleep 2; elapsed=$((elapsed+2)); [[ $elapsed -lt $timeout ]] || { echo "[fail] $name healthcheck timed out: $url"; return 1; }
  done
  echo "[ok] $name reachable: $url"
}

assert_port_free() {
  local port="$1" label="$2"
  if lsof -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "[fail] Configured $label port :$port is already in use."
    echo "[hint] Stop the process using :$port or set OPENPULSE_FRONTEND_PORT to a free port in your environment before starting."
    return 1
  fi
}

health_docker() {
  compose_cmd exec -T postgres pg_isready -U "${OPENPULSE_DB_USERNAME:-openpulse}" -d "${OPENPULSE_DB_NAME:-openpulse}" >/dev/null
  wait_http backend "http://localhost:${OPENPULSE_PORT}/api/v1/health" 120
  wait_http frontend "http://localhost:${OPENPULSE_FRONTEND_PORT}" 120
}

start_local() {
  pg_isready -h localhost -p "$OPENPULSE_DB_PORT" >/dev/null 2>&1 || sudo systemctl start postgresql || true
  pg_isready -h localhost -p "$OPENPULSE_DB_PORT" >/dev/null 2>&1 || { echo "[fail] PostgreSQL not reachable on :$OPENPULSE_DB_PORT"; exit 1; }

  [[ -d "$ROOT_DIR/frontend/node_modules" ]] || (cd "$ROOT_DIR/frontend" && npm ci)

  if [[ ! -f "$RUN_DIR/backend.pid" ]] || ! kill -0 "$(cat "$RUN_DIR/backend.pid")" 2>/dev/null; then
    (cd "$ROOT_DIR" && nohup mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=${OPENPULSE_PORT}" > "$RUN_DIR/backend.log" 2>&1 & echo $! > "$RUN_DIR/backend.pid")
  fi
  if [[ ! -f "$RUN_DIR/frontend.pid" ]] || ! kill -0 "$(cat "$RUN_DIR/frontend.pid")" 2>/dev/null; then
    assert_port_free "$OPENPULSE_FRONTEND_PORT" "frontend" || exit 1
    (cd "$ROOT_DIR/frontend" && nohup node ./node_modules/vite/bin/vite.js --host 0.0.0.0 --strictPort --port "$OPENPULSE_FRONTEND_PORT" > "$RUN_DIR/frontend.log" 2>&1 & echo $! > "$RUN_DIR/frontend.pid")
  fi

  wait_http backend "http://localhost:${OPENPULSE_PORT}/api/v1/health" 120
  wait_http frontend "http://localhost:${OPENPULSE_FRONTEND_PORT}" 120
}

stop_local() {
  for svc in backend frontend; do
    [[ -f "$RUN_DIR/$svc.pid" ]] || continue
    kill "$(cat "$RUN_DIR/$svc.pid")" 2>/dev/null || true
    rm -f "$RUN_DIR/$svc.pid"
  done

  # Frontend can outlive npm wrappers in some environments; ensure configured port is released.
  if lsof -iTCP:"$OPENPULSE_FRONTEND_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    lsof -tiTCP:"$OPENPULSE_FRONTEND_PORT" -sTCP:LISTEN | xargs -r kill 2>/dev/null || true
    sleep 1
    lsof -tiTCP:"$OPENPULSE_FRONTEND_PORT" -sTCP:LISTEN | xargs -r kill -9 2>/dev/null || true
  fi
}

doctor() {
  normalize_env
  stop_local
  pkill -f "spring-boot:run" 2>/dev/null || true
  pkill -f "vite" 2>/dev/null || true
  echo "[ok] doctor finished"
  echo "[info] API: http://localhost:${OPENPULSE_PORT}/api/v1"
  echo "[info] FE : http://localhost:${OPENPULSE_FRONTEND_PORT}"
}

normalize_env
ACTIVE_MODE="$(pick_mode)"
echo "[run] command=$COMMAND mode=$ACTIVE_MODE"

case "$COMMAND" in
  start) [[ "$ACTIVE_MODE" == docker ]] && { docker_ready || { echo "[fail] Docker not ready"; exit 1; }; compose_cmd up -d --build; health_docker; } || start_local ;;
  stop) [[ "$ACTIVE_MODE" == docker ]] && docker_ready && compose_cmd down || stop_local ;;
  restart) "$0" stop "$ACTIVE_MODE"; "$0" start "$ACTIVE_MODE" ;;
  status) [[ "$ACTIVE_MODE" == docker ]] && docker_ready && compose_cmd ps || { for s in backend frontend; do [[ -f "$RUN_DIR/$s.pid" ]] && kill -0 "$(cat "$RUN_DIR/$s.pid")" 2>/dev/null && echo "[status] $s: running" || echo "[status] $s: stopped"; done; } ;;
  health) [[ "$ACTIVE_MODE" == docker ]] && health_docker || { wait_http backend "http://localhost:${OPENPULSE_PORT}/api/v1/health" 60; wait_http frontend "http://localhost:${OPENPULSE_FRONTEND_PORT}" 60; } ;;
  logs) [[ "$ACTIVE_MODE" == docker ]] && docker_ready && compose_cmd logs --tail=200 || tail -n 120 "$RUN_DIR/backend.log" "$RUN_DIR/frontend.log" 2>/dev/null || true ;;
  doctor) doctor ;;
  *) echo "Usage: ./scripts/run.sh {start|stop|restart|status|health|logs|doctor} [auto|docker|local]"; exit 1 ;;
esac
