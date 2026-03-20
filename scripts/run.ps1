param(
    [ValidateSet("start", "stop", "restart", "status", "health", "logs", "reset")]
    [string]$Command = "start",
    [switch]$PurgeEnv
)

$root = Resolve-Path "$PSScriptRoot/.."

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

function Get-EnvMap {
    $envMap = @{}
    Get-Content "$root/.env" | ForEach-Object {
        if ($_ -match '^[A-Za-z_][A-Za-z0-9_]*=') {
            $parts = $_ -split '=', 2
            $envMap[$parts[0]] = $parts[1]
        }
    }
    return $envMap
}

function Compose([string[]]$Args) {
    docker compose -f "$root/docker-compose.full.yml" --env-file "$root/.env" @Args
}

function Wait-Http($name, $url, $timeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5 | Out-Null
            Write-Host "[ok] $name reachable: $url"
            return
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "[fail] $name healthcheck timed out: $url"
}

function Print-Endpoints {
    param([hashtable]$EnvMap)

    $backendPort = if ($EnvMap.ContainsKey('OPENPULSE_PORT')) { $EnvMap['OPENPULSE_PORT'] } else { '8888' }
    $frontendPort = if ($EnvMap.ContainsKey('OPENPULSE_FRONTEND_PORT')) { $EnvMap['OPENPULSE_FRONTEND_PORT'] } else { '5173' }

    Write-Host "[next] Open Pulse Checker endpoints:"
    Write-Host "  - Frontend UI: http://localhost:$frontendPort"
    Write-Host "  - API via frontend proxy (recommended): http://localhost:$frontendPort/api/v1"
    Write-Host "  - Direct backend API: http://localhost:$backendPort/api/v1"
    Write-Host "  - Backend health: http://localhost:$backendPort/api/v1/health"
    Write-Host "[next] Login path check: curl -i http://localhost:$frontendPort/api/v1/admin/auth/login"
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "[fail] Docker + Compose are required."
}

try {
    docker info | Out-Null
    docker compose version | Out-Null
} catch {
    throw "[fail] Docker + Compose are required. Start Docker and retry."
}

if (-not (Get-Command Invoke-WebRequest -ErrorAction SilentlyContinue)) {
    throw "[fail] PowerShell Invoke-WebRequest is required for health checks."
}

if (-not (Test-Path "$root/.env")) {
    Copy-Item "$root/.env.example" "$root/.env"
    Write-Host "[run] Created .env from template."
}
if (-not (Test-Path "$root/frontend/.env")) {
    Copy-Item "$root/frontend/.env.example" "$root/frontend/.env"
    Write-Host "[run] Created frontend/.env from template."
}

Set-EnvValue -File "$root/.env" -Key "OPENPULSE_RUNTIME_MODE" -Value "docker"

try {
    Compose @("config") | Out-Null
} catch {
    throw "[fail] docker compose configuration is invalid. Check .env values and retry."
}

$envMap = Get-EnvMap
$backendPort = if ($envMap.ContainsKey('OPENPULSE_PORT')) { $envMap['OPENPULSE_PORT'] } else { '8888' }
$frontendPort = if ($envMap.ContainsKey('OPENPULSE_FRONTEND_PORT')) { $envMap['OPENPULSE_FRONTEND_PORT'] } else { '5173' }
$dbUser = if ($envMap.ContainsKey('OPENPULSE_DB_USERNAME')) { $envMap['OPENPULSE_DB_USERNAME'] } else { 'openpulse' }
$dbName = if ($envMap.ContainsKey('OPENPULSE_DB_NAME')) { $envMap['OPENPULSE_DB_NAME'] } else { 'openpulse' }

function Health-Docker {
    Compose @("exec", "-T", "postgres", "pg_isready", "-U", $dbUser, "-d", $dbName) | Out-Null
    Write-Host "[ok] postgres reachable"
    Wait-Http "backend" "http://localhost:$backendPort/api/v1/health"
    Wait-Http "frontend" "http://localhost:$frontendPort"
}

function Reset-Stack {
    Compose @("down", "--remove-orphans", "--volumes") | Out-Null
    if ($PurgeEnv) {
        Remove-Item "$root/.env" -Force -ErrorAction SilentlyContinue
        Remove-Item "$root/frontend/.env" -Force -ErrorAction SilentlyContinue
        Write-Host "[run] removed generated env files (.env, frontend/.env)"
    }
    Write-Host "[ok] reset complete"
}

Write-Host "[run] command=$Command"

switch ($Command) {
    "start" { Compose @("up", "-d", "--build"); Health-Docker; Print-Endpoints -EnvMap $envMap }
    "stop" { Compose @("down", "--remove-orphans"); Write-Host "[ok] stack stopped" }
    "restart" { Compose @("down", "--remove-orphans"); Compose @("up", "-d", "--build"); Health-Docker; Print-Endpoints -EnvMap $envMap }
    "status" { Compose @("ps") }
    "health" { Health-Docker; Print-Endpoints -EnvMap $envMap }
    "logs" { Compose @("logs", "--tail=200") }
    "reset" { Reset-Stack }
}
