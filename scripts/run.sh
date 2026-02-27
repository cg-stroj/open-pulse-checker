#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.openpulse/run"
mkdir -p "$RUN_DIR"

COMMAND="${1:-start}"
MODE="${2:-auto}"

compose_cmd() {
  docker compose -f "$ROOT_DIR/docker-compose.full.yml" --env-file "$ROOT_DIR/.env" "$@"
}

docker_available() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1
}

pick_mode() {
  if [[ "$MODE" == "docker" || "$MODE" == "local" ]]; then
    echo "$MODE"
    return
  fi
  if docker_available; then
    echo "docker"
  else
    echo "local"
  fi
}

wait_http() {
  local name="$1" url="$2" timeout="${3:-90}"
  local elapsed=0
  until curl -fsS "$url" >/dev/null 2>&1; do
    sleep 2
    elapsed=$((elapsed+2))
    if (( elapsed >= timeout )); then
      echo "[fail] $name healthcheck timed out: $url"
      return 1
    fi
  done
  echo "[ok] $name reachable: $url"
}

health_docker() {
  echo "[health] verifying docker stack..."
  compose_cmd exec -T postgres pg_isready -U "$(grep '^OPENPULSE_DB_USERNAME=' "$ROOT_DIR/.env" | cut -d= -f2)" -d "$(grep '^OPENPULSE_DB_NAME=' "$ROOT_DIR/.env" | cut -d= -f2)" >/dev/null
  echo "[ok] postgres readiness passed"
  wait_http "backend" "http://localhost:8080/api/v1/health" 120
  wait_http "frontend" "http://localhost:5173" 120
}

start_local() {
  echo "[run] Starting local fallback stack (backend + frontend; DB via in-memory H2)."
  if [[ ! -d "$ROOT_DIR/frontend/node_modules" ]]; then
    echo "[run] frontend dependencies missing; running npm ci"
    (cd "$ROOT_DIR/frontend" && npm ci)
  fi

  if [[ ! -f "$RUN_DIR/backend.pid" ]] || ! kill -0 "$(cat "$RUN_DIR/backend.pid")" 2>/dev/null; then
    (cd "$ROOT_DIR" && nohup mvn spring-boot:run > "$RUN_DIR/backend.log" 2>&1 & echo $! > "$RUN_DIR/backend.pid")
    echo "[run] backend started"
  else
    echo "[run] backend already running"
  fi

  if [[ ! -f "$RUN_DIR/frontend.pid" ]] || ! kill -0 "$(cat "$RUN_DIR/frontend.pid")" 2>/dev/null; then
    (cd "$ROOT_DIR/frontend" && nohup npm run dev -- --host 0.0.0.0 --port 5173 > "$RUN_DIR/frontend.log" 2>&1 & echo $! > "$RUN_DIR/frontend.pid")
    echo "[run] frontend started"
  else
    echo "[run] frontend already running"
  fi

  wait_http "backend" "http://localhost:8080/api/v1/health" 120
  wait_http "frontend" "http://localhost:5173" 120
  echo "[health] Local mode uses embedded H2 DB; no external DB readiness probe required."
}

stop_local() {
  for svc in backend frontend; do
    if [[ -f "$RUN_DIR/$svc.pid" ]]; then
      pid="$(cat "$RUN_DIR/$svc.pid")"
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" || true
        echo "[run] stopped $svc ($pid)"
      fi
      rm -f "$RUN_DIR/$svc.pid"
    fi
  done
}

status_local() {
  for svc in backend frontend; do
    if [[ -f "$RUN_DIR/$svc.pid" ]] && kill -0 "$(cat "$RUN_DIR/$svc.pid")" 2>/dev/null; then
      echo "[status] $svc: running (pid $(cat "$RUN_DIR/$svc.pid"))"
    else
      echo "[status] $svc: stopped"
    fi
  done
}

logs_local() {
  tail -n 100 "$RUN_DIR/backend.log" "$RUN_DIR/frontend.log" 2>/dev/null || echo "[logs] No local logs yet."
}

if [[ ! -f "$ROOT_DIR/.env" && "$COMMAND" != "stop" && "$COMMAND" != "status" && "$COMMAND" != "logs" ]]; then
  echo "[run] Missing .env; creating from template."
  cp "$ROOT_DIR/.env.example" "$ROOT_DIR/.env"
fi

ACTIVE_MODE="$(pick_mode)"
echo "[run] command=$COMMAND mode=$ACTIVE_MODE"

case "$COMMAND" in
  start)
    if [[ "$ACTIVE_MODE" == "docker" ]]; then
      compose_cmd up -d --build
      health_docker
    else
      start_local
    fi
    ;;
  stop)
    if [[ "$ACTIVE_MODE" == "docker" ]]; then
      compose_cmd down
    else
      stop_local
    fi
    ;;
  restart)
    "$0" stop "$ACTIVE_MODE"
    "$0" start "$ACTIVE_MODE"
    ;;
  status)
    if [[ "$ACTIVE_MODE" == "docker" ]]; then
      compose_cmd ps
    else
      status_local
    fi
    ;;
  health)
    if [[ "$ACTIVE_MODE" == "docker" ]]; then
      health_docker
    else
      wait_http "backend" "http://localhost:8080/api/v1/health" 60
      wait_http "frontend" "http://localhost:5173" 60
      echo "[health] Local mode uses embedded H2 DB."
    fi
    ;;
  logs)
    if [[ "$ACTIVE_MODE" == "docker" ]]; then
      compose_cmd logs --tail=200
    else
      logs_local
    fi
    ;;
  *)
    echo "Usage: ./scripts/run.sh {start|stop|restart|status|health|logs} [auto|docker|local]"
    exit 1
    ;;
esac
