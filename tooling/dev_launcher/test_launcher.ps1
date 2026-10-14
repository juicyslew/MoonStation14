[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$oldLocalAppData = $env:LOCALAPPDATA
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$temp = Join-Path (Join-Path $env:LOCALAPPDATA 'Temp\opencode') ('ms14 launcher fixture-' + [char]0x00e9 + '-' + [Guid]::NewGuid().ToString('N'))
$fixtureGroup = [Guid]::NewGuid().ToString('N')
$env:MS14_FIXTURE_GROUP = $fixtureGroup
$portProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$portProbe.Start(); $fixturePort = $portProbe.LocalEndpoint.Port; $portProbe.Stop()
$root = Join-Path $temp 'MoonStation14'
function Get-FixtureJvm([string]$Role, [string]$RootPath) {
    @(Get-ManagedDevProcesses -Roots @($RootPath) | Where-Object { $_.Role -eq $Role } | ForEach-Object {
        [pscustomobject]@{ Id=$_.Id; ProcessId=$_.Id; Created=$_.Created; CreationDate=$_.Created; Role=$_.Role }
    })
}
function Wait-FixtureProcessExit($Identity, [string]$Description) {
    $limit = [DateTime]::UtcNow.AddSeconds(12)
    do {
        $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($Identity.Id)" -ErrorAction SilentlyContinue
        if (!$current -or $current.CreationDate -ne $Identity.Created) { return }
        Start-Sleep -Milliseconds 100
    } while ([DateTime]::UtcNow -lt $limit)
    throw "$Description PID $($Identity.Id) remained alive."
}
$outerCallerDirectory = $PWD.Path
$otherCallerDirectory = Join-Path $temp 'other-caller'
function Invoke-FixtureLauncher([string[]]$Arguments) {
    $scriptPath = Join-Path $root 'ms14.ps1'
    try {
        if ($Arguments -contains '-Stop') { & $scriptPath -Stop *> $null }
        else {
            $worktree = 'main'; $clients = 1
            for ($i = 0; $i -lt $Arguments.Count; $i++) {
                if ($Arguments[$i] -eq '-Worktree') { $worktree = $Arguments[$i + 1] }
                if ($Arguments[$i] -eq '-Clients') { $clients = [int]$Arguments[$i + 1] }
            }
            & $scriptPath -Worktree $worktree -Clients $clients *> $null
        }
        [pscustomobject]@{ ExitCode=0; Error='' }
    } catch {
        [pscustomobject]@{ ExitCode=1; Error=$_.ToString() }
    }
}
New-Item -ItemType Directory -Path $root, $otherCallerDirectory -Force | Out-Null
Push-Location $otherCallerDirectory
try {
    $env:LOCALAPPDATA = Join-Path $temp 'isolated-profile'
    $fixtureSource = Join-Path $temp 'fixture-src\net\neoforged\devlaunch\Main.java'
    $fixtureClasses = Join-Path $temp 'fixture-classes'
    New-Item -ItemType Directory -Path (Split-Path $fixtureSource -Parent), $fixtureClasses -Force | Out-Null
    @'
package net.neoforged.devlaunch;
public final class Main {
    public static void main(String[] args) throws Exception {
        while (true) Thread.sleep(1000L);
    }
}
'@ | Set-Content -LiteralPath $fixtureSource -Encoding ASCII
    & javac -encoding UTF-8 -d $fixtureClasses $fixtureSource
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the dummy JVM fixture.' }
    $java = (Get-Command java).Source
    $foreignOut = Join-Path $temp 'foreign-jvm.out'
    $foreignErr = Join-Path $temp 'foreign-jvm.err'
    $foreignJvm = Start-Process -FilePath $java -ArgumentList ('-Dms14.fixture.group=' + $fixtureGroup + ' -cp "' + $fixtureClasses + '" net.neoforged.devlaunch.Main') -PassThru -WindowStyle Hidden -RedirectStandardOutput $foreignOut -RedirectStandardError $foreignErr
    $foreignCreated = (Get-CimInstance Win32_Process -Filter "ProcessId=$($foreignJvm.Id)").CreationDate

    Copy-Item (Join-Path $repo 'ms14.ps1') $root
    New-Item -ItemType Directory -Path (Join-Path $root 'tooling\dev_launcher'), (Join-Path $root 'run\world'), (Join-Path $root 'sibling\run\world'), (Join-Path $root 'build\moddev') -Force | Out-Null
    $gameTestArgsFile = Join-Path $root 'build\moddev\gameTestServerRunProgramArgs.txt'
    Set-Content -LiteralPath $gameTestArgsFile -Value '' -Encoding ASCII
    $gameTestOut = Join-Path $temp 'gametest-jvm.out'
    $gameTestErr = Join-Path $temp 'gametest-jvm.err'
    $gameTestCommand = '-Dms14.fixture.group=' + $fixtureGroup + ' -Dfml.modFolders=moonstation14%%C:\fixture\build\classes\java\gametest -cp "' + $fixtureClasses + '" net.neoforged.devlaunch.Main "@' + $gameTestArgsFile + '"'
    $gameTestJvm = Start-Process -FilePath $java -ArgumentList $gameTestCommand -PassThru -WindowStyle Hidden -RedirectStandardOutput $gameTestOut -RedirectStandardError $gameTestErr
    $gameTestCreated = (Get-CimInstance Win32_Process -Filter "ProcessId=$($gameTestJvm.Id)").CreationDate
    Copy-Item (Join-Path $repo 'tooling\dev_launcher\prepare.init.gradle') (Join-Path $root 'tooling\dev_launcher')
    Set-Content (Join-Path $root 'run\server.properties') "level-name=world`nserver-port=25565`nserver-ip=" -Encoding ASCII
    Set-Content (Join-Path $root 'run\world\keep.txt') 'selected-world'
    Set-Content (Join-Path $root 'sibling\run\world\keep.txt') 'sibling-world'

    $fakeGradle = @'
@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0fixture-gradlew.ps1" %*
exit /b %ERRORLEVEL%
'@
    Set-Content (Join-Path $root 'gradlew.bat') $fakeGradle -Encoding ASCII
    $fakeGradlePs = @'
$ErrorActionPreference = 'Stop'
if ($env:MS14_FIXTURE_FAIL -eq '1') { exit 17 }
$projectRoot = (Get-Location).Path
for ($i = 0; $i -lt $args.Count; $i++) {
    if ($args[$i] -eq '--project-dir' -and $i + 1 -lt $args.Count) { $projectRoot = $args[$i + 1] }
}
[IO.File]::AppendAllText($env:MS14_FIXTURE_PROJECTS, $projectRoot + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
$moddev = Join-Path $projectRoot 'build\moddev'
$run = Join-Path $projectRoot 'run'
$build = Join-Path $projectRoot 'build\dev-launcher'
New-Item -ItemType Directory -Path $moddev,$build -Force | Out-Null
    $roles = @('server','client')
    if ($args -contains '-Pms14LauncherClients=2') { $roles += 'client2' }
    $launches = @()
    foreach ($role in $roles) {
        $filename = switch ($role) { server {'runServer.cmd'} client {'runClient.cmd'} client2 {'runClient2.cmd'} }
        $script = Join-Path $moddev $filename
        $argFilename = switch ($role) { server {'serverRunProgramArgs.txt'} client {'clientRunProgramArgs.txt'} client2 {'client2RunProgramArgs.txt'} }
        $argFile = Join-Path $moddev $argFilename
        Set-Content -LiteralPath $argFile -Value '' -Encoding ASCII
        $identityArgFile = if ($role -eq 'client2') { $argFile.Replace('\','\\') } else { $argFile }
        $javaCommand = '"%MS14_FIXTURE_JAVA%" "-Dms14.fixture.group=%MS14_FIXTURE_GROUP%" "-Dfml.modFolders=moonstation14%%C:\fixture\build\classes\java\main;moonstation14%%C:\fixture\build\classes\java\gametest" -cp "%MS14_FIXTURE_CLASSES%" net.neoforged.devlaunch.Main "@' + $identityArgFile + '"'
        $lines = @('@echo off','chcp 65001 >nul',('echo {0}>>"%MS14_FIXTURE_MARKER%"' -f $role),$javaCommand)
        [IO.File]::WriteAllLines($script, $lines, [Text.UTF8Encoding]::new($false))
        $launches += @{ role=$role; script=$script; workingDirectory=$run }
}
$level = if ($env:MS14_FIXTURE_LEVEL) { $env:MS14_FIXTURE_LEVEL } else { 'world' }
@{launches=$launches;server=@{port=$env:MS14_FIXTURE_PORT;address='';levelName=$level}} | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $build 'launches.json') -Encoding UTF8
'@
    Set-Content (Join-Path $root 'fixture-gradlew.ps1') $fakeGradlePs -Encoding ASCII
    & git -C $root init -b main *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Git fixture initialization failed.' }
    & git -C $root config user.name 'Launcher Fixture'
    & git -C $root config user.email 'launcher-fixture@example.invalid'
    & git -C $root add -A
    & git -C $root commit --quiet -m 'fixture root'
    if ($LASTEXITCODE -ne 0) { throw 'Could not commit isolated Git fixture files.' }
    $agentRoot = Join-Path $temp 'MoonStation14-agent-01'
    & git -C $root worktree add --quiet -b agent/01 $agentRoot
    if ($LASTEXITCODE -ne 0) { throw 'Could not register isolated agent-01 fixture worktree.' }
    $launcherAst = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $root 'ms14.ps1'), [ref]$null, [ref]$null)
    $processHelpers = $launcherAst.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -in @('Assert-NoReparse','Get-ManagedDevProcesses') }, $true)
    Invoke-Expression (($processHelpers | ForEach-Object { $_.Extent.Text }) -join "`n")

    $marker = Join-Path $temp 'launches.txt'
    $env:MS14_FIXTURE_MARKER = $marker
    $env:MS14_FIXTURE_PORT = [string]$fixturePort
    $env:MS14_FIXTURE_JAVA = $java
    $env:MS14_FIXTURE_CLASSES = $fixtureClasses
    $env:MS14_FIXTURE_GROUP = $fixtureGroup
    $env:MS14_FIXTURE_PROJECTS = Join-Path $temp 'project-roots.txt'
    $env:MS14_FIXTURE_FAIL = '1'
    $env:MS14_FIXTURE_LEVEL = ''
    $result = Invoke-FixtureLauncher @('-Clients','2')
    if ($result.ExitCode -eq 0) { throw 'Expected preparation failure, but launcher succeeded.' }
    if (!(Test-Path (Join-Path $root 'run\world\keep.txt'))) { throw 'Preparation failure removed the selected world.' }

    $env:MS14_FIXTURE_FAIL = '0'
    $env:MS14_FIXTURE_LEVEL = 'otherworld'
    $result = Invoke-FixtureLauncher @('-Clients','2')
    if ($result.ExitCode -eq 0) { throw 'Expected level-name refusal, but launcher succeeded.' }
    if (!(Test-Path (Join-Path $root 'run\world\keep.txt'))) { throw 'Wrong configured world removed the selected world.' }

    $env:MS14_FIXTURE_LEVEL = 'world'
    $result = Invoke-FixtureLauncher @('-Clients','2')
    if ($result.ExitCode -ne 0) { Write-Host $result.Error; throw 'Fake launch did not complete successfully.' }
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        $devJvm = @(Get-FixtureJvm -Role 'server' -RootPath $root) + @(Get-FixtureJvm -Role 'client' -RootPath $root) + @(Get-FixtureJvm -Role 'client2' -RootPath $root)
        if ($devJvm.Count -lt 3) { Start-Sleep -Milliseconds 100 }
    } while ($devJvm.Count -lt 3 -and [DateTime]::UtcNow -lt $deadline)
    if ($devJvm.Count -lt 3) { throw "Expected three dummy dev JVMs, found $($devJvm.Count)." }
    if (@($devJvm | Where-Object { (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)").CommandLine -match '(?i)gametest' }).Count -ne 3) { throw 'Normal fake server/client JVMs did not carry a GameTest classpath marker.' }
    $roles = @(Get-Content $marker | Sort-Object -Unique)
    if (($roles -join ',') -ne 'client,client2,server') { throw "Expected all fake roles to launch, got: $($roles -join ',')" }
    if (Test-Path (Join-Path $root 'run\world')) { throw 'Successful launch did not remove only selected world.' }
    if (!(Test-Path (Join-Path $root 'sibling\run\world\keep.txt'))) { throw 'Launcher changed a sibling world.' }

    $oldDevPids = @($devJvm | ForEach-Object { [pscustomobject]@{ Id=[int]$_.ProcessId; Created=$_.CreationDate } })
    New-Item -ItemType Directory -Path (Join-Path $root 'run\world') -Force | Out-Null
    Set-Content (Join-Path $root 'run\world\keep.txt') 'stop-preserved'
    $env:MS14_FIXTURE_FAIL = '0'

    # Inject a failure on the third wrapper start to verify catch cleanup kills
    # first-started wrappers before rescanning their Java children.
    $global:fixtureStartCount = 0
    function global:Start-Process {
        [CmdletBinding()]
        param([string]$FilePath, [object]$ArgumentList, [string]$WorkingDirectory,
              [System.Diagnostics.ProcessWindowStyle]$WindowStyle, [switch]$PassThru)
        $global:fixtureStartCount++
        if ($global:fixtureStartCount -eq 3) { throw 'Injected third-role startup failure.' }
        Microsoft.PowerShell.Management\Start-Process @PSBoundParameters
    }
    try { $result = Invoke-FixtureLauncher @('-Clients','2') }
    finally { Remove-Item Function:Start-Process -Force -ErrorAction SilentlyContinue; Remove-Variable fixtureStartCount -Scope Global -ErrorAction SilentlyContinue }
    if ($result.ExitCode -eq 0) { throw 'Expected injected partial-start failure, but launcher succeeded.' }
    foreach ($oldPid in $oldDevPids) {
        $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($oldPid.Id)" -ErrorAction SilentlyContinue
        if ($current -and $current.CreationDate -eq $oldPid.Created) { throw "Prior dummy PID $($oldPid.Id) survived partial launch cleanup." }
    }
    $partialJava = @(Get-ManagedDevProcesses @($root))
    $partialCmd = @(Get-CimInstance Win32_Process -Filter "Name='cmd.exe'" | Where-Object { $_.CommandLine -like "*$root*build*dev-launcher*launch-*.cmd*" })
    if ($partialJava.Count -or $partialCmd.Count) { throw 'Partial-launch cleanup left a dummy JVM or launcher wrapper running.' }
    if (!(Get-CimInstance Win32_Process -Filter "ProcessId=$($foreignJvm.Id)" -ErrorAction SilentlyContinue)) { throw 'Partial-launch cleanup stopped the unrelated Java process.' }
    $activeGameTest = Get-CimInstance Win32_Process -Filter "ProcessId=$($gameTestJvm.Id)" -ErrorAction SilentlyContinue
    if (!$activeGameTest -or $activeGameTest.CreationDate -ne $gameTestCreated) { throw 'Partial-launch cleanup stopped the separately typed GameTest JVM.' }

    $result = Invoke-FixtureLauncher @('-Clients','2')
    if ($result.ExitCode -ne 0) { Write-Host $result.Error; throw 'Second launch failed to replace the previous dummy JVM family.' }
    foreach ($oldPid in $oldDevPids) {
        $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($oldPid.Id)" -ErrorAction SilentlyContinue
        if ($current -and $current.CreationDate -eq $oldPid.Created) { throw "Previous dummy dev JVM PID $($oldPid.Id) survived launch replacement: $($current.CommandLine)" }
    }

    $stateFiles = @(Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Temp\opencode') -Filter 'moonstation14-launch-*.json' -ErrorAction SilentlyContinue)
    if (!$stateFiles.Count) { throw 'Launcher did not persist wrapper ownership records.' }
    $wrapperState = Get-Content $stateFiles[0].FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $launcherAst = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $root 'ms14.ps1'), [ref]$null, [ref]$null)
    $helperDefinitions = $launcherAst.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -in @('Assert-NoReparse','Test-OwnedWrapperIdentity','Test-OwnedMonitorIdentity','Get-OwnedWrappers','Get-OwnedMonitor') }, $true)
    Invoke-Expression (($helperDefinitions | ForEach-Object { $_.Extent.Text }) -join "`n")
    $verifiedWrappers = @(Get-OwnedWrappers $stateFiles[0].FullName @($root))
    if ($verifiedWrappers.Count -ne 3 -or $wrapperState.wrappers.Count -ne 3) { throw 'PowerShell did not verify all three persisted wrappers from the UTF-8 state file.' }
    $verifiedMonitors = @(Get-OwnedMonitor $stateFiles[0].FullName)
    if ($verifiedMonitors.Count -ne 1) { throw 'PowerShell did not verify the hidden client-lifetime monitor.' }
    $wrapperPids = @($verifiedWrappers | ForEach-Object { $_.Id })
    $devJvm = @(Get-FixtureJvm -Role 'server' -RootPath $root) + @(Get-FixtureJvm -Role 'client' -RootPath $root) + @(Get-FixtureJvm -Role 'client2' -RootPath $root)
    $visibleWrappers = @($wrapperPids | ForEach-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue } | Where-Object { $_.MainWindowHandle -ne 0 })
    if ($visibleWrappers.Count) { throw 'Launcher wrapper unexpectedly owns a visible window.' }
    $stopPids = @($wrapperPids + @($devJvm | ForEach-Object { [int]$_.ProcessId }) + $verifiedMonitors[0].Id)
    New-Item -ItemType Directory -Path (Join-Path $root 'run\world') -Force | Out-Null
    Set-Content (Join-Path $root 'run\world\keep.txt') 'stop-preserved'
    $result = Invoke-FixtureLauncher @('-Stop')
    if ($result.ExitCode -ne 0 -or !(Test-Path (Join-Path $root 'run\world\keep.txt'))) { Write-Host $result.Error; throw '-Stop unexpectedly required preparation or changed the world.' }
    foreach ($stopPid in $stopPids) {
        if (Get-CimInstance Win32_Process -Filter "ProcessId=$stopPid" -ErrorAction SilentlyContinue) { throw "Owned wrapper or dev JVM PID $stopPid survived -Stop." }
    }
    if (!(Get-CimInstance Win32_Process -Filter "ProcessId=$($foreignJvm.Id)" -ErrorAction SilentlyContinue)) { throw 'Foreign dummy Java process was stopped.' }
    $activeGameTest = Get-CimInstance Win32_Process -Filter "ProcessId=$($gameTestJvm.Id)" -ErrorAction SilentlyContinue
    if (!$activeGameTest -or $activeGameTest.CreationDate -ne $gameTestCreated) { throw '-Stop stopped the GameTestServer argument-file process.' }

    # Closing the only client ends its wrapper and automatically shuts down its server.
    $result = Invoke-FixtureLauncher @('-Clients','1')
    if ($result.ExitCode -ne 0) { throw 'Could not start one-client lifecycle fixture.' }
    $state = Get-Content $stateFiles[0].FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $oneClient = @(Get-FixtureJvm -Role 'client' -RootPath $root); $oneServer = @(Get-FixtureJvm -Role 'server' -RootPath $root)
    if ($oneClient.Count -ne 1 -or $oneServer.Count -ne 1) {
        $debugJava = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match [regex]::Escape($fixtureGroup) } | ForEach-Object { "$($_.ProcessId):$($_.CommandLine.Substring([Math]::Max(0,$_.CommandLine.Length-100)))" })
        $debugWrappers = @($state.wrappers | ForEach-Object { $p=Get-CimInstance Win32_Process -Filter "ProcessId=$($_.id)" -ErrorAction SilentlyContinue; "$($_.role)=$([bool]$p)" })
        $debugManaged = @(Get-ManagedDevProcesses @($root) | ForEach-Object { "$($_.Role)=$($_.Id)" })
        throw "One-client fixture role counts client=$($oneClient.Count), server=$($oneServer.Count); JVMs=$($debugJava -join ';'); managed=$($debugManaged -join ','); wrappers=$($debugWrappers -join ',')."
    }
    $oneClientIdentity = [pscustomobject]@{ Id=[int]$oneClient[0].ProcessId; Created=$oneClient[0].CreationDate }
    $oneServerIdentity = [pscustomobject]@{ Id=[int]$oneServer[0].ProcessId; Created=$oneServer[0].CreationDate }
    $clientNow = Get-CimInstance Win32_Process -Filter "ProcessId=$($oneClientIdentity.Id)"
    if ($clientNow.CreationDate -eq $oneClientIdentity.Created -and $clientNow.CommandLine -match 'clientRunProgramArgs\.txt') { Stop-Process -Id $oneClientIdentity.Id -Force }
    Wait-FixtureProcessExit $oneServerIdentity 'Auto-stopped one-client server'
    Wait-FixtureProcessExit ([pscustomobject]@{ Id=[int]$state.monitor.id; Created=[Management.ManagementDateTimeConverter]::ToDateTime([string]$state.monitor.created) }) 'One-client lifetime monitor'

    # If the launch CMD wrapper exits unexpectedly, the client's final exit still
    # shuts down only the server JVM captured by that wrapper's PID lineage.
    $result = Invoke-FixtureLauncher @('-Clients','1')
    if ($result.ExitCode -ne 0) { throw 'Could not start orphan-wrapper lifecycle fixture.' }
    $state = Get-Content $stateFiles[0].FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $orphanClient = @(Get-FixtureJvm -Role 'client' -RootPath $root)
    $orphanServer = @(Get-FixtureJvm -Role 'server' -RootPath $root)
    if ($orphanClient.Count -ne 1 -or $orphanServer.Count -ne 1) { throw 'Could not identify orphan-wrapper lifecycle JVMs.' }
    $orphanServerIdentity = [pscustomobject]@{ Id=[int]$orphanServer[0].Id; Created=$orphanServer[0].Created }
    $serverWrapperRecord = @($state.wrappers | Where-Object { $_.role -eq 'server' })[0]
    $serverWrapperNow = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$serverWrapperRecord.id)" -ErrorAction SilentlyContinue
    if (!(Test-OwnedWrapperIdentity $serverWrapperNow $serverWrapperRecord)) { throw 'Could not verify server wrapper for orphan-wrapper fixture.' }
    Stop-Process -Id ([int]$serverWrapperRecord.id) -Force
    Wait-FixtureProcessExit ([pscustomobject]@{ Id=[int]$serverWrapperRecord.id; Created=[Management.ManagementDateTimeConverter]::ToDateTime([string]$serverWrapperRecord.created) }) 'Unexpectedly exited server wrapper'
    $clientNow = Get-CimInstance Win32_Process -Filter "ProcessId=$($orphanClient[0].Id)" -ErrorAction SilentlyContinue
    if ($clientNow -and $clientNow.CreationDate -eq $orphanClient[0].Created -and $clientNow.CommandLine -match 'clientRunProgramArgs\.txt') { Stop-Process -Id $orphanClient[0].Id -Force }
    Wait-FixtureProcessExit $orphanServerIdentity 'Auto-stopped orphan-wrapper server JVM'
    Wait-FixtureProcessExit ([pscustomobject]@{ Id=[int]$state.monitor.id; Created=[Management.ManagementDateTimeConverter]::ToDateTime([string]$state.monitor.created) }) 'Orphan-wrapper lifetime monitor'

    # With two clients, closing the first leaves the server up until the last exits.
    $result = Invoke-FixtureLauncher @('-Clients','2')
    if ($result.ExitCode -ne 0) { throw 'Could not start two-client lifecycle fixture.' }
    $state = Get-Content $stateFiles[0].FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $twoClients = @(Get-FixtureJvm -Role 'client' -RootPath $root); $secondClient = @(Get-FixtureJvm -Role 'client2' -RootPath $root); $twoServer = @(Get-FixtureJvm -Role 'server' -RootPath $root)
    if ($twoClients.Count -ne 1 -or $secondClient.Count -ne 1 -or $twoServer.Count -ne 1) { throw 'Two-client lifecycle fixture did not start the expected JVM group.' }
    $twoServerIdentity = [pscustomobject]@{ Id=[int]$twoServer[0].ProcessId; Created=$twoServer[0].CreationDate }
    $client1 = [pscustomobject]@{ Id=[int]$twoClients[0].ProcessId; Created=$twoClients[0].CreationDate }
    $client2 = [pscustomobject]@{ Id=[int]$secondClient[0].ProcessId; Created=$secondClient[0].CreationDate }
    $clientNow = Get-CimInstance Win32_Process -Filter "ProcessId=$($client1.Id)"
    if ($clientNow.CreationDate -eq $client1.Created -and $clientNow.CommandLine -match 'clientRunProgramArgs\.txt') { Stop-Process -Id $client1.Id -Force }
    Wait-FixtureProcessExit $client1 'First client'
    Start-Sleep -Milliseconds 700
    $stillRunningServer = Get-CimInstance Win32_Process -Filter "ProcessId=$($twoServerIdentity.Id)" -ErrorAction SilentlyContinue
    $stillRunningMonitor = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$state.monitor.id)" -ErrorAction SilentlyContinue
    if (!$stillRunningServer -or $stillRunningServer.CreationDate -ne $twoServerIdentity.Created -or !$stillRunningMonitor) { throw 'Server shut down before the final client exited.' }
    $clientNow = Get-CimInstance Win32_Process -Filter "ProcessId=$($client2.Id)"
    if ($clientNow.CreationDate -eq $client2.Created -and $clientNow.CommandLine -match 'client2RunProgramArgs\.txt') { Stop-Process -Id $client2.Id -Force }
    Wait-FixtureProcessExit $twoServerIdentity 'Auto-stopped two-client server after final client exit'
    Wait-FixtureProcessExit ([pscustomobject]@{ Id=[int]$state.monitor.id; Created=[Management.ManagementDateTimeConverter]::ToDateTime([string]$state.monitor.created) }) 'Two-client lifetime monitor'

    # Replacing an active group terminates its old monitor; it must not stop the new server.
    $result = Invoke-FixtureLauncher @('-Clients','1')
    if ($result.ExitCode -ne 0) { throw 'Could not start server-replacement fixture.' }
    $oldState = Get-Content $stateFiles[0].FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $oldMonitor = [pscustomobject]@{ Id=[int]$oldState.monitor.id; Created=[Management.ManagementDateTimeConverter]::ToDateTime([string]$oldState.monitor.created) }
    $result = Invoke-FixtureLauncher @('-Clients','1')
    if ($result.ExitCode -ne 0) { throw 'Could not replace active client/server group.' }
    Wait-FixtureProcessExit $oldMonitor 'Replaced lifecycle monitor'
    $newState = Get-Content $stateFiles[0].FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $newServer = @(Get-FixtureJvm -Role 'server' -RootPath $root)
    if ($newState.groupId -ceq $oldState.groupId -or $newServer.Count -ne 1) { throw 'Old monitor interfered with the replacement server group.' }
    $result = Invoke-FixtureLauncher @('-Stop')
    if ($result.ExitCode -ne 0) { throw 'Could not stop replacement lifecycle fixture.' }
    if (!(Get-CimInstance Win32_Process -Filter "ProcessId=$($foreignJvm.Id)" -ErrorAction SilentlyContinue) -or !(Get-CimInstance Win32_Process -Filter "ProcessId=$($gameTestJvm.Id)" -ErrorAction SilentlyContinue)) { throw 'Lifecycle shutdown stopped an unrelated/GameTest JVM.' }

    # Exercise selection from the Main caller directory: the fake Gradle wrapper
    # uses the explicit --project-dir, not its own location, as Gradle does.
    $mainKeep = Join-Path $root 'run\world\keep.txt'
    $agentKeep = Join-Path $agentRoot 'run\world\keep.txt'
    New-Item -ItemType Directory -Path (Join-Path $root 'run\world'), (Join-Path $agentRoot 'run\world') -Force | Out-Null
    Set-Content $mainKeep 'main-world-must-survive'
    Set-Content $agentKeep 'agent-world-must-reset'
    Remove-Item $marker -Force -ErrorAction SilentlyContinue
    Remove-Item $env:MS14_FIXTURE_PROJECTS -Force -ErrorAction SilentlyContinue
    $callerDirectory = $PWD.Path
    Push-Location $root
    try { $result = Invoke-FixtureLauncher @('-Worktree','01','-Clients','1') }
    finally { Pop-Location }
    if ($PWD.Path -ne $callerDirectory) { throw 'Launcher changed its caller working directory.' }
    if ($result.ExitCode -ne 0) { Write-Host $result.Error; throw 'Registered agent-01 launch failed from the Main caller directory.' }
    $preparedRoots = @(Get-Content $env:MS14_FIXTURE_PROJECTS -Encoding UTF8)
    if ($preparedRoots.Count -ne 1 -or [IO.Path]::GetFullPath($preparedRoots[0]) -ne [IO.Path]::GetFullPath($agentRoot)) { throw "Gradle prepared the wrong project root: $($preparedRoots -join ', ')" }
    if (!(Test-Path (Join-Path $agentRoot 'build\dev-launcher\launches.json'))) { throw 'Selected worktree did not receive its launch manifest.' }
    if (!(Test-Path $mainKeep) -or (Test-Path $agentKeep)) { throw 'Agent-01 launch did not reset only its selected world.' }
    $agentRoles = @(Get-Content $marker | Sort-Object -Unique)
    if (($agentRoles -join ',') -ne 'client,server') { throw "Agent-01 -Clients 1 launched unexpected roles: $($agentRoles -join ',')" }
    $result = Invoke-FixtureLauncher @('-Stop')
    if ($result.ExitCode -ne 0) { throw 'Could not stop the isolated agent-01 fixture process family.' }
    Write-Host 'Launcher fixtures passed: failure guards preserve worlds; Unicode/spaced paths launch all roles; restart and -Stop kill only tracked wrappers and typed family JVMs.'
} finally {
    if (Test-Path (Join-Path $root 'ms14.ps1')) { try { & (Join-Path $root 'ms14.ps1') -Stop *> $null } catch { } }
    if ($foreignJvm) {
        $currentForeign = Get-CimInstance Win32_Process -Filter "ProcessId=$($foreignJvm.Id)" -ErrorAction SilentlyContinue
        if ($currentForeign -and $currentForeign.CreationDate -eq $foreignCreated -and $currentForeign.Name -eq 'java.exe' -and $currentForeign.CommandLine -match [regex]::Escape($fixtureGroup)) {
            Stop-Process -Id $foreignJvm.Id -Force -ErrorAction SilentlyContinue
        }
    }
    if ($gameTestJvm) {
        $currentGameTest = Get-CimInstance Win32_Process -Filter "ProcessId=$($gameTestJvm.Id)" -ErrorAction SilentlyContinue
        if ($currentGameTest -and $currentGameTest.CreationDate -eq $gameTestCreated -and $currentGameTest.Name -eq 'java.exe' -and $currentGameTest.CommandLine -like "*$gameTestArgsFile*") {
            Stop-Process -Id $gameTestJvm.Id -Force -ErrorAction SilentlyContinue
        }
    }
    $env:LOCALAPPDATA = $oldLocalAppData
    Remove-Item Env:MS14_FIXTURE_MARKER, Env:MS14_FIXTURE_PORT, Env:MS14_FIXTURE_FAIL, Env:MS14_FIXTURE_LEVEL, Env:MS14_FIXTURE_JAVA, Env:MS14_FIXTURE_CLASSES, Env:MS14_FIXTURE_PROJECTS, Env:MS14_FIXTURE_GROUP -ErrorAction SilentlyContinue
    Pop-Location
    if ($PWD.Path -ne $outerCallerDirectory) { throw 'Launcher fixture did not restore the original caller directory.' }
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
