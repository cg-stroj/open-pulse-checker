#!/usr/bin/env bash
set -euo pipefail

echo "[preflight] Running Pulseguard preflight checks (Linux/macOS)..."

check_cmd() {
  local cmd="$1"
  if command -v "$cmd" >/dev/null 2>&1; then
    echo "[ok] $cmd found"
  else
    echo "[fail] $cmd not found"
    return 1
  fi
}

check_port_free() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1 && lsof -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "[fail] Port $port is in use"
    return 1
  fi
  echo "[ok] Port $port appears available"
}

check_cmd java
check_cmd docker
check_cmd docker-compose || check_cmd docker

JAVA_VERSION=$(java -version 2>&1 | head -n 1)
echo "[info] Java: $JAVA_VERSION"

docker info >/dev/null 2>&1 && echo "[ok] Docker daemon reachable" || { echo "[fail] Docker daemon not reachable"; exit 1; }

check_port_free 8080

echo "[preflight] Completed."
