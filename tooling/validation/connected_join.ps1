[CmdletBinding()]
param(
    [int]$PreparationTimeoutSeconds = 300,
    [int]$WallTimeoutSeconds = 120,
    [ValidateSet('Parrots','Kobolds','Mixed')][string]$PopulationScenario,
    [switch]$PopulationSustained,
    [switch]$FlyerPoseDiagnostic,
    [switch]$MeleePresentationDiagnostic,
    [switch]$MeleeStrikePresentationDiagnostic
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'connected_join_parameters.ps1')
. (Join-Path $PSScriptRoot 'melee_capture_proof.ps1')
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$fixture = Join-Path $repo ('build\connected-join-fixture\' + [Guid]::NewGuid().ToString('N'))
$reportDir = Join-Path $repo 'build\reports\nonhostile-animals'
$passReport = Join-Path $reportDir 'connected-join-proof.log'
$populationMode = $PSBoundParameters.ContainsKey('PopulationScenario')
$meleeMode = [bool]($MeleePresentationDiagnostic -or $MeleeStrikePresentationDiagnostic)
$meleeStrikeMode = [bool]$MeleeStrikePresentationDiagnostic
$meleeDir = if ($meleeMode) { Join-Path $repo ('build\reports\melee-visual-capture\' + [Guid]::NewGuid().ToString('N')) } else { $null }
$populationDir = if ($populationMode) { Join-Path (Join-Path $reportDir 'population-profiles') ($PopulationScenario.ToLowerInvariant() + '-' + [Guid]::NewGuid().ToString('N')) } else { $null }
if ($populationMode) { $passReport = Join-Path $populationDir 'join-proof.log' }
$owned = @{}
$roots = @{}
$success = $false
$meleeAdmissionProven = $false
$runStarted = $null
$profileProcesses = @{}
$meleePublicationFailure = $null
$ownedProofPath = Join-Path $fixture 'owned-processes.json'
$cleanupOutcomes = @{}
if (!$populationMode) { Remove-Item -LiteralPath $passReport -Force -ErrorAction SilentlyContinue }

Assert-ConnectedJoinParameters $WallTimeoutSeconds $PreparationTimeoutSeconds $populationMode ([bool]$PopulationSustained) ([bool]$FlyerPoseDiagnostic) $PopulationScenario $meleeMode $meleeStrikeMode ([bool]$MeleePresentationDiagnostic)

function Convert-CimCreationDate($Value) {
    if ($Value -is [DateTime]) {
        $utc = $Value.ToUniversalTime()
        # Win32_Process CreationDate has microsecond precision; normalize the
        # higher-precision StartTime ticks to that same 10-tick quantum.
        return [DateTime]::new($utc.Ticks - ($utc.Ticks % 10), [DateTimeKind]::Utc)
    }
    throw "Unexpected Win32_Process.CreationDate type: $($Value.GetType().FullName)"
}

function Test-ProcessIdentity($CimProcess, $Identity) {
    if (!$CimProcess -or !$Identity) { return $false }
    try {
        if ($null -eq $CimProcess.ProcessId -or $null -eq $CimProcess.CreationDate -or
            $null -eq $Identity.Id -or $null -eq $Identity.Created -or $Identity.Created -isnot [DateTime]) { return $false }
        if ([int]$CimProcess.ProcessId -ne [int]$Identity.Id) { return $false }
        $current = Convert-CimCreationDate $CimProcess.CreationDate
        return $current -eq $Identity.Created
    } catch { return $false }
}

function Get-ProcessObservation([object]$CimProcess, $Identity) {
    if ($null -eq $CimProcess) { return 'GONE' }
    if (!$Identity -or $null -eq $Identity.Id -or $Identity.Created -isnot [DateTime] -or
        $null -eq $CimProcess.ProcessId -or $null -eq $CimProcess.CreationDate -or
        $CimProcess.CreationDate -isnot [DateTime]) { return 'UNKNOWN' }
    try {
        $observedPid = [int]$CimProcess.ProcessId
        $observedCreated = Convert-CimCreationDate $CimProcess.CreationDate
    } catch { return 'UNKNOWN' }
    if ($observedPid -le 0) { return 'UNKNOWN' }
    if ($observedPid -ne [int]$Identity.Id -or $observedCreated -ne $Identity.Created) { return 'REUSED' }
    return 'MATCHED_OWNED'
}

function Get-IdentityKey($Identity) { return "$([int]$Identity.Id):$([long]$Identity.Created.Ticks)" }

function Publish-MeleeLogs([string]$SourceDir, [string]$DestinationDir, [bool]$RequireComplete) {
    New-Item -ItemType Directory -Path $DestinationDir -Force | Out-Null
    foreach ($name in @('server.log','server.stderr.log','client.log','client.stderr.log')) {
        $source = Join-Path $SourceDir $name
        if (Test-Path -LiteralPath $source -PathType Leaf) {
            if ($RequireComplete -and $name -in @('server.log','client.log') -and (Get-Item -LiteralPath $source).Length -eq 0) { throw "Required melee log is empty: $name" }
            Copy-Item -LiteralPath $source -Destination (Join-Path $DestinationDir $name) -Force
        } elseif ($RequireComplete -and $name -in @('server.log','client.log')) { throw "Required melee log is missing: $name" }
    }
}

function Capture-ProcessHandle($Process) {
    # Start-Process -PassThru may not have opened its Process handle yet. Open
    # it now, while the original process is alive, so exit status remains
    # readable after a fast exit; never reacquire by PID later.
    $handle = $Process.Handle
    if ($handle -eq [IntPtr]::Zero) { throw 'Could not capture original launched-process handle.' }
    return $handle
}

function Get-ExitedProcessExitCode($Process, [string]$Description, [string]$OutputPath, [string]$ErrorPath) {
    # Read status only from the original Start-Process object/handle. A PID
    # lookup here could observe a replacement process and is not an exit proof.
    if (!$Process.WaitForExit(0)) {
        throw "$Description exit state was not collectible; see $OutputPath (stderr: $ErrorPath)."
    }
    $Process.Refresh()
    $exitCode = $Process.ExitCode
    if ($null -eq $exitCode) {
        throw "$Description exit code unavailable (null); see $OutputPath (stderr: $ErrorPath)."
    }
    return [int]$exitCode
}

function Register-Root($Process, [string]$Phase) {
    # StartTime is available immediately from Start-Process, before a child can exit.
    $identity = [pscustomobject]@{
        Id=[int]$Process.Id
        Created=Convert-CimCreationDate $Process.StartTime
        IsRoot=$true; Phase=$Phase; ObservedUtc=[DateTime]::UtcNow
    }
    $key = Get-IdentityKey $identity
    $roots[$key] = $identity
    $owned[$key] = $identity
    return $identity
}

function Get-VerifiedDescendants($Snapshot, $RootIdentity) {
    $byPid = @{}
    $seen = [Collections.Generic.HashSet[int]]::new()
    $ambiguous = [Collections.Generic.HashSet[int]]::new()
    foreach ($p in $Snapshot) {
        try {
            $processId = [int]$p.ProcessId
            if ($processId -le 0 -or $null -eq $p.CreationDate) { continue }
            if (!$seen.Add($processId)) { [void]$ambiguous.Add($processId); $byPid.Remove($processId); continue }
            $byPid[$processId] = $p
        } catch { continue }
    }
    foreach ($processId in $ambiguous) { $byPid.Remove($processId) }
    $root = $byPid[[int]$RootIdentity.Id]
    if (!(Test-ProcessIdentity $root $RootIdentity)) { return @() }
    $verified = @{}
    $verified[[int]$RootIdentity.Id] = [pscustomobject]@{ Process=$root; Identity=$RootIdentity }
    $result = [Collections.Generic.List[object]]::new()
    $result.Add([pscustomobject]@{ Process=$root; Identity=$RootIdentity })
    do {
        $changed = $false
        foreach ($p in $byPid.Values) {
            $processId = [int]$p.ProcessId
            if ($verified.ContainsKey($processId)) { continue }
            $parentPid = [int]$p.ParentProcessId
            if (!$verified.ContainsKey($parentPid)) { continue }
            try { $created = Convert-CimCreationDate $p.CreationDate } catch { continue }
            $parent = $verified[$parentPid]
            if ($created -lt $parent.Identity.Created) { continue }
            $identity = [pscustomobject]@{
                Id=$processId; Created=$created; IsRoot=$false
                ParentId=$parentPid; ParentCreated=$parent.Identity.Created
                RootId=$RootIdentity.Id; RootCreated=$RootIdentity.Created
            }
            $verified[$processId] = [pscustomobject]@{ Process=$p; Identity=$identity }
            $result.Add([pscustomobject]@{ Process=$p; Identity=$identity }); $changed = $true
        }
    } while ($changed)
    return @($result.ToArray())
}

function Get-Descendants($RootIdentity) {
    $all = @(Get-CimInstance Win32_Process -ErrorAction Stop)
    return @(Get-VerifiedDescendants $all $RootIdentity)
}

function Track-Tree($RootIdentity) {
    if (!$RootIdentity) { throw 'Launch root has no captured identity.' }
    foreach ($entry in (Get-Descendants $RootIdentity)) {
        $identity = $entry.Identity
        if ($identity.IsRoot) { continue }
        $identity | Add-Member -NotePropertyName Phase -NotePropertyValue $RootIdentity.Phase -Force
        $identity | Add-Member -NotePropertyName ObservedUtc -NotePropertyValue ([DateTime]::UtcNow) -Force
        $owned[(Get-IdentityKey $identity)] = $identity
    }
}

function Write-OwnedProof([string]$CleanupState) {
    $records = @($script:owned.Values | Sort-Object Id,Created | ForEach-Object {
        $identityKey = Get-IdentityKey $_
        [ordered]@{ pid=$_.Id; createdUtc=$_.Created.ToString('o'); isRoot=[bool]$_.IsRoot
            parentPid=$_.ParentId; parentCreatedUtc=$(if ($_.ParentCreated) { $_.ParentCreated.ToString('o') } else { $null })
            rootPid=$(if ($_.RootId) { $_.RootId } else { $_.Id }); rootCreatedUtc=$(if ($_.RootCreated) { $_.RootCreated.ToString('o') } else { $_.Created.ToString('o') })
            phase=$_.Phase; observedUtc=$_.ObservedUtc.ToString('o'); identityKey=$identityKey; cleanup=$script:cleanupOutcomes[$identityKey] }
    })
    $doc = [ordered]@{ schema=1; updatedUtc=[DateTime]::UtcNow.ToString('o'); cleanupState=$CleanupState; processes=$records }
    $temp = $script:ownedProofPath + '.tmp'
    $doc | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $temp -Encoding UTF8
    Move-Item -LiteralPath $temp -Destination $script:ownedProofPath -Force
}

function Stop-OwnedTree {
    $stopErrors = @()
    foreach ($identity in $script:owned.Values) {
        $identityKey = Get-IdentityKey $identity
        try {
            $p = Get-CimInstance Win32_Process -Filter "ProcessId=$($identity.Id)" -ErrorAction Stop
            $observation = Get-ProcessObservation $p $identity
            if ($observation -eq 'GONE') { $script:cleanupOutcomes[$identityKey] = @{ state='gone'; stopAttempted=$false; observedUtc=[DateTime]::UtcNow.ToString('o') }; continue }
            if ($observation -eq 'REUSED') { $script:cleanupOutcomes[$identityKey] = @{ state='reused'; stopAttempted=$false; observedUtc=[DateTime]::UtcNow.ToString('o') }; continue }
            if ($observation -eq 'UNKNOWN') { $script:cleanupOutcomes[$identityKey] = @{ state='unknown'; stopAttempted=$false; observedUtc=[DateTime]::UtcNow.ToString('o') }; $stopErrors += "PID $($identity.Id) created $($identity.Created.ToString('o')) cleanup observation UNKNOWN"; continue }
            try {
                Stop-Process -Id $identity.Id -Force -ErrorAction Stop
                $script:cleanupOutcomes[$identityKey] = @{ state='stop-requested'; stopAttempted=$true; stopResult='success'; observedUtc=[DateTime]::UtcNow.ToString('o') }
            } catch {
                $stopError = $_
                $alreadyExited = $_.CategoryInfo.Category -eq [System.Management.Automation.ErrorCategory]::ObjectNotFound
                if ($alreadyExited) {
                    try {
                        $afterStop = Get-CimInstance Win32_Process -Filter "ProcessId=$($identity.Id)" -ErrorAction Stop
                        $afterObservation = Get-ProcessObservation $afterStop $identity
                    } catch { $afterObservation = 'UNKNOWN' }
                    if ($afterObservation -eq 'GONE' -or $afterObservation -eq 'REUSED') {
                        $script:cleanupOutcomes[$identityKey] = @{ state=$(if ($afterObservation -eq 'GONE') { 'gone-before-stop' } else { 'reused-before-stop' }); stopAttempted=$true; stopResult='already-exited'; error=$stopError.Exception.Message; observedUtc=[DateTime]::UtcNow.ToString('o') }
                        continue
                    }
                }
                $script:cleanupOutcomes[$identityKey] = @{ state='unknown'; stopAttempted=$true; stopResult='failure'; error=$stopError.Exception.Message; observedUtc=[DateTime]::UtcNow.ToString('o') }
            $stopErrors += "PID $($identity.Id) created $($identity.Created.ToString('o')) proof=$($identity.ParentId):$($identity.ParentCreated): id=$($stopError.FullyQualifiedErrorId) category=$($stopError.CategoryInfo.Category) $($stopError.Exception.Message)"
            }
        } catch {
            $script:cleanupOutcomes[$identityKey] = @{ state='unknown'; stopAttempted=$false; observedUtc=[DateTime]::UtcNow.ToString('o') }
            $stopErrors += "PID $($identity.Id) created $($identity.Created.ToString('o')) proof=$($identity.ParentId):$($identity.ParentCreated): $($_.Exception.Message)"
        }
    }
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        $alive = @()
        foreach ($identity in $script:owned.Values) {
            $identityKey = Get-IdentityKey $identity
            try {
                $p = Get-CimInstance Win32_Process -Filter "ProcessId=$($identity.Id)" -ErrorAction Stop
                $observation = Get-ProcessObservation $p $identity
                if ($observation -eq 'MATCHED_OWNED') { $alive += $identity.Id; $script:cleanupOutcomes[$identityKey].state='still-live' }
                elseif ($observation -eq 'GONE') { $script:cleanupOutcomes[$identityKey].state='gone' }
                elseif ($observation -eq 'REUSED') { $script:cleanupOutcomes[$identityKey].state='reused' }
                else { $script:cleanupOutcomes[$identityKey] = @{ state='unknown'; stopAttempted=$false; observedUtc=[DateTime]::UtcNow.ToString('o') }; $stopErrors += "PID $($identity.Id) cleanup observation UNKNOWN" }
            } catch {
                $script:cleanupOutcomes[$identityKey] = @{ state='unknown'; stopAttempted=$false; observedUtc=[DateTime]::UtcNow.ToString('o') }
                $stopErrors += "PID $($identity.Id) created $($identity.Created.ToString('o')) cleanup observation unknown: $($_.Exception.Message)"
            }
        }
        if (!$alive.Count) { break }
        Start-Sleep -Milliseconds 150
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($alive.Count -or $stopErrors.Count) {
        throw "Owned cleanup could not verify exit; alive=[$($alive -join ',')] errors=[$($stopErrors -join '; ')]"
    }
}

function Assert-SessionHealthy([string]$ServerText, [string]$ClientText, [string]$Epoch) {
    if ($ClientText -match '(?i)Failed to assemble prototype catalog sync') { throw 'Client reported prototype catalog assembly failure.' }
    if ($ServerText -match '(?i)Failed to assemble prototype catalog sync') { throw 'Server reported prototype catalog assembly failure.' }
    if ($Epoch -and $ServerText -match "Lifecycle CHARACTER terminal failure reason=READY_TIMEOUT[^\r\n]*epoch=$([regex]::Escape($Epoch))(?:\D|$)") {
        throw "Server reported READY_TIMEOUT for epoch $Epoch."
    }
    if ($Epoch -and $ServerText -match "Lifecycle CHARACTER terminal failure reason=[^ \r\n]+[^\r\n]*epoch=$([regex]::Escape($Epoch))(?:\D|$)") {
        throw "Server reported a terminal lifecycle failure for epoch $Epoch."
    }
    if ($ServerText -match '(?i)JoinDiagnostic (?:lost connection:|left the game)') { throw 'Server log reports the diagnostic player disconnected.' }
    if ($ClientText -match '(?i)(?:Disconnected from server|Client disconnected with reason:|Connection closed|Stopping!)') { throw 'Client log reports disconnect or client shutdown.' }
    if ($ClientText -match '(?m)\[[^\r\n]*/FATAL\]') { throw 'Client emitted a FATAL log entry.' }
}

function Test-PopulationCompletion([string]$ServerText, [string]$Scenario) {
    $expectedCount = if ($Scenario -eq 'mixed') { 40 } else { 100 }
    $pattern = '(?m)^.*MS14_ANIMAL_POPULATION_COMPLETE scenario=' + [regex]::Escape($Scenario) + ' animalCount=' + $expectedCount + '(?:\s|$)'
    return [regex]::IsMatch($ServerText, $pattern)
}

function Test-PopulationBootstrapProof([string]$ServerText) {
    return [regex]::IsMatch($ServerText, '(?m)^.*MS14_STARTER_STATION_DISABLED reason=run-local property ms14\.starterStation\.enabled=false(?:\s|$)')
}

function Get-PopulationTerminalFailure([string]$ServerText, [string]$ClientText) {
    foreach ($entry in @(@{role='server';text=$ServerText},@{role='client';text=$ClientText})) {
        $match = [regex]::Match($entry.text, '(?m)^.*(?:MS14_ANIMAL_POPULATION_(?:ABORTED|RECORDING_FAILED)[^\r\n]*|Animal population diagnostic aborted:[^\r\n]*)')
        if ($match.Success) { return "$($entry.role): $($match.Value.Trim())" }
    }
    return $null
}

function Test-ConnectedJoinAdmission([bool]$Melee, [bool]$Tracking, [bool]$Control, [bool]$Intent) {
    return $Tracking -and $Control -and ($Melee -or $Intent)
}

function Wait-ConnectedServerReady([scriptblock]$ReadServerLog, [scriptblock]$TrackServer, [scriptblock]$LauncherHasExited, [scriptblock]$GetLauncherExitCode, [int]$BudgetSeconds, [int]$PollMilliseconds = 250) {
    $startedUtc = [DateTime]::UtcNow
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $budgetMilliseconds = $BudgetSeconds * 1000
    Write-Host ("Connected server startup wait begin utc={0:o} budgetSeconds={1} monotonicTicks={2}" -f $startedUtc, $BudgetSeconds, $timer.ElapsedTicks)
    $donePattern = '\bDone \([^\r\n]*\)!'
    $serverText = ''
    while ($timer.ElapsedMilliseconds -lt $budgetMilliseconds) {
        & $TrackServer
        $serverText = & $ReadServerLog
        if ($serverText -match $donePattern) {
            Write-Host ("Connected server startup wait success utcEnd={0:o} elapsedMilliseconds={1} lastLogLength={2} doneMatch=true launcherHasExited={3}" -f [DateTime]::UtcNow, $timer.ElapsedMilliseconds, $serverText.Length, (& $LauncherHasExited))
            return [pscustomobject]@{ ServerText = $serverText; ElapsedMilliseconds = $timer.ElapsedMilliseconds }
        }
        if (& $LauncherHasExited) { $exit = & $GetLauncherExitCode; throw "Server launcher exited before Done ($exit)." }
        Start-Sleep -Milliseconds $PollMilliseconds
    }
    # The last sleep can straddle the budget; accept a Done marker written at the boundary.
    & $TrackServer
    $serverText = & $ReadServerLog
    $done = $serverText -match $donePattern
    $exited = & $LauncherHasExited
    Write-Host ("Connected server startup wait timeout utcEnd={0:o} elapsedMilliseconds={1} lastLogLength={2} doneMatch={3} launcherHasExited={4}" -f [DateTime]::UtcNow, $timer.ElapsedMilliseconds, $serverText.Length, $done, $exited)
    if ($done) { return [pscustomobject]@{ ServerText = $serverText; ElapsedMilliseconds = $timer.ElapsedMilliseconds } }
    if ($exited) { throw "Server launcher exited before Done ($(& $GetLauncherExitCode))." }
    throw "Server did not reach Done within its bounded startup wait (budgetSeconds=$BudgetSeconds elapsedMilliseconds=$($timer.ElapsedMilliseconds))."
}

function Get-MeleeTerminalFailure([string]$Directory, [string]$ServerText, [string]$ClientText) {
    $failedCapture = Join-Path $Directory 'melee-capture-failed.json'
    if (Test-Path -LiteralPath $failedCapture -PathType Leaf) {
        try { $record = Get-Content -LiteralPath $failedCapture -Raw | ConvertFrom-Json -ErrorAction Stop }
        catch { return "Melee failure record is invalid: $($_.Exception.Message)" }
        if ($record.status -ceq 'FAILED') { return "Melee diagnostic failed phase=$($record.phase): $($record.reason)" }
    }
    foreach ($entry in @(@{role='server';text=$ServerText},@{role='client';text=$ClientText})) {
        $match = [regex]::Match($entry.text, '(?m)^.*MS14_MELEE_CAPTURE_FAILED[^\r\n]*')
        if ($match.Success) { return "$($entry.role): $($match.Value.Trim())" }
    }
    return $null
}

function Get-MeleeExpectedPhases([bool]$MotionMode = $false) {
    if ($MotionMode) {
        $scenes = @('motion_fist_first','motion_fist_third','motion_observer_up','motion_observer_down','motion_observer_close','motion_observer_wide','motion_knife_first','motion_knife_wide','motion_wall_clip','motion_disarm_first','motion_crowbar_light','motion_jug_hit','motion_knife_repeat','motion_knife_wide_up','motion_knife_wide_vertical_up','motion_knife_wide_down','motion_knife_wide_vertical_down','motion_crowbar_wall_wide','motion_disarm_flight','motion_observer_disarm_flight')
        $samples = @('outward','extended','return')
        return @($scenes | ForEach-Object { $scene = $_; $samples | ForEach-Object { "${scene}_$_" } })
    }
    return @('fist_first','fist_third','disarm_first','fist_hit_near','fist_hit_edge','wall_first','knife_first','observer')
}

function Format-MeleeIncompleteFailure([string]$Directory) {
    $phase = 'unknown'
    $scenePath = Join-Path $Directory 'scene.json'
    if (Test-Path -LiteralPath $scenePath -PathType Leaf) {
        try { $scene = Get-Content -LiteralPath $scenePath -Raw | ConvertFrom-Json; if ($scene.phase) { $phase = [string]$scene.phase } } catch { }
    }
    $pngs = @(Get-ChildItem -LiteralPath $Directory -Filter '*.png' -File -ErrorAction SilentlyContinue)
    $records = 0
    $manifestPath = Join-Path $Directory 'manifest.json'
    if (Test-Path -LiteralPath $manifestPath -PathType Leaf) {
        try { $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json; $records = @($manifest.phases).Count } catch { }
    }
    $expectedCount = (Get-MeleeExpectedPhases $script:meleeStrikeMode).Count
    return "Melee capture incomplete phase=$phase completed=$($pngs.Count)/$expectedCount PNGs manifestRecords=$records directory=$Directory"
}

function Format-PrimaryFailure([string]$Primary, [string[]]$Secondary) {
    $details = @($Secondary | Where-Object { $_ })
    if (!$Primary) { $Primary = 'Diagnostic failed without a captured primary error.' }
    if ($details.Count) { return "$Primary`nSecondary failures: $($details -join '; ')" }
    return $Primary
}

function Get-PopulationProofPath([string]$Directory) { return Join-Path $Directory 'join-proof.log' }

function Copy-PopulationEvidence([string]$Fixture, [string]$Directory) {
    foreach ($name in @('server.log','server.stderr.log','client.log','client.stderr.log')) {
        $source = Join-Path $Fixture $name
        if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination (Join-Path $Directory $name) -Force }
    }
}

function Test-PopulationRecordingReady([string]$LogText, [string]$Role, [string]$Scenario, [int]$OwnedPid, [string]$CaptureDir) {
    $expectedFile = "$Role.jfr"
    if ($LogText -notmatch "(?m)^.*MS14_ANIMAL_POPULATION_RECORDING_READY role=$Role scenario=$([regex]::Escape($Scenario))(?:\s|$)") { return $false }
    $marker = Join-Path $CaptureDir ($Role + '-ready.json')
    $expectedPath = [IO.Path]::GetFullPath((Join-Path $CaptureDir $expectedFile))
    if (!(Test-Path -LiteralPath $marker -PathType Leaf)) { return $false }
    try { $record = Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json -ErrorAction Stop } catch { return $false }
    if ($record.role -cne $Role -or $record.scenario -cne $Scenario -or [long]$record.pid -ne $OwnedPid -or
        $record.filename -cne $expectedFile -or [long]$record.bytes -le 0) { return $false }
    if ($Role -eq 'server' -and [int]$record.actualCount -ne $(if ($Scenario -eq 'mixed') { 40 } else { 100 })) { return $false }
    if ($Role -eq 'client' -and [int]$record.actualCount -ne 0) { return $false }
    if (!(Test-Path -LiteralPath $expectedPath -PathType Leaf)) { return $false }
    $file = Get-Item -LiteralPath $expectedPath
    return $file.Length -gt 0 -and $file.Length -eq [long]$record.bytes
}

function Get-OwnedJava([string]$Role) {
    $rootIdentity = if ($Role -eq 'server') { $serverRootIdentity } else { $clientRootIdentity }
    $matches = @(Get-Descendants $rootIdentity | Where-Object { $_.Process.Name -match '^java(w)?\.exe$' })
    if ($matches.Count -ne 1) { throw "Expected exactly one owned Java process for $Role; found $($matches.Count)." }
    $p = $matches[0].Process
    $identity = $matches[0].Identity
    $again = Get-CimInstance Win32_Process -Filter "ProcessId=$($identity.Id)" -ErrorAction Stop
    if ((Get-ProcessObservation $again $identity) -ne 'MATCHED_OWNED') { throw "Owned $Role Java identity changed." }
    return [pscustomobject]@{ Identity=$identity; Jfr=(Join-Path (Split-Path -Parent $again.ExecutablePath) 'jfr.exe') }
}

try {
    New-Item -ItemType Directory -Path $fixture -Force | Out-Null
    if ($populationMode) { New-Item -ItemType Directory -Path $populationDir -Force | Out-Null }
    if ($meleeMode) { New-Item -ItemType Directory -Path $meleeDir -Force | Out-Null }
    $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $probe.Start(); $port = $probe.LocalEndpoint.Port; $probe.Stop()
    $serverDir = Join-Path $fixture 'server'
    $clientDir = Join-Path $fixture 'client'
    New-Item -ItemType Directory -Path $serverDir, $clientDir -Force | Out-Null
    # On a clean client, Minecraft's default onboardAccessibility=true wraps
    # Quick Play behind AccessibilityOnboardingScreen and prevents auto-connect.
    # This is isolated to the disposable client options file; no owner profile is touched.
    $clientOptions = @('onboardAccessibility:false')
    if ($meleeStrikeMode) { $clientOptions += @('renderDistance:3','simulationDistance:3','maxFps:60','enableVsync:false') }
    Set-Content -LiteralPath (Join-Path $clientDir 'options.txt') -Value $clientOptions -Encoding ASCII
    @"
server-ip=127.0.0.1
server-port=$port
online-mode=false
level-name=world
level-type=minecraft\:flat
generate-structures=false
motd=Connected join diagnostic
"@ | Set-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $serverDir 'eula.txt') -Value 'eula=true' -Encoding ASCII

    # Preparation is intentionally separate from the 120-second native process budget.
    $prepOut = Join-Path $fixture 'prepare.log'
    $prepErr = Join-Path $fixture 'prepare.stderr.log'
    $populationArgs = if ($populationMode) { ' "-Pms14AnimalPopulationScenario=' + $PopulationScenario.ToLowerInvariant() + '" "-Pms14AnimalPopulationCaptureDir=' + $populationDir + '"' + $(if ($PopulationSustained) { ' -Pms14AnimalPopulationSustained=true' } else { '' }) + $(if ($FlyerPoseDiagnostic) { ' -Pms14AnimalFlyerPoseDiagnostic=true' } else { '' }) } else { '' }
    if ($meleeMode) { $populationArgs += ' "-Pms14MeleePresentationCaptureDir=' + $meleeDir + '"' }
    if ($meleeStrikeMode) { $populationArgs += ' -Pms14MeleeStrikePresentationDiagnostic=true' }
    $gradleArgs = '/d /s /c ""' + (Join-Path $repo 'gradlew.bat') + '" -I tooling/validation/connected_join.init.gradle ms14PrepareConnectedJoin ' +
        '"-Pms14ConnectedJoinRoot=' + $fixture + '" "-Pms14ConnectedJoinPort=' + $port + '"' + $populationArgs + ' --no-configuration-cache --no-daemon"'
    $prepProc = Start-Process -FilePath $env:ComSpec -ArgumentList $gradleArgs -WorkingDirectory $repo `
        -RedirectStandardOutput $prepOut -RedirectStandardError $prepErr -PassThru
    $prepHandle = Capture-ProcessHandle $prepProc
    $prepRoot = Register-Root $prepProc 'preparation'
    $prepDeadline = [DateTime]::UtcNow.AddSeconds($PreparationTimeoutSeconds)
    do {
        Track-Tree $prepRoot
        if ($prepProc.HasExited) { break }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $prepDeadline)
    if (!$prepProc.HasExited) { throw "Gradle preparation exceeded $PreparationTimeoutSeconds seconds." }
    Track-Tree $prepRoot
    $prepExitCode = Get-ExitedProcessExitCode $prepProc 'Connected-join launch preparation' $prepOut $prepErr
    if ($prepExitCode -ne 0) { throw "Connected-join launch preparation failed ($prepExitCode); see $prepOut (stderr: $prepErr)." }
    # Give the --no-daemon single-use Gradle process family time to exit normally.
    $prepChildrenDeadline = [DateTime]::UtcNow.AddSeconds(5)
    do {
        $prepChildrenAlive = @()
        foreach ($identity in @($owned.Values | Where-Object { $_.Id -ne $prepRoot.Id })) {
            $p = Get-CimInstance Win32_Process -Filter "ProcessId=$($identity.Id)" -ErrorAction Stop
            if (Test-ProcessIdentity $p $identity) { $prepChildrenAlive += $identity.Id }
        }
        if (!$prepChildrenAlive.Count) { break }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $prepChildrenDeadline)
    if ($prepChildrenAlive.Count) { throw "Gradle preparation left owned process(es) alive: $($prepChildrenAlive -join ',')." }
    $manifestPath = Join-Path $fixture 'launches.json'
    if (!(Test-Path -LiteralPath $manifestPath)) { throw 'Preparation did not emit isolated launch manifest.' }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $server = @($manifest.launches | Where-Object role -eq 'server') | Select-Object -First 1
    $client = @($manifest.launches | Where-Object role -eq 'client') | Select-Object -First 1
    if (!$server -or !$client) { throw 'Manifest is missing server/client launch scripts.' }
    $serverLog = Join-Path $fixture 'server.log'
    $clientLog = Join-Path $fixture 'client.log'
    $serverErr = Join-Path $fixture 'server.stderr.log'
    $clientErr = Join-Path $fixture 'client.stderr.log'
    $runStarted = [DateTime]::UtcNow

    $serverProc = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d','/c',"`"$($server.script)`"") `
        -WorkingDirectory $serverDir -RedirectStandardOutput $serverLog -RedirectStandardError $serverErr -PassThru
    $serverRootIdentity = Register-Root $serverProc 'server'
    Track-Tree $serverRootIdentity
    $startupBudget = [Math]::Min(55, $WallTimeoutSeconds / 2)
    $startup = Wait-ConnectedServerReady `
        { if (Test-Path $serverLog) { Get-Content $serverLog -Raw -ErrorAction Stop } else { '' } } `
        { Track-Tree $serverRootIdentity } `
        { $serverProc.HasExited } `
        { $serverProc.ExitCode } `
        $startupBudget
    $serverText = $startup.ServerText

    $clientProc = Start-Process -FilePath $env:ComSpec -ArgumentList @('/d','/c',"`"$($client.script)`"") `
        -WorkingDirectory $clientDir -RedirectStandardOutput $clientLog -RedirectStandardError $clientErr -PassThru
    $clientRootIdentity = Register-Root $clientProc 'client'
    Track-Tree $clientRootIdentity
    $deadline = $runStarted.AddSeconds($WallTimeoutSeconds)
    $committed = $null
    do {
        Track-Tree $serverRootIdentity; Track-Tree $clientRootIdentity
        $serverText = if (Test-Path $serverLog) { Get-Content $serverLog -Raw -ErrorAction Stop } else { '' }
        $clientText = if (Test-Path $clientLog) { Get-Content $clientLog -Raw -ErrorAction Stop } else { '' }
        Assert-SessionHealthy $serverText $clientText ([string]($committed.Epoch))
        if ($meleeMode -and $meleeDir) { $terminal = Get-MeleeTerminalFailure $meleeDir $serverText $clientText; if ($terminal) { throw $terminal } }
        if ($populationMode) { $terminal = Get-PopulationTerminalFailure $serverText $clientText; if ($terminal) { throw "Population diagnostic terminal failure: $terminal" } }
        if ($clientText -match '(?m)^.*\[harness ready\].*readySent=true.*reason=READY_SENT') {
            $ready = [regex]::Match($clientText, '(?m)^.*\[harness ready\] epoch=(\d+) harnessKind=CHARACTER expectedEntityId=(\d+).*?recognizedPolicy=true.*?readySent=true.*?reason=READY_SENT')
            if ($ready.Success) {
                $epoch = $ready.Groups[1].Value
                $expectedBody = $ready.Groups[2].Value
                $commit = [regex]::Match($serverText, "Lifecycle CHARACTER Ready committed \(epoch=$([regex]::Escape($epoch)), body=$([regex]::Escape($expectedBody))\)")
                if ($commit.Success) { $committed = [pscustomobject]@{ Epoch=$epoch; Body=$expectedBody } }
            }
        }
        if ($committed) {
            if ($serverText -notmatch '(?m)JoinDiagnostic joined the game') {
                throw 'Committed session lacks the expected diagnostic username join log.'
            }
            $epochRx = [regex]::Escape($committed.Epoch)
            $bodyRx = [regex]::Escape($committed.Body)
            $trackingRx = "(?m)^.*\[harness tracking\] kind=CHARACTER epoch=$epochRx target=[^\r\n]*#$bodyRx expectedId=$bodyRx candidate=true sessionReady=true camera=$bodyRx cameraOwns=true medicalOwns=true result=true(?:\s|$)"
            $controlRx = "(?m)^.*\[harness control\] epoch=$epochRx entityId=$bodyRx eligible=true[^\r\n]*camera=$bodyRx cameraLookupIdentical=true owner=true[^\r\n]*"
            $intentRx = "(?m)^.*\[harness control\] server epoch=$epochRx entity=$bodyRx[^\r\n]*acceptedFirstIntent sequence=\d+ noAi=true movementOwned=true(?:\s|$)"
            $trackingProven = [regex]::IsMatch($clientText, $trackingRx)
            $controlProven = [regex]::IsMatch($clientText, $controlRx)
            $intentProven = [regex]::IsMatch($serverText, $intentRx)
            if (Test-ConnectedJoinAdmission $meleeMode $trackingProven $controlProven $intentProven) {
                if ($meleeMode) { $meleeAdmissionProven = $true }
                $holdUntil = if (!$populationMode -and !$meleeMode) { [DateTime]::UtcNow.AddSeconds(8) } else { $null }
                if (!$populationMode -and !$meleeMode -and $holdUntil -gt $deadline) { throw 'Insufficient remaining native wall budget for the complete 8-second connected hold.' }
                do {
                    Track-Tree $serverRootIdentity; Track-Tree $clientRootIdentity
                    if ($serverProc.HasExited -or $clientProc.HasExited) { throw 'Server/client exited during post-commit connection hold.' }
                    $serverText = Get-Content $serverLog -Raw -ErrorAction Stop
                    $clientText = Get-Content $clientLog -Raw -ErrorAction Stop
                    Assert-SessionHealthy $serverText $clientText $committed.Epoch
                    if ($populationMode) { $terminal = Get-PopulationTerminalFailure $serverText $clientText; if ($terminal) { throw "Population diagnostic terminal failure: $terminal" } }
                    if ($meleeMode) {
                        $terminal = Get-MeleeTerminalFailure $meleeDir $serverText $clientText
                        if ($terminal) { throw $terminal }
                        $completion = Join-Path $meleeDir 'melee-capture-complete.json'
                        if (Test-Path -LiteralPath $completion -PathType Leaf) {
                            $capture = Get-Content -LiteralPath $completion -Raw | ConvertFrom-Json
                            $expectedMeleePhases = Get-MeleeExpectedPhases $meleeStrikeMode
                            $expectedMeleeCount = $expectedMeleePhases.Count
                            if ($capture.status -cne 'COMPLETE' -or [int]$capture.phaseCount -ne $expectedMeleeCount) { throw 'Melee capture completion marker is incomplete.' }
                            $manifestPath = Join-Path $meleeDir 'manifest.json'
                            if (!(Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Melee capture manifest is missing.' }
                            $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
                            $manifestPhases = @($manifest.phases | ForEach-Object { $_.phase })
                            $manifestPngPaths = @($manifest.phases | ForEach-Object { $_.png })
                            if ($manifest.status -cne 'COMPLETE' -or @($manifest.phases).Count -ne $expectedMeleeCount -or (@($manifestPhases) -join ',') -cne (@($expectedMeleePhases) -join ',') -or $manifestPngPaths.Count -ne $expectedMeleeCount -or @($manifestPngPaths | Where-Object { [string]::IsNullOrWhiteSpace([string]$_) }).Count -gt 0) { throw 'Melee capture manifest does not contain the exact expected completed phases.' }
                            for ($i=0; $i -lt $expectedMeleeCount; $i++) {
                                $phase = @($manifest.phases)[$i]
                                if ([string]$phase.phase -cne [string]$expectedMeleePhases[$i]) { throw "Melee phase order mismatch at index=$i." }
                                Assert-MeleeCaptureProof $phase $meleeDir $meleeStrikeMode
                            }
                            $pngs = @(Get-ChildItem -LiteralPath $meleeDir -Filter '*.png' -File)
                            if ($pngs.Count -ne $expectedMeleeCount -or @($pngs | Where-Object Length -le 0).Count -gt 0) { throw 'Melee capture did not produce exactly the expected nonempty PNG frames.' }
                            $success = $true
                            break
                        }
                    }
                    if ($populationMode -and (Test-PopulationCompletion $serverText $PopulationScenario.ToLowerInvariant())) {
                        if (!$profileProcesses.server) { $profileProcesses.server = Get-OwnedJava 'server' }
                        if (!$profileProcesses.client) { $profileProcesses.client = Get-OwnedJava 'client' }
                        $serverReady = Test-PopulationRecordingReady $serverText 'server' $PopulationScenario.ToLowerInvariant() $profileProcesses.server.Identity.Id $populationDir
                        $clientReady = Test-PopulationRecordingReady $clientText 'client' $PopulationScenario.ToLowerInvariant() $profileProcesses.client.Identity.Id $populationDir
                        if ($serverReady -and $clientReady) { break }
                    }
                    Start-Sleep -Milliseconds 250
                } while ([DateTime]::UtcNow -lt $(if ($populationMode -or $meleeMode) { $deadline } else { $holdUntil }))
                if ($populationMode -and (!(Test-PopulationCompletion $serverText $PopulationScenario.ToLowerInvariant()) -or !$serverReady -or !$clientReady)) { throw 'Population completion and both owned server/client recording-ready markers/files were not confirmed within native wall deadline.' }
                if ($populationMode -and !(Test-PopulationBootstrapProof $serverText)) { throw 'Population server log lacks the current-run starter-station opt-out proof; fixture completion is not accepted.' }
                if (!$meleeMode) { $success = $true }
                break
            }
        }
        if ($serverProc.HasExited -or $clientProc.HasExited) { throw 'Server/client exited before matching Ready commit.' }
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)
    if (!$success -and $meleeMode -and $meleeAdmissionProven) {
        $terminal = Get-MeleeTerminalFailure $meleeDir $serverText $clientText
        if ($terminal) { throw $terminal }
        throw (Format-MeleeIncompleteFailure $meleeDir)
    }
    if (!$success) { throw 'Timed out waiting for ReadySent/commit plus matching CHARACTER tracking, eligible control, and accepted first intent.' }
} catch {
    $failure = $_.ToString()
} finally {
    if ($populationMode) {
        try {
            $profileManifest = Join-Path $populationDir 'manifest.json'
            $profileData = @{ scenario=$PopulationScenario.ToLowerInvariant(); flyerPoseDiagnostic=[bool]$FlyerPoseDiagnostic; protocol=$(if ($PopulationSustained) { 'sustained' } else { 'stationary' });
                expectedSustainedPhaseTicks=$(if ($PopulationSustained) { 1200 } else { $null });
                sustainedPhases=$(if ($PopulationSustained) { @('population_idle','population_move','population_jump','population_away','population_return') } else { @() });
                fixture=$fixture; originalFixture=$fixture }
            if ($success -and $serverText -and (Test-PopulationBootstrapProof $serverText)) { $profileData.starterStationDisabled = $true }
            Copy-PopulationEvidence $fixture $populationDir
            $profileData.serverLog = Join-Path $populationDir 'server.log'; $profileData.clientLog = Join-Path $populationDir 'client.log'
            foreach ($role in @('server','client')) {
                if (!$profileProcesses[$role]) { throw "No verified owned $role Java identity was captured while its recording-ready event was observed." }
                $identity = $profileProcesses[$role].Identity
                $raw = Join-Path $populationDir ($role + '.jfr')
                if (!(Test-Path -LiteralPath $raw -PathType Leaf) -or (Get-Item -LiteralPath $raw).Length -le 0) { throw "Missing nonempty self-dumped recording: $raw" }
                $profileData[$role+'Jfr']=$raw
                $profileData[$role+'Process'] = @{ pid=$identity.Id; createdUtc=$identity.Created.ToString('o') }
                $profileData[$role+'Bytes'] = (Get-Item -LiteralPath $raw).Length
                $profileData[$role+'JfrTool'] = $profileProcesses[$role].Jfr
                $profileData[$role+'Ready'] = Get-Content -LiteralPath (Join-Path $populationDir ($role + '-ready.json')) -Raw | ConvertFrom-Json
            }
            $profileData | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $profileManifest -Encoding UTF8
        } catch { $profileFailure = $_.ToString() }
    }
    try {
        # Destructive cleanup is forbidden unless the complete captured identity proof is durable first.
        Write-OwnedProof 'cleanup-pending'
    } catch { $cleanupFailure = "Could not persist ownership proof before cleanup; no processes stopped: $($_.ToString())" }
    if (!$cleanupFailure) {
        try { Stop-OwnedTree } catch { $cleanupFailure = $_.ToString() }
        try { Write-OwnedProof $(if ($cleanupFailure) { 'cleanup-incomplete' } else { 'cleanup-complete' }) }
        catch { $writeFailure = "Could not persist cleanup outcomes: $($_.ToString())"; $cleanupFailure = if ($cleanupFailure) { "$cleanupFailure; $writeFailure" } else { $writeFailure } }
    }
    if (($populationMode -or $meleeMode) -and (Test-Path -LiteralPath $ownedProofPath)) {
        try { Copy-Item -LiteralPath $ownedProofPath -Destination (Join-Path $(if ($meleeMode) { $meleeDir } else { $populationDir }) 'owned-processes.json') -Force }
        catch { $profileFailure = if ($profileFailure) { "$profileFailure; ownership proof copy failed: $($_.Exception.Message)" } else { "Ownership proof copy failed: $($_.Exception.Message)" } }
    }
    if ($populationMode -and !$cleanupFailure -and !$profileFailure -and $profileData) {
        try {
            foreach ($role in @('server','client')) {
                $json = Join-Path $populationDir ($role + '.json')
                & $profileData[$role+'JfrTool'] print --json --events ms14.PopulationPhase,ms14.ServerTickDuration,ms14.ClientFrameDuration,ms14.NpcRouteWork,ms14.ServerFlyerPose,ms14.ClientFlyerPose,jdk.ExecutionSample,jdk.GarbageCollection,jdk.GCPhasePause $profileData[$role+'Jfr'] | Set-Content -LiteralPath $json -Encoding UTF8
                if ($LASTEXITCODE -ne 0) { throw "JFR JSON export failed for $role." }
                $profileData[$role+'Json']=$json
            }
            $summary = Join-Path $populationDir 'summary.json'
            $protocolArg = if ($PopulationSustained) { 'sustained' } else { 'stationary' }
            [string[]]$analyzerArgs = New-PopulationAnalyzerArguments $profileData.serverJson $profileData.clientJson $PopulationScenario.ToLowerInvariant() $protocolArg ([bool]$FlyerPoseDiagnostic) $summary
            & python (Join-Path $PSScriptRoot 'analyze_population_profile.py') @analyzerArgs
            if ($LASTEXITCODE -ne 0) { throw 'Population profile analyzer reported inadequate coverage.' }
            if ($FlyerPoseDiagnostic) {
                $analysis = Get-Content -LiteralPath $summary -Raw | ConvertFrom-Json
                $pose = $analysis.flyer_pose
                Write-Output ("Flyer pose: status={0} pairedRowCount={1} matchedUUIDCount={2} rejectedServerRows={3} rejectedClientRows={4} summary={5}" -f $pose.status, $pose.pairedRowCount, $pose.matchedUUIDCount, $pose.rejectedServerRows, $pose.rejectedClientRows, $summary)
            }
            $profileData.analysis = $summary
            $profileData | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $profileManifest -Encoding UTF8
        } catch { $profileFailure = $_.ToString() }
    }
    if ($meleeMode) { try { Publish-MeleeLogs $fixture $meleeDir ([bool]($success -and !$cleanupFailure)) } catch { $meleePublicationFailure = $_.ToString() } }
    if ($success -and !$cleanupFailure -and !$meleePublicationFailure -and (!$populationMode -or (!$profileFailure -and !$failure))) {
        New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
        @(
            'MS14_CONNECTED_JOIN_PASS'
            "epoch=$($committed.Epoch) body=$($committed.Body)"
            "tracking: epoch=$($committed.Epoch) body=$($committed.Body) candidate/sessionReady/result=true cameraOwns=true medicalOwns=true"
            "control: epoch=$($committed.Epoch) entity=$($committed.Body) eligible=true camera=$($committed.Body) cameraLookupIdentical=true owner=true"
            $(if ($meleeMode) { 'Melee proof: actual combat input and capture evidence; no accepted movement intent is claimed.' } else { "server: epoch=$($committed.Epoch) entity=$($committed.Body) acceptedFirstIntent noAi=true movementOwned=true" })
            'Evidence also includes client READY_SENT and matching server Lifecycle CHARACTER Ready committed.'
            $(if ($populationMode) { 'Population phase completed after logged Ready/commit/control evidence; no owner physical-input claim.' } else { 'Connected hold=8s. Proves logged camera/control ownership, not camera pixels or owner physical input.' })
            'No >100-server-tick claim (server game-time tick count is not logged).'
        ) | Set-Content -LiteralPath $passReport -Encoding UTF8
        foreach ($name in $(if ($populationMode) { @() } else { @('server.log','server.stderr.log','client.log','client.stderr.log') })) {
            $source = Join-Path $fixture $name
            if (Test-Path $source) { Add-Content -LiteralPath $passReport -Value "--- $name ---"; Get-Content $source | Add-Content -LiteralPath $passReport }
        }
        $proofArchive = Join-Path $reportDir ('connected-join-owned-' + [Guid]::NewGuid().ToString('N') + '.json')
        Copy-Item -LiteralPath $ownedProofPath -Destination $proofArchive -Force
        Remove-Item -LiteralPath $fixture -Recurse -Force
    }
    if ($populationMode -and $success -and !$cleanupFailure -and !$profileFailure) {
        @('MS14_ANIMAL_POPULATION_CAPTURE_COVERAGE_RECORDED', "scenario=$($PopulationScenario.ToLowerInvariant())", "mode=$(if ($PopulationSustained) { 'sustained' } else { 'stationary' })", "manifest=$profileManifest", "summary=$(Join-Path $populationDir 'summary.json')", 'Coverage validity is not lag resolution or owner acceptance; no physical keyboard or camera-pixel proof.') | Set-Content -LiteralPath (Join-Path $populationDir 'capture-coverage.log') -Encoding UTF8
    }
    if ($meleeMode) {
        if ($success -and !$cleanupFailure -and !$meleePublicationFailure) { Set-Content -LiteralPath (Join-Path $meleeDir 'diagnostic-run.log') -Value 'MS14_MELEE_PRESENTATION_CAPTURE_COMPLETE; frames and manifest emitted by diagnostic client.' -Encoding UTF8 }
    }
}
if ($cleanupFailure -or $profileFailure -or $meleePublicationFailure -or !$success) { $details = @(); if ($profileFailure) { $details += "Population profiling failed: $profileFailure" }; if ($meleePublicationFailure) { $details += "Melee log publication failed: $meleePublicationFailure" }; if ($cleanupFailure) { $details += "Cleanup could not verify owned process exit: $cleanupFailure" }; if ($populationMode) { $details += "Artifacts: $populationDir; fixture retained at $fixture" }; if ($meleeMode) { $details += "Melee artifacts: $meleeDir; fixture retained at $fixture" }; throw (Format-PrimaryFailure $failure $details) }
Write-Output "MS14_CONNECTED_JOIN_PASS report=$passReport"
if ($populationMode) { Write-Output "Population artifacts=$populationDir manifest=$profileManifest summary=$(Join-Path $populationDir 'summary.json')" }
if ($meleeMode) { Write-Output "MS14_MELEE_PRESENTATION_CAPTURE_PASS artifacts=$meleeDir manifest=$(Join-Path $meleeDir 'manifest.json')" }
