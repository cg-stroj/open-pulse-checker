param(
    [string]$Mode = "docker"
)

$root = Resolve-Path "$PSScriptRoot/.."

function Show-Usage {
@"
Usage: ./scripts/install.ps1 [-Mode docker]

Docker-only installer.
Any non-docker mode (auto/local) is not supported.
"@ | Write-Host
}

if ($Mode -in @("auto", "local")) {
    throw "[fail] Runtime mode '$Mode' is not supported. Open Pulse Checker is Docker-only. Use: ./scripts/install.ps1 -Mode docker"
}
if ($Mode -ne "docker") {
    Show-Usage
    throw "[fail] Unsupported mode '$Mode'."
}

function Set-EnvValue {
    param(
        [string]$File,
        [string]$Key,
        [string]$Value
    )

    $lines = @()
    if (Test-Path $File) {
        $lines = Get-Content $File
    }

    $updated = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^$([regex]::Escape($Key))=") {
            $lines[$i] = "$Key=$Value"
            $updated = $true
        }
    }

    if (-not $updated) {
        $lines += "$Key=$Value"
    }

    Set-Content -Path $File -Value $lines
}

Write-Host "[install] Open Pulse Checker install (mode: docker)"

if (-not (Test-Path "$root/.env")) {
    Copy-Item "$root/.env.example" "$root/.env"
}
if (-not (Test-Path "$root/frontend/.env")) {
    Copy-Item "$root/frontend/.env.example" "$root/frontend/.env"
}

Set-EnvValue -File "$root/.env" -Key "OPENPULSE_RUNTIME_MODE" -Value "docker"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "[fail] Docker + Compose are required for install."
}

try {
    docker info | Out-Null
    docker compose version | Out-Null
} catch {
    throw "[fail] Docker + Compose are required for install. Start Docker and retry."
}

docker compose -f "$root/docker-compose.full.yml" --env-file "$root/.env" pull postgres | Out-Null

Write-Host "[install] Docker install complete."
Write-Host "[install] Done. Next: ./scripts/run.ps1 -Command start -Mode docker"
