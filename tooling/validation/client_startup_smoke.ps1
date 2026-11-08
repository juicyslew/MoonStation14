[CmdletBinding()]
param([ValidateRange(30,300)][int]$TimeoutSeconds = 300)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$attempt = [Guid]::NewGuid().ToString('N')
$base = Join-Path $root ("build\client-startup-smoke\$attempt")
$run = Join-Path $base 'run'
$evidence = Join-Path $base 'evidence'
New-Item -ItemType Directory -Force -Path $run,$evidence | Out-Null
# Seed only the disposable attempt's own client options. This avoids the first-run
# accessibility onboarding screen without reading or modifying the owner's run/options.txt.
Set-Content -LiteralPath (Join-Path $run 'options.txt') -Value @('version:3955','fullscreen:false','onboardAccessibility:false') -Encoding ASCII
$marker = Join-Path $evidence 'result.json'
$stdout = Join-Path $evidence 'stdout.log'
$stderr = Join-Path $evidence 'stderr.log'
$exitCodeFile = Join-Path $evidence 'gradle-exit-code.txt'
$wrapper = Join-Path $base 'run-smoke.cmd'
$gradle = Join-Path $root 'gradlew.bat'
# Batch quoting preserves spaces for cmd/Gradle; unique attempt paths bind process ownership.
$line = 'call "' + $gradle + '" runClient --no-daemon "-Pms14ClientRunDir=' + $run + '" "-Pms14ClientStartupProbe=' + $marker + '"'
Set-Content -LiteralPath $wrapper -Value @('@echo off', $line, 'set "ms14_smoke_exit=%ERRORLEVEL%"',('>"' + $exitCodeFile + '" echo %ms14_smoke_exit%'),'exit /b %ms14_smoke_exit%') -Encoding ASCII
$process = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d','/c',('"' + $wrapper + '"')) -WorkingDirectory $root -PassThru -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr
$identity = $null
try {
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        $identity = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)" -ErrorAction SilentlyContinue
        if ($identity) { break }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $deadline)
    if (-not $identity -or $identity.CommandLine -notlike "*$attempt*") { throw 'Could not verify unique smoke wrapper process identity.' }
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($identity.ProcessId)" -ErrorAction SilentlyContinue
        if ($current -and $current.CreationDate -eq $identity.CreationDate -and $current.CommandLine -like "*$attempt*") {
            & taskkill.exe /PID $identity.ProcessId /T /F | Out-Null
        }
        throw "Client startup timed out after ${TimeoutSeconds}s. Evidence: $evidence"
    }
    $process.Refresh()
    if (-not (Test-Path -LiteralPath $exitCodeFile)) { throw "Owned launcher completed but did not record Gradle's exit code. Evidence: $evidence" }
    $gradleExitCode = 0
    if (-not [int]::TryParse((Get-Content -LiteralPath $exitCodeFile -Raw).Trim(), [ref]$gradleExitCode)) { throw "Owned launcher wrote an invalid Gradle exit code. Evidence: $evidence" }
    if ($gradleExitCode -ne 0) { throw "Gradle/client exited $gradleExitCode (wrapper exit $($process.ExitCode)). Evidence: $evidence" }
    if (-not (Test-Path $marker)) { throw "Client exited without reaching TitleScreen. Evidence: $evidence" }
    $result = Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json
    if ($result.testId -ne 'client-startup-smoke' -or $result.status -ne 'title_screen_ready' -or $result.screen -ne 'TitleScreen' -or $null -ne $result.overlay -or $result.probeClassSha256 -notmatch '^[0-9a-f]{64}$') {
        throw 'Startup marker did not prove TitleScreen readiness.'
    }
    $result | Add-Member -NotePropertyName attemptId -NotePropertyValue $attempt
    $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $marker -Encoding UTF8
    Get-Content -LiteralPath $marker -Raw | Write-Output
} catch {
    $failure = [ordered]@{ attemptId=$attempt; status='failed'; error=$_.Exception.Message; exceptionType=$_.Exception.GetType().FullName; gradleExitCode=if (Test-Path $exitCodeFile) { Get-Content $exitCodeFile -Raw } else { $null }; markerExists=(Test-Path $marker); stdout=$stdout; stderr=$stderr; runLog=(Join-Path $run 'logs\latest.log') }
    $failure | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidence 'failure.json') -Encoding UTF8
    Write-Output ("Client startup smoke failed: {0}. Diagnostic summary: {1}" -f $_.Exception.Message,(Join-Path $evidence 'failure.json'))
    throw
}
