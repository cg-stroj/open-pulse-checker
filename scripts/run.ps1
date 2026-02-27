param(
    [ValidateSet("start", "stop", "restart", "status", "health", "logs")]
    [string]$Command = "start",
    [ValidateSet("auto", "docker", "local")]
    [string]$Mode = "auto"
)

$root = Resolve-Path "$PSScriptRoot/.."
$runDir = "$root/.openpulse/run"
New-Item -ItemType Directory -Path $runDir -Force | Out-Null

function Test-DockerReady {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { return $false }
    try {
        docker info | Out-Null
        docker compose version | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Compose($args) {
    docker compose -f "$root/docker-compose.full.yml" --env-file "$root/.env" $args
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

function Get-ActiveMode {
    if ($Mode -eq "docker" -or $Mode -eq "local") { return $Mode }
    if (Test-DockerReady) { return "docker" }
    return "local"
}

$activeMode = Get-ActiveMode
Write-Host "[run] command=$Command mode=$activeMode"

if (-not (Test-Path "$root/.env") -and $Command -in @("start", "restart", "health")) {
    Copy-Item "$root/.env.example" "$root/.env"
    Write-Host "[run] Created .env from template."
}

if ($activeMode -eq "docker") {
    switch ($Command) {
        "start" {
            Compose "up -d --build"
            Compose "exec -T postgres pg_isready -U openpulse -d openpulse"
            Wait-Http "backend" "http://localhost:8080/api/v1/health"
            Wait-Http "frontend" "http://localhost:5173"
        }
        "stop" { Compose "down" }
        "restart" { Compose "down"; Compose "up -d --build" }
        "status" { Compose "ps" }
        "health" {
            Compose "exec -T postgres pg_isready -U openpulse -d openpulse"
            Wait-Http "backend" "http://localhost:8080/api/v1/health"
            Wait-Http "frontend" "http://localhost:5173"
        }
        "logs" { Compose "logs --tail=200" }
    }
    exit 0
}

# Local fallback mode
switch ($Command) {
    "start" {
        if (-not (Test-Path "$root/frontend/node_modules")) {
            Push-Location "$root/frontend"; npm ci; Pop-Location
        }

        if (-not (Test-Path "$runDir/backend.pid") -or -not (Get-Process -Id (Get-Content "$runDir/backend.pid") -ErrorAction SilentlyContinue)) {
            $backend = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" -WorkingDirectory $root -RedirectStandardOutput "$runDir/backend.log" -RedirectStandardError "$runDir/backend.log" -PassThru
            $backend.Id | Set-Content "$runDir/backend.pid"
            Write-Host "[run] backend started"
        }

        if (-not (Test-Path "$runDir/frontend.pid") -or -not (Get-Process -Id (Get-Content "$runDir/frontend.pid") -ErrorAction SilentlyContinue)) {
            $frontend = Start-Process -FilePath "npm" -ArgumentList "run dev -- --host 0.0.0.0 --port 5173" -WorkingDirectory "$root/frontend" -RedirectStandardOutput "$runDir/frontend.log" -RedirectStandardError "$runDir/frontend.log" -PassThru
            $frontend.Id | Set-Content "$runDir/frontend.pid"
            Write-Host "[run] frontend started"
        }

        Wait-Http "backend" "http://localhost:8080/api/v1/health"
        Wait-Http "frontend" "http://localhost:5173"
        Write-Host "[health] Local mode uses embedded H2 DB."
    }
    "stop" {
        foreach ($svc in @("backend", "frontend")) {
            $pidPath = "$runDir/$svc.pid"
            if (Test-Path $pidPath) {
                $pid = Get-Content $pidPath
                Stop-Process -Id $pid -ErrorAction SilentlyContinue
                Remove-Item $pidPath -Force -ErrorAction SilentlyContinue
                Write-Host "[run] stopped $svc"
            }
        }
    }
    "restart" { & "$PSScriptRoot/run.ps1" -Command stop -Mode local; & "$PSScriptRoot/run.ps1" -Command start -Mode local }
    "status" {
        foreach ($svc in @("backend", "frontend")) {
            $pidPath = "$runDir/$svc.pid"
            if (Test-Path $pidPath -and (Get-Process -Id (Get-Content $pidPath) -ErrorAction SilentlyContinue)) {
                Write-Host "[status] $svc running"
            } else {
                Write-Host "[status] $svc stopped"
            }
        }
    }
    "health" {
        Wait-Http "backend" "http://localhost:8080/api/v1/health"
        Wait-Http "frontend" "http://localhost:5173"
        Write-Host "[health] Local mode uses embedded H2 DB."
    }
    "logs" {
        if (Test-Path "$runDir/backend.log") { Get-Content "$runDir/backend.log" -Tail 100 }
        if (Test-Path "$runDir/frontend.log") { Get-Content "$runDir/frontend.log" -Tail 100 }
    }
}
