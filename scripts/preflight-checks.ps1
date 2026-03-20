Write-Host "[preflight] Open Pulse Checker Docker checks"
$failed = $false
$warned = $false
$root = Resolve-Path "$PSScriptRoot/.."

if ($args.Count -gt 0 -and $args[0] -in @('-h','--help')) {
    Write-Host "Usage: ./scripts/preflight-checks.ps1"
    exit 0
}

if ($args.Count -gt 0) {
    throw "[fail] Usage: ./scripts/preflight-checks.ps1"
}

function Get-EnvValue {
    param(
        [string]$Key,
        [string]$Default
    )

    $file = "$root/.env"
    if (-not (Test-Path $file)) {
        $file = "$root/.env.example"
    }

    $line = Get-Content $file | Where-Object { $_ -match "^$([regex]::Escape($Key))=" } | Select-Object -Last 1
    if ($line) {
        return ($line -split '=', 2)[1]
    }
    return $Default
}

function Warn($message) {
    Write-Host "[warn] $message"
    $script:warned = $true
}

function Test-Port($port) {
    try {
        $inUse = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop
        if ($inUse) {
            Warn "Port $port is in use. Update .env (OPENPULSE_PORT/OPENPULSE_FRONTEND_PORT/OPENPULSE_DB_PORT) before start."
        }
    } catch {
        Write-Host "[ok] Port $port appears available"
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[fail] Docker not found. Install Docker Desktop."
    $failed = $true
} else {
    Write-Host "[ok] docker found"
    try {
        docker info | Out-Null
        Write-Host "[ok] Docker daemon reachable"
    } catch {
        Write-Host "[fail] Docker installed but daemon not reachable. Start Docker and retry."
        $failed = $true
    }

    try {
        docker compose version | Out-Null
        Write-Host "[ok] docker compose plugin available"
    } catch {
        Write-Host "[fail] docker compose plugin not available. Install Docker Compose v2."
        $failed = $true
    }
}

if (Get-Command curl -ErrorAction SilentlyContinue) {
    Write-Host "[ok] curl found (recommended for health/login checks)"
} else {
    Warn "curl not found. Install curl for easier health/login checks."
}

$backendPort = Get-EnvValue -Key 'OPENPULSE_PORT' -Default '8888'
$frontendPort = Get-EnvValue -Key 'OPENPULSE_FRONTEND_PORT' -Default '5173'
$dbPort = Get-EnvValue -Key 'OPENPULSE_DB_PORT' -Default '5432'
Write-Host "[ok] Planned ports -> backend:$backendPort frontend:$frontendPort postgres:$dbPort"

Test-Port $backendPort
Test-Port $frontendPort
Test-Port $dbPort

if (-not $failed) {
    try {
        docker compose -f "$root/docker-compose.full.yml" --env-file "$root/.env.example" config | Out-Null
        Write-Host "[ok] docker-compose.full.yml resolves with .env.example"
    } catch {
        Write-Host "[fail] docker-compose.full.yml validation failed against .env.example"
        $failed = $true
    }
}

if ($failed) {
    throw "[preflight] Failed. Resolve [fail] items and retry."
}

if ($warned) {
    Write-Host "[preflight] Completed with warnings."
} else {
    Write-Host "[preflight] Completed successfully."
}
