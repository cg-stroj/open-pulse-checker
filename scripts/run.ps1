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

function Compose([string[]]$Args) {
    docker compose -f "$root/docker-compose.full.yml" --env-file "$root/.env" @Args
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

if (-not (Test-Path "$root/.env")) {
    Copy-Item "$root/.env.example" "$root/.env"
    Write-Host "[run] Created .env from template."
}
Set-EnvValue -File "$root/.env" -Key "OPENPULSE_RUNTIME_MODE" -Value "docker"

$envMap = @{}
Get-Content "$root/.env" | ForEach-Object {
    if ($_ -match '^[A-Za-z_][A-Za-z0-9_]*=') {
        $parts = $_ -split '=', 2
        $envMap[$parts[0]] = $parts[1]
    }
}
$backendPort = if ($envMap.ContainsKey('OPENPULSE_PORT')) { $envMap['OPENPULSE_PORT'] } else { '8080' }
$frontendPort = if ($envMap.ContainsKey('OPENPULSE_FRONTEND_PORT')) { $envMap['OPENPULSE_FRONTEND_PORT'] } else { '5173' }
$dbUser = if ($envMap.ContainsKey('OPENPULSE_DB_USERNAME')) { $envMap['OPENPULSE_DB_USERNAME'] } else { 'openpulse' }
$dbName = if ($envMap.ContainsKey('OPENPULSE_DB_NAME')) { $envMap['OPENPULSE_DB_NAME'] } else { 'openpulse' }

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

function Health-Docker {
    Compose @("exec", "-T", "postgres", "pg_isready", "-U", $dbUser, "-d", $dbName) | Out-Null
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
}

Write-Host "[run] command=$Command"

switch ($Command) {
    "start" { Compose @("up", "-d", "--build"); Health-Docker }
    "stop" { Compose @("down", "--remove-orphans") }
    "restart" { Compose @("down", "--remove-orphans"); Compose @("up", "-d", "--build"); Health-Docker }
    "status" { Compose @("ps") }
    "health" { Health-Docker }
    "logs" { Compose @("logs", "--tail=200") }
    "reset" { Reset-Stack }
}
