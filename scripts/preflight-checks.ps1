Write-Host "[preflight] Open Pulse Checker Docker checks"
$failed = $false

if ($args.Count -gt 0 -and $args[0] -in @('-h','--help')) {
    Write-Host "Usage: ./scripts/preflight-checks.ps1"
    exit 0
}

if ($args.Count -gt 0) {
    throw "[fail] Usage: ./scripts/preflight-checks.ps1"
}

function Test-Port($port) {
    try {
        $inUse = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop
        if ($inUse) {
            Write-Host "[warn] Port $port is in use. Startup may fail unless you change .env port settings."
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

Test-Port 8080
Test-Port 5173
Test-Port 5432

if ($failed) {
    throw "Preflight checks failed."
}

Write-Host "[preflight] Completed."
