[CmdletBinding()]
param(
    [ValidateSet('main','01','02','03','04','05')][string]$Worktree = 'main',
    [ValidateSet(1,2)][int]$Clients = 1,
    [switch]$Stop,
    [string]$MonitorSessionFile
)
$ErrorActionPreference = 'Stop'

function Get-CanonicalPath([string]$Path) {
    [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $Path).Path).TrimEnd('\')
}
function Assert-NoReparse([string]$Path, [switch]$Required) {
    $full = [IO.Path]::GetFullPath($Path)
    $driveRoot = [IO.Path]::GetPathRoot($full)
    $current = $driveRoot
    foreach ($part in $full.Substring($driveRoot.Length).Split([IO.Path]::DirectorySeparatorChar, [StringSplitOptions]::RemoveEmptyEntries)) {
        $current = Join-Path $current $part
        if (!(Test-Path -LiteralPath $current)) { continue }
        $item = Get-Item -LiteralPath $current -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing path through reparse point: $current" }
    }
    if ($Required -and !(Test-Path -LiteralPath $full)) { throw "Required path missing: $full" }
}
function Get-ManagedDevProcesses($Roots) {
    foreach ($proc in Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'") {
        $cmd = [string]$proc.CommandLine
        if ($cmd -notmatch '(?i)net\.neoforged\.devlaunch\.Main') { continue }
        foreach ($root in $Roots) {
            Assert-NoReparse (Join-Path $root 'build')
            $moddev = Join-Path $root 'build\moddev'
            Assert-NoReparse $moddev
            # Real ModDev process arguments live in these role-specific files; do not
            # infer ownership from a port, project classpath, or a Gradle process.
            $role = $null
            foreach ($candidate in @(
                @{ Name='serverRunProgramArgs.txt'; Role='server' },
                @{ Name='clientRunProgramArgs.txt'; Role='client' },
                @{ Name='client2RunProgramArgs.txt'; Role='client2' }
            )) {
                $argFile = Join-Path $moddev $candidate.Name
                if (!(Test-Path -LiteralPath $argFile)) { continue }
                Assert-NoReparse $argFile
                $normalizedCommand = ($cmd.Replace('/', '\')) -replace '\\{2,}', '\'
                $pathPattern = [regex]::Escape($argFile.Replace('/', '\'))
                $argumentPattern = '(?i)(?:^|\s|")@?"?' + $pathPattern + '"?(?=$|\s|")'
                if ($normalizedCommand -match $argumentPattern) { $role = $candidate.Role; break }
            }
            if ($role) {
                [pscustomobject]@{ Id=[int]$proc.ProcessId; Created=$proc.CreationDate; Role=$role; Root=$root; ParentId=[int]$proc.ParentProcessId }
                break
            }
        }
    }
}
function Stop-VerifiedProcesses($Processes) {
    foreach ($entry in $Processes) {
        $now = Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.Id)" -ErrorAction SilentlyContinue
        if (!$now -or $now.CreationDate -ne $entry.Created) { continue }
        Stop-Process -Id $entry.Id -Force -ErrorAction Stop
    }
    foreach ($entry in $Processes) {
        $deadline = [DateTime]::UtcNow.AddSeconds(10)
        do {
            $now = Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.Id)" -ErrorAction SilentlyContinue
            if (!$now -or $now.CreationDate -ne $entry.Created) { break }
            Start-Sleep -Milliseconds 150
        } while ([DateTime]::UtcNow -lt $deadline)
        $now = Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.Id)" -ErrorAction SilentlyContinue
        if ($now -and $now.CreationDate -eq $entry.Created) { throw "Could not stop verified development $($entry.Role) PID $($entry.Id). No world was removed." }
    }
}
function Test-OwnedWrapperIdentity($Process, $Record) {
    if (!$Process -or $Process.Name -ine 'cmd.exe' -or $Process.CreationDate -ne [Management.ManagementDateTimeConverter]::ToDateTime([string]$Record.created)) { return $false }
    $cmd = ([string]$Process.CommandLine).Replace('/', '\')
    $needle = [IO.Path]::GetFullPath([string]$Record.path).Replace('/', '\')
    $at = $cmd.IndexOf($needle, [StringComparison]::OrdinalIgnoreCase)
    if ($at -lt 0) { return $false }
    $before = if ($at -eq 0) { ' ' } else { $cmd[$at - 1] }
    $afterIndex = $at + $needle.Length
    $after = if ($afterIndex -ge $cmd.Length) { ' ' } else { $cmd[$afterIndex] }
    return (($before -eq ' ' -or $before -eq '"') -and ($after -eq ' ' -or $after -eq '"'))
}
function Test-OwnedMonitorIdentity($Process, $Record) {
    if (!$Process -or $Process.Name -notin @('powershell.exe','pwsh.exe') -or $Process.CreationDate -ne [Management.ManagementDateTimeConverter]::ToDateTime([string]$Record.created)) { return $false }
    $cmd = ([string]$Process.CommandLine).Replace('/', '\')
    $needle = [IO.Path]::GetFullPath([string]$Record.sessionFile).Replace('/', '\')
    return ($cmd.IndexOf('-MonitorSessionFile', [StringComparison]::OrdinalIgnoreCase) -ge 0 -and $cmd.IndexOf($needle, [StringComparison]::OrdinalIgnoreCase) -ge 0)
}
function Get-OwnedWrappers($StatePath, $Roots) {
    if (!(Test-Path -LiteralPath $StatePath)) { return @() }
    $state = Get-Content -LiteralPath $StatePath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($state.version -notin @(1,2) -or !$state.wrappers) { throw "Invalid launcher ownership state: $StatePath" }
    $owned = @()
    foreach ($record in $state.wrappers) {
        $path = [IO.Path]::GetFullPath([string]$record.path)
        $root = @($Roots | Where-Object {
            $allowed = [IO.Path]::GetFullPath((Join-Path $_ 'build\dev-launcher')).TrimEnd('\') + '\'
            $path.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)
        } | Select-Object -First 1)
        if (!$root.Count -or [IO.Path]::GetFileName($path) -notmatch '^launch-(server|client|client2)\.cmd$' -or !(Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        Assert-NoReparse $path
        $now = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$record.id)" -ErrorAction SilentlyContinue
        if (Test-OwnedWrapperIdentity $now $record) {
            $owned += [pscustomobject]@{ Id=[int]$now.ProcessId; Created=$now.CreationDate; Role=[string]$record.role; Root=[string]$root[0] }
        }
    }
    return $owned
}
function Get-OwnedMonitor([string]$StatePath) {
    if (!(Test-Path -LiteralPath $StatePath)) { return @() }
    $state = Get-Content -LiteralPath $StatePath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($state.version -ne 2 -or !$state.monitor) { return @() }
    $record = $state.monitor
    $now = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$record.id)" -ErrorAction SilentlyContinue
    if (Test-OwnedMonitorIdentity $now $record) {
        return @([pscustomobject]@{ Id=[int]$now.ProcessId; Created=$now.CreationDate; Role='lifecycle monitor'; SessionFile=[string]$record.sessionFile; GroupId=[string]$record.groupId })
    }
    return @()
}
function Write-WrapperState([string]$StatePath, $Wrappers, [string]$GroupId, $Monitor = $null) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $StatePath) -Force | Out-Null
    $payload = @{ version=2; groupId=$GroupId; wrappers=@($Wrappers); monitor=$Monitor } | ConvertTo-Json -Depth 5
    $tempPath = "$StatePath.$PID.tmp"
    [IO.File]::WriteAllText($tempPath, $payload, [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $tempPath -Destination $StatePath -Force
}
function Wait-OwnedWrapperExit($Record) {
    $current = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$Record.id)" -ErrorAction SilentlyContinue
    if (!(Test-OwnedWrapperIdentity $current $Record)) { return }
    try {
        $process = Get-Process -Id ([int]$Record.id) -ErrorAction Stop
        $process.WaitForExit()
    } catch { }
}
function Invoke-LifecycleMonitor([string]$SessionFile) {
    Assert-NoReparse $SessionFile -Required
    $session = Get-Content -LiteralPath $SessionFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($session.version -ne 1 -or !$session.groupId -or !$session.clientWrappers -or !$session.serverWrapper) { throw "Invalid lifecycle session: $SessionFile" }
    foreach ($client in $session.clientWrappers) { Wait-OwnedWrapperExit $client }

    $mutex = [Threading.Mutex]::new($false, [string]$session.mutexName)
    $acquired = $false
    try {
        try { $mutex.WaitOne() | Out-Null; $acquired = $true }
        catch [Threading.AbandonedMutexException] { $acquired = $true }
        $state = Get-Content -LiteralPath $session.statePath -Raw -Encoding UTF8 -ErrorAction SilentlyContinue | ConvertFrom-Json
        $self = Get-CimInstance Win32_Process -Filter "ProcessId=$PID" -ErrorAction SilentlyContinue
        if (!$state -or $state.version -ne 2 -or $state.groupId -cne $session.groupId -or
            !$state.monitor -or [int]$state.monitor.id -ne $PID -or
            $state.monitor.sessionFile -cne $SessionFile -or !$self -or
            $self.CreationDate -ne [Management.ManagementDateTimeConverter]::ToDateTime([string]$state.monitor.created) -or
            ([string]$self.CommandLine).IndexOf($SessionFile, [StringComparison]::OrdinalIgnoreCase) -lt 0) { return }

        $serverWrapperRecord = $session.serverWrapper
        $serverWrapper = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$serverWrapperRecord.id)" -ErrorAction SilentlyContinue
        $serverWrapperValid = Test-OwnedWrapperIdentity $serverWrapper $serverWrapperRecord
        $serverCreated = [Management.ManagementDateTimeConverter]::ToDateTime([string]$serverWrapperRecord.created)
        if ($serverWrapperValid) {
            Stop-VerifiedProcesses @([pscustomobject]@{ Id=[int]$serverWrapper.ProcessId; Created=$serverWrapper.CreationDate; Role='server wrapper' })
        }

        # The launch-script wrapper is the verified parent of this session's server JVM.
        # Restrict shutdown to that PID lineage and the exact selected server args file.
        $serverJvm = @(Get-ManagedDevProcesses @([string]$session.root) | Where-Object {
            $_.Role -eq 'server' -and $_.ParentId -eq [int]$serverWrapperRecord.id -and $_.Created -ge $serverCreated
        })
        Stop-VerifiedProcesses $serverJvm
        $afterStop = Get-Content -LiteralPath $session.statePath -Raw -Encoding UTF8 -ErrorAction SilentlyContinue | ConvertFrom-Json
        if ($afterStop -and $afterStop.version -eq 2 -and $afterStop.groupId -ceq $session.groupId -and
            $afterStop.monitor -and [int]$afterStop.monitor.id -eq $PID) {
            Remove-Item -LiteralPath $session.statePath -Force -ErrorAction SilentlyContinue
        }
    } finally {
        if ($acquired) { $mutex.ReleaseMutex() }
        $mutex.Dispose()
        Remove-Item -LiteralPath $SessionFile -Force -ErrorAction SilentlyContinue
    }
}
function Assert-WorldTarget([string]$Root) {
    $run = Join-Path $Root 'run'
    $world = Join-Path $run 'world'
    Assert-NoReparse $Root -Required
    Assert-NoReparse $run
    Assert-NoReparse $world
    if (Test-Path -LiteralPath $world) {
        $stack = [Collections.Generic.Stack[string]]::new(); $stack.Push($world)
        while ($stack.Count) {
            $dir = $stack.Pop()
            foreach ($item in Get-ChildItem -LiteralPath $dir -Force) {
                if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing world containing reparse point: $($item.FullName)" }
                if ($item.PSIsContainer) { $stack.Push($item.FullName) }
            }
        }
    }
    return $world
}

if ($MonitorSessionFile) {
    Invoke-LifecycleMonitor ([IO.Path]::GetFullPath($MonitorSessionFile))
    return
}

$main = Get-CanonicalPath $PSScriptRoot
$previousOutputEncoding = [Console]::OutputEncoding
try {
    [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
    $gitLines = @(& git -C $main -c core.quotepath=false worktree list --porcelain)
    if ($LASTEXITCODE -ne 0) { throw 'Could not enumerate registered Git worktrees.' }
    $commonDir = (& git -C $main rev-parse --git-common-dir).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the Git common directory.' }
} finally { [Console]::OutputEncoding = $previousOutputEncoding }
$treePaths = @{}
$pending = $null
foreach ($line in $gitLines) {
    if ($line -match '^worktree (.+)$') {
        $path = [IO.Path]::GetFullPath($Matches[1]).TrimEnd('\')
        $leaf = Split-Path $path -Leaf
        if ($leaf -eq 'MoonStation14') { $treePaths['main'] = $path }
        elseif ($leaf -match '^MoonStation14-agent-(0[1-5])$') { $treePaths[$Matches[1]] = $path }
    }
}
if (!$treePaths.ContainsKey($Worktree)) { throw "Worktree '$Worktree' is not registered in this repository family." }
$selected = Get-CanonicalPath $treePaths[$Worktree]
$allRoots = @($treePaths.Values | ForEach-Object { Get-CanonicalPath $_ })
if (![IO.Path]::IsPathRooted($commonDir)) { $commonDir = Join-Path $main $commonDir }
$sha = [Security.Cryptography.SHA256]::Create()
try { $hash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes([IO.Path]::GetFullPath($commonDir).ToLowerInvariant()))).Replace('-', '')).Substring(0,16) }
finally { $sha.Dispose() }
$mutex = [Threading.Mutex]::new($false, "Local\MoonStation14-launch-$hash")
$stateRoot = if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Temp\opencode' } else { Join-Path $env:TEMP 'opencode' }
$statePath = Join-Path $stateRoot "moonstation14-launch-$hash.json"
if (!$mutex.WaitOne([TimeSpan]::FromSeconds(30))) { throw 'Another family launcher operation is active; retry shortly.' }
try {
    $ownedWrappers = @(Get-OwnedWrappers $statePath $allRoots)
    $ownedMonitor = @(Get-OwnedMonitor $statePath)
    if ($Stop) {
        Stop-VerifiedProcesses $ownedMonitor
        Stop-VerifiedProcesses $ownedWrappers
        $active = @(Get-ManagedDevProcesses $allRoots)
        Stop-VerifiedProcesses $active
        foreach ($monitor in $ownedMonitor) { Remove-Item -LiteralPath $monitor.SessionFile -Force -ErrorAction SilentlyContinue }
        Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped $($active.Count) verified development server/client JVM(s), $($ownedWrappers.Count) launcher wrapper(s), and $($ownedMonitor.Count) lifecycle monitor(s)."
        return
    }

    # Fully prepare before stopping anything or deleting the selected disposable world.
    $init = Join-Path $PSScriptRoot 'tooling\dev_launcher\prepare.init.gradle'
    if (!(Test-Path -LiteralPath $init)) { throw "Launcher preparation init script missing: $init" }
    $gradleArgs = @('--project-dir', $selected, '-I', $init, 'ms14PrepareLaunch', "-Pms14LauncherClients=$Clients", '--no-daemon', '--console=plain')
    $prepareOutput = @(& (Join-Path $selected 'gradlew.bat') @gradleArgs 2>&1)
    if ($LASTEXITCODE -ne 0) { $prepareOutput | Select-Object -Last 30 | ForEach-Object { Write-Host $_ }; throw 'ModDev launch preparation failed; existing processes and worlds were left untouched.' }
    $manifestPath = Join-Path $selected 'build\dev-launcher\launches.json'
    Assert-NoReparse $manifestPath -Required
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $expectedRoles = if ($Clients -eq 2) { @('server','client','client2') } else { @('server','client') }
    if (!$manifest.launches -or !$manifest.server -or $manifest.launches.Count -ne $expectedRoles.Count) { throw 'Launch manifest has an unexpected shape or role count; refusing destructive actions.' }
    $launches = @()
    foreach ($role in $expectedRoles) {
        $entry = @($manifest.launches | Where-Object { $_.role -ceq $role })
        if ($entry.Count -ne 1) { throw "Launch manifest is missing unique role '$role'." }
        $script = [IO.Path]::GetFullPath([string]$entry[0].script)
        $moddevRoot = [IO.Path]::GetFullPath((Join-Path $selected 'build\moddev')).TrimEnd('\') + '\'
        $workingDirectory = [IO.Path]::GetFullPath([string]$entry[0].workingDirectory)
        $selectedPrefix = $selected.TrimEnd('\') + '\'
        if (!$script.StartsWith($moddevRoot, [StringComparison]::OrdinalIgnoreCase) -or !(Test-Path -LiteralPath $script -PathType Leaf)) { throw "Invalid ModDev launch script for $role; refusing destructive actions." }
        Assert-NoReparse $script -Required
        if (!$workingDirectory.StartsWith($selectedPrefix, [StringComparison]::OrdinalIgnoreCase) -or !(Test-Path -LiteralPath $workingDirectory -PathType Container)) { throw "Invalid ModDev working directory for $role; refusing destructive actions." }
        Assert-NoReparse $workingDirectory -Required
        $launches += [pscustomobject]@{ Role=$role; Script=$script; WorkingDirectory=$workingDirectory }
    }
    $expectedServerRun = [IO.Path]::GetFullPath((Join-Path $selected 'run')).TrimEnd('\')
    if (![string]::Equals($launches[0].WorkingDirectory.TrimEnd('\'), $expectedServerRun, [StringComparison]::OrdinalIgnoreCase)) {
        throw "ModDev server working directory is not the selected worktree's run directory; refusing to reset the wrong world."
    }
    if ([string]$manifest.server.levelName -cne 'world') { throw "run/server.properties level-name is '$($manifest.server.levelName)', not world; refusing deletion." }
    $port = 0
    if (![int]::TryParse([string]$manifest.server.port, [ref]$port) -or $port -lt 1 -or $port -gt 65535) { throw 'Invalid configured server-port in run/server.properties; refusing deletion.' }
    $bindAddress = [string]$manifest.server.address
    $world = Assert-WorldTarget $selected
    # Preparation can take minutes; include family IDE-launched JVMs started since it began.
    Stop-VerifiedProcesses $ownedMonitor
    Stop-VerifiedProcesses $ownedWrappers
    $active = @(Get-ManagedDevProcesses $allRoots)
    Stop-VerifiedProcesses $active
    foreach ($monitor in $ownedMonitor) { Remove-Item -LiteralPath $monitor.SessionFile -Force -ErrorAction SilentlyContinue }
    Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue

    # Never erase a world while another process holds the server port. This is a
    # refusal check (not ownership inference or foreign-process cleanup).
    $listening = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
    if ($bindAddress) {
        try { $resolvedBindAddresses = @([Net.Dns]::GetHostAddresses($bindAddress) | ForEach-Object { $_.ToString() }) }
        catch { throw "Cannot resolve configured server-ip '$bindAddress'; refusing to remove the world." }
        $listening = @($listening | Where-Object { $resolvedBindAddresses -contains $_.LocalAddress -or $_.LocalAddress -eq '0.0.0.0' -or $_.LocalAddress -eq '::' })
    }
    if ($listening.Count) { throw "Server port $port is already occupied by an unverified process; selected world was not removed." }

    if (Test-Path -LiteralPath $world) { Remove-Item -LiteralPath $world -Recurse -Force }
    $beforeLaunch = @(Get-ManagedDevProcesses $allRoots | ForEach-Object { [pscustomobject]@{ Id=$_.Id; Created=$_.Created } })
    $launchId = [Guid]::NewGuid().ToString('N')
    $newWrappers = @()
    $monitorRecord = $null
    $sessionFile = $null
    try {
        foreach ($launch in $launches) {
            if (!(Test-Path -LiteralPath $launch.WorkingDirectory -PathType Container)) { throw "ModDev working directory missing: $($launch.WorkingDirectory)" }
            $logRoot = Join-Path $selected 'run\logs'
            New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
            $log = Join-Path $logRoot ("dev-launcher-$($launch.Role).log")
            $wrapper = Join-Path (Split-Path $manifestPath -Parent) ("launch-$($launch.Role).cmd")
            $wrapperLines = @('@echo off', 'chcp 65001 >nul', ('call "{0}" > "{1}" 2>&1' -f $launch.Script, $log), 'exit /b %ERRORLEVEL%')
            [IO.File]::WriteAllLines($wrapper, $wrapperLines, [Text.UTF8Encoding]::new($false))
            $startArgument = '/d /s /c ""' + $wrapper + '""'
            $process = Start-Process -FilePath $env:ComSpec -ArgumentList $startArgument -WorkingDirectory $launch.WorkingDirectory -WindowStyle Hidden -PassThru
            $wrapperRecord = $null
            for ($attempt = 0; $attempt -lt 20 -and !$wrapperRecord; $attempt++) {
                $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)" -ErrorAction SilentlyContinue
                if ($current -and $current.Name -ieq 'cmd.exe') {
                    $wrapperRecord = @{ id=[int]$current.ProcessId; created=[Management.ManagementDateTimeConverter]::ToDmtfDateTime($current.CreationDate); path=$wrapper; role=$launch.Role }
                } else { Start-Sleep -Milliseconds 50 }
            }
            if (!$wrapperRecord) { throw "Could not verify launcher wrapper process for $($launch.Role)." }
            $newWrappers += $wrapperRecord
            Write-WrapperState $statePath $newWrappers $launchId
        }

        $serverWrapper = @($newWrappers | Where-Object { $_.role -eq 'server' })[0]
        $clientWrappers = @($newWrappers | Where-Object { $_.role -eq 'client' -or $_.role -eq 'client2' })
        $sessionFile = Join-Path $stateRoot "moonstation14-session-$hash-$launchId.json"
        $session = [ordered]@{
            version=1; groupId=$launchId; statePath=$statePath
            mutexName="Local\MoonStation14-launch-$hash"; root=$selected
            serverWrapper=$serverWrapper; clientWrappers=$clientWrappers
        }
        [IO.File]::WriteAllText($sessionFile, ($session | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
        $monitorOut = Join-Path $logRoot "dev-launcher-lifecycle-$launchId.out.log"
        $monitorErr = Join-Path $logRoot "dev-launcher-lifecycle-$launchId.err.log"
        $scriptPath = Join-Path $PSScriptRoot 'ms14.ps1'
        $engine = (Get-Process -Id $PID).Path
        $monitorArgs = '-NoProfile -NonInteractive -File "' + $scriptPath + '" -MonitorSessionFile "' + $sessionFile + '"'
        $monitorProcess = Start-Process -FilePath $engine -ArgumentList $monitorArgs -WorkingDirectory $selected -WindowStyle Hidden -PassThru -RedirectStandardOutput $monitorOut -RedirectStandardError $monitorErr
        $monitorCim = $null
        for ($attempt = 0; $attempt -lt 20 -and !$monitorCim; $attempt++) {
            $candidate = Get-CimInstance Win32_Process -Filter "ProcessId=$($monitorProcess.Id)" -ErrorAction SilentlyContinue
            if ($candidate -and $candidate.Name -match '^(?i:powershell|pwsh)\.exe$' -and ([string]$candidate.CommandLine).IndexOf($sessionFile, [StringComparison]::OrdinalIgnoreCase) -ge 0) { $monitorCim = $candidate }
            else { Start-Sleep -Milliseconds 50 }
        }
        if (!$monitorCim) { throw 'Could not verify lifecycle monitor process.' }
        $monitorRecord = @{ id=[int]$monitorCim.ProcessId; created=[Management.ManagementDateTimeConverter]::ToDmtfDateTime($monitorCim.CreationDate); sessionFile=$sessionFile; groupId=$launchId }
        Write-WrapperState $statePath $newWrappers $launchId $monitorRecord
    } catch {
        if ($monitorRecord) {
            $monitorCim = Get-CimInstance Win32_Process -Filter "ProcessId=$($monitorRecord.id)" -ErrorAction SilentlyContinue
            if (Test-OwnedMonitorIdentity $monitorCim $monitorRecord) {
                Stop-VerifiedProcesses @([pscustomobject]@{ Id=[int]$monitorCim.ProcessId; Created=$monitorCim.CreationDate; Role='lifecycle monitor' })
            }
        }
        # Stop the tracked parent wrappers first so a late child cannot appear after
        # the JVM scan; then rescan and stop only JVM identities absent beforehand.
        Stop-VerifiedProcesses (Get-OwnedWrappers $statePath $allRoots)
        $owned = @(Get-ManagedDevProcesses $allRoots | Where-Object {
            $candidate = $_
            !($beforeLaunch | Where-Object { $_.Id -eq $candidate.Id -and $_.Created -eq $candidate.Created })
        })
        Stop-VerifiedProcesses $owned
        Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
        if ($sessionFile) { Remove-Item -LiteralPath $sessionFile -Force -ErrorAction SilentlyContinue }
        throw
    }
    Write-Host "Started server and $Clients client(s) in '$Worktree'. Closing the last launched client stops this session's server."
} finally {
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}
