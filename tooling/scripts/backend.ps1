param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('apply', 'check', 'verify')]
    [string]$Command,

    [ValidateRange(1, 4)]
    [int]$Parallel = 1,

    [string]$LogDir = '.logs/backend'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$services = @('ecommerce', 'identity', 'notification', 'media')
$goal = switch ($Command) {
    'apply' { 'spotless:apply' }
    'check' { 'spotless:check' }
    'verify' { 'verify' }
}

# Relative paths are resolved against the repository root, not the caller's directory.
if ([System.IO.Path]::IsPathRooted($LogDir)) {
    $logBase = $LogDir
} else {
    $logBase = Join-Path $root $LogDir
}
$runId = '{0}-{1}' -f (Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'), ([guid]::NewGuid().ToString('N').Substring(0, 8))
$runDir = Join-Path $logBase $runId
New-Item -ItemType Directory -Path $runDir -Force | Out-Null

$pending = [System.Collections.Queue]::new()
foreach ($service in $services) { $pending.Enqueue($service) }
$running = @()
$results = @()
$summaryPath = Join-Path $runDir 'summary.log'

Write-Host "Running '$Command' (max parallel: $Parallel)" -ForegroundColor Cyan
Write-Host "Logs: $runDir"

while ($pending.Count -gt 0 -or $running.Count -gt 0) {
    while ($pending.Count -gt 0 -and $running.Count -lt $Parallel) {
        $service = [string]$pending.Dequeue()
        $directory = Join-Path $root "services/$service"
        $wrapper = Join-Path $directory 'mvnw.cmd'
        $stdout = Join-Path $runDir "$service.stdout.log"
        $stderr = Join-Path $runDir "$service.stderr.log"
        $started = Get-Date

        if (-not (Test-Path $wrapper)) {
            $results += [pscustomobject]@{ Service = $service; ExitCode = 1; Seconds = 0; Error = "Missing Maven wrapper: $wrapper" }
            Write-Host "[$service] Missing Maven wrapper" -ForegroundColor Red
            continue
        }

        try {
            # cmd.exe is required for reliable invocation of Windows .cmd wrappers.
            $arguments = '/d /c "' + $wrapper + '" ' + $goal
            $process = Start-Process -FilePath 'cmd.exe' -ArgumentList $arguments -WorkingDirectory $directory -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
            $running += [pscustomobject]@{ Service = $service; Process = $process; Started = $started }
            Write-Host "[$service] Started (PID $($process.Id))" -ForegroundColor Cyan
        } catch {
            $results += [pscustomobject]@{ Service = $service; ExitCode = 1; Seconds = 0; Error = $_.Exception.Message }
            Write-Host "[$service] Could not start: $($_.Exception.Message)" -ForegroundColor Red
        }
    }

    if ($running.Count -eq 0) { continue }

    Start-Sleep -Milliseconds 250
    $remaining = @()
    foreach ($task in $running) {
        $task.Process.Refresh()
        if (-not $task.Process.HasExited) {
            $remaining += $task
            continue
        }

        $exitCode = $task.Process.ExitCode
        $seconds = [math]::Round(((Get-Date) - $task.Started).TotalSeconds, 1)
        $results += [pscustomobject]@{ Service = $task.Service; ExitCode = $exitCode; Seconds = $seconds; Error = '' }
        if ($exitCode -eq 0) {
            Write-Host "[$($task.Service)] Passed ($seconds s)" -ForegroundColor Green
        } else {
            Write-Host "[$($task.Service)] Failed (exit $exitCode, $seconds s). See $runDir" -ForegroundColor Red
        }
        $task.Process.Dispose()
    }
    $running = $remaining
}

$lines = @(
    "Command: $Command",
    "Parallel: $Parallel",
    "Logs: $runDir",
    ''
)
foreach ($service in $services) {
    $result = $results | Where-Object { $_.Service -eq $service } | Select-Object -First 1
    $status = if ($result.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }
    $lines += "$service : $status (exit $($result.ExitCode), $($result.Seconds) s) $($result.Error)"
}
$lines | Set-Content -Path $summaryPath -Encoding UTF8
Write-Host ($lines -join [Environment]::NewLine)

if (@($results | Where-Object { $_.ExitCode -ne 0 }).Count -gt 0) {
    exit 1
}
