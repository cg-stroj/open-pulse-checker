param(
    [ValidateSet("start", "stop", "restart", "status", "health", "logs")]
    [string]$Command = "start",
    [ValidateSet("auto", "docker", "local")]
    [string]$Mode = "auto"
)

$root = Resolve-Path "$PSScriptRoot/.."

if ($Mode -eq "local") {
    throw "[fail] Windows local mode is not supported yet. Use '-Mode docker' on Windows, or run local mode from Linux/macOS (or WSL)."
}

function Compose([string[]]$Args) {
    docker compose -f "$root/docker-compose.full.yml" --env-file "$root/.env" @Args
}

if (-not (Test-Path "$root/.env") -and $Command -in @("start", "restart", "health")) {
    Copy-Item "$root/.env.example" "$root/.env"
    Write-Host "[run] Created .env from template."
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "[fail] Docker + Compose are required on Windows (local mode unsupported)."
}

try {
    docker info | Out-Null
    docker compose version | Out-Null
} catch {
    throw "[fail] Docker + Compose are required on Windows (local mode unsupported)."
}

$envMap = @{}
if (Test-Path "$root/.env") {
    Get-Content "$root/.env" | ForEach-Object {
        if ($_ -match '^[A-Za-z_][A-Za-z0-9_]*=') {
            $parts = $_ -split '=', 2
            $envMap[$parts[0]] = $parts[1]
        }
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
    Write-Host "[health] verifying docker stack..."
    Compose @("exec", "-T", "postgres", "pg_isready", "-U", $dbUser, "-d", $dbName) | Out-Null
    Write-Host "[ok] postgres readiness passed"
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
