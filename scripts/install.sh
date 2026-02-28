#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-auto}"

generate_password() {
  if command -v openssl >/dev/null 2>&1; then openssl rand -hex 16; else date +%s | sha256sum | cut -c1-32; fi
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

  if ! grep -q '^OPENPULSE_DB_PASSWORD=' "$ROOT_DIR/.env"; then
    set_env_value "$ROOT_DIR/.env" OPENPULSE_DB_PASSWORD "$(generate_password)"
  fi

  if grep -q '^SERVER_PORT=' "$ROOT_DIR/.env" && ! grep -q '^OPENPULSE_PORT=' "$ROOT_DIR/.env"; then
    set_env_value "$ROOT_DIR/.env" OPENPULSE_PORT "$(grep '^SERVER_PORT=' "$ROOT_DIR/.env" | tail -n1 | cut -d= -f2-)"
  fi

  awk -F= '!seen[$1]++{k[++n]=$1} {v[$1]=$0} END{for(i=1;i<=n;i++) print v[k[i]]}' "$ROOT_DIR/.env" > "$ROOT_DIR/.env.tmp" && mv "$ROOT_DIR/.env.tmp" "$ROOT_DIR/.env"
}

docker_ready() { command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; }

install_local_deps_ubuntu() {
  sudo apt-get update
  sudo apt-get install -y openjdk-21-jdk maven nodejs postgresql postgresql-contrib postgresql-client
  sudo systemctl enable --now postgresql
}

provision_local_postgres() {
  set -a; source "$ROOT_DIR/.env"; set +a
  local db_name="${OPENPULSE_DB_NAME:-openpulse}" db_user="${OPENPULSE_DB_USERNAME:-openpulse}" db_pass="${OPENPULSE_DB_PASSWORD:-openpulse}"
  sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='${db_user}'" | grep -q 1 || sudo -u postgres psql -c "CREATE USER ${db_user} WITH PASSWORD '${db_pass}';"
  sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='${db_name}'" | grep -q 1 || sudo -u postgres psql -c "CREATE DATABASE ${db_name} OWNER ${db_user};"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_DB_URL "jdbc:postgresql://localhost:5432/${db_name}"
}

echo "[install] Open Pulse Checker install"
bootstrap_env

RUNTIME_MODE="local"
if [[ "$MODE" == "docker" ]]; then
  RUNTIME_MODE="docker"
elif docker_ready; then
  RUNTIME_MODE="docker"
fi

if [[ "$RUNTIME_MODE" == "docker" ]]; then
  set_env_value "$ROOT_DIR/.env" OPENPULSE_RUNTIME_MODE docker
  docker compose -f "$ROOT_DIR/docker-compose.full.yml" --env-file "$ROOT_DIR/.env" pull postgres || true
else
  set_env_value "$ROOT_DIR/.env" OPENPULSE_RUNTIME_MODE local
  if command -v apt-get >/dev/null 2>&1; then install_local_deps_ubuntu; fi
  provision_local_postgres
  (cd "$ROOT_DIR" && mvn -q -DskipTests package)
  (cd "$ROOT_DIR/frontend" && npm ci)
fi

echo "[install] Done. Next: ./scripts/run.sh start"
