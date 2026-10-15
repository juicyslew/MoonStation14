param([switch]$NoOpen)
$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot
$sharedModule = Join-Path (Split-Path $repo -Parent) 'MoonStation14-shared/tooling/agent_workflow/gate_progress.py'
$exchange = Join-Path (Split-Path $repo -Parent) 'Asset Building Workspace/work/agent_exchange'
if (-not (Test-Path -LiteralPath $sharedModule)) { throw "Shared gate_progress module missing: $sharedModule" }
if (-not (Test-Path -LiteralPath $exchange -PathType Container)) { throw "Shared exchange missing: $exchange" }
$python = Get-Command python -ErrorAction Stop
$info = [System.Diagnostics.ProcessStartInfo]::new()
$info.FileName = $python.Source
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
$info.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$info.StandardErrorEncoding = [System.Text.Encoding]::UTF8
function Quote-WindowsArgument([string]$Argument) {
    # CommandLineToArgvW-compatible quoting for paths passed through ProcessStartInfo.Arguments.
    $quoted = $Argument -replace '(\\*)"', '$1$1\"'
    $quoted = $quoted -replace '(\\+)$', '$1$1'
    return '"' + $quoted + '"'
}
$info.Arguments = (Quote-WindowsArgument $sharedModule) + ' --exchange ' + (Quote-WindowsArgument $exchange)
$feedbackModule = Join-Path (Split-Path $sharedModule -Parent) 'owner_feedback.py'
function Invoke-Feedback([string]$Mode, [switch]$Wait) {
    if (-not (Test-Path -LiteralPath $feedbackModule)) { return $false }
    $feedback = [System.Diagnostics.ProcessStartInfo]::new()
    $feedback.FileName = $python.Source
    $feedback.UseShellExecute = $false
    $feedback.CreateNoWindow = $true
    $feedback.RedirectStandardOutput = $Wait
    $feedback.RedirectStandardError = $Wait
    $feedback.Arguments = (Quote-WindowsArgument $feedbackModule) + ' --exchange ' + (Quote-WindowsArgument $exchange) + ' --' + $Mode
    $child = [System.Diagnostics.Process]::new(); $child.StartInfo = $feedback
    [void]$child.Start()
    if ($Wait) {
        $outTask = $child.StandardOutput.ReadToEndAsync(); $errTask = $child.StandardError.ReadToEndAsync()
        if (-not $child.WaitForExit(25000)) { $child.Kill(); return $false }
        if ($child.ExitCode -ne 0) { [Console]::Error.WriteLine('Owner queue refresh unavailable: ' + $errTask.GetAwaiter().GetResult().Trim()); return $false }
    }
    return $true
}
function Write-ObserverError([string]$Reason) {
    try {
        $health = [ordered]@{ status = 'error'; updated_at = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0; errors = @($Reason) }
        $target = Join-Path $exchange 'owner-feedback-observer.json'
        $temp = $target + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
        [IO.File]::WriteAllText($temp, ($health | ConvertTo-Json -Depth 4) + "`n", [Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $temp -Destination $target -Force
    } catch { [Console]::Error.WriteLine('Could not persist observer error health: ' + $_.Exception.Message) }
}
# Refresh once with a strict bound, then invoke the shared singleton-safe observer bootstrap.
if (-not (Invoke-Feedback 'once' -Wait)) {
    [Console]::Error.WriteLine('Owner queue refresh unavailable; board will show observation health.')
    Write-ObserverError 'Bounded owner queue refresh failed or timed out'
}
if (-not (Invoke-Feedback 'ensure-started' -Wait)) {
    [Console]::Error.WriteLine('Owner feedback observer could not be confirmed started.')
    Write-ObserverError 'Owner feedback observer ensure-started failed or timed out'
}
$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $info
[void]$process.Start()
$stdoutTask = $process.StandardOutput.ReadToEndAsync()
$stderrTask = $process.StandardError.ReadToEndAsync()
$process.WaitForExit()
$stdout = $stdoutTask.GetAwaiter().GetResult().Trim()
$stderr = $stderrTask.GetAwaiter().GetResult().Trim()
if ($process.ExitCode -ne 0) { throw "Status render failed: $stderr" }
$board = Join-Path $exchange 'integration-status.html'
if ($NoOpen) { Write-Output $board; exit 0 }
Start-Process -FilePath $board
