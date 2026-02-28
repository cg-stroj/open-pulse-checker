param(
    [ValidateSet("start", "stop", "restart", "status", "health", "logs")]
    [string]$Command = "start",
    [string]$Mode = "docker"
)

$root = Resolve-Path "$PSScriptRoot/.."

if ($Mode -in @("auto", "local")) {
    throw "[fail] Runtime mode '$Mode' is not supported. Open Pulse Checker is Docker-only. Use: ./scripts/run.ps1 -Command $Command -Mode docker"
}
if ($Mode -ne "docker") {
    throw "[fail] Unsupported mode '$Mode'. Usage: ./scripts/run.ps1 -Command <start|stop|restart|status|health|logs> -Mode docker"
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

function Compose([string[]]$Args) {
    docker compose -f "$root/docker-compose.full.yml" --env-file "$root/.env" @Args
}

if (-not (Test-Path "$root/.env")) {
    Copy-Item "$root/.env.example" "$root/.env"
    Write-Host "[run] Created .env from template."
}
Set-EnvValue -File "$root/.env" -Key "OPENPULSE_RUNTIME_MODE" -Value "docker"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "[fail] Docker + Compose are required for docker runtime."
}

try {
    docker info | Out-Null
    docker compose version | Out-Null
} catch {
    throw "[fail] Docker + Compose are required for docker runtime. Start Docker and retry."
}

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

Write-Host "[run] command=$Command mode=docker"

switch ($Command) {
    "start" { Compose @("up", "-d", "--build"); Health-Docker }
    "stop" { Compose @("down") }
    "restart" { Compose @("down"); Compose @("up", "-d", "--build"); Health-Docker }
    "status" { Compose @("ps") }
    "health" { Health-Docker }
    "logs" { Compose @("logs", "--tail=200") }
}
