$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'connected_join.ps1'
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw "connected_join.ps1 parse failed: $($errors[0])" }
$capture = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Capture-ProcessHandle' }, $true)
$function = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Get-ExitedProcessExitCode' }, $true)
if (!$capture -or !$function) { throw 'Missing production process-handle/exit-code helpers.' }
$admissionAst = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Test-ConnectedJoinAdmission' }, $true)
$terminalAst = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Get-MeleeTerminalFailure' }, $true)
$incompleteAst = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Format-MeleeIncompleteFailure' }, $true)
$publishAst = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Publish-MeleeLogs' }, $true)
$phasesAst = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Get-MeleeExpectedPhases' }, $true)
$waitAst = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Wait-ConnectedServerReady' }, $true)
if (!$admissionAst -or !$terminalAst -or !$incompleteAst -or !$publishAst -or !$waitAst -or !$phasesAst) { throw 'Missing production melee gate/failure/publication/startup-wait helpers.' }
Invoke-Expression $capture.Extent.Text
Invoke-Expression $function.Extent.Text
Invoke-Expression $admissionAst.Extent.Text
Invoke-Expression $terminalAst.Extent.Text
Invoke-Expression $incompleteAst.Extent.Text
Invoke-Expression $publishAst.Extent.Text
Invoke-Expression $phasesAst.Extent.Text
Invoke-Expression $waitAst.Extent.Text
$gateCases = 0
if (!(Test-ConnectedJoinAdmission $true $true $true $false)) { throw 'Melee mode incorrectly requires an accepted movement intent.' }; $gateCases++
if (Test-ConnectedJoinAdmission $false $true $true $false) { throw 'Ordinary join incorrectly bypasses accepted movement intent.' }; $gateCases++
$cases = 0
$script:meleeStrikeMode = $false
$oldPhases = Get-MeleeExpectedPhases
if ($oldPhases.Count -ne 8 -or $oldPhases[0] -cne 'fist_first' -or $oldPhases[-1] -cne 'observer') { throw 'Legacy melee phase contract changed.' }
$motionPhases = Get-MeleeExpectedPhases $true
$expectedMotion = @('motion_fist_first','motion_fist_third','motion_observer_up','motion_observer_down','motion_observer_close','motion_observer_wide','motion_knife_first','motion_knife_wide','motion_wall_clip') | ForEach-Object { $scene=$_; @('outward','extended','return') | ForEach-Object { "${scene}_$_" } }
if ($motionPhases.Count -ne 27 -or ($motionPhases -join ',') -cne ($expectedMotion -join ',')) { throw 'Strike-motion phase labels/order changed.' }
$gateCases++
$startupCases = 0
$startupRoot = Join-Path ([IO.Path]::GetTempPath()) ('ms14-startup-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $startupRoot | Out-Null
try {
    $script:startupLog = 'Done (immediate)!'; $script:startupExited = $false; $script:startupReads = 0; $script:startupExitCode = 7
    $readLog = { $script:startupReads++; $script:startupLog }
    $track = { }
    $hasExited = { $script:startupExited }
    $exitCode = { $script:startupExitCode }
    $immediate = Wait-ConnectedServerReady $readLog $track $hasExited $exitCode 1 1
    if ($immediate.ElapsedMilliseconds -ge 1000) { throw 'Immediate Done was not accepted.' }; $startupCases++
    $script:startupReads = 0; $script:startupLog = ''
    $delayedRead = { $script:startupReads++; if ($script:startupReads -ge 2) { $script:startupLog = 'Done (test)!' }; $script:startupLog }
    [void](Wait-ConnectedServerReady $delayedRead $track $hasExited $exitCode 1 1); $startupCases++
    $script:startupLog = 'Done (boundary)!'
    [void](Wait-ConnectedServerReady $readLog $track $hasExited $exitCode 0 1); $startupCases++
    $script:startupLog = ''; $timeoutFailed = $false
    try { [void](Wait-ConnectedServerReady $readLog $track $hasExited $exitCode 0 1) } catch { $timeoutFailed = $_.Exception.Message -match 'bounded startup wait.*budgetSeconds=0.*elapsedMilliseconds=' }
    if (!$timeoutFailed) { throw 'Finite startup timeout did not report configured budget and measured elapsed time.' }; $startupCases++
    $script:startupExited = $true; $exitFailed = $false
    try { [void](Wait-ConnectedServerReady $readLog $track $hasExited $exitCode 1 1) } catch { $exitFailed = $_.Exception.Message -match 'Server launcher exited before Done \(7\)' }
    if (!$exitFailed) { throw 'Launcher exit before Done did not preserve its exit code.' }; $startupCases++
} finally { Remove-Item $startupRoot -Recurse -Force -ErrorAction SilentlyContinue }
$temp = Join-Path ([IO.Path]::GetTempPath()) ('ms14-process-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $temp | Out-Null
try {
    $failureDir = Join-Path $temp 'melee'; New-Item -ItemType Directory $failureDir | Out-Null
    $sourceDir = Join-Path $temp 'owned-fixture'; $destinationDir = Join-Path $temp 'published-melee'
    New-Item -ItemType Directory $sourceDir | Out-Null
    'server evidence' | Set-Content (Join-Path $sourceDir 'server.log') -NoNewline
    'client evidence' | Set-Content (Join-Path $sourceDir 'client.log') -NoNewline
    Publish-MeleeLogs $sourceDir $destinationDir $true
    Remove-Item $sourceDir -Recurse -Force
    foreach ($log in @('server.log','client.log')) { $published = Join-Path $destinationDir $log; $expected = if ($log -eq 'server.log') { 'server evidence' } else { 'client evidence' }; if (!(Test-Path $published) -or (Get-Item $published).Length -eq 0 -or (Get-Content $published -Raw) -cne $expected) { throw "Published $log was not retained byte-for-byte after fixture cleanup." }; $gateCases++ }
    $missingFailed = $false
    $missingSource = Join-Path $temp 'missing-fixture'; New-Item -ItemType Directory $missingSource | Out-Null
    'server evidence' | Set-Content (Join-Path $missingSource 'server.log') -NoNewline
    try { Publish-MeleeLogs $missingSource (Join-Path $temp 'missing-output') $true } catch { $missingFailed = $_.Exception.Message -match 'Required melee log is missing: client.log' }
    if (!$missingFailed) { throw 'Successful publication did not fail closed for missing required server.log.' }; $gateCases++
    $partialDir = Join-Path $temp 'partial-fixture'; New-Item -ItemType Directory $partialDir | Out-Null
    'partial server diagnostics' | Set-Content (Join-Path $partialDir 'server.log') -NoNewline
    $partialOutput = Join-Path $temp 'partial-output'; Publish-MeleeLogs $partialDir $partialOutput $false
    if (!(Test-Path (Join-Path $partialOutput 'server.log'))) { throw 'Failure-path partial diagnostics were not published.' }; $gateCases++
    '{"status":"FAILED","reason":"synthetic","phase":"capture"}' | Set-Content (Join-Path $failureDir 'melee-capture-failed.json')
    if (!(Get-MeleeTerminalFailure $failureDir '' '')) { throw 'Melee structured failure was hidden by missing movement proof.' }; $gateCases++
    Remove-Item (Join-Path $failureDir 'melee-capture-failed.json')
    Set-Content (Join-Path $failureDir 'scene.json') '{"phase":"knife_first"}'
    1..2 | ForEach-Object { Set-Content (Join-Path $failureDir "frame$_.png") 'partial' }
    $incomplete = Format-MeleeIncompleteFailure $failureDir
    if ($incomplete -notmatch '^Melee capture incomplete phase=knife_first completed=2/8 PNGs manifestRecords=0 ' -or $incomplete -match 'PASS') { throw "Admitted incomplete capture was not reported explicitly: $incomplete" }; $gateCases++
    $script:meleeStrikeMode = $true
    $incompleteMotion = Format-MeleeIncompleteFailure $failureDir
    if ($incompleteMotion -notmatch 'completed=2/27 PNGs') { throw "Motion incomplete denominator wrong: $incompleteMotion" }; $gateCases++
    $script:meleeStrikeMode = $false
    $notAdmitted = Test-ConnectedJoinAdmission $true $false $true $false
    if ($notAdmitted -or $incomplete -notmatch 'phase=knife_first') { throw 'Not-admitted gate behavior changed or incomplete formatter failed.' }; $gateCases++
    foreach ($expected in @(0,7) + (1..10 | ForEach-Object { 0,7 })) {
        $out = Join-Path $temp "out-$cases.log"; $err = Join-Path $temp "err-$cases.log"
        $proc = Start-Process -FilePath $env:ComSpec -ArgumentList "/d /c exit $expected" -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
        $handle = Capture-ProcessHandle $proc
        if ($handle -eq [IntPtr]::Zero) { throw 'Original process handle was not captured.' }
        if (!$proc.WaitForExit(10000)) { throw "cmd.exe child timed out ($expected)." }
        $actual = Get-ExitedProcessExitCode $proc 'test child' $out $err
        if ($actual -ne $expected) { throw "Expected exit $expected; got $actual." }
        $cases++
    }
    $running = Start-Process -FilePath $env:ComSpec -ArgumentList '/d /c ping -n 3 127.0.0.1 >nul' -PassThru
    try {
        $failedClosed = $false
        try { [void](Get-ExitedProcessExitCode $running 'running child' 'out' 'err') } catch { $failedClosed = $_.Exception.Message -match 'exit state was not collectible' }
        if (!$failedClosed) { throw 'Running/unavailable process exit state did not fail closed.' }
    } finally { if (!$running.HasExited) { Stop-Process -Id $running.Id -Force -ErrorAction SilentlyContinue }; $running.WaitForExit() }
} finally { Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue }
"CONNECTED_JOIN_PROCESS_TESTS_PASS cases=$cases; gateCases=$gateCases; startupCases=$startupCases; original-handle-captured=true; unavailability-fail-closed=true"
