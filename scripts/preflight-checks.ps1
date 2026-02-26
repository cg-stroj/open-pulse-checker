Write-Host "[preflight] Running Pulseguard preflight checks (Windows)..."

$failed = $false

function Test-Cmd($name) {
    if (Get-Command $name -ErrorAction SilentlyContinue) {
        Write-Host "[ok] $name found"
    } else {
        Write-Host "[fail] $name not found"
        $script:failed = $true
    }
}

Test-Cmd "java"
Test-Cmd "docker"

try {
    $portInUse = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction Stop
    if ($portInUse) {
        Write-Host "[fail] Port 8080 is in use"
        $failed = $true
    }
} catch {
    Write-Host "[ok] Port 8080 appears available"
}

try {
    docker info | Out-Null
    Write-Host "[ok] Docker daemon reachable"
} catch {
    Write-Host "[fail] Docker daemon not reachable"
    $failed = $true
}

if ($failed) {
    throw "Preflight checks failed."
}

Write-Host "[preflight] Completed."
