# Use -JvmOptions '-XX:AOTMode=on' to reject silent cache fallback.
param([string] $Executable, [string] $JvmOptions = '', [int] $Runs = 1, [string] $Label = 'default', [string[]] $Arguments = @(), [switch] $Portable)
$ErrorActionPreference = 'Stop'
$executablePath = (Resolve-Path -LiteralPath $Executable).Path
foreach ($run in 1..$Runs) {
    $eventName = 'Local\Downlet-perf-' + [guid]::NewGuid().ToString('N')
    $ready = [Threading.EventWaitHandle]::new($false, [Threading.EventResetMode]::ManualReset, $eventName)
    $start = [Diagnostics.ProcessStartInfo]::new($executablePath)
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $start.WorkingDirectory = Split-Path -Parent $executablePath
    $start.Environment['DOWNLET_STARTUP_EVENT'] = $eventName
    if ($JvmOptions) { $start.Environment['_JAVA_OPTIONS'] = $JvmOptions }
    $owned = @()
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $application = [Diagnostics.Process]::Start($start)
    $stdout = $application.StandardOutput.ReadToEndAsync()
    $stderr = $application.StandardError.ReadToEndAsync()
    try {
        if ($Portable) {
            $ready.Dispose()
            $ready = $null
            while (-not $ready) {
                try { $ready = [Threading.EventWaitHandle]::OpenExisting("Local\Downlet-startup-$($application.Id)") }
                catch [Threading.WaitHandleCannotBeOpenedException] {
                    if ($application.HasExited -or $clock.Elapsed.TotalSeconds -gt 40) { throw }
                    Start-Sleep -Milliseconds 10
                }
            }
        }
        while (-not $ready.WaitOne(10)) {
            if ($application.HasExited) { throw "Application exited: $($application.ExitCode): $($stderr.Result) $($stdout.Result)" }
            if ($clock.Elapsed.TotalSeconds -gt 40) { throw 'Startup timed out' }
        }
        $milliseconds = $clock.ElapsedMilliseconds
        $allProcesses = @(Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId)
        $ownedIds = @($application.Id)
        foreach ($level in 1..3) {
            $ownedIds = @($ownedIds + @($allProcesses | Where-Object ParentProcessId -in $ownedIds | ForEach-Object ProcessId) | Select-Object -Unique)
        }
        $owned = @(Get-Process -Id $ownedIds -ErrorAction SilentlyContinue)
        $jvm = $owned | Sort-Object WorkingSet64 -Descending | Select-Object -First 1
        $jvm.Refresh()
        [pscustomobject]@{
            Label = $Label
            Run = $run
            ReadyMilliseconds = $milliseconds
            CpuMilliseconds = [math]::Round($jvm.TotalProcessorTime.TotalMilliseconds)
            WorkingSetMiB = [math]::Round($jvm.WorkingSet64 / 1MB)
        } | ConvertTo-Json -Compress
    } finally {
        foreach ($ownedProcess in $owned) { if (-not $ownedProcess.HasExited) { $null = $ownedProcess.CloseMainWindow() } }
        if (-not $application.WaitForExit(5000)) { $application.Kill($true); $application.WaitForExit() }
        $application.Dispose()
        if ($ready) { $ready.Dispose() }
    }
}
