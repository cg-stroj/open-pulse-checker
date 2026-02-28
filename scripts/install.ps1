param(
    [ValidateSet("auto", "docker")]
    [string]$Mode = "auto"
)

$root = Resolve-Path "$PSScriptRoot/.."

Write-Host "[install] Open Pulse Checker install (mode: $Mode)"
& "$PSScriptRoot/preflight-checks.ps1" -Mode $Mode

if (-not (Test-Path "$root/.env")) {
    Copy-Item "$root/.env.example" "$root/.env"
    $pw = -join ((48..57 + 65..90 + 97..122) | Get-Random -Count 24 | ForEach-Object {[char]$_})
    (Get-Content "$root/.env") -replace '^OPENPULSE_DB_PASSWORD=.*', "OPENPULSE_DB_PASSWORD=$pw" | Set-Content "$root/.env"
    Write-Host "[install] Created .env from template with generated DB password."
} else {
    Write-Host "[install] .env already exists; leaving unchanged."
}

if (-not (Test-Path "$root/frontend/.env")) {
    Copy-Item "$root/frontend/.env.example" "$root/frontend/.env"
    Write-Host "[install] Created frontend/.env from template."
}

docker compose -f "$root/docker-compose.full.yml" --env-file "$root/.env" pull postgres | Out-Null
Write-Host "[install] Docker install complete."
Write-Host "[install] Done. Next: ./scripts/run.sh start (or ./scripts/run.ps1 start)"
