[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $AppImage,
    [Parameter(Mandatory)] [string] $JavaExecutable
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $AppImage).Path
$cache = Join-Path $root 'app/downlet.aot'
$java = Join-Path $root 'runtime/bin/java.exe'
$config = Join-Path $root 'app/Downlet.cfg'
if (Test-Path -LiteralPath $java) { throw 'Training requires the stripped application runtime.' }
$eventName = 'Local\Downlet-training-' + [guid]::NewGuid().ToString('N')
$ready = [Threading.EventWaitHandle]::new($false, [Threading.EventResetMode]::ManualReset, $eventName)
$process = $null
try {
    # CAB/MSI timestamps have two-second precision; AOT requires exact jar modification times.
    foreach ($jar in Get-ChildItem -LiteralPath (Join-Path $root 'app') -Filter '*.jar') {
        $ticks = $jar.LastWriteTimeUtc.Ticks
        $jar.LastWriteTimeUtc = [DateTime]::new($ticks - ($ticks % [TimeSpan]::FromSeconds(2).Ticks), [DateTimeKind]::Utc)
    }
    # The AOT assembler needs java.exe from the exact JBR used to link this runtime.
    Copy-Item -LiteralPath $JavaExecutable -Destination $java
    $start = [Diagnostics.ProcessStartInfo]::new((Join-Path $root 'Downlet.exe'))
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WorkingDirectory = $root
    $start.RedirectStandardError = $true
    $start.RedirectStandardOutput = $true
    $start.Environment['DOWNLET_STARTUP_EVENT'] = $eventName
    $start.Environment['_JAVA_OPTIONS'] = '-XX:AOTCacheOutput="' + $cache + '"'
    $null = $start.Environment.Remove('JAVA_TOOL_OPTIONS')
    $null = $start.Environment.Remove('JDK_JAVA_OPTIONS')
    $process = [Diagnostics.Process]::Start($start)
    $errors = $process.StandardError.ReadToEndAsync()
    $output = $process.StandardOutput.ReadToEndAsync()
    if (-not $ready.WaitOne(60000)) { throw 'Startup cache training did not reach a responsive window.' }
    # jpackage's outer process can own a child JVM window on Windows.
    $processes = @(Get-CimInstance Win32_Process | Select-Object ProcessId, ParentProcessId)
    $ownedIds = @($process.Id)
    foreach ($level in 1..3) {
        $ownedIds = @($ownedIds + @($processes | Where-Object ParentProcessId -in $ownedIds |
            ForEach-Object ProcessId) | Select-Object -Unique)
    }
    foreach ($owned in @(Get-Process -Id $ownedIds -ErrorAction SilentlyContinue)) {
        $null = $owned.CloseMainWindow()
    }
    if (-not $process.WaitForExit(60000)) { throw 'Startup cache assembly timed out.' }
    if ($process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $cache) -or (Get-Item -LiteralPath $cache).Length -eq 0) {
        throw "Startup cache assembly failed: $($errors.Result) $($output.Result)"
    }
    # APPDIR is expanded by jpackage; auto mode preserves normal startup if the cache is unusable.
    Add-Content -LiteralPath $config -Value 'java-options=-XX:AOTCache=$APPDIR/downlet.aot'
    Write-Output ('Trained startup cache: {0:N2} MiB' -f ((Get-Item -LiteralPath $cache).Length / 1MB))
} finally {
    if ($process) {
        if (-not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
        $process.Dispose()
    }
    $ready.Dispose()
    Remove-Item -LiteralPath $java -ErrorAction SilentlyContinue
}
