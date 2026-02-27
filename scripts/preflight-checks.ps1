param(
    [ValidateSet("auto", "docker", "local")]
    [string]$Mode = "auto"
)

Write-Host "[preflight] Open Pulse Checker checks (mode: $Mode)"
$failed = $false

function Test-Cmd($name, $hint) {
    if (Get-Command $name -ErrorAction SilentlyContinue) {
        Write-Host "[ok] $name found"
    } else {
        Write-Host "[fail] $name not found. $hint"
        $script:failed = $true
    }
}

function Test-Port($port) {
    try {
        $inUse = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop
        if ($inUse) {
            Write-Host "[warn] Port $port is in use. Startup may fail unless you change it."
        }
    } catch {
        Write-Host "[ok] Port $port appears available"
    }
}

$dockerOk = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
    try {
        docker info | Out-Null
        docker compose version | Out-Null
        Write-Host "[ok] Docker daemon + compose reachable"
        $dockerOk = $true
    } catch {
        if ($Mode -eq "docker") {
            Write-Host "[fail] Docker installed but daemon/compose not ready."
            $failed = $true
        } else {
            Write-Host "[warn] Docker unavailable; local fallback required."
        }
    }
} elseif ($Mode -eq "docker") {
    Write-Host "[fail] Docker not found. Install Docker Desktop."
    $failed = $true
}

if ($Mode -eq "local" -or ($Mode -eq "auto" -and -not $dockerOk)) {
    Test-Cmd "java" "Install Java 21+."
    Test-Cmd "mvn" "Install Maven 3.9+."
    Test-Cmd "node" "Install Node.js 20+ (22 recommended)."
    Test-Cmd "npm" "Install npm."
}

Test-Port 8080
Test-Port 5173
Test-Port 5432

if ($failed) {
    throw "Preflight checks failed."
}

Write-Host "[preflight] Completed."
