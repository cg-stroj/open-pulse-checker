#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="docker"

usage() {
  cat <<'EOF'
Usage: ./scripts/install.sh [docker]

Docker-only installer.
Any non-docker mode (auto/local) is not supported.
EOF
}

if [[ $# -gt 1 ]]; then
  usage
  exit 1
fi

if [[ $# -eq 1 ]]; then
  case "$1" in
    docker) MODE="docker" ;;
    auto|local)
      echo "[fail] Runtime mode '$1' is not supported. Open Pulse Checker is Docker-only."
      echo "[hint] Use: ./scripts/install.sh docker"
      exit 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
fi

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

echo "[install] Open Pulse Checker install (mode: $MODE)"
bootstrap_env

if ! docker_ready; then
  echo "[fail] Docker + Compose are required for install."
  echo "[hint] Install/start Docker and retry: ./scripts/install.sh docker"
  exit 1
fi

docker compose -f "$ROOT_DIR/docker-compose.full.yml" --env-file "$ROOT_DIR/.env" pull postgres || true

echo "[install] Docker install complete."
echo "[install] Done. Next: ./scripts/run.sh start docker"
