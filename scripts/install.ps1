$root = Resolve-Path "$PSScriptRoot/.."

function Show-Usage {
@"
Usage: ./scripts/install.ps1

Docker-only installer.
"@ | Write-Host
}

if ($args.Count -gt 0 -and $args[0] -in @('-h','--help')) {
    Show-Usage
    exit 0
}

if ($args.Count -gt 0) {
    Show-Usage
    throw "[fail] This command takes no arguments."
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

Write-Host "[install] Open Pulse Checker install"

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

try {
    docker compose -f "$root/docker-compose.full.yml" --env-file "$root/.env" config | Out-Null
} catch {
    throw "[fail] docker compose config failed. Fix .env values and retry."
}

docker compose -f "$root/docker-compose.full.yml" --env-file "$root/.env" pull postgres | Out-Null

Write-Host "[install] Docker install complete."
Write-Host "[next] Start stack: ./scripts/run.ps1 -Command start"
