Write-Host "Pulseguard installer skeleton (Windows)"
Write-Host "This is Phase 0 preflight only."

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

& "$scriptDir/preflight-checks.ps1"

Write-Host "Next steps:"
Write-Host "  1) docker compose up --build -d"
Write-Host "  2) Invoke-RestMethod http://localhost:8080/api/v1/health"
Write-Host "Installer framework complete; full install logic arrives in Phase 1+."
