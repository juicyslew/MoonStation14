$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$build = Join-Path $root 'build/diagnostic-fixtures/jfr-dump'
$classes = Join-Path $build 'classes'
$dump = Join-Path $build 'positive path with spaces/population.jfr'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$helper = Join-Path $root 'src/main/java/com/juicyslew/moonstation14/ms14/animals/diagnostics/AnimalPopulationRecording.java'
$fixture = Join-Path $PSScriptRoot 'JfrDumpFixture.java'
& javac -d $classes $helper $fixture
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
try {
    $negative = & $java -cp $classes JfrDumpFixture missing
    if ($LASTEXITCODE -ne 0 -or ($negative -join "`n") -notmatch 'MISSING_RECORDING_REJECTED') {
        throw 'Negative missing-recording assertion failed'
    }
    $proc = Start-Process -FilePath $java -ArgumentList @('-cp', "`"$classes`"", 'JfrDumpFixture', "`"$dump`"") -PassThru -Wait -NoNewWindow
    if ($proc.ExitCode -ne 0) { throw "Fixture JVM exited $($proc.ExitCode)" }
    if (!(Test-Path -LiteralPath $dump) -or (Get-Item -LiteralPath $dump).Length -le 0) { throw 'Dump missing or empty' }
    $summary = & (Join-Path $env:JAVA_HOME 'bin/jfr.exe') summary $dump
    if ($LASTEXITCODE -ne 0 -or ($summary -join "`n") -notmatch 'ms14\.fixture\.Event') { throw 'JFR is unreadable or fixture event absent' }
    Write-Output 'PASS: negative missing recording; positive dump/read with fixture event and spaced path'
} catch {
    Write-Output "Artifacts retained at $build"
    throw
}
