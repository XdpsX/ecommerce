param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('apply', 'check', 'verify')]
    [string]$Command
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\\..')).Path
$services = @('ecommerce', 'identity', 'notification', 'media')
$goals = switch ($Command) {
    'apply' { @('spotless:apply') }
    'check' { @('spotless:check') }
    'verify' { @('verify') }
}

foreach ($service in $services) {
    $directory = Join-Path $root "services/$service"
    $wrapper = Join-Path $directory 'mvnw.cmd'
    if (-not (Test-Path $wrapper)) {
        throw "Maven wrapper not found: $wrapper"
    }

    Write-Host "[$service] Running Maven $($goals -join ' ')" -ForegroundColor Cyan
    Push-Location $directory
    try {
        & $wrapper @goals
        if ($LASTEXITCODE -ne 0) {
            Write-Error "[$service] Failed with exit code $LASTEXITCODE"
            exit $LASTEXITCODE
        }
    }
    finally {
        Pop-Location
    }
}

Write-Host "All backend services passed: $Command" -ForegroundColor Green
