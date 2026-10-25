$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'connected_join.ps1'
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw "connected_join.ps1 parse failed: $($errors[0])" }
$capture = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Capture-ProcessHandle' }, $true)
$function = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq 'Get-ExitedProcessExitCode' }, $true)
if (!$capture -or !$function) { throw 'Missing production process-handle/exit-code helpers.' }
Invoke-Expression $capture.Extent.Text
Invoke-Expression $function.Extent.Text
$cases = 0
$temp = Join-Path ([IO.Path]::GetTempPath()) ('ms14-process-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $temp | Out-Null
try {
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
"CONNECTED_JOIN_PROCESS_TESTS_PASS cases=$cases; original-handle-captured=true; unavailability-fail-closed=true"
