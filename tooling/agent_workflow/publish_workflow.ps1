[CmdletBinding()]
param(
    [switch] $Apply,
    [switch] $BindWorktrees,
    [string] $PreviousReceipt,
    [string] $TestFixtureRoot
)

$ErrorActionPreference = 'Stop'
$script:SharedSources = @(
    'tooling/agent_workflow/workflow.py',
    'tooling/agent_workflow/validation_evidence.py',
    'tooling/agent_workflow/gate_dispatch.py',
    'docs/development-workflow/README.md',
    'docs/development-workflow/CLI.md',
    'docs/development-workflow/coordinator.md',
    'docs/development-workflow/source-fidelity.md',
    'docs/development-workflow/request-template.json',
    'docs/development-workflow/review-report.md',
    'docs/development-workflow/HANDOFF.md',
    'docs/development-workflow/roles/integration-lead.md',
    'docs/development-workflow/roles/fidelity-reviewer.md',
    '.opencode/agents/integration-lead.md',
    '.opencode/agents/fidelity-reviewer.md',
    'tooling/agent_workflow/workflow_entry.py',
    'AGENTS.md',
    'opencode.jsonc'
)
$script:DeleteOnBind = @(
    'tooling/agent_workflow/validation_evidence.py',
    'tooling/agent_workflow/gate_dispatch.py',
    'tooling/agent_workflow/test_workflow.py',
    'tooling/agent_workflow/test_validation_evidence.py',
    'tooling/agent_workflow/test_gate_dispatch.py',
    'docs/development-workflow/CLI.md',
    'docs/development-workflow/coordinator.md',
    'docs/development-workflow/source-fidelity.md',
    'docs/development-workflow/request-template.json',
    'docs/development-workflow/review-report.md',
    'docs/development-workflow/HANDOFF.md'
)

function Get-Sha256([byte[]] $Bytes) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return ([System.BitConverter]::ToString($sha.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Get-FileHashValue([string] $Path) { return Get-Sha256 ([System.IO.File]::ReadAllBytes($Path)) }

function Get-TextData([byte[]] $Bytes) {
    $hasBom = $Bytes.Length -ge 3 -and $Bytes[0] -eq 0xEF -and $Bytes[1] -eq 0xBB -and $Bytes[2] -eq 0xBF
    $encoding = [System.Text.UTF8Encoding]::new($hasBom)
    $offset = if ($hasBom) { 3 } else { 0 }
    return [pscustomobject]@{ Text = $encoding.GetString($Bytes, $offset, $Bytes.Length - $offset); Encoding = $encoding; Bom = $hasBom }
}

function ConvertTo-TextBytes([string] $Text, $TextData) {
    $payload = $TextData.Encoding.GetBytes($Text)
    if (-not $TextData.Bom) { return ,$payload }
    $result = New-Object byte[] ($payload.Length + 3)
    $result[0] = 0xEF; $result[1] = 0xBB; $result[2] = 0xBF
    [Array]::Copy($payload, 0, $result, 3, $payload.Length)
    return ,$result
}

function ConvertTo-CanonicalConfigJson($Value) {
    if ($null -eq $Value) { return 'null' }
    if ($Value -is [string]) { return ConvertTo-Json -InputObject $Value -Compress }
    if ($Value -is [System.Collections.IDictionary]) {
        $keys = @($Value.Keys | ForEach-Object { [string]$_ }); [Array]::Sort($keys, [System.StringComparer]::Ordinal)
        $properties = @()
        foreach ($key in $keys) { $properties += ((ConvertTo-Json -InputObject $key -Compress) + ':' + (ConvertTo-CanonicalConfigJson $Value[$key])) }
        return '{' + ($properties -join ',') + '}'
    }
    if ($Value -is [System.Collections.IList]) {
        $items = foreach ($item in $Value) { ConvertTo-CanonicalConfigJson $item }
        return '[' + ($items -join ',') + ']'
    }
    return ConvertTo-Json -InputObject $Value -Compress
}

function Get-CanonicalConfigHash([byte[]] $Bytes) {
    $value = (Get-TextData $Bytes).Text | ConvertFrom-Json -AsHashtable
    return Get-Sha256 ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $value)))
}

function Add-Rule($Agent, [string] $Action, [string] $Resource, [string] $Effect) {
    if (-not $Agent.ContainsKey('permissions') -or $null -eq $Agent.permissions) { $Agent.permissions = @() }
    foreach ($rule in $Agent.permissions) { if ($rule.action -eq $Action -and $rule.resource -eq $Resource -and $rule.effect -eq $Effect) { return } }
    $Agent.permissions = @($Agent.permissions) + @([ordered]@{ action = $Action; resource = $Resource; effect = $Effect })
}

