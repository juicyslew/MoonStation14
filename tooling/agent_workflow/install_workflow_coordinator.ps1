[CmdletBinding()]
param(
    [switch] $Install,
    [switch] $VerifyOnly,
    [string] $PackageRoot,
    [string] $PythonPath
)
$ErrorActionPreference = 'Stop'
function Assert-NoReparsePath([string] $Path) {
    $current = [System.IO.Path]::GetFullPath($Path)
    $ancestors = [System.Collections.Generic.List[string]]::new()
    while ($current) {
        $ancestors.Add($current)
        $parent = [System.IO.Directory]::GetParent($current)
        if ($null -eq $parent) { break }
        if ($parent.FullName -ceq $current) { break }
        $current = $parent.FullName
    }
    for ($index = $ancestors.Count - 1; $index -ge 0; $index--) {
        $candidate = $ancestors[$index]
        if (Test-Path -LiteralPath $candidate) {
            $item = Get-Item -LiteralPath $candidate -Force
            if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse-point path refused: $candidate" }
        }
    }
}
function Assert-LocalPathNoReparse([string] $Base, [string] $Path) {
    Assert-NoReparsePath $Base
    $relative = [System.IO.Path]::GetRelativePath($Base,$Path)
    if ($relative.StartsWith('..') -or [System.IO.Path]::IsPathRooted($relative)) { throw "Path escapes its assigned root: $Path" }
    $current=$Base
    foreach($part in $relative.Split([System.IO.Path]::DirectorySeparatorChar,[System.StringSplitOptions]::RemoveEmptyEntries)) {
        $current=Join-Path $current $part
        if(Test-Path -LiteralPath $current) { if (((Get-Item -LiteralPath $current -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse-point path refused: $current" } }
    }
}
if ($Install -and $VerifyOnly) { throw 'Choose a plan/verification mode or explicit future install, not both.' }
if (-not $PackageRoot) { throw 'Use an explicit inactive package root; no live installation target is configured.' }
$root = [System.IO.Path]::GetFullPath($PackageRoot)
$manifestPath = Join-Path $root 'manifest.json'
# Fence the package path (including its base and all ancestors) before reading its manifest.
Assert-NoReparsePath $root
Assert-NoReparsePath $manifestPath
$privateTemp = [System.IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'Temp/opencode'))
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Package manifest missing.' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$main = [System.IO.Path]::GetFullPath([string]$manifest.source_root)
Assert-NoReparsePath $main
$approvedStage = [System.IO.Path]::GetFullPath((Join-Path $main 'build/agent-workflow/workflow-redesign-plan/wr10/stage'))
if ($root -cne $approvedStage -and -not $root.StartsWith($privateTemp + [System.IO.Path]::DirectorySeparatorChar,[System.StringComparison]::OrdinalIgnoreCase)) { throw 'PackageRoot must be the approved WR10 stage or private OpenCode temp child.' }
if ($root -cne $approvedStage) { Assert-LocalPathNoReparse $privateTemp $root }
$receiptPath = [string]$manifest.previous_receipt.path
Assert-NoReparsePath $receiptPath
if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf) -or
    (Get-FileHash -LiteralPath $receiptPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $manifest.previous_receipt.sha256) {
    throw 'Previous receipt changed since package staging; plan is stale.'
}
$receipt = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
$sharedRoot = [string]$receipt.shared_root
if (-not [System.IO.Path]::IsPathRooted($sharedRoot)) { throw 'Receipt shared_root must be absolute.' }
Assert-NoReparsePath $sharedRoot
$entries = @($manifest.files.PSObject.Properties | ForEach-Object { $_.Value })
foreach ($entry in $entries) {
    if ([System.IO.Path]::IsPathRooted([string]$entry.source) -or [string]$entry.source -match '(^|[\\/])\.\.([\\/]|$)' -or
        [System.IO.Path]::IsPathRooted([string]$entry.target) -or [string]$entry.target -match '(^|[\\/])\.\.([\\/]|$)') { throw 'Unsafe package manifest path.' }
    $source = Join-Path $main $entry.source
    $target = Join-Path $root $entry.target
    Assert-LocalPathNoReparse $main $source; Assert-LocalPathNoReparse $root $target
    if (-not (Test-Path -LiteralPath $source -PathType Leaf) -or -not (Test-Path -LiteralPath $target -PathType Leaf)) { throw "Missing package member $($entry.target)." }
    $hash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    $sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -cne $entry.sha256 -or $sourceHash -cne $entry.sha256) { throw "Source/package hash mismatch: $($entry.target)." }
    $live = Join-Path $sharedRoot $entry.target
    Assert-LocalPathNoReparse $sharedRoot $live
    if ($entry.old_target_sha256) {
        if (-not $entry.backup_path -or [System.IO.Path]::IsPathRooted([string]$entry.backup_path) -or
            [string]$entry.backup_path -match '(^|[\\/])\.\.([\\/]|$)') { throw "Missing/unsafe verified backup binding: $($entry.target)." }
        $backup = Join-Path $root $entry.backup_path
        Assert-LocalPathNoReparse $root $backup
        if (-not (Test-Path -LiteralPath $backup -PathType Leaf) -or
            (Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash.ToLowerInvariant() -cne $entry.backup_sha256 -or
            $entry.backup_sha256 -cne $entry.old_target_sha256) { throw "Verified backup bytes do not match old target: $($entry.target)." }
        if (-not (Test-Path -LiteralPath $live -PathType Leaf) -or (Get-FileHash -LiteralPath $live -Algorithm SHA256).Hash.ToLowerInvariant() -cne $entry.old_target_sha256) {
            throw "Receipt-owned target changed since staging: $($entry.target)."
        }
        $rows = @($receipt.files | Where-Object { $_.target -eq $entry.receipt_target -and $_.path -eq $entry.receipt_path })
        if ($rows.Count -ne 1 -or $rows[0].new_sha256 -cne $entry.old_target_sha256 -or $rows[0].deleted -or
            (Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash.ToLowerInvariant() -cne $rows[0].new_sha256) { throw "Receipt ownership/backup mismatch: $($entry.target)." }
    }
    elseif ($entry.backup_path -or $entry.backup_sha256) { throw "Create target unexpectedly has a backup: $($entry.target)." }
    elseif (Test-Path -LiteralPath $live) { throw "Planned create target now exists: $($entry.target)." }
}
if ($manifest.bindings.status -eq 'planned-explicit-targets') {
    $bindingManifestPath = Join-Path $root $manifest.bindings.manifest
    Assert-LocalPathNoReparse $root $bindingManifestPath
    if (-not (Test-Path -LiteralPath $bindingManifestPath -PathType Leaf) -or
        (Get-FileHash -LiteralPath $bindingManifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $manifest.bindings.sha256) { throw 'Binding plan manifest missing/stale.' }
    $bindingManifest = Get-Content -LiteralPath $bindingManifestPath -Raw | ConvertFrom-Json
    if ($bindingManifest.receipt_path -cne $receiptPath -or $bindingManifest.receipt_sha256 -cne $manifest.previous_receipt.sha256) { throw 'Binding manifest is not bound to the package receipt.' }
    if (-not (Test-Path -LiteralPath $bindingManifest.source_plan -PathType Leaf) -or
        (Get-FileHash -LiteralPath $bindingManifest.source_plan -Algorithm SHA256).Hash.ToLowerInvariant() -cne $bindingManifest.source_plan_sha256) { throw 'Binding plan source changed since staging.' }
    $copiedPlan=Join-Path $root $bindingManifest.staged_plan
    Assert-LocalPathNoReparse $root $copiedPlan
    Assert-LocalPathNoReparse $privateTemp $bindingManifest.source_plan
    if (-not (Test-Path -LiteralPath $copiedPlan -PathType Leaf) -or (Get-FileHash -LiteralPath $copiedPlan -Algorithm SHA256).Hash.ToLowerInvariant() -cne $bindingManifest.staged_plan_sha256) { throw 'Staged binding plan copy changed.' }
    foreach ($property in $bindingManifest.files.PSObject.Properties) {
        $entry = $property.Value
        if ($entry.target -ne 'main' -and $entry.target -notmatch '^MoonStation14-agent-0[1-5]$') { throw 'Unsafe binding target label.' }
        if ([System.IO.Path]::IsPathRooted([string]$entry.path) -or [string]$entry.path -match '(^|[\\/])\.\.([\\/]|$)' -or
            [System.IO.Path]::IsPathRooted([string]$entry.backup_path) -or [string]$entry.backup_path -match '(^|[\\/])\.\.([\\/]|$)' -or
            [System.IO.Path]::IsPathRooted([string]$entry.staged_path) -or [string]$entry.staged_path -match '(^|[\\/])\.\.([\\/]|$)') { throw 'Unsafe binding manifest path.' }
        $targetRoot = if ($entry.target -eq 'main') { [string]$receipt.main_root } else { Join-Path (Split-Path -Parent $receipt.main_root) $entry.target }
        $live = Join-Path $targetRoot $entry.path; $backup = Join-Path $root $entry.backup_path; $staged = Join-Path $root $entry.staged_path
        Assert-LocalPathNoReparse (Split-Path -Parent $targetRoot) $live
        Assert-LocalPathNoReparse $targetRoot $live; Assert-LocalPathNoReparse $root $backup; Assert-LocalPathNoReparse $root $staged
        foreach ($path in @($live,$backup,$staged)) { if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing binding plan member: $($entry.path)." } }
        $liveHash = (Get-FileHash -LiteralPath $live -Algorithm SHA256).Hash.ToLowerInvariant()
        $backupHash = (Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash.ToLowerInvariant()
        $newHash = (Get-FileHash -LiteralPath $staged -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($liveHash -cne $entry.old_sha256 -or $backupHash -cne $entry.backup_sha256 -or $backupHash -cne $entry.old_sha256 -or $newHash -cne $entry.new_sha256 -or $newHash -cne $entry.staged_sha256) { throw "Binding config/profile or backup hash mismatch: $($entry.target)/$($entry.path)." }
        $rows = @($receipt.files | Where-Object { $_.target -eq $entry.target -and $_.path -eq $entry.path })
        if ($rows.Count -ne 1 -or $rows[0].deleted -or $rows[0].new_sha256 -cne $entry.receipt_sha256 -or $liveHash -cne $rows[0].new_sha256) { throw "Binding target receipt mismatch: $($entry.target)/$($entry.path)." }
        Assert-LocalPathNoReparse $privateTemp $entry.source
        if (-not (Test-Path -LiteralPath $entry.source -PathType Leaf) -or (Get-FileHash -LiteralPath $entry.source -Algorithm SHA256).Hash.ToLowerInvariant() -cne $entry.source_sha256) { throw 'Coordinator config source changed since staging.' }
    }
}
elseif ($manifest.bindings.status -cne 'runtime-only-incomplete') { throw 'Unknown staged binding status.' }
if ($Install) { throw 'Runtime installation is deliberately not enabled in WR10; use VerifyOnly or the default plan.' }
if ($VerifyOnly -and -not $PythonPath) { throw '-VerifyOnly requires explicit -PythonPath metadata verification.' }
if ($PythonPath) {
    $requirements = Join-Path $root 'tooling/agent_workflow/requirements-workflow-coordinator.txt'
    $pins = Get-Content -LiteralPath $requirements | Where-Object { $_ -match '^([A-Za-z0-9_-]+)==(.+)$' } | ForEach-Object { @{name=$Matches[1];version=$Matches[2]} }
    foreach ($pin in $pins) {
        $found = (& $PythonPath -I -c "from importlib.metadata import version;print(version('$($pin.name)'))" 2>$null).Trim()
        if ($LASTEXITCODE -ne 0 -or $found -cne $pin.version) { throw "Dependency metadata mismatch for $($pin.name): expected $($pin.version), found '$found'." }
    }
    "Read-only dependency metadata verified: $($pins.Count) pins; target environment unchanged."
}
"Verified inactive package: $($entries.Count) manifest files; no environment or runtime mutation."
