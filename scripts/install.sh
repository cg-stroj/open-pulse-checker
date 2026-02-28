#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="auto"
INTERACTIVE=false

usage() {
  echo "Usage: ./scripts/install.sh [auto|docker|local] [--wizard|-w]"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    auto|docker|local)
      MODE="$1"
      ;;
    --wizard|-w)
      INTERACTIVE=true
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
  shift
done

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
  local db_pass_sql="${db_pass//\'/\'\'}"

  sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='${db_user}'" | grep -q 1 || sudo -u postgres psql -c "CREATE USER ${db_user} WITH PASSWORD '${db_pass_sql}';"
  # Keep DB user password in sync with .env on every install run (prevents auth mismatch after password regeneration)
  sudo -u postgres psql -c "ALTER USER ${db_user} WITH PASSWORD '${db_pass_sql}';"

  sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='${db_name}'" | grep -q 1 || sudo -u postgres psql -c "CREATE DATABASE ${db_name} OWNER ${db_user};"
  sudo -u postgres psql -c "ALTER DATABASE ${db_name} OWNER TO ${db_user};" || true

  set_env_value "$ROOT_DIR/.env" OPENPULSE_DB_URL "jdbc:postgresql://localhost:5432/${db_name}"
}

is_windows() {
  local uname_s
  uname_s="$(uname -s 2>/dev/null || true)"
  [[ "$uname_s" =~ MINGW|MSYS|CYGWIN|Windows_NT ]]
}

read_current_env() {
  set -a; source "$ROOT_DIR/.env"; set +a
}

prompt_with_default() {
  local prompt="$1" default="$2" value
  read -r -p "$prompt [$default]: " value
  if [[ -z "$value" ]]; then
    echo "$default"
  else
    echo "$value"
  fi
}

validate_port() {
  local port="$1"
  [[ "$port" =~ ^[0-9]+$ ]] || return 1
  (( port >= 1 && port <= 65535 ))
}

prompt_port() {
  local prompt="$1" default="$2" value
  while true; do
    value="$(prompt_with_default "$prompt" "$default")"
    if validate_port "$value"; then
      echo "$value"
      return
    fi
    echo "[fail] Port must be a number between 1 and 65535."
  done
}

prompt_non_empty() {
  local prompt="$1" default="$2" value
  while true; do
    value="$(prompt_with_default "$prompt" "$default")"
    if [[ -n "$value" ]]; then
      echo "$value"
      return
    fi
    echo "[fail] Value cannot be empty."
  done
}

prompt_yes_no() {
  local prompt="$1" default="$2" answer
  while true; do
    read -r -p "$prompt [$default]: " answer
    answer="${answer:-$default}"
    case "${answer,,}" in
      y|yes) echo "yes"; return ;;
      n|no) echo "no"; return ;;
      *) echo "[fail] Enter y/yes or n/no." ;;
    esac
  done
}

run_wizard() {
  read_current_env
  local default_mode="${OPENPULSE_RUNTIME_MODE:-$MODE}"
  local runtime_mode backend_port frontend_port db_name db_user db_port db_password
  local bootstrap_enabled bootstrap_username bootstrap_password

  echo "[wizard] Interactive setup"
  while true; do
    runtime_mode="$(prompt_with_default "Runtime mode (auto/docker/local)" "$default_mode")"
    case "$runtime_mode" in
      auto|docker|local) break ;;
      *) echo "[fail] Runtime mode must be one of: auto, docker, local." ;;
    esac
  done

  if is_windows && [[ "$runtime_mode" == "local" ]]; then
    echo "[fail] Windows local mode is not supported yet. Choose docker/auto, or run local mode from Linux/macOS (or WSL)."
    exit 1
  fi

  backend_port="$(prompt_port "Backend port" "${OPENPULSE_PORT:-8888}")"
  frontend_port="$(prompt_port "Frontend port" "${OPENPULSE_FRONTEND_PORT:-5173}")"
  db_port="$(prompt_port "Database port" "${OPENPULSE_DB_PORT:-5432}")"
  db_name="$(prompt_non_empty "Database name" "${OPENPULSE_DB_NAME:-openpulse}")"
  db_user="$(prompt_non_empty "Database user" "${OPENPULSE_DB_USERNAME:-openpulse}")"

  if [[ "$(prompt_yes_no "Generate secure DB password?" "y")" == "yes" ]]; then
    db_password="$(generate_password)"
    echo "[wizard] Generated DB password."
  else
    db_password="$(prompt_non_empty "Database password" "${OPENPULSE_DB_PASSWORD:-}")"
  fi

  if [[ "$(prompt_yes_no "Enable bootstrap admin?" "n")" == "yes" ]]; then
    bootstrap_enabled="true"
    bootstrap_username="$(prompt_non_empty "Bootstrap admin username" "${OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_USERNAME:-admin}")"
    if [[ "$(prompt_yes_no "Generate bootstrap admin password?" "y")" == "yes" ]]; then
      bootstrap_password="$(generate_password)"
      echo "[wizard] Generated bootstrap admin password."
    else
      bootstrap_password="$(prompt_non_empty "Bootstrap admin password" "")"
    fi
  else
    bootstrap_enabled="false"
    bootstrap_username="${OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_USERNAME:-admin}"
    bootstrap_password=""
  fi

  set_env_value "$ROOT_DIR/.env" OPENPULSE_RUNTIME_MODE "$runtime_mode"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_PORT "$backend_port"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_FRONTEND_PORT "$frontend_port"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_DB_PORT "$db_port"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_DB_NAME "$db_name"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_DB_USERNAME "$db_user"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_DB_PASSWORD "$db_password"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_DB_URL "jdbc:postgresql://localhost:${db_port}/${db_name}"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_ENABLED "$bootstrap_enabled"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_USERNAME "$bootstrap_username"
  set_env_value "$ROOT_DIR/.env" OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_PASSWORD "$bootstrap_password"

  set_env_value "$ROOT_DIR/frontend/.env" VITE_API_BASE_URL "${VITE_API_BASE_URL:-http://localhost:${backend_port}/api/v1}"

  MODE="$runtime_mode"
}

echo "[install] Open Pulse Checker install"
bootstrap_env

if [[ "$INTERACTIVE" == true ]]; then
  run_wizard
fi

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