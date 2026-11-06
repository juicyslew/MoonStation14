[CmdletBinding()]
param(
    [ValidateSet('prepare', 'test', 'dll')]
    [string] $Stage = 'prepare',
    [switch] $BootstrapRust,
    [switch] $CheckSummaryParser,
    [switch] $CheckDllReceipt,
    [switch] $PrepareOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$work = Join-Path $repoRoot 'build/native-physics'
$manifest = Get-Content -Raw (Join-Path $PSScriptRoot 'rapier-source.json') | ConvertFrom-Json
$rust = Get-Content -Raw (Join-Path $PSScriptRoot 'rust-toolchain.json') | ConvertFrom-Json
$sableManifest = Get-Content -Raw (Join-Path $PSScriptRoot 'sable-source.json') | ConvertFrom-Json
$sableRevision = $sableManifest.commit
$prepared = Join-Path $work 'prepared'
$rapierDir = Join-Path $prepared 'rapier'
$sablePrepared = Join-Path $prepared 'sable-native'
$privateRapier = Join-Path $work 'rapier-patched'
$privateSable = Join-Path $work 'sable-dll-source'
$sableRustRootRelative = 'sable_rapier/src/main/rust'
$sableRustRoot = Join-Path $privateSable $sableRustRootRelative
$sableRapierSource = Join-Path $privateSable $sableManifest.file
$report = Join-Path $work 'driver-report.json'
$driverLog = Join-Path $work 'native-driver.log'
New-Item -ItemType Directory -Force $work | Out-Null
$script:driverPhase = 'startup'
$script:lastCommand = $null
$script:lastExitCode = $null
$script:rustMetadata = $null
$script:vsMetadata = $null
Set-Content -Encoding utf8 $driverLog "Native candidate driver started: $([DateTime]::UtcNow.ToString('o'))"

function Get-Sha256([string] $Path) {
    $stream = [IO.File]::OpenRead($Path)
    try { $sha = [Security.Cryptography.SHA256]::Create(); try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() } }
    finally { $stream.Dispose() }
}
function Get-Sha256FromString([string] $Text) {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Get-FileHashSha256([string] $Path) { return Get-Sha256 $Path }
function Get-SourceSha256([string] $Path) {
    # Git blob hashes are LF-based; normalize Windows checkout line endings for reproducible comparisons.
    $text = [IO.File]::ReadAllText($Path).Replace("`r`n", "`n")
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($text)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Assert-Hash([string] $Path, [string] $Expected, [string] $Label) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { throw "$Label missing: $Path" }
    $actual = Get-Sha256 $Path
    if ($actual -ne $Expected.ToLowerInvariant()) { throw "$Label SHA-256 mismatch: expected $Expected, got $actual" }
    return $actual
}
function Format-Command([string] $Exe, [string[]] $NativeArgs) {
    $parts = @('"' + $Exe.Replace('"','\"') + '"')
    foreach ($arg in $NativeArgs) { $parts += '"' + ([string]$arg).Replace('"','\"') + '"' }
    return ($parts -join ' ')
}
function Quote-NativeArgument([string] $Argument) {
    $builder = [Text.StringBuilder]::new()
    [void]$builder.Append('"')
    $slashes = 0
    foreach ($character in $Argument.ToCharArray()) {
        if ($character -eq '\') { $slashes++; continue }
        if ($character -eq '"') {
            [void]$builder.Append(('\' * (2 * $slashes + 1)))
            [void]$builder.Append('"')
        } else {
            [void]$builder.Append(('\' * $slashes))
            [void]$builder.Append($character)
        }
        $slashes = 0
    }
    [void]$builder.Append(('\' * (2 * $slashes)))
    [void]$builder.Append('"')
    return $builder.ToString()
}
function Invoke-Native([string] $Exe, [string[]] $NativeArgs, [string] $Directory, [hashtable] $EnvironmentVariables = @{}, [string] $RawArguments = $null, [switch] $SuppressOutputLog) {
    $rawWasExplicit = $PSBoundParameters.ContainsKey('RawArguments')
    if ($rawWasExplicit -and [string]::IsNullOrEmpty($RawArguments)) { throw 'Explicit raw command-line mode requires a non-empty RawArguments string' }
    $useRawArguments = $rawWasExplicit -and ![string]::IsNullOrEmpty($RawArguments)
    $script:lastCommand = Format-Command $Exe $NativeArgs
    Add-Content -Encoding utf8 $driverLog "`n[$($script:driverPhase)] CWD=$Directory`nCOMMAND=$($script:lastCommand)"
    if ($null -ne $RawArguments) { Add-Content -Encoding utf8 $driverLog "RAW_ARGUMENTS=$RawArguments" }
    $process = New-Object Diagnostics.Process
    try {
        $start = New-Object Diagnostics.ProcessStartInfo
        $start.FileName = $Exe
        $start.Arguments = if ($useRawArguments) { $RawArguments } else { (($NativeArgs | ForEach-Object { Quote-NativeArgument ([string]$_) }) -join ' ') }
        Add-Content -Encoding utf8 $driverLog "LAUNCH_MODE=$(if ($useRawArguments) {'raw-cmd'} else {'quoted-native-argv'})`nPROCESS_ARGUMENTS=$($start.Arguments)"
        $start.WorkingDirectory = $Directory
        $start.UseShellExecute = $false
        $start.CreateNoWindow = $true
        $start.RedirectStandardOutput = $true
        $start.RedirectStandardError = $true
        foreach ($name in $EnvironmentVariables.Keys) { $start.EnvironmentVariables[$name] = [string]$EnvironmentVariables[$name] }
        $process.StartInfo = $start
        if (!$process.Start()) { throw 'Process.Start returned false' }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $stdout = $stdoutTask.Result
        $stderr = $stderrTask.Result
        $script:lastExitCode = $process.ExitCode
        foreach ($line in ($stdout -split "`r?`n" | Where-Object { $_ -ne '' })) {
            if (!$SuppressOutputLog) { Write-Host $line; Add-Content -Encoding utf8 $driverLog "STDOUT: $line" }
        }
        foreach ($line in ($stderr -split "`r?`n" | Where-Object { $_ -ne '' })) { Write-Host $line; Add-Content -Encoding utf8 $driverLog "STDERR: $line" }
        Add-Content -Encoding utf8 $driverLog "EXIT=$($script:lastExitCode)"
        return [pscustomobject]@{ ExitCode=$process.ExitCode; StdOut=$stdout; StdErr=$stderr }
    } catch {
        $script:lastExitCode = if ($null -ne $process -and $process.HasExited) { $process.ExitCode } else { -1 }
        Add-Content -Encoding utf8 $driverLog "COMMAND_EXCEPTION=$($_.Exception.Message)`nEXIT=$($script:lastExitCode)"
        throw
    } finally { $process.Dispose() }
}
function Import-BatchEnvironment([string] $BatchPath, [string] $BatchArguments, [string] $Directory) {
    if (!(Test-Path -LiteralPath $BatchPath -PathType Leaf)) { throw "Environment batch file missing: $BatchPath" }
    # cmd.exe /s /c uses its own quote grammar: do not CRT-escape the embedded command quotes.
    $raw = '/d /s /c ""' + $BatchPath + '"' + $(if ($BatchArguments) { " $BatchArguments" }) + ' >nul && set"'
    $result = Invoke-Native $env:ComSpec @() $Directory @{} $raw -SuppressOutputLog
    if ($result.ExitCode -ne 0) { throw "Environment setup batch failed (exit $($result.ExitCode)): $BatchPath $BatchArguments; stderr: $($result.StdErr.Trim())" }
    $environment = @{}
    foreach ($line in ($result.StdOut -split "`r?`n")) {
        if (!$line -or $line.StartsWith('=')) { continue }
        $separator = $line.IndexOf('=')
        if ($separator -gt 0) { $environment[$line.Substring(0,$separator)] = $line.Substring($separator + 1) }
    }
    if (!$environment.Count) { throw "Environment batch succeeded but returned no environment: $BatchPath" }
    Add-Content -Encoding utf8 $driverLog "IMPORTED_CHILD_ENVIRONMENT_KEYS=$($environment.Keys -join ',')"
    return $environment
}
function Get-Vs2019X64Environment {
    $vsDevCmd = 'C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\Common7\Tools\VsDevCmd.bat'
    $environment = Import-BatchEnvironment $vsDevCmd '-no_logo -arch=x64 -host_arch=x64' $repoRoot
    foreach ($name in @('VCToolsInstallDir','WindowsSdkDir','WindowsSDKVersion')) {
        if (!$environment.ContainsKey($name) -or !$environment[$name]) { throw "VsDevCmd did not initialize required child environment variable $name" }
    }
    $whereCl = Invoke-Native $env:ComSpec @() $repoRoot $environment '/d /s /c "where cl.exe"'
    if ($whereCl.ExitCode -ne 0) { throw "VsDevCmd x64 environment cannot locate cl.exe (exit $($whereCl.ExitCode)): $($whereCl.StdErr.Trim())" }
    $clPath = ($whereCl.StdOut -split "`r?`n" | Where-Object { $_ -match '\\cl\.exe$' } | Select-Object -First 1)
    if (!$clPath -or $clPath -notmatch '14\.29\.30133') { throw "Unexpected VS compiler path from VsDevCmd: $clPath" }
    return [pscustomobject]@{ Variables=$environment; Metadata=[ordered]@{ vc_tools_install_dir=$environment['VCToolsInstallDir']; windows_sdk_dir=$environment['WindowsSdkDir']; windows_sdk_version=$environment['WindowsSDKVersion']; cl_path=$clPath.Trim() } }
}
function Invoke-Checked([string] $Exe, [string[]] $NativeArgs, [string] $Directory) {
    $result = Invoke-Native $Exe $NativeArgs $Directory
    if ($result.ExitCode -ne 0) { throw "Command failed (exit $($result.ExitCode)): $($script:lastCommand); stderr: $($result.StdErr.Trim())" }
}
function Git-Head([string] $Directory) {
    $result = Invoke-Native 'git' @('-C',$Directory,'rev-parse','HEAD') $repoRoot
    if ($result.ExitCode -ne 0) { throw "Git revision command failed (exit $($result.ExitCode)): $($script:lastCommand); stderr: $($result.StdErr.Trim())" }
    return $result.StdOut.Trim()
}
function Git-Status([string] $Directory, [string[]] $StatusArgs) {
    $nativeArgs = @('-C',$Directory,'status') + $StatusArgs
    $result = Invoke-Native 'git' $nativeArgs $repoRoot
    if ($result.ExitCode -ne 0) { throw "Git status failed (exit $($result.ExitCode)): $($script:lastCommand); stderr: $($result.StdErr.Trim())" }
    return ,@($result.StdOut -split "`r?`n" | Where-Object { $_ -ne '' })
}
function Assert-CleanPinned([string] $Directory, [string] $Revision, [string] $Label) {
    if (!(Test-Path $Directory)) { return $false }
    if ((Git-Head $Directory) -ne $Revision) { throw "$Label checkout exists at a different revision; refusing to overwrite: $Directory" }
    $status = Git-Status $Directory @('--porcelain')
    if ($status) { throw "$Label checkout is dirty; refusing to overwrite: $Directory; status: $($status -join '; ')" }
    return $true
}
function Write-Report([string] $Status, [hashtable] $Extra = @{}) {
    $record = [ordered]@{ stage=$Stage; status=$Status; phase=$script:driverPhase; last_command=$script:lastCommand; exit_code=$script:lastExitCode; log=$driverLog; local_rust=$script:rustMetadata; windows_build_environment=$script:vsMetadata; sable_source_pin=$sableRevision; sable_checkout='deferred-to-dll-stage'; rapier_revision=$manifest.revision; patch_sha256=$manifest.patch_sha256; toolchain=$rust.toolchain; target=$rust.target; updated_utc=[DateTime]::UtcNow.ToString('o') }
    foreach ($key in $Extra.Keys) { $record[$key] = $Extra[$key] }
    $record | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 $report
}
function Parse-CargoTestSummary([string[]] $Lines, [int] $ExpectedTests = 2) {
    $summaries = @($Lines | Where-Object { $_ -match '^test result:' })
    if ($summaries.Count -ne 1) { throw "Expected exactly one Cargo test result summary, found $($summaries.Count)" }
    $pattern = '^test result: (?<state>ok|FAILED)\.\s+(?<passed>\d+) passed;\s+(?<failed>\d+) failed;\s+(?<ignored>\d+) ignored;(?:\s+\d+ measured;)?\s+(?<filtered>\d+) filtered out(?:;.*)?$'
    if ($summaries[0] -notmatch $pattern) { throw "Malformed Cargo test result summary: $($summaries[0])" }
    $result = [ordered]@{ passed=[int]$Matches.passed; failed=[int]$Matches.failed; ignored=[int]$Matches.ignored; filtered=[int]$Matches.filtered; summary=$summaries[0] }
    if ($Matches.state -ne 'ok' -or $result.failed -ne 0) { throw "Cargo regression test summary reports failure: $($summaries[0])" }
    if ($result.passed -lt $ExpectedTests) { throw "Only $($result.passed) regression tests passed; at least $ExpectedTests are required" }
    return $result
}
function Get-LockPackages([string] $LockText) {
    $packages = @()
    $stanzas = [regex]::Matches($LockText, '(?ms)^\[\[package\]\]\r?\n(?<body>.*?)(?=^\[\[package\]\]|\z)')
    foreach ($stanza in $stanzas) {
        $body = $stanza.Groups['body'].Value
        $nameMatch = [regex]::Match($body, '(?m)^name = "(?<value>[^"]+)"\r?$')
        $versionMatch = [regex]::Match($body, '(?m)^version = "(?<value>[^"]+)"\r?$')
        $sourceMatch = [regex]::Match($body, '(?m)^source = "(?<value>[^"]+)"\r?$')
        $checksumMatch = [regex]::Match($body, '(?m)^checksum = "(?<value>[^"]+)"\r?$')
        if (!$nameMatch.Success -or !$versionMatch.Success) { throw 'Malformed Cargo.lock package stanza (missing name/version)' }
        $dependencies = @()
        $dependencyBlock = [regex]::Match($body, '(?ms)^dependencies = \[\r?\n(?<items>.*?)^\]')
        if ($dependencyBlock.Success) {
            foreach ($dependency in [regex]::Matches($dependencyBlock.Groups['items'].Value, '(?m)^\s+"(?<value>[^"]+)"[,]?\r?$')) { $dependencies += $dependency.Groups['value'].Value }
        }
        $packages += [pscustomobject]@{
            Name=$nameMatch.Groups['value'].Value
            Version=$versionMatch.Groups['value'].Value
            Source=if ($sourceMatch.Success) { $sourceMatch.Groups['value'].Value } else { $null }
            Checksum=if ($checksumMatch.Success) { $checksumMatch.Groups['value'].Value } else { $null }
            Dependencies=$dependencies
        }
    }
    if (!$packages.Count) { throw 'Cargo.lock contains no parseable package stanzas' }
    # Return each record through the pipeline. A unary-comma return wraps the whole array
    # as one object in callers, causing Where-Object to compare array-valued .Name fields.
    return $packages
}
function Get-CargoLockIdentity([string] $Text) {
    $packages = @(Get-LockPackages $Text)
    $identities = @($packages | ForEach-Object {
        [pscustomobject]@{ Name=$_.Name; Version=$_.Version; Source=$_.Source; Checksum=$_.Checksum; Dependencies=(@($_.Dependencies) -join ',') }
    })
    return $identities
}
function Assert-SableLockSourceOnlyChange([string] $Before, [string] $After, [string] $ExpectedRapierSource) {
    $beforePackages = @(Get-CargoLockIdentity $Before)
    $afterPackages = @(Get-CargoLockIdentity $After)
    $beforeRapier = @($beforePackages | Where-Object { $_.Name -eq 'rapier3d' -and $_.Version -eq '0.32.0' })
    $afterRapier = @($afterPackages | Where-Object { $_.Name -eq 'rapier3d' -and $_.Version -eq '0.32.0' })
    if ($beforeRapier.Count -ne 1 -or $beforeRapier[0].Source -ne $ExpectedRapierSource -or $afterRapier.Count -ne 1 -or $afterRapier[0].Source) {
        throw 'Shipping lock must replace exactly the pinned Rapier git source identity with the local path identity'
    }
    if ($beforePackages.Count -ne $afterPackages.Count) { throw 'Shipping Cargo.lock package record count changed during source substitution' }
    $beforeOthers = @($beforePackages | Where-Object { $_.Name -ne 'rapier3d' } | ForEach-Object { "$($_.Name)|$($_.Version)|$($_.Source)|$($_.Checksum)|$($_.Dependencies)" } | Sort-Object)
    $afterOthers = @($afterPackages | Where-Object { $_.Name -ne 'rapier3d' } | ForEach-Object { "$($_.Name)|$($_.Version)|$($_.Source)|$($_.Checksum)|$($_.Dependencies)" } | Sort-Object)
    $otherChanges = @(Compare-Object -ReferenceObject $beforeOthers -DifferenceObject $afterOthers | ForEach-Object { [ordered]@{ package_record=$_.InputObject; direction=$_.SideIndicator } })
    if ($otherChanges.Count) { throw "Shipping lock source substitution changed non-Rapier records: $($otherChanges | ConvertTo-Json -Compress)" }
    if (($beforeRapier[0].Version -ne $afterRapier[0].Version) -or
        ($beforeRapier[0].Checksum -ne $afterRapier[0].Checksum) -or
        ($beforeRapier[0].Dependencies -ne $afterRapier[0].Dependencies)) {
        throw 'Rapier lock package version/checksum/dependency graph changed; only git-to-path source identity is allowed'
    }
    $sablePackages = @($afterPackages | Where-Object { $_.Name -eq 'sable_rapier' })
    if ($sablePackages.Count -ne 1 -or @($sablePackages[0].Dependencies -split ',' | Where-Object { $_ -eq 'rapier3d' }).Count -ne 1) {
        throw 'Sable package lock dependency must remain the unchanged rapier3d package reference'
    }
    return [pscustomobject]@{ package_count=$afterPackages.Count; rapier_version=$afterRapier[0].Version; rapier_source_before=$beforeRapier[0].Source; rapier_source_after='local path'; non_rapier_package_records='identical' }
}
function Rewrite-SableRapierDependency([string] $CargoToml, [string] $RapierCargoPath, [string] $ExpectedRevision) {
    $pattern = '(?ms)^rapier3d\s*=\s*\{\s*git\s*=\s*"https://github\.com/ryanhcode/rapier",\s*rev\s*=\s*"' + [regex]::Escape($ExpectedRevision) + '",\s*features\s*=\s*\[\s*"simd-nightly",\s*"parallel",?\s*\]\s*\}\s*'
    $matches = [regex]::Matches($CargoToml,$pattern)
    if ($matches.Count -ne 1) { throw "Expected exactly one Sable Rapier dependency matching pinned URL/revision/features; found $($matches.Count)" }
    $forwardPath = $RapierCargoPath.Replace('\','/')
    $replacement = 'rapier3d = { path = "' + $forwardPath + '", features = ["simd-nightly", "parallel"] }' + "`n"
    $match = $matches[0]
    $rewritten = $CargoToml.Substring(0,$match.Index) + $replacement + $CargoToml.Substring($match.Index + $match.Length)
    if ($rewritten -eq $CargoToml) { throw 'Sable manifest Rapier path override produced no change' }
    return [pscustomobject]@{ Text=$rewritten; Path=$forwardPath; Before=$CargoToml; After=$rewritten }
}
function Rewrite-SableRapierLockSource([string] $CargoLock, [string] $ExpectedRapierSource) {
    $packages = @(Get-LockPackages $CargoLock)
    $rapier = @($packages | Where-Object { $_.Name -eq 'rapier3d' -and $_.Version -eq '0.32.0' })
    if ($rapier.Count -ne 1 -or $rapier[0].Source -ne $ExpectedRapierSource -or $rapier[0].Checksum) { throw 'Pinned production Sable lock does not contain the expected single Rapier git source package' }
    $sourceLine = '(?m)^source = "' + [regex]::Escape($ExpectedRapierSource) + '"\r?\n'
    $packagePattern = '(?ms)(^\[\[package\]\]\r?\nname = "rapier3d"\r?\nversion = "0\.32\.0"\r?\n)' + $sourceLine
    if ([regex]::Matches($CargoLock,$packagePattern).Count -ne 1) { throw 'Expected exactly one source line for pinned Rapier in production lock' }
    $lockMatch = [regex]::Match($CargoLock,$packagePattern)
    $rewritten = $CargoLock.Substring(0,$lockMatch.Groups[1].Index) + $lockMatch.Groups[1].Value + $CargoLock.Substring($lockMatch.Index + $lockMatch.Length)
    $identity = Assert-SableLockSourceOnlyChange $CargoLock $rewritten $ExpectedRapierSource
    return [pscustomobject]@{ Text=$rewritten; Identity=$identity }
}
function Assert-UnitEvidenceMetadata($Evidence, $RapierManifest) {
    if ($Evidence.status -ne 'tests-passed' -or $Evidence.passed -ne 2 -or $Evidence.failed -ne 0 -or $Evidence.filtered -lt 1) {
        throw 'Preserved unit evidence must prove at least two passed, zero failed, and a filtered focused test run'
    }
    if ($Evidence.source_revision -ne $RapierManifest.revision -or
        $Evidence.patch_sha256 -ne $RapierManifest.patch_sha256 -or
        $Evidence.patched_source_sha256 -ne $RapierManifest.patched_source_sha256) {
        throw 'Preserved Rust unit proof is stale for current pinned Rapier source/patch'
    }
    return $true
}
function Read-VerifiedUnitEvidence {
    $path = Join-Path $work "evidence/rapier-unit-$($manifest.patch_sha256).json"
    if (!(Test-Path $path)) { throw "Hash-bound managed unit evidence missing: $path" }
    $evidence = Get-Content -Raw $path | ConvertFrom-Json
    $null = Assert-UnitEvidenceMetadata $evidence $manifest
    $testLog = Join-Path $repoRoot $evidence.test_log
    $testLogHash = Assert-Hash $testLog $evidence.test_log_sha256 'Managed unit test log evidence'
    $summary = Parse-CargoTestSummary @(Get-Content $testLog) 2
    if ($summary.passed -ne $evidence.passed -or $summary.failed -ne $evidence.failed -or $summary.filtered -ne $evidence.filtered) {
        throw 'Hash-bound unit evidence counts differ from the preserved Cargo test log'
    }
    return [pscustomobject]@{ Evidence=$evidence; Path=$path; EvidenceSha256=(Get-Sha256 $path); TestLogSha256=$testLogHash; Counts=$summary }
}
function Get-PeMachineFromBytes([byte[]] $Bytes) {
    if ($Bytes.Length -lt 70 -or $Bytes[0] -ne 0x4d -or $Bytes[1] -ne 0x5a) { throw 'Native DLL is missing a valid DOS MZ header' }
    $peOffset = [BitConverter]::ToInt32($Bytes,0x3c)
    if ($peOffset -lt 64 -or $peOffset + 6 -gt $Bytes.Length) { throw 'Native DLL PE header offset is out of bounds' }
    if ($Bytes[$peOffset] -ne 0x50 -or $Bytes[$peOffset+1] -ne 0x45 -or $Bytes[$peOffset+2] -ne 0 -or $Bytes[$peOffset+3] -ne 0) { throw 'Native DLL has invalid PE signature' }
    return [BitConverter]::ToUInt16($Bytes,$peOffset + 4)
}
function Get-PeMachine([string] $Path) {
    return Get-PeMachineFromBytes ([IO.File]::ReadAllBytes($Path))
}
function Add-ValidatedCargoExitCodeToReceipt([System.Collections.IDictionary] $Receipt, [int] $CargoExitCode) {
    if ($CargoExitCode -ne 0) { throw "Cannot create a successful DLL receipt for Cargo exit code $CargoExitCode" }
    $Receipt['cargo_exit_code'] = $CargoExitCode
    return $Receipt
}
function Merge-MetadataOverlay([System.Collections.IDictionary] $Base, [System.Collections.IDictionary] $Overlay) {
    $merged = @{}
    foreach ($key in $Base.Keys) { $merged[$key] = $Base[$key] }
    foreach ($key in $Overlay.Keys) { $merged[$key] = $Overlay[$key] }
    return $merged
}
function New-DllBuildMetadata([System.Collections.IDictionary] $PreparedInfo, [System.Collections.IDictionary] $BuildOverlay) {
    return Merge-MetadataOverlay $PreparedInfo $BuildOverlay
}
function Complete-ValidatedDllReceipt([System.Collections.IDictionary] $Receipt, [int] $CargoExitCode, [int] $PeMachine, [bool] $UnitProofValid, [bool] $PrepareOnly) {
    if ($PrepareOnly) { throw 'PrepareOnly cannot produce a built DLL receipt' }
    if ($CargoExitCode -ne 0) { throw "Cannot create a successful DLL receipt for Cargo exit code $CargoExitCode" }
    if ($PeMachine -ne 0x8664) { throw ('Cannot create a successful DLL receipt for PE machine 0x{0:X4}' -f $PeMachine) }
    if (!$UnitProofValid) { throw 'Cannot create a successful DLL receipt without verified managed Rust unit evidence' }
    $built = Merge-MetadataOverlay $Receipt ([ordered]@{ status='built'; cargo_exit_code=$CargoExitCode; pe_machine='AMD64 (0x8664)' })
    return $built
}
function Prepare-SableSource {
    $patch = Join-Path $repoRoot $sableManifest.patch
    $patchHash = Assert-Hash $patch $sableManifest.patchSha256 'Canonical Sable source patch'
    if (!(Assert-CleanPinned $sablePrepared $sableRevision 'Sparse Sable source')) {
        New-Item -ItemType Directory -Force $prepared | Out-Null
        Invoke-Checked 'git' @('clone','--filter=blob:none','--no-checkout',$sableManifest.source,$sablePrepared) $repoRoot
        Invoke-Checked 'git' @('-C',$sablePrepared,'config','core.longpaths','true') $repoRoot
        Invoke-Checked 'git' @('-C',$sablePrepared,'config','core.autocrlf','false') $repoRoot
        Invoke-Checked 'git' @('-C',$sablePrepared,'fetch','--no-tags','origin',"refs/tags/$($sableManifest.tag):refs/tags/$($sableManifest.tag)") $repoRoot
        $tagCommit = Invoke-Native 'git' @('-C',$sablePrepared,'rev-parse',"refs/tags/$($sableManifest.tag)^{commit}") $repoRoot
        if ($tagCommit.ExitCode -ne 0 -or $tagCommit.StdOut.Trim() -ne $sableRevision) { throw "Pinned Sable tag does not resolve to declared commit: $($tagCommit.StdOut.Trim())" }
        Invoke-Checked 'git' @('-C',$sablePrepared,'sparse-checkout','init','--cone') $repoRoot
        Invoke-Checked 'git' @('-C',$sablePrepared,'sparse-checkout','set','--cone','sable_rapier/src/main/rust','sable_rapier/build.gradle','LICENSE.md') $repoRoot
        Invoke-Checked 'git' @('-C',$sablePrepared,'checkout','--detach',$sableRevision) $repoRoot
    }
    Invoke-Checked 'git' @('-C',$sablePrepared,'config','core.longpaths','true') $repoRoot
    Invoke-Checked 'git' @('-C',$sablePrepared,'config','core.autocrlf','false') $repoRoot
    if ((Git-Head $sablePrepared) -ne $sableRevision) { throw 'Sparse Sable checkout is not at the pinned commit' }
    $sourcePath = Join-Path $sablePrepared $sableManifest.file
    if (!(Test-Path $sourcePath) -or !(Test-Path (Join-Path $sablePrepared "$sableRustRootRelative/Cargo.lock")) -or !(Test-Path (Join-Path $sablePrepared 'sable_rapier/build.gradle'))) {
        throw 'Sparse Sable checkout is missing a required Rust source, locked workspace, or native build metadata path'
    }
    $status = Git-Status $sablePrepared @('--porcelain','--untracked-files=all')
    if ($status) { throw "Sparse Sable pinned checkout is dirty: $($status -join '; ')" }
    $baseHash = Get-SourceSha256 $sourcePath
    if ($baseHash -ne $sableManifest.baseSha256) { throw "Pinned Sable source hash mismatch: expected $($sableManifest.baseSha256), got $baseHash" }

    if (!(Test-Path $privateSable)) {
        New-Item -ItemType Directory -Force $privateSable | Out-Null
        Copy-Item -LiteralPath (Join-Path $sablePrepared 'sable_rapier') -Destination $privateSable -Recurse
        Copy-Item -LiteralPath (Join-Path $sablePrepared 'LICENSE.md') -Destination $privateSable
    }
    $privateSource = Join-Path $privateSable $sableManifest.file
    $privateBaseHash = Get-SourceSha256 $privateSource
    if ($privateBaseHash -eq $sableManifest.baseSha256) {
        Invoke-Checked 'git' @('-C',$privateSable,'apply','--check',$patch) $repoRoot
        Invoke-Checked 'git' @('-C',$privateSable,'apply',$patch) $repoRoot
    } elseif ($privateBaseHash -ne $sableManifest.patchedSha256) { throw "Private Sable source hash is neither declared base nor patched hash: $privateBaseHash" }
    $patchedHash = Get-SourceSha256 $privateSource
    if ($patchedHash -ne $sableManifest.patchedSha256) { throw "Patched Sable source hash mismatch: expected $($sableManifest.patchedSha256), got $patchedHash" }
    $patchWhitespace = Invoke-Native 'git' @('diff','--no-index','--check','--',$sourcePath,$privateSource) $repoRoot
    if ($patchWhitespace.ExitCode -notin @(0,1)) { throw "Sable source patch whitespace validation failed: $($patchWhitespace.StdErr)" }
    $privateRoot = Join-Path $privateSable 'sable_rapier'
    $pinnedRoot = Join-Path $sablePrepared 'sable_rapier'
    $privateFiles = @(Get-ChildItem -LiteralPath $privateRoot -File -Recurse | ForEach-Object { $_.FullName.Substring($privateRoot.Length + 1).Replace('\','/') } | Sort-Object)
    $pinnedFiles = @(Get-ChildItem -LiteralPath $pinnedRoot -File -Recurse | ForEach-Object { $_.FullName.Substring($pinnedRoot.Length + 1).Replace('\','/') } | Sort-Object)
    if (($privateFiles -join "`n") -ne ($pinnedFiles -join "`n")) { throw 'Private Sable source file inventory differs from pinned sparse source' }
    if ((Get-Sha256 (Join-Path $privateSable 'LICENSE.md')) -ne (Get-Sha256 (Join-Path $sablePrepared 'LICENSE.md'))) { throw 'Private Sable license differs from the pinned sparse source' }
    foreach ($relative in $pinnedFiles) {
        if ($relative -eq 'src/main/rust/rapier/src/lib.rs' -or $relative -eq 'src/main/rust/rapier/Cargo.toml' -or $relative -eq 'src/main/rust/Cargo.lock') { continue }
        $pinnedFile = Join-Path $pinnedRoot $relative
        $candidateFile = Join-Path $privateRoot $relative
        if ((Get-Sha256 $pinnedFile) -ne (Get-Sha256 $candidateFile)) { throw "Private Sable source contains an unexpected edit: $relative" }
    }
    return @{ sable_revision=(Git-Head $sablePrepared); sable_tag=$sableManifest.tag; sable_source_base_sha256=$baseHash; sable_source_patched_sha256=$patchedHash; sable_patch_sha256=$patchHash; sable_license=$sableManifest.license }
}
function Prepare-SableCargoInputs {
    $cargoManifest = Join-Path $sableRustRoot 'rapier/Cargo.toml'
    $cargoLock = Join-Path $sableRustRoot 'Cargo.lock'
    $baseCargoManifest = Join-Path (Join-Path $sablePrepared $sableRustRootRelative) 'rapier/Cargo.toml'
    $baseCargoLock = Join-Path (Join-Path $sablePrepared $sableRustRootRelative) 'Cargo.lock'
    $rapierCargoDir = Join-Path $privateRapier 'crates/rapier3d'
    $manifestBefore = [IO.File]::ReadAllText($baseCargoManifest)
    $lockBefore = [IO.File]::ReadAllText($baseCargoLock)
    $sourceId = "git+https://github.com/ryanhcode/rapier?rev=$($manifest.revision)#$($manifest.revision)"
    $rewrittenManifest = Rewrite-SableRapierDependency $manifestBefore $rapierCargoDir $manifest.revision
    $rewrittenLock = Rewrite-SableRapierLockSource $lockBefore $sourceId
    $manifestCurrent = [IO.File]::ReadAllText($cargoManifest)
    $lockCurrent = [IO.File]::ReadAllText($cargoLock)
    if ($manifestCurrent -ne $manifestBefore -and $manifestCurrent -ne $rewrittenManifest.Text) { throw 'Private Sable Cargo.toml has unrelated/unrecognized modifications' }
    if ($lockCurrent -ne $lockBefore -and $lockCurrent -ne $rewrittenLock.Text) { throw 'Private Sable Cargo.lock has unrelated/unrecognized modifications' }
    if ($manifestCurrent -ne $rewrittenManifest.Text) { [IO.File]::WriteAllText($cargoManifest,$rewrittenManifest.Text,[Text.UTF8Encoding]::new($false)) }
    if ($lockCurrent -ne $rewrittenLock.Text) { [IO.File]::WriteAllText($cargoLock,$rewrittenLock.Text,[Text.UTF8Encoding]::new($false)) }
    if ((Get-SourceSha256 (Join-Path $privateSable $sableManifest.file)) -ne $sableManifest.patchedSha256) { throw 'Private Sable source changed after patch validation' }
    return @{
        shipping_manifest_original_sha256=(Get-Sha256FromString $manifestBefore)
        shipping_manifest_candidate_sha256=(Get-FileHashSha256 $cargoManifest)
        shipping_lock_original_sha256=(Get-Sha256FromString $lockBefore)
        shipping_lock_candidate_sha256=(Get-FileHashSha256 $cargoLock)
        shipping_lock_identity=$rewrittenLock.Identity
        shipping_lock_scope='official Sable lock; only pinned Rapier git source identity converted to local patched path'
        rapier_path=$rewrittenManifest.Path
    }
}
function Get-UnitLockPinPlan([string] $LockText, [string] $ExpectedVersion, [string] $ExpectedChecksum) {
    $packages = Get-LockPackages $LockText
    $parryPackages = @($packages | Where-Object { $_.Name -eq 'parry3d' })
    if ($parryPackages.Count -ne 1) { throw "Expected exactly one parry3d package in unit lock; found $($parryPackages.Count)" }
    $rapierPackages = @($packages | Where-Object { $_.Name -eq 'rapier3d' -and $_.Version -eq '0.32.0' })
    if ($rapierPackages.Count -ne 1) { throw "Expected exactly one rapier3d 0.32.0 package; found $($rapierPackages.Count)" }
    $parryDependency = @($rapierPackages[0].Dependencies | Where-Object { $_ -match '^parry3d(?:\s|$)' })
    if ($parryDependency.Count -ne 1) { throw "rapier3d 0.32.0 must select exactly one parry3d dependency; found $($parryDependency.Count)" }
    if ($parryDependency[0] -match '^parry3d\s+(?<version>\S+)$' -and $Matches.version -ne $ExpectedVersion) {
        throw "rapier3d lock dependency explicitly selects parry3d $($Matches.version), not pinned $ExpectedVersion"
    }
    $parry = $parryPackages[0]
    if ($parry.Version -eq $ExpectedVersion) {
        if ($parry.Checksum -ne $ExpectedChecksum) { throw "Pinned parry3d $ExpectedVersion checksum mismatch: $($parry.Checksum)" }
        return [pscustomobject]@{ Action='reuse'; ParryVersion=$parry.Version; ParryChecksum=$parry.Checksum }
    }
    return [pscustomobject]@{ Action='precise-pin'; ParryVersion=$parry.Version; ParryChecksum=$parry.Checksum }
}
if ($CheckSummaryParser) {
    $cases = @(
        @{ lines=@('test result: ok. 2 passed; 0 failed; 1 ignored; 4 measured; 9 filtered out; finished in 0.01s'); pass=$true },
        @{ lines=@('test result: ok. 0 passed; 0 failed; 0 ignored; 0 measured; 14 filtered out; finished in 0.00s'); pass=$false },
        @{ lines=@('test result: FAILED. 2 passed; 1 failed; 0 ignored; 0 measured; 8 filtered out; finished in 0.01s'); pass=$false },
        @{ lines=@('test result: ok. two passed; 0 failed; 0 ignored; 0 measured; 8 filtered out'); pass=$false },
        @{ lines=@(); pass=$false }
    )
    foreach ($case in $cases) {
        $accepted = $true
        try { $null = Parse-CargoTestSummary $case.lines } catch { $accepted = $false }
        if ($accepted -ne $case.pass) { throw "Summary-parser fixture failed: $($case.lines -join '')" }
    }
    $expectedParryChecksum = $manifest.dependency_api.sha256
    $lockFixturePrefix = "[[package]]`nname = `"rapier3d`"`nversion = `"0.32.0`"`ndependencies = [`n `"nalgebra 0.34.1`",`n `"parry3d`",`n]`n`n[[package]]`nname = `"parry3d`"`nversion = `"0.26.1`"`nsource = `"registry+https://github.com/rust-lang/crates.io-index`"`nchecksum = `"wrong-version-checksum`"`n"
    $lockTests = @(
        @{ Name='drift-requires-precise-pin'; Text=$lockFixturePrefix; Action='precise-pin' },
        @{ Name='exact-production-leaf-reuses-lock'; Text=$lockFixturePrefix.Replace('0.26.1','0.26.0').Replace('wrong-version-checksum',$expectedParryChecksum).Replace(' "parry3d",',' "parry3d 0.26.0",'); Action='reuse' },
        @{ Name='wrong-checksum-rejected'; Text=$lockFixturePrefix.Replace('0.26.1','0.26.0'); Action='reject' },
        @{ Name='duplicate-parry-rejected'; Text=$lockFixturePrefix + "`n[[package]]`nname = `"parry3d`"`nversion = `"0.26.1`"`nchecksum = `"duplicate`"`n"; Action='reject' },
        @{ Name='explicit-other-selection-rejected'; Text=$lockFixturePrefix.Replace('"parry3d"','"parry3d 0.26.1"'); Action='reject' }
    )
    foreach ($case in $lockTests) {
        $action = 'reject'
        try { $action = (Get-UnitLockPinPlan $case.Text '0.26.0' $expectedParryChecksum).Action } catch { $action = 'reject' }
        if ($action -ne $case.Action) { throw "Cargo.lock fixture '$($case.Name)' expected $($case.Action), got $action" }
    }
    $sableGitSource = "git+https://github.com/ryanhcode/rapier?rev=$($manifest.revision)#$($manifest.revision)"
    $manifestFixture = "[dependencies]`nrapier3d = { git = `"https://github.com/ryanhcode/rapier`", rev = `"$($manifest.revision)`", features = [`n    `"simd-nightly`",`n    `"parallel`",`n] }`nother = `"unchanged`"`n"
    $manifestRewrite = Rewrite-SableRapierDependency $manifestFixture 'C:/local source/rapier/crates/rapier3d' $manifest.revision
    if ($manifestRewrite.Text -notmatch 'path = "C:/local source/rapier/crates/rapier3d"' -or $manifestRewrite.Text -notmatch 'other = "unchanged"') { throw 'Pinned Sable Cargo.toml path override fixture failed' }
    $manifestMismatchRejected = $false
    try { $null = Rewrite-SableRapierDependency $manifestFixture 'C:/local/rapier' 'different-revision' } catch { $manifestMismatchRejected = $true }
    if (!$manifestMismatchRejected) { throw 'Sable Cargo.toml fixture accepted a different Rapier git revision' }
    $shippingLockFixture = @"
version = 4

[[package]]
name = "rapier3d"
version = "0.32.0"
source = "$sableGitSource"
dependencies = [
 "parry3d",
]

[[package]]
name = "sable_rapier"
version = "0.1.0"
dependencies = [
 "rapier3d",
]

[[package]]
name = "parry3d"
version = "0.26.0"
source = "registry+https://github.com/rust-lang/crates.io-index"
checksum = "parry-checksum"

[[package]]
name = "smallvec"
version = "1.15.1"
source = "registry+https://github.com/rust-lang/crates.io-index"
checksum = "smallvec-checksum"
"@
    $shippingLockRewrite = Rewrite-SableRapierLockSource $shippingLockFixture $sableGitSource
    if ($shippingLockRewrite.Identity.package_count -ne 4 -or $shippingLockRewrite.Identity.rapier_source_after -ne 'local path') { throw 'Shipping lock exact Rapier source-identity fixture failed' }
    $otherDriftRejected = $false
    try { $null = Assert-SableLockSourceOnlyChange $shippingLockFixture ($shippingLockRewrite.Text.Replace('smallvec-checksum','unexpected-checksum')) $sableGitSource } catch { $otherDriftRejected = $true }
    if (!$otherDriftRejected) { throw 'Shipping lock fixture failed to reject an unrelated registry checksum change' }
    $evidenceFixturePath = Join-Path $work "evidence/rapier-unit-$($manifest.patch_sha256).json"
    if (Test-Path $evidenceFixturePath) {
        $evidenceFixture = Get-Content -Raw $evidenceFixturePath | ConvertFrom-Json
        $null = Assert-UnitEvidenceMetadata $evidenceFixture $manifest
        $staleEvidence = $evidenceFixture | ConvertTo-Json -Depth 6 | ConvertFrom-Json
        $staleEvidence.patch_sha256 = 'stale-patch'
        $staleRejected = $false
        try { $null = Assert-UnitEvidenceMetadata $staleEvidence $manifest } catch { $staleRejected = $true }
        if (!$staleRejected) { throw 'Stale managed Rust unit proof fixture was accepted' }
    } else { throw "Current-source hash-bound unit proof fixture missing: $evidenceFixturePath" }
    $multiPackageLock = $lockFixturePrefix + "`n[[package]]`nname = `"nalgebra`"`nversion = `"0.34.1`"`nchecksum = `"nalgebra-checksum`"`n"
    $parsedFixtureRecords = @(Get-LockPackages $multiPackageLock)
    $fixtureRecordNames = @($parsedFixtureRecords | ForEach-Object { $_.Name } | Sort-Object)
    if ($parsedFixtureRecords.Count -ne 3 -or ($fixtureRecordNames -join ',') -ne 'nalgebra,parry3d,rapier3d') {
        throw "Cargo.lock package enumeration fixture expected 3 individual records (nalgebra,parry3d,rapier3d), got count=$($parsedFixtureRecords.Count) names=$($fixtureRecordNames -join ',')"
    }
    $beforeNonParry = @($parsedFixtureRecords | Where-Object { $_.Name -ne 'parry3d' } | ForEach-Object { "$($_.Name)|$($_.Version)|$($_.Checksum)" } | Sort-Object)
    $pinnedFixtureLock = $multiPackageLock.Replace('0.26.1','0.26.0').Replace('wrong-version-checksum',$expectedParryChecksum)
    $afterPinRecords = @(Get-LockPackages $pinnedFixtureLock)
    $afterNonParry = @($afterPinRecords | Where-Object { $_.Name -ne 'parry3d' } | ForEach-Object { "$($_.Name)|$($_.Version)|$($_.Checksum)" } | Sort-Object)
    if (($beforeNonParry -join "`n") -ne ($afterNonParry -join "`n")) { throw 'Three-package precise-pin fixture falsely detected non-Parry drift' }
    $driftedOtherLock = $pinnedFixtureLock.Replace("name = `"nalgebra`"`nversion = `"0.34.1`"", "name = `"nalgebra`"`nversion = `"0.34.2`"")
    $driftedRecords = @(Get-LockPackages $driftedOtherLock)
    $driftedNonParry = @($driftedRecords | Where-Object { $_.Name -ne 'parry3d' } | ForEach-Object { "$($_.Name)|$($_.Version)|$($_.Checksum)" } | Sort-Object)
    if (($beforeNonParry -join "`n") -eq ($driftedNonParry -join "`n")) { throw 'Three-package fixture failed to detect changed non-Parry package version' }
    $existingUnitLock = Join-Path $work 'rapier-patched/Cargo.lock'
    if (Test-Path $existingUnitLock) {
        $actualPlan = Get-UnitLockPinPlan (Get-Content -Raw $existingUnitLock) '0.26.0' $expectedParryChecksum
        if ($actualPlan.Action -notin @('reuse','precise-pin')) { throw 'Existing unit Cargo.lock did not produce a recognized pin plan' }
        Write-Host "Existing ignored unit Cargo.lock inspection: parry3d $($actualPlan.ParryVersion), action=$($actualPlan.Action); no Cargo command executed"
    }
    $realUnitProof = Read-VerifiedUnitEvidence
    if ($realUnitProof.Counts.passed -ne 2 -or $realUnitProof.Counts.failed -ne 0) { throw 'Current hash-bound managed unit evidence did not validate' }
    $staleEvidenceRejected = $false
    $staleProof = $realUnitProof.Evidence | ConvertTo-Json -Depth 8 | ConvertFrom-Json
    $staleProof.patched_source_sha256 = 'stale-source'
    try { $null = Assert-UnitEvidenceMetadata $staleProof $manifest } catch { $staleEvidenceRejected = $true }
    if (!$staleEvidenceRejected) { throw 'Stale unit evidence fixture was accepted' }
    $gitVersion = Invoke-Native 'git' @('--version') $repoRoot
    if ($gitVersion.ExitCode -ne 0 -or $gitVersion.StdOut -notmatch '^git version ') { throw "Quoted-native-argv git --version fixture failed (exit $($gitVersion.ExitCode))" }
    $gitHead = Invoke-Native 'git' @('-C',$repoRoot,'rev-parse','HEAD') $repoRoot
    $expectedGitHead = (& git -C $repoRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $gitHead.ExitCode -ne 0 -or $gitHead.StdOut.Trim() -ne $expectedGitHead) { throw 'Quoted-native-argv git path-with-spaces/rev-parse fixture failed' }
    $pythonExe = (Get-Command 'python.exe' -ErrorAction Stop).Source
    $pythonCode = 'import json,sys; print(json.dumps(sys.argv[1:]))'
    $argvFixture = @('path with spaces','quote "inside"','', 'C:\native fixture\ends\')
    $pythonProbe = Invoke-Native $pythonExe (@('-c',$pythonCode) + $argvFixture) $repoRoot
    if ($pythonProbe.ExitCode -ne 0) { throw "Quoted-native-argv child probe failed (exit $($pythonProbe.ExitCode)): $($pythonProbe.StdErr)" }
    $reportedArgv = @(($pythonProbe.StdOut.Trim() | ConvertFrom-Json))
    if ($reportedArgv.Count -ne $argvFixture.Count) { throw "Quoted-native-argv child returned $($reportedArgv.Count) arguments, expected $($argvFixture.Count)" }
    for ($index = 0; $index -lt $argvFixture.Count; $index++) {
        if ($reportedArgv[$index] -cne $argvFixture[$index]) { throw "Quoted-native-argv child argument $index mismatch: expected [$($argvFixture[$index])], got [$($reportedArgv[$index])]" }
    }
    $emptyRawRejected = $false
    try { $null = Invoke-Native 'git' @('--version') $repoRoot @{} '' } catch { $emptyRawRejected = $_.Exception.Message.Contains('Explicit raw command-line mode requires a non-empty') }
    if (!$emptyRawRejected) { throw 'Explicit empty raw-argument mode was not rejected' }
    Write-Host 'Quoted-native-argv fixtures: git version, spaced repo path/revision, child argv with spaces/quotes/empty/trailing-backslash, and explicit-empty-raw rejection PASS'
    $fixtureDir = Join-Path $work 'native launcher fixture (spaces)'
    New-Item -ItemType Directory -Force $fixtureDir | Out-Null
    $fixtureBatch = Join-Path $fixtureDir 'set fixture environment.bat'
    $probeBatch = Join-Path $fixtureDir 'argument probe (spaces).bat'
    $failureBatch = Join-Path $fixtureDir 'expected failure.bat'
    Set-Content -Encoding ascii $fixtureBatch "@echo off`r`nset MS14_NATIVE_FIXTURE=from-batch`r`nexit /b 0`r`n"
    Set-Content -Encoding ascii $probeBatch "@echo off`r`necho ENV=%MS14_NATIVE_FIXTURE%`r`necho ARG1=%~1`r`necho ARG2=%~2`r`nexit /b 0`r`n"
    Set-Content -Encoding ascii $failureBatch "@echo off`r`nexit /b 7`r`n"
    $originalFixtureValue = [Environment]::GetEnvironmentVariable('MS14_NATIVE_FIXTURE','Process')
    $fixtureEnvironment = Import-BatchEnvironment $fixtureBatch '' $fixtureDir
    if ($fixtureEnvironment['MS14_NATIVE_FIXTURE'] -ne 'from-batch' -or [Environment]::GetEnvironmentVariable('MS14_NATIVE_FIXTURE','Process') -ne $originalFixtureValue) {
        throw 'Batch child environment was not captured or leaked into the parent process'
    }
    $script:driverPhase = 'driver-fixture'
    $script:lastExitCode = $null
    $probeRaw = '/d /s /c ""' + $probeBatch + '" "' + (Join-Path $PSScriptRoot 'rapier-source.json') + '" "argument with spaces""'
    $probe = Invoke-Native $env:ComSpec @() $fixtureDir $fixtureEnvironment $probeRaw
    if ($probe.ExitCode -ne 0) { throw "Windows argument/environment probe failed (exit $($probe.ExitCode)): $($probe.StdErr)" }
    $probeLines = $probe.StdOut -split "`r?`n"
    $manifestArgument = Join-Path $PSScriptRoot 'rapier-source.json'
    if ($probeLines -notcontains ('ARG1=' + $manifestArgument) -or $probeLines -notcontains 'ARG2=argument with spaces' -or $probeLines -notcontains 'ENV=from-batch') {
        throw "Windows process argument/environment fixture mismatch: $($probe.StdOut)"
    }
    $failureContextVerified = $false
    try { $null = Import-BatchEnvironment $failureBatch '' $fixtureDir }
    catch {
        $failureContextVerified = $_.Exception.Message.Contains('exit 7') -and $_.Exception.Message.Contains($failureBatch)
    }
    if (!$failureContextVerified -or [Environment]::GetEnvironmentVariable('MS14_NATIVE_FIXTURE','Process') -ne $originalFixtureValue) { throw 'Batch nonzero-exit context or parent-environment isolation fixture failed' }
    $peFixture = New-Object byte[] 70
    $peFixture[0]=0x4d; $peFixture[1]=0x5a; $peFixture[0x3c]=64; $peFixture[64]=0x50; $peFixture[65]=0x45; $peFixture[68]=0x64; $peFixture[69]=0x86
    if ((Get-PeMachineFromBytes $peFixture) -ne 0x8664) { throw 'PE AMD64 metadata fixture failed' }
    $peFixture[68]=0x4c; $peFixture[69]=0x01
    $wrongMachineRejected = $false
    try { if ((Get-PeMachineFromBytes $peFixture) -ne 0x8664) { throw 'wrong machine' } } catch { $wrongMachineRejected = $true }
    if (!$wrongMachineRejected) { throw 'PE x86 fixture was not rejected as non-AMD64' }
    $vsEnvironment = Get-Vs2019X64Environment
    $script:vsMetadata = $vsEnvironment.Metadata
    Write-Host "VS2019 x64 child environment PASS: $($script:vsMetadata.cl_path); SDK=$($script:vsMetadata.windows_sdk_version)"
    Write-Host "Cargo summary fixtures: $($cases.Count)/$($cases.Count); unit lock pin fixtures: $($lockTests.Count)/$($lockTests.Count); shipping lock/source-substitution fixtures: manifest 2/2, lock 2/2; managed proof freshness: 2/2; package-enumeration/pin-drift: 3/3; PE machine fixtures: 2/2; Windows batch environment/argument/failure: 2/2 PASS"
    return
}
if ($CheckDllReceipt) {
    $receiptFixture = [ordered]@{ status='built'; cargo_command='fixture cargo build' }
    $acceptedReceipt = Add-ValidatedCargoExitCodeToReceipt $receiptFixture 0
    if (!$acceptedReceipt.Contains('cargo_exit_code') -or $acceptedReceipt.cargo_exit_code -ne 0 -or $acceptedReceipt.cargo_command -ne 'fixture cargo build') {
        throw 'Successful Cargo exit-code receipt fixture failed to preserve the actual zero result and command'
    }
    $failedReceiptRejected = $false
    try { $null = Add-ValidatedCargoExitCodeToReceipt ([ordered]@{ status='built'; cargo_command='fixture cargo build' }) 7 } catch { $failedReceiptRejected = $true }
    if (!$failedReceiptRejected) { throw 'Nonzero Cargo exit-code receipt fixture was accepted' }
    $sourceMetadata = [ordered]@{ stage='dll'; dll_build_status='source-prepared-only'; sable_source_pin='synthetic-sable-pin'; nested=@{ untouched='yes' } }
    $sourceBefore = $sourceMetadata | ConvertTo-Json -Depth 5 -Compress
    $buildingMetadata = New-DllBuildMetadata $sourceMetadata ([ordered]@{ dll_build_status='building'; command='synthetic cargo build'; windows_build_environment='synthetic VS x64' })
    if ($buildingMetadata.dll_build_status -ne 'building' -or $sourceMetadata.dll_build_status -ne 'source-prepared-only') { throw 'DLL building metadata overlay did not intentionally replace status or mutated prepared source metadata' }
    $syntheticPe = New-Object byte[] 70
    $syntheticPe[0]=0x4d; $syntheticPe[1]=0x5a; $syntheticPe[0x3c]=64; $syntheticPe[64]=0x50; $syntheticPe[65]=0x45; $syntheticPe[68]=0x64; $syntheticPe[69]=0x86
    $syntheticMachine = Get-PeMachineFromBytes $syntheticPe
    $receiptCandidate = [ordered]@{ stage='dll'; cargo_command='synthetic cargo build'; artifact_path='fixture-only/synthetic.dll'; cargo_lock_candidate_sha256='fixture-lock-hash' }
    $completed = Complete-ValidatedDllReceipt $receiptCandidate 0 $syntheticMachine $true $false
    $builtMetadata = New-DllBuildMetadata $buildingMetadata ([ordered]@{ dll_build_status='built'; cargo_exit_code=$completed.cargo_exit_code; pe_machine=$completed.pe_machine; receipt_path='fixture-only/synthetic-receipt.json' })
    if ($completed.status -ne 'built' -or $completed.cargo_exit_code -ne 0 -or $completed.cargo_command -ne 'synthetic cargo build' -or
        $completed.pe_machine -ne 'AMD64 (0x8664)' -or $builtMetadata.dll_build_status -ne 'built' -or $builtMetadata.cargo_exit_code -ne 0) {
        throw 'Synthetic successful DLL metadata/receipt assembly did not preserve final fields'
    }
    if (($sourceMetadata | ConvertTo-Json -Depth 5 -Compress) -ne $sourceBefore) { throw 'DLL metadata overlays mutated the original prepared source metadata' }
    $fixtureFolder = Join-Path $work 'receipt-fixtures'
    New-Item -ItemType Directory -Force $fixtureFolder | Out-Null
    $completed | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 (Join-Path $fixtureFolder 'synthetic-dll-receipt.json')
    $invalidPe = [byte[]]$syntheticPe.Clone()
    $invalidPe[68]=0x4c; $invalidPe[69]=0x01
    $gateCases = @(
        @{ Name='cargo-nonzero'; Cargo=7; Machine=$syntheticMachine; Proof=$true; Prepare=$false },
        @{ Name='pe-invalid'; Cargo=0; Machine=(Get-PeMachineFromBytes $invalidPe); Proof=$true; Prepare=$false },
        @{ Name='unit-proof-missing'; Cargo=0; Machine=$syntheticMachine; Proof=$false; Prepare=$false },
        @{ Name='prepare-only'; Cargo=0; Machine=$syntheticMachine; Proof=$true; Prepare=$true }
    )
    foreach ($gate in $gateCases) {
        $rejected = $false
        try { $null = Complete-ValidatedDllReceipt ([ordered]@{ status='pending'; cargo_command='synthetic cargo build' }) $gate.Cargo $gate.Machine $gate.Proof $gate.Prepare } catch { $rejected = $true }
        if (!$rejected) { throw "DLL receipt gate fixture '$($gate.Name)' failed to reject invalid/missing prerequisites" }
    }
    Write-Host 'DLL receipt Cargo exit-code fixtures: 2/2 PASS; DLL stage metadata overlay + synthetic receipt/control-flow fixtures: 5/5 PASS (synthetic receipt saved under ignored receipt-fixtures; no production receipt/artifact)'
    return
}
function Prepare-Source {
    $patch = Join-Path $PSScriptRoot $manifest.patch
    $patchHash = Assert-Hash $patch $manifest.patch_sha256 'Canonical Rapier patch'
    if (!(Assert-CleanPinned $rapierDir $manifest.revision 'Rapier source')) {
        New-Item -ItemType Directory -Force $prepared | Out-Null
        $existingCandidate = Join-Path $work 'rapier-source-candidate'
        if ((Test-Path $existingCandidate) -and ((Git-Head $existingCandidate) -eq $manifest.revision)) {
            Invoke-Checked 'git' @('clone','--no-hardlinks','--local','--no-checkout',$existingCandidate,$rapierDir) $repoRoot
        } else { Invoke-Checked 'git' @('clone','--no-checkout',$manifest.repository,$rapierDir) $repoRoot }
    }
    Invoke-Checked 'git' @('-C',$rapierDir,'config','core.autocrlf','false') $repoRoot
    if (!(Test-Path (Join-Path $rapierDir $manifest.source_file))) { Invoke-Checked 'git' @('checkout','--detach',$manifest.revision) $rapierDir }
    if (!(Assert-CleanPinned $rapierDir $manifest.revision 'Rapier source')) { throw 'Pinned Rapier source verification failed' }

    $source = Join-Path $rapierDir $manifest.source_file
    if (!(Test-Path $source)) { throw "Pinned upstream Rapier source missing: $source" }
    $upstreamHash = Get-SourceSha256 $source
    if ($upstreamHash -ne $manifest.upstream_source_sha256) { throw "Pinned upstream source hash mismatch: expected $($manifest.upstream_source_sha256), got $upstreamHash" }
    if (Test-Path $privateRapier) {
        if ((Git-Head $privateRapier) -ne $manifest.revision) { throw "Private source exists at unexpected revision; refusing to overwrite: $privateRapier" }
        $status = Git-Status $privateRapier @('--porcelain','--untracked-files=all')
        foreach ($line in $status) {
            if ($line -ne (' M ' + $manifest.source_file) -and $line -ne '?? Cargo.lock') { throw "Unexpected private source change: $line" }
        }
        $patched = Join-Path $privateRapier $manifest.source_file
        $patchedHash = if (Test-Path $patched) { Get-SourceSha256 $patched } else { '' }
        if ($status -and $patchedHash -ne $manifest.patched_source_sha256) { throw 'Private patched source is dirty and does not match the declared patched hash' }
        if (!$status -and $patchedHash -ne $manifest.upstream_source_sha256) { throw 'Private clean source differs from pinned upstream hash' }
    } else {
        Invoke-Checked 'git' @('clone','--no-hardlinks','--local','--no-checkout',$rapierDir,$privateRapier) $repoRoot
        Invoke-Checked 'git' @('-C',$privateRapier,'config','core.autocrlf','false') $repoRoot
        Invoke-Checked 'git' @('checkout','--detach',$manifest.revision) $privateRapier
    }
    $privateSource = Join-Path $privateRapier $manifest.source_file
    $beforeHash = Get-SourceSha256 $privateSource
    if ($beforeHash -eq $manifest.upstream_source_sha256) {
        Invoke-Checked 'git' @('apply','--check',$patch) $privateRapier
        Invoke-Checked 'git' @('apply',$patch) $privateRapier
    } elseif ($beforeHash -ne $manifest.patched_source_sha256) { throw "Private source hash is neither declared upstream nor patched hash: $beforeHash" }
    $patchedHash = Get-SourceSha256 $privateSource
    if ($patchedHash -ne $manifest.patched_source_sha256) { throw "Patched source hash mismatch: expected $($manifest.patched_source_sha256), got $patchedHash" }
    Invoke-Checked 'git' @('-C',$privateRapier,'diff','--check') $repoRoot
    return @{ upstream_source_sha256=$upstreamHash; patched_source_sha256=$patchedHash; patch_sha256=$patchHash; rapier_head=(Git-Head $rapierDir) }
}
function Set-LocalRustEnvironment {
    $env:RUSTUP_HOME = Join-Path $work 'rustup-home'
    $env:CARGO_HOME = Join-Path $work 'cargo-home'
    $env:RUSTUP_TOOLCHAIN = $rust.toolchain
    $env:RUSTUP_NO_UPDATE_CHECK = '1'
    $env:CARGO_NET_GIT_FETCH_WITH_CLI = 'true'
}
function Configure-LocalRust {
    $rustupExe = Join-Path $env:CARGO_HOME 'bin/rustup.exe'
    $rustcExe = Join-Path $env:CARGO_HOME 'bin/rustc.exe'
    $cargoExe = Join-Path $env:CARGO_HOME 'bin/cargo.exe'
    Invoke-Checked $rustupExe @('set','auto-self-update','disable') $work
    $active = Invoke-Native $rustupExe @('show','active-toolchain') $work
    $rustcVersion = Invoke-Native $rustcExe @('--version','--verbose') $work
    $cargoVersion = Invoke-Native $cargoExe @('--version') $work
    foreach ($result in @($active,$rustcVersion,$cargoVersion)) { if ($result.ExitCode -ne 0) { throw "Local Rust version inspection failed (exit $($result.ExitCode)): $($script:lastCommand)" } }
    if ($active.StdOut -notmatch [regex]::Escape($rust.toolchain)) { throw "Active local toolchain does not match pin $($rust.toolchain): $($active.StdOut.Trim())" }
    $script:rustMetadata = [ordered]@{ rustup_path=$rustupExe; rustup_sha256=(Get-Sha256 $rustupExe); active_toolchain=$active.StdOut.Trim(); rustc_path=$rustcExe; rustc_version=($rustcVersion.StdOut.Trim() -replace "`r?`n",'; '); cargo_path=$cargoExe; cargo_version=$cargoVersion.StdOut.Trim(); auto_self_update='disabled in project-local RUSTUP_HOME' }
    return $script:rustMetadata
}
function Install-LocalRust {
    $bootstrap = Join-Path $work 'bootstrap'
    New-Item -ItemType Directory -Force $bootstrap | Out-Null
    $exe = Join-Path $bootstrap 'rustup-init.exe'
    if (!(Test-Path $exe)) {
        $partial = "$exe.download"
        try { Invoke-WebRequest -Uri $rust.rustup_url -OutFile $partial -UseBasicParsing } catch { Remove-Item $partial -Force -ErrorAction SilentlyContinue; throw "rustup-init download failed: $($_.Exception.Message)" }
        $null = Assert-Hash $partial $rust.rustup_sha256 'Official rustup-init installer'
        Move-Item $partial $exe
    } else { $null = Assert-Hash $exe $rust.rustup_sha256 'Cached official rustup-init installer' }
    $env:RUSTUP_HOME = Join-Path $work 'rustup-home'
    $env:CARGO_HOME = Join-Path $work 'cargo-home'
    $env:RUSTUP_NO_UPDATE_CHECK = '1'
    Invoke-Checked $exe @('-y','--no-modify-path','--default-toolchain','none','--profile','minimal') $work
    $rustupExe = Join-Path $env:CARGO_HOME 'bin/rustup.exe'
    if (!(Test-Path $rustupExe)) { throw 'Verified rustup-init completed but local rustup.exe is missing' }
    Invoke-Checked $rustupExe @('set','auto-self-update','disable') $work
    Invoke-Checked $rustupExe @('toolchain','install',$rust.toolchain,'--profile',$rust.profile,'--target',$rust.target) $work
}

$oldEnvironment = @{}
foreach ($name in @('RUSTUP_HOME','CARGO_HOME','RUSTUP_TOOLCHAIN','RUSTUP_NO_UPDATE_CHECK','CARGO_NET_GIT_FETCH_WITH_CLI','PATH')) { $oldEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    Write-Report 'running'
    $script:driverPhase = 'source-preparation'
    $sourceInfo = Prepare-Source
    if ($sourceInfo -isnot [hashtable]) { throw "Prepare-Source returned unexpected metadata type: $($sourceInfo.GetType().FullName)" }
    if ($Stage -eq 'prepare') { Write-Report 'prepared' $sourceInfo; Write-Host "Pinned source prepared; report: $report"; return }
    if ($Stage -eq 'dll') {
        $script:driverPhase = 'dll-unit-proof-verification'
        $unitProof = Read-VerifiedUnitEvidence
        $sableSourceInfo = Prepare-SableSource
        $cargoInfo = Prepare-SableCargoInputs
        $dllSourceInfo = Merge-MetadataOverlay $sourceInfo $sableSourceInfo
        $dllSourceInfo = Merge-MetadataOverlay $dllSourceInfo $cargoInfo
        $dllSourceInfo = Merge-MetadataOverlay $dllSourceInfo ([ordered]@{
            rapier_source_pin=$manifest.revision
            rapier_patch_sha256=$manifest.patch_sha256
            rapier_patched_source_sha256=$manifest.patched_source_sha256
            managed_rust_unit_evidence_path=($unitProof.Path.Substring($repoRoot.Length + 1).Replace('\','/'))
            managed_rust_unit_evidence_sha256=$unitProof.EvidenceSha256
            managed_rust_unit_test_log_sha256=$unitProof.TestLogSha256
            managed_rust_unit_result='reused: 2 passed, 0 failed, 20 filtered; not rerun in DLL stage'
            managed_rust_unit_historical_run_id=$unitProof.Evidence.historical_run_id
            managed_rust_unit_artifact_id=$unitProof.Evidence.artifact_id
            target=$rust.target
            dll_build_status='source-prepared-only'
        })
        if ($PrepareOnly) {
            Write-Report 'dll-sources-prepared' $dllSourceInfo
            Write-Host "DLL source/manifest/production-lock preparation complete; no Rust/Cargo build invoked. Report: $report"
            return
        }

        Set-LocalRustEnvironment
        $rustupExe = Join-Path $env:CARGO_HOME 'bin/rustup.exe'
        $cargoExe = Join-Path $env:CARGO_HOME 'bin/cargo.exe'
        $rustcExe = Join-Path $env:CARGO_HOME 'bin/rustc.exe'
        if (!(Test-Path $rustupExe) -or !(Test-Path $cargoExe) -or !(Test-Path $rustcExe)) {
            throw 'DLL build requires the already-installed project-local Rust toolchain; tool installation/bootstrap is disabled in this stage.'
        }
        $null = Configure-LocalRust
        $vsEnvironment = Get-Vs2019X64Environment
        $script:vsMetadata = $vsEnvironment.Metadata
        $buildEnvironment = @{}
        foreach ($key in $vsEnvironment.Variables.Keys) { $buildEnvironment[$key] = $vsEnvironment.Variables[$key] }
        $buildEnvironment['RUSTUP_HOME'] = Join-Path $work 'rustup-home'
        $buildEnvironment['CARGO_HOME'] = Join-Path $work 'cargo-home'
        $buildEnvironment['RUSTUP_TOOLCHAIN'] = $rust.toolchain
        $buildEnvironment['RUSTUP_NO_UPDATE_CHECK'] = '1'
        $buildEnvironment['CARGO_NET_GIT_FETCH_WITH_CLI'] = 'true'
        $buildEnvironment['CARGO_BUILD_JOBS'] = '4'
        $buildArgs = @('build','--locked','--release','--target',$rust.target,'--manifest-path',(Join-Path $sableRustRoot 'Cargo.toml'),'-p','sable_rapier')
        $buildInfo = New-DllBuildMetadata $dllSourceInfo @{
            dll_build_status='building'
            cargo_executable=$cargoExe
            rust_toolchain=$rust.toolchain
            rustc_metadata=$script:rustMetadata
            windows_build_environment=$script:vsMetadata
            command=(Format-Command $cargoExe $buildArgs)
            environment='child-only VS2019 x64 + project-local Rust/Cargo; CARGO_BUILD_JOBS=4'
        }
        Write-Report 'dll-building' $buildInfo
        $script:driverPhase = 'sable-windows-x64-dll-build'
        $buildResult = Invoke-Native $cargoExe $buildArgs $sableRustRoot $buildEnvironment
        if ($buildResult.ExitCode -ne 0) { throw "Locked Sable Windows x64 DLL Cargo build failed (exit $($buildResult.ExitCode)): $($script:lastCommand)" }
        $builtDll = Join-Path $sableRustRoot 'target/x86_64-pc-windows-msvc/release/sable_rapier.dll'
        if (!(Test-Path -LiteralPath $builtDll -PathType Leaf)) { throw "Cargo succeeded but expected native DLL is missing: $builtDll" }
        $machine = Get-PeMachine $builtDll
        if ($machine -ne 0x8664) { throw ('Sable DLL is not PE AMD64 (machine=0x{0:X4})' -f $machine) }
        $artifactDir = Join-Path $work 'artifacts'
        New-Item -ItemType Directory -Force $artifactDir | Out-Null
        $artifactDll = Join-Path $artifactDir 'sable_rapier_x86_64_windows.dll'
        Copy-Item -LiteralPath $builtDll -Destination $artifactDll -Force
        $dllHash = Get-Sha256 $artifactDll
        $receipt = [ordered]@{
            stage='dll'
            status='built'
            updated_utc=[DateTime]::UtcNow.ToString('o')
            artifact_path='build/native-physics/artifacts/sable_rapier_x86_64_windows.dll'
            artifact_sha256=$dllHash
            pe_machine='AMD64 (0x8664)'
            target=$rust.target
            cargo_command=(Format-Command $cargoExe $buildArgs)
            cargo_lock_original_sha256=$cargoInfo.shipping_lock_original_sha256
            cargo_lock_candidate_sha256=$cargoInfo.shipping_lock_candidate_sha256
            cargo_lock_identity_proof=$cargoInfo.shipping_lock_identity
            rust_toolchain=$rust.toolchain
            rustc_metadata=$script:rustMetadata
            windows_build_environment=$script:vsMetadata
            sable_source_pin=$sableManifest.commit
            sable_source_sha256=$sableSourceInfo.sable_source_base_sha256
            sable_patched_source_sha256=$sableSourceInfo.sable_source_patched_sha256
            sable_patch_sha256=$sableSourceInfo.sable_patch_sha256
            rapier_source_pin=$manifest.revision
            rapier_patched_source_sha256=$manifest.patched_source_sha256
            rapier_patch_sha256=$manifest.patch_sha256
            managed_rust_unit_evidence_path=$dllSourceInfo.managed_rust_unit_evidence_path
            managed_rust_unit_evidence_sha256=$unitProof.EvidenceSha256
            managed_rust_unit_test_log_sha256=$unitProof.TestLogSha256
            managed_rust_unit_result=$dllSourceInfo.managed_rust_unit_result
            managed_rust_unit_historical_run_id=$unitProof.Evidence.historical_run_id
            managed_rust_unit_artifact_id=$unitProof.Evidence.artifact_id
            scope='Sable Rapier Windows x64 DLL only; no candidate JAR or installed-game claim'
        }
        $receipt = Complete-ValidatedDllReceipt $receipt $buildResult.ExitCode $machine ($null -ne $unitProof) $PrepareOnly
        $receiptPath = Join-Path $work 'dll-build-receipt.json'
        $receipt | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 $receiptPath
        $builtReportInfo = New-DllBuildMetadata $dllSourceInfo ([ordered]@{ dll_build_status='built'; artifact_path=$receipt.artifact_path; artifact_sha256=$dllHash; pe_machine=$receipt.pe_machine; receipt_path='build/native-physics/dll-build-receipt.json'; cargo_exit_code=$receipt.cargo_exit_code })
        Write-Report 'dll-built' $builtReportInfo
        Write-Host "Sable Windows x64 DLL built and PE AMD64 validated: $artifactDll; SHA-256 $dllHash"
        return
    }

    Set-LocalRustEnvironment
    $rustupExe = Join-Path $env:CARGO_HOME 'bin/rustup.exe'
    $cargoExe = Join-Path $env:CARGO_HOME 'bin/cargo.exe'
    if (!(Test-Path $rustupExe) -or !(Test-Path $cargoExe)) {
        if (!$BootstrapRust) { throw "Project-local Rust toolchain is missing. Re-run with -BootstrapRust to explicitly download and checksum-verify rustup-init; no global Rust command is used." }
        Install-LocalRust
    }
    $rustcExe = Join-Path $env:CARGO_HOME 'bin/rustc.exe'
    if (!(Test-Path $rustcExe)) { throw 'Project-local rustc is missing after toolchain setup' }
    $null = Configure-LocalRust

    $unitManifest = Join-Path $privateRapier 'crates/rapier3d/Cargo.toml'
    $lock = Join-Path $privateRapier 'Cargo.lock'
    $lockStatus = if (Test-Path $lock) { 'unit-lock-existing' } else { 'unit-lock-generated' }
    if (!(Test-Path $lock)) {
        # Deliberately generate a separate lock only for Rapier's focused unit test; DLL shipping lock remains untouched.
        Invoke-Checked $cargoExe @('generate-lockfile','--manifest-path',$unitManifest) $privateRapier
    }
    $lockTextBeforePin = Get-Content -Raw $lock
    $lockHashBeforePin = Get-Sha256 $lock
    $parryVersion = '0.26.0'
    $parryChecksum = $manifest.dependency_api.sha256
    $lockPlan = Get-UnitLockPinPlan $lockTextBeforePin $parryVersion $parryChecksum
    $pinAction = $lockPlan.Action
    $pinCommand = $null
    if ($lockPlan.Action -eq 'precise-pin') {
        $beforeOtherPackages = @(Get-LockPackages $lockTextBeforePin | Where-Object { $_.Name -ne 'parry3d' } | ForEach-Object { "$($_.Name)|$($_.Version)|$($_.Checksum)" } | Sort-Object)
        $script:driverPhase = 'focused-unit-lock-pin'
        $pinArgs = @('update','--manifest-path',$unitManifest,'-p','parry3d','--precise',$parryVersion)
        $pinCommand = "$cargoExe $($pinArgs -join ' ')"
        Invoke-Checked $cargoExe $pinArgs $privateRapier
        $lockTextAfterPin = Get-Content -Raw $lock
        $afterOtherPackages = @(Get-LockPackages $lockTextAfterPin | Where-Object { $_.Name -ne 'parry3d' } | ForEach-Object { "$($_.Name)|$($_.Version)|$($_.Checksum)" } | Sort-Object)
        $otherPackageDrift = @(Compare-Object -ReferenceObject $beforeOtherPackages -DifferenceObject $afterOtherPackages | ForEach-Object { [ordered]@{ package_record=$_.InputObject; direction=$_.SideIndicator } })
        if ($otherPackageDrift.Count) {
            throw "Targeted parry3d pin changed other unit-lock package versions/checksums; refusing test stage. Drift records: $($otherPackageDrift | ConvertTo-Json -Compress)"
        }
        $lockPlan = Get-UnitLockPinPlan $lockTextAfterPin $parryVersion $parryChecksum
        if ($lockPlan.Action -ne 'reuse') { throw "Targeted Parry pin did not produce the pinned version/checksum: $($lockPlan | ConvertTo-Json -Compress)" }
    }
    $lockHashAfterPin = Get-Sha256 $lock
    $cargoArgs = @('test','--manifest-path',$unitManifest,'--lib','gyroscopic_frame_tests','--features','simd-nightly,parallel')
    $cargoArgs += '--locked'
    $vsEnvironment = Get-Vs2019X64Environment
    $script:vsMetadata = $vsEnvironment.Metadata
    $script:driverPhase = 'focused-rust-tests'
    $nativeResult = Invoke-Native $cargoExe $cargoArgs $privateRapier $vsEnvironment.Variables
    $output = @($nativeResult.StdOut -split "`r?`n") + @($nativeResult.StdErr -split "`r?`n")
    $exitCode = $nativeResult.ExitCode
    $output | Set-Content -Encoding utf8 (Join-Path $work 'focused-rust-tests.txt')
    if ($exitCode -ne 0) { throw "Focused Rust test command failed (exit $exitCode): $($script:lastCommand); see ignored build/native-physics/focused-rust-tests.txt and native-driver.log" }
    $testCounts = Parse-CargoTestSummary @($output | ForEach-Object { [string]$_ }) 2
    Write-Report 'tests-passed' ($sourceInfo + @{ command=($cargoArgs -join ' '); test_counts=$testCounts; lock_status=$lockStatus; unit_lock_sha256_before_pin=$lockHashBeforePin; unit_lock_sha256_after_pin=$lockHashAfterPin; parry3d_version=$lockPlan.ParryVersion; parry3d_checksum=$lockPlan.ParryChecksum; parry3d_pin_action=$pinAction; parry3d_pin_command=$pinCommand; unit_lock_scope='focused Rapier workspace unit lock; not shipping Sable lock' })
    Write-Host "Focused Rapier tests passed: $($testCounts.passed); failed: $($testCounts.failed); ignored: $($testCounts.ignored); filtered: $($testCounts.filtered)"
} catch {
    Add-Content -Encoding utf8 $driverLog "`nDRIVER_FAILURE phase=$($script:driverPhase) command=$($script:lastCommand) exit=$($script:lastExitCode) error=$($_.Exception.Message)"
    Write-Report 'failed' @{ error=$_.Exception.Message }
    throw
} finally {
    foreach ($name in $oldEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $oldEnvironment[$name], 'Process') }
}
