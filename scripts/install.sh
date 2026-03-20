#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage: ./scripts/install.sh

Docker-only installer.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -ne 0 ]]; then
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

bootstrap_env() {
  [[ -f "$ROOT_DIR/.env" ]] || cp "$ROOT_DIR/.env.example" "$ROOT_DIR/.env"
  [[ -f "$ROOT_DIR/frontend/.env" ]] || cp "$ROOT_DIR/frontend/.env.example" "$ROOT_DIR/frontend/.env"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_RUNTIME_MODE docker
}

docker_ready() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1
}

echo "[install] Open Pulse Checker install"
bootstrap_env

if ! docker_ready; then
  fail "Docker + Compose are required for install. Install/start Docker and retry."
fi

docker compose -f "$ROOT_DIR/docker-compose.full.yml" --env-file "$ROOT_DIR/.env" config >/dev/null || fail "docker compose config failed. Fix .env values and retry."
docker compose -f "$ROOT_DIR/docker-compose.full.yml" --env-file "$ROOT_DIR/.env" pull postgres || true

echo "[install] Docker install complete."
echo "[next] Start stack: ./scripts/run.sh start"