function Get-WorkflowSection([string] $Text) {
    $match = [regex]::Match($Text, '(?ms)^## Shared worker and review workflow\r?\n.*?(?=^## |\z)')
    if (-not $match.Success) { throw 'Canonical AGENTS.md lacks the shared workflow section.' }
    return $match.Value
}

function Set-WorkflowSection([string] $TargetText, [string] $CanonicalSection) {
    $match = [regex]::Match($TargetText, '(?ms)^## Shared worker and review workflow\r?\n.*?(?=^## |\z)')
    if (-not $match.Success) { throw 'Target AGENTS.md lacks the known shared workflow section; refusing to synthesize or replace its prefix.' }
    $nl = if ($TargetText.Contains("`r`n")) { "`r`n" } else { "`n" }
    $section = [regex]::Replace($CanonicalSection, "`r?`n", [System.Text.RegularExpressions.MatchEvaluator]{ param($m) $nl })
    return $TargetText.Substring(0, $match.Index) + $section + $TargetText.Substring($match.Index + $match.Length)
}

function Assert-NoReparsePath([string] $Root, [string] $Destination) {
    $relative = [System.IO.Path]::GetRelativePath($Root, $Destination)
    if ($relative.StartsWith('..') -or [System.IO.Path]::IsPathRooted($relative)) { throw "Path escaped its assigned root: $Destination" }
    $current = $Root
    foreach ($part in $relative.Split([System.IO.Path]::DirectorySeparatorChar, [System.StringSplitOptions]::RemoveEmptyEntries)) {
        $current = Join-Path $current $part
        if ((Test-Path -LiteralPath $current) -and (((Get-Item -LiteralPath $current -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)) { throw "Refusing reparse-point path: $current" }
    }
}

function Get-ReceiptHash($Receipt, [string] $Target, [string] $RelativePath) {
    $row = @($Receipt.files | Where-Object { $_.target -eq $Target -and $_.path -eq $RelativePath }) | Select-Object -First 1
    if ($row) { return $row.new_sha256 }
    return $null
}

function Assert-ReceiptMatch($Receipt, [string] $Target, [string] $RelativePath, [string] $CurrentHash) {
    $rows = @($Receipt.files | Where-Object { $_.target -eq $Target -and $_.path -eq $RelativePath })
    if ($rows.Count) {
        $row = $rows[-1]
        if ($row.deleted -or -not $row.new_sha256 -or $row.new_sha256 -ne $CurrentHash) {
            throw "Existing managed file is not receipt-verified; refusing to replace/remove: $Target/$RelativePath"
        }
        return
    }
    $sourceHash = $null
    if ($Receipt.source_hashes -is [System.Collections.IDictionary]) { $sourceHash = $Receipt.source_hashes[$RelativePath] }
    elseif ($Receipt.source_hashes -and $Receipt.source_hashes.PSObject.Properties[$RelativePath]) { $sourceHash = $Receipt.source_hashes.PSObject.Properties[$RelativePath].Value }
    if (-not $sourceHash -or $sourceHash -ne $CurrentHash) {
        throw "File is absent from the installation receipt and does not match its known source snapshot: $Target/$RelativePath"
    }
}

function Get-TailoredProjectConfig([byte[]] $Bytes, [string] $SharedRoot, [string] $WorkspaceParent, [int] $TargetNumber) {
    $config = (Get-TextData $Bytes).Text | ConvertFrom-Json -AsHashtable
    $shared = $SharedRoot.Replace('\', '/') + '/**'
    $roles = @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')
    foreach ($role in $roles) {
        $config.agents[$role].permissions = @($config.agents[$role].permissions | Where-Object {
            -not ($_.action -eq 'external_directory' -and $_.resource -match '/MoonStation14(?:-agent-[^/]+)?/\*\*$')
        })
        Add-Rule $config.agents[$role] 'external_directory' $shared 'allow'
        Add-Rule $config.agents[$role] 'edit' $shared 'deny'
    }
    if ($TargetNumber -eq 5) {
        foreach ($number in 1..4) {
            $peer = Join-Path $WorkspaceParent ('MoonStation14-agent-{0:D2}' -f $number)
            Add-Rule $config.agents['integration-lead'] 'external_directory' ($peer.Replace('\', '/') + '/**') 'allow'
        }
    }
    else {
        $central = Join-Path $WorkspaceParent 'MoonStation14-agent-05'
        foreach ($role in @('orchestrate-ms14','3d-modeler')) {
            Add-Rule $config.agents[$role] 'external_directory' ($central.Replace('\', '/') + '/**') 'allow'
        }
    }
    $json = ConvertTo-CanonicalConfigJson $config
    return ,[System.Text.UTF8Encoding]::new($false).GetBytes($json + "`n")
}

function Get-TailoredIntegrationProfile([byte[]] $Bytes, [string] $WorkspaceParent, [int] $TargetNumber) {
    $data = Get-TextData $Bytes
    $nl = if ($data.Text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $mainGrant = '    "C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14/**": allow'
    if (-not $data.Text.Contains($mainGrant)) { throw 'Integration profile lacks its known MAIN grant.' }
    $text = $data.Text.Replace($mainGrant + "`r`n", '').Replace($mainGrant + "`n", '')
    if ($TargetNumber -eq 5) {
        $assetAnchor = '    "C:/Users/William/Documents/Minecraft Mod Dev Files/Asset Building Workspace/**": allow'
        if (-not $text.Contains($assetAnchor)) { throw 'Integration profile lacks its approved asset-workspace grant.' }
        $peers = foreach ($number in 1..4) { '    "C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14-agent-{0:D2}/**": allow' -f $number }
        $text = $text.Replace($assetAnchor, $assetAnchor + $nl + ($peers -join $nl))
    }
    return ConvertTo-TextBytes $text $data
}

function Get-TailoredFidelityProfile([byte[]] $Bytes) {
    $data = Get-TextData $Bytes
    $mainGrant = '    "C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14/**": allow'
    $text = $data.Text.Replace($mainGrant + "`r`n", '').Replace($mainGrant + "`n", '')
    return ConvertTo-TextBytes $text $data
}

$mainRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if ($TestFixtureRoot) {
    if ($Apply) { throw '-TestFixtureRoot is dry-run-only and cannot be combined with -Apply.' }
    $fixtureRoot = [System.IO.Path]::GetFullPath($TestFixtureRoot)
    $approvedTemp = [System.IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'Temp\opencode'))
    if (-not $fixtureRoot.StartsWith($approvedTemp + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Test fixture root must be inside the approved OpenCode temp directory.'
    }
    $mainRoot = Join-Path $fixtureRoot 'MoonStation14'
}
if ((Split-Path -Leaf $mainRoot) -ne 'MoonStation14') { throw "Run publisher from MAIN MoonStation14; resolved $mainRoot" }
$workspaceParent = Split-Path -Parent $mainRoot
$sharedRoot = [System.IO.Path]::GetFullPath((Join-Path $workspaceParent 'MoonStation14-shared'))
$sourceBytes = @{}; $sourceHashes = @{}
foreach ($relative in $script:SharedSources) {
    $path = Join-Path $mainRoot ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing shared workflow source: $relative" }
    $sourceBytes[$relative] = [System.IO.File]::ReadAllBytes($path)
    $sourceHashes[$relative] = Get-Sha256 $sourceBytes[$relative]
}
$previous = $null
if ($PreviousReceipt) {
    if (-not (Test-Path -LiteralPath $PreviousReceipt -PathType Leaf)) { throw "Previous receipt not found: $PreviousReceipt" }
    $previous = Get-Content -LiteralPath $PreviousReceipt -Raw | ConvertFrom-Json
}
if ($BindWorktrees -and -not $previous) { throw '-BindWorktrees requires -PreviousReceipt from the prior installed workflow snapshot.' }

$targets = @()
foreach ($n in 1..5) {
    $name = 'MoonStation14-agent-{0:D2}' -f $n
    $path = Join-Path $workspaceParent $name
    if (-not (Test-Path -LiteralPath $path -PathType Container)) { throw "Assigned worktree missing: $path" }
    if ((Get-Item -LiteralPath $path -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint) { throw "Refusing linked worktree root: $path" }
    $targets += [pscustomobject]@{ Name = $name; Number = $n; Path = [System.IO.Path]::GetFullPath($path) }
}

$plans = New-Object System.Collections.Generic.List[object]
$canonicalSection = Get-WorkflowSection (Get-TextData $sourceBytes['AGENTS.md']).Text
$roleFiles = [ordered]@{
    'docs/development-workflow/roles/integration-lead.md' = 'docs/development-workflow/roles/integration-lead.md'
    'docs/development-workflow/roles/fidelity-reviewer.md' = 'docs/development-workflow/roles/fidelity-reviewer.md'
}
foreach ($relative in $script:SharedSources) {
    if ($relative -in @('AGENTS.md','opencode.jsonc','.opencode/agents/integration-lead.md','.opencode/agents/fidelity-reviewer.md','tooling/agent_workflow/workflow_entry.py')) { continue }
    if ($relative -match '^docs/development-workflow/roles/') { continue }
    $plans.Add([pscustomobject]@{ Target = 'shared'; Path = (Join-Path $sharedRoot ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))); Relative = $relative; Action = 'publish'; Bytes = $sourceBytes[$relative]; Delete = $false })
}
foreach ($contract in $roleFiles.Keys) {
    $text = (Get-TextData $sourceBytes[$roleFiles[$contract]]).Text.Replace('@SHARED_ROOT@', $sharedRoot.Replace('\', '/'))
    $plans.Add([pscustomobject]@{ Target = 'shared'; Path = (Join-Path $sharedRoot ($contract.Replace('/', [System.IO.Path]::DirectorySeparatorChar))); Relative = $contract; Action = 'publish'; Bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($text); Delete = $false })
}

if (Test-Path -LiteralPath $sharedRoot -PathType Container) {
    if ((Get-Item -LiteralPath $sharedRoot -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint) { throw "Refusing linked shared root: $sharedRoot" }
    if (-not $previous) { throw "Shared publication directory exists without a previous publisher receipt: $sharedRoot" }
    foreach ($plan in @($plans | Where-Object Target -eq 'shared')) {
        Assert-NoReparsePath $sharedRoot $plan.Path
        if (Test-Path -LiteralPath $plan.Path -PathType Leaf) {
            $currentHash = Get-FileHashValue $plan.Path
            $desiredHash = Get-Sha256 $plan.Bytes
            if ($currentHash -ne $desiredHash) { Assert-ReceiptMatch $previous 'shared' $plan.Relative $currentHash }
            else { $plan.Action = 'up-to-date' }
        }
        else { $plan.Action = 'create' }
    }
}
else {
    foreach ($plan in @($plans | Where-Object Target -eq 'shared')) { $plan.Action = 'create' }
}

if ($BindWorktrees) {
    $sharedDirectoryGrant = $sharedRoot.Replace('\', '/') + '/**'
    $shim = $sourceBytes['tooling/agent_workflow/workflow_entry.py']
    $readmeRedirect = "# Shared workflow documentation`n`nCanonical workflow docs are published at ``$sharedRoot/docs/development-workflow/README.md``. See the root ``AGENTS.md`` for the shared workflow link.`n"
    $localOutputs = [ordered]@{
        'tooling/agent_workflow/workflow.py' = $shim
        '.opencode/agents/integration-lead.md' = $sourceBytes['.opencode/agents/integration-lead.md']
        '.opencode/agents/fidelity-reviewer.md' = $sourceBytes['.opencode/agents/fidelity-reviewer.md']
        'AGENTS.md' = $sourceBytes['AGENTS.md']
        'docs/development-workflow/README.md' = [System.Text.UTF8Encoding]::new($false).GetBytes($readmeRedirect)
    }
    foreach ($target in $targets) {
        $configPath = Join-Path $target.Path 'opencode.jsonc'
        if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) { throw "Missing project config: $configPath" }
        Assert-NoReparsePath $target.Path $configPath
        $oldConfig = [System.IO.File]::ReadAllBytes($configPath)
        $newConfig = Get-TailoredProjectConfig $oldConfig $sharedRoot $workspaceParent $target.Number
        $expectedOld = Get-ReceiptHash $previous $target.Name 'opencode.jsonc'
        $oldHash = Get-Sha256 $oldConfig
        if ($oldHash -ne $expectedOld -and (Get-CanonicalConfigHash $oldConfig) -ne (Get-CanonicalConfigHash $newConfig)) {
            throw "Project config changed since the previous receipt; refusing to alter it: $configPath"
        }
        $localOutputs['opencode.jsonc'] = $newConfig

        foreach ($relative in $localOutputs.Keys) {
            $destination = Join-Path $target.Path ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
            $bytes = $localOutputs[$relative]
            if ($relative -eq '.opencode/agents/integration-lead.md') {
                $bytes = Get-TailoredIntegrationProfile $bytes $workspaceParent $target.Number
            }
            if ($relative -eq '.opencode/agents/fidelity-reviewer.md') {
                $bytes = Get-TailoredFidelityProfile $bytes
            }
            if ($relative -eq 'AGENTS.md') {
                if (-not (Test-Path -LiteralPath $destination -PathType Leaf)) { throw "Missing assigned AGENTS.md: $destination" }
                $data = Get-TextData ([System.IO.File]::ReadAllBytes($destination))
                $bytes = ConvertTo-TextBytes (Set-WorkflowSection $data.Text $canonicalSection) $data
            }
            if (-not (Test-Path -LiteralPath $destination -PathType Leaf)) { throw "Missing receipt-owned binding: $destination" }
            Assert-NoReparsePath $target.Path $destination
            $currentHash = Get-FileHashValue $destination
            $desiredHash = Get-Sha256 $bytes
            if ($currentHash -ne $desiredHash -and $relative -ne 'AGENTS.md') { Assert-ReceiptMatch $previous $target.Name $relative $currentHash }
            $plans.Add([pscustomobject]@{ Target = $target.Name; Path = $destination; Relative = $relative; Action = $(if ($currentHash -eq $desiredHash) {'up-to-date'} else {'replace'}); Bytes = $bytes; Delete = $false })
        }
        foreach ($relative in $script:DeleteOnBind) {
            $destination = Join-Path $target.Path ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
            if (Test-Path -LiteralPath $destination -PathType Leaf) {
                Assert-NoReparsePath $target.Path $destination
                $currentHash = Get-FileHashValue $destination
                Assert-ReceiptMatch $previous $target.Name $relative $currentHash
                $plans.Add([pscustomobject]@{ Target = $target.Name; Path = $destination; Relative = $relative; Action = 'remove'; Bytes = $null; Delete = $true })
            }
        }
    }
}

foreach ($relative in $script:SharedSources) {
    if ((Get-FileHashValue (Join-Path $mainRoot ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar)))) -ne $sourceHashes[$relative]) { throw "Publication source changed during planning: $relative" }
}
foreach ($plan in $plans) { '{0,-24} {1,-12} {2}' -f $plan.Target, $plan.Action, $plan.Relative }
$changes = @($plans | Where-Object Action -ne 'up-to-date')
"Shared-root: $sharedRoot; changed paths: $($changes.Count); bind worktrees: $([bool]$BindWorktrees)."
if (-not $Apply) { 'Dry run only; no shared or worktree files were written.'; return }
if ($changes.Count -eq 0) { 'Publication already current; no backups or writes performed.'; return }

$backupRoot = Join-Path $env:LOCALAPPDATA ('Temp\opencode\workflow-publish-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
$receiptFiles = @($plans | ForEach-Object {
    $oldHash = if (Test-Path -LiteralPath $_.Path -PathType Leaf) { Get-FileHashValue $_.Path } else { $null }
    @{ target = $_.Target; path = $_.Relative; old_sha256 = $oldHash; new_sha256 = $(if ($_.Delete) { $null } else { Get-Sha256 $_.Bytes }); deleted = [bool]$_.Delete }
})
if (-not $BindWorktrees -and $previous) {
    $receiptFiles += @($previous.files | Where-Object { $_.target -match '^MoonStation14-agent-0[1-5]$' })
}
$receipt = @{ created_utc = [DateTime]::UtcNow.ToString('o'); main_root = $mainRoot; shared_root = $sharedRoot; source_hashes = $sourceHashes; files = $receiptFiles }
[System.IO.File]::WriteAllText((Join-Path $backupRoot 'publish-receipt.json'), ($receipt | ConvertTo-Json -Depth 30), [System.Text.UTF8Encoding]::new($false))
foreach ($plan in $changes) {
    if (Test-Path -LiteralPath $plan.Path -PathType Leaf) {
        $backup = Join-Path $backupRoot (Join-Path $plan.Target ($plan.Relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar)))
        $directory = Split-Path -Parent $backup
        if (-not (Test-Path -LiteralPath $directory)) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }
        [System.IO.File]::WriteAllBytes($backup, [System.IO.File]::ReadAllBytes($plan.Path))
    }
}
foreach ($plan in $changes) {
    if ($plan.Delete) { Remove-Item -LiteralPath $plan.Path -Force; continue }
    $directory = Split-Path -Parent $plan.Path
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }
    [System.IO.File]::WriteAllBytes($plan.Path, $plan.Bytes)
}
"Published $($changes.Count) managed changes. Backup and receipt: $backupRoot"
'Partial failures are not automatically rolled back; preserve the receipt and reconcile explicitly.'
