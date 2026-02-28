param(
    [ValidateSet("auto", "docker", "local")]
    [string]$Mode = "auto",
    [switch]$Wizard
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

function Read-EnvMap {
    param([string]$File)
    $map = @{}
    if (-not (Test-Path $File)) { return $map }
    Get-Content $File | ForEach-Object {
        if ($_ -match "^\s*#" -or $_ -notmatch "=") { return }
        $parts = $_.Split("=", 2)
        $map[$parts[0]] = $parts[1]
    }
    return $map
}

function New-SecurePassword {
    $chars = (48..57 + 65..90 + 97..122)
    return -join ($chars | Get-Random -Count 24 | ForEach-Object { [char]$_ })
}

function Prompt-Choice {
    param([string]$Prompt, [string]$Default, [string[]]$Allowed)
    while ($true) {
        $answer = Read-Host "$Prompt [$Default]"
        if ([string]::IsNullOrWhiteSpace($answer)) { $answer = $Default }
        if ($Allowed -contains $answer) { return $answer }
        Write-Host "[fail] Allowed values: $($Allowed -join ', ')"
    }
}

function Prompt-Port {
    param([string]$Prompt, [string]$Default)
    while ($true) {
        $answer = Read-Host "$Prompt [$Default]"
        if ([string]::IsNullOrWhiteSpace($answer)) { $answer = $Default }
        if ($answer -match '^[0-9]+$') {
            $p = [int]$answer
            if ($p -ge 1 -and $p -le 65535) { return "$p" }
        }
        Write-Host "[fail] Port must be in range 1..65535"
    }
}

function Prompt-NonEmpty {
    param([string]$Prompt, [string]$Default)
    while ($true) {
        $answer = Read-Host "$Prompt [$Default]"
        if ([string]::IsNullOrWhiteSpace($answer)) { $answer = $Default }
        if (-not [string]::IsNullOrWhiteSpace($answer)) { return $answer }
        Write-Host "[fail] Value cannot be empty"
    }
}

function Prompt-YesNo {
    param([string]$Prompt, [bool]$DefaultYes)
    $defaultToken = if ($DefaultYes) { "y" } else { "n" }
    while ($true) {
        $answer = Read-Host "$Prompt [y/n, default=$defaultToken]"
        if ([string]::IsNullOrWhiteSpace($answer)) { $answer = $defaultToken }
        switch ($answer.ToLower()) {
            "y" { return $true }
            "yes" { return $true }
            "n" { return $false }
            "no" { return $false }
            default { Write-Host "[fail] Enter y/yes or n/no" }
        }
    }
}

Write-Host "[install] Open Pulse Checker install (mode: $Mode)"

if ($Mode -eq "local" -and -not $Wizard) {
    throw "[fail] Windows local mode is not supported yet. Use '-Mode docker' on Windows, or run local mode from Linux/macOS (or WSL)."
}

& "$PSScriptRoot/preflight-checks.ps1" -Mode $Mode

if (-not (Test-Path "$root/.env")) {
    Copy-Item "$root/.env.example" "$root/.env"
    $pw = New-SecurePassword
    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_DB_PASSWORD" -Value $pw
    Write-Host "[install] Created .env from template with generated DB password."
} else {
    Write-Host "[install] .env already exists; leaving unchanged."
}

if (-not (Test-Path "$root/frontend/.env")) {
    Copy-Item "$root/frontend/.env.example" "$root/frontend/.env"
    Write-Host "[install] Created frontend/.env from template."
}

if ($Wizard) {
    $envMap = Read-EnvMap -File "$root/.env"
    $defaultMode = if ($envMap.ContainsKey('OPENPULSE_RUNTIME_MODE')) { $envMap['OPENPULSE_RUNTIME_MODE'] } else { $Mode }

    Write-Host "[wizard] Interactive setup"
    $selectedMode = Prompt-Choice -Prompt "Runtime mode (auto/docker/local)" -Default $defaultMode -Allowed @("auto", "docker", "local")
    if ($selectedMode -eq "local") {
        throw "[fail] Windows local mode is not supported yet. Choose docker/auto, or run local mode from Linux/macOS (or WSL)."
    }

    $backendPort = Prompt-Port -Prompt "Backend port" -Default ($(if ($envMap.ContainsKey('OPENPULSE_PORT')) { $envMap['OPENPULSE_PORT'] } else { '8888' }))
    $frontendPort = Prompt-Port -Prompt "Frontend port" -Default ($(if ($envMap.ContainsKey('OPENPULSE_FRONTEND_PORT')) { $envMap['OPENPULSE_FRONTEND_PORT'] } else { '5173' }))
    $dbPort = Prompt-Port -Prompt "Database port" -Default ($(if ($envMap.ContainsKey('OPENPULSE_DB_PORT')) { $envMap['OPENPULSE_DB_PORT'] } else { '5432' }))
    $dbName = Prompt-NonEmpty -Prompt "Database name" -Default ($(if ($envMap.ContainsKey('OPENPULSE_DB_NAME')) { $envMap['OPENPULSE_DB_NAME'] } else { 'openpulse' }))
    $dbUser = Prompt-NonEmpty -Prompt "Database user" -Default ($(if ($envMap.ContainsKey('OPENPULSE_DB_USERNAME')) { $envMap['OPENPULSE_DB_USERNAME'] } else { 'openpulse' }))

    if (Prompt-YesNo -Prompt "Generate secure DB password?" -DefaultYes $true) {
        $dbPassword = New-SecurePassword
        Write-Host "[wizard] Generated DB password."
    } else {
        $dbPassword = Prompt-NonEmpty -Prompt "Database password" -Default ""
    }

    if (Prompt-YesNo -Prompt "Enable bootstrap admin?" -DefaultYes $false) {
        $bootstrapEnabled = "true"
        $bootstrapUser = Prompt-NonEmpty -Prompt "Bootstrap admin username" -Default ($(if ($envMap.ContainsKey('OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_USERNAME')) { $envMap['OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_USERNAME'] } else { 'admin' }))
        if (Prompt-YesNo -Prompt "Generate bootstrap admin password?" -DefaultYes $true) {
            $bootstrapPassword = New-SecurePassword
            Write-Host "[wizard] Generated bootstrap admin password."
        } else {
            $bootstrapPassword = Prompt-NonEmpty -Prompt "Bootstrap admin password" -Default ""
        }
    } else {
        $bootstrapEnabled = "false"
        $bootstrapUser = $(if ($envMap.ContainsKey('OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_USERNAME')) { $envMap['OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_USERNAME'] } else { 'admin' })
        $bootstrapPassword = ""
    }

    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_RUNTIME_MODE" -Value $selectedMode
    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_PORT" -Value $backendPort
    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_FRONTEND_PORT" -Value $frontendPort
    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_DB_PORT" -Value $dbPort
    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_DB_NAME" -Value $dbName
    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_DB_USERNAME" -Value $dbUser
    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_DB_PASSWORD" -Value $dbPassword
    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_DB_URL" -Value "jdbc:postgresql://localhost:$dbPort/$dbName"
    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_ENABLED" -Value $bootstrapEnabled
    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_USERNAME" -Value $bootstrapUser
    Set-EnvValue -File "$root/.env" -Key "OPENPULSE_SECURITY_BOOTSTRAP_ADMIN_PASSWORD" -Value $bootstrapPassword

    $frontendEnv = Read-EnvMap -File "$root/frontend/.env"
    $viteUrl = if ($frontendEnv.ContainsKey('VITE_API_BASE_URL')) { $frontendEnv['VITE_API_BASE_URL'] } else { "http://localhost:$backendPort/api/v1" }
    Set-EnvValue -File "$root/frontend/.env" -Key "VITE_API_BASE_URL" -Value $viteUrl

    $Mode = $selectedMode
}

Set-EnvValue -File "$root/.env" -Key "OPENPULSE_RUNTIME_MODE" -Value "docker"
docker compose -f "$root/docker-compose.full.yml" --env-file "$root/.env" pull postgres | Out-Null
Write-Host "[install] Docker install complete."
Write-Host "[install] Done. Next: ./scripts/run.sh start docker (or ./scripts/run.ps1 start docker)"
