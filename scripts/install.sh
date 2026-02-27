#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-auto}"

generate_password() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 16
  else
    date +%s | sha256sum | cut -c1-32
  fi
}

set_env_value() {
  local file="$1" key="$2" value="$3"
  local tmp
  tmp="$(mktemp)"
  awk -F= -v k="$key" -v v="$value" 'BEGIN{updated=0} $1==k {print k"="v; updated=1; next} {print} END{if(!updated) print k"="v}' "$file" > "$tmp"
  mv "$tmp" "$file"
}

bootstrap_env() {
  if [[ ! -f "$ROOT_DIR/.env" ]]; then
    cp "$ROOT_DIR/.env.example" "$ROOT_DIR/.env"
    local pw
    pw="$(generate_password)"
    set_env_value "$ROOT_DIR/.env" "OPENPULSE_DB_PASSWORD" "$pw"
    echo "[install] Created .env from template (generated local DB password)."
  else
    echo "[install] .env already exists; leaving it unchanged."
  fi

  if [[ ! -f "$ROOT_DIR/frontend/.env" ]]; then
    cp "$ROOT_DIR/frontend/.env.example" "$ROOT_DIR/frontend/.env"
    echo "[install] Created frontend/.env from template."
  else
    echo "[install] frontend/.env already exists; leaving it unchanged."
  fi
}

docker_available() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1
}

echo "[install] Open Pulse Checker install (mode: $MODE)"
bash "$ROOT_DIR/scripts/preflight-checks.sh" "$MODE"
bootstrap_env

DOCKER_OK=0
if docker_available; then
  DOCKER_OK=1
fi

if [[ "$MODE" == "docker" || ( "$MODE" == "auto" && "$DOCKER_OK" -eq 1 ) ]]; then
  echo "[install] Docker mode selected. Pulling base images..."
  docker compose -f "$ROOT_DIR/docker-compose.full.yml" --env-file "$ROOT_DIR/.env" pull postgres || true
  echo "[install] Docker install complete."
else
  echo "[install] Local fallback mode selected. Installing backend/frontend dependencies..."
  (cd "$ROOT_DIR" && mvn -q -DskipTests package)
  (cd "$ROOT_DIR/frontend" && npm ci)
  echo "[install] Local dependency install complete."
fi

echo "[install] Done. Next: ./scripts/run.sh start"
