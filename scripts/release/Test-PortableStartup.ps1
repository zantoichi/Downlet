[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $PortablePath,
    [int] $TimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
$executable = (Resolve-Path -LiteralPath $PortablePath).Path
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class DownletStartupWindows {
    public delegate bool WindowVisitor(IntPtr window, IntPtr data);
    [DllImport("user32.dll")] public static extern bool EnumWindows(WindowVisitor visitor, IntPtr data);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr window, out uint process);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr window);
    public static IntPtr VisibleWindow(int process) {
        IntPtr found = IntPtr.Zero;
        EnumWindows((window, data) => {
            uint owner;
            GetWindowThreadProcessId(window, out owner);
            if (owner == process && IsWindowVisible(window)) found = window;
            return found == IntPtr.Zero;
        }, IntPtr.Zero);
        return found;
    }
}
'@

# Run against a newly built version for extraction, then again for the warm cache.
# Leave the launched application open for visual inspection and close it normally afterward.
$timer = [Diagnostics.Stopwatch]::StartNew()
$application = Start-Process -FilePath $executable -WindowStyle Hidden -PassThru
$splashMilliseconds = $null
$mainMilliseconds = $null
$splash = [IntPtr]::Zero
$nextProcessScan = 0
$descendants = @($application.Id)
while ($timer.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
    if ($null -eq $splashMilliseconds -and [DownletStartupWindows]::VisibleWindow($application.Id) -ne [IntPtr]::Zero) {
        $splashMilliseconds = $timer.ElapsedMilliseconds
        $splash = [DownletStartupWindows]::VisibleWindow($application.Id)
    }
    if ($timer.ElapsedMilliseconds -ge $nextProcessScan) {
        $processes = @(Get-CimInstance Win32_Process | Select-Object ProcessId, ParentProcessId)
        foreach ($level in 1..3) {
            $descendants = @($descendants + @($processes | Where-Object ParentProcessId -in $descendants | ForEach-Object ProcessId) | Select-Object -Unique)
        }
        $nextProcessScan = $timer.ElapsedMilliseconds + 250
    }
    foreach ($childId in ($descendants | Where-Object { $_ -ne $application.Id })) {
        if ($null -eq $mainMilliseconds -and [DownletStartupWindows]::VisibleWindow($childId) -ne [IntPtr]::Zero) { $mainMilliseconds = $timer.ElapsedMilliseconds }
    }
    if ($null -ne $mainMilliseconds -and -not [DownletStartupWindows]::IsWindowVisible($splash)) { break }
    if ($application.HasExited) { throw "Portable exited before handoff: $($application.ExitCode)" }
    Start-Sleep -Milliseconds 10
}
if ($null -eq $mainMilliseconds) { throw 'Main window did not appear.' }
if ([DownletStartupWindows]::IsWindowVisible($splash)) { throw 'Splash remained visible after the main window appeared.' }
[pscustomobject]@{
    PortableProcessId = $application.Id
    SplashMilliseconds = $splashMilliseconds
    MainWindowMilliseconds = $mainMilliseconds
    SplashSkipped = $null -eq $splashMilliseconds
    SplashTargetMet = $null -eq $splashMilliseconds -or ($splashMilliseconds -ge 250 -and $splashMilliseconds -le 500)
}
