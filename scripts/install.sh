#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Pulseguard installer skeleton (Linux/macOS)"
echo "This is Phase 0 preflight only."

bash "$SCRIPT_DIR/preflight-checks.sh"

echo "Next steps:"
echo "  1) docker compose up --build -d"
echo "  2) curl http://localhost:8080/api/v1/health"
echo "Installer framework complete; full install logic arrives in Phase 1+."
