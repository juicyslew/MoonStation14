[CmdletBinding()]
param(
    [switch] $Apply,
    [switch] $BindWorktrees,
    [string] $PreviousReceipt,
    [string] $TestFixtureRoot,
    [switch] $StageNeutral05,
    [switch] $ShellPolicyOnly,
    [switch] $WorkflowMcpOnly,
    [int[]] $TargetWorktrees,
    [switch] $CoordinatorPackage,
    [string] $PackageStage,
    [string] $BindingPlan,
    [switch] $CoordinatorPackageApply,
    [string] $CoordinatorPackageRollback,
    [string] $CoordinatorPackageReceipt,
    [string] $CoordinatorPackagePointerPath,
    [string] $ExpectedCoordinatorPointerSha256
)

$ErrorActionPreference = 'Stop'
$script:SharedSources = @(
    'tooling/agent_workflow/gate_liveness.py',
    'tooling/agent_workflow/agent_status.py',
    'tooling/agent_workflow/workflow_observer.py',
    'tooling/agent_workflow/owner_attention.py',
    'tooling/agent_workflow/owner_feedback.py',
    'tooling/agent_workflow/gate_progress.py',
    'tooling/agent_workflow/validation_evidence.py',
    'tooling/agent_workflow/admission.py',
    'tooling/agent_workflow/gate_dispatch.py',
    'tooling/agent_workflow/gate_execution.py',
    'tooling/agent_workflow/workflow_runtime.py',
    'tooling/agent_workflow/workflow_mcp.py',
    'tooling/agent_workflow/requirements-workflow-mcp.txt',
    'docs/development-workflow/README.runtime.md',
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
$script:SourceMap = @{
    'tooling/agent_workflow/workflow_runtime.py' = 'tooling/agent_workflow/workflow.py'
    'docs/development-workflow/README.runtime.md' = 'docs/development-workflow/README.md'
}
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

function Assert-GradleShellPolicy($Config) {
    $roles = @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')
    foreach ($role in $roles) {
        $permissions = @($Config.agents[$role].permissions | Where-Object action -eq 'shell')
        foreach ($rule in $permissions) {
            if ($rule.resource -match '(?i)gradle' -and $rule.resource -match '(?i)(?:test|build|check)' -and
                $rule.effect -eq 'deny' -and $rule.resource -notmatch '^\*gradle\* (?:(?::|:\*:|\*:)?)(?:test|build|check)(?: \*)?$') {
                throw "Unexpected Gradle task deny in role ${role}: $($rule.resource)"
            }
        }
        if (@($permissions | Where-Object { $_.resource -match '^\*gradlew\*(?:build|check|test)\*' }).Count) {
            throw "Overbroad Gradle task policy in role $role."
        }
        $commands = @('gradle test','gradle :test','gradle :sub:test','gradle build','gradle :build','gradle :sub:build','gradle check','gradle :check','gradle :sub:check', 'gradle runGameTestServer test --tests X', 'gradle test --tests X runGameTestServer')
        foreach ($command in $commands) {
            $effect = 'allow'
            foreach ($rule in $permissions) { if ($command -like $rule.resource) { $effect = $rule.effect } }
            if ($effect -ne 'deny') { throw "Gradle task boundary is not denied in role ${role}: $command" }
        }
        if ($role -ne 'integration-lead') {
            foreach ($focus in @('gradle test --tests X','gradle :sub:test --tests=X')) {
                $effect = 'deny'
                foreach ($rule in $permissions) { if ($focus -like $rule.resource) { $effect = $rule.effect } }
                if ($effect -ne 'allow') { throw "Focused JVM test exception is missing in role ${role}: $focus" }
            }
        }
        if ($role -ne 'integration-lead' -and @($permissions | Where-Object { $_.resource -like '*gradle* test *--tests*' -and $_.effect -eq 'allow' }).Count -lt 1) {
            throw "Missing focused JVM test exceptions in role $role."
        }
        foreach ($hard in @('*runGameTestServer*','*runServer*','*runClient*','*featureReadiness*')) {
            $command = $hard.Trim('*')
            $effect = 'allow'
            foreach ($rule in $permissions) { if ($command -like $rule.resource) { $effect = $rule.effect } }
            if ($effect -ne 'deny') { throw "Hard task deny '$hard' must follow focused exceptions in role $role." }
        }
    }
}

function Get-ShellPolicyConfig([byte[]] $Bytes, $Canonical) {
    $config = (Get-TextData $Bytes).Text | ConvertFrom-Json -AsHashtable
    $owned = @{}
    foreach ($role in @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')) {
        if (-not $config.agents.ContainsKey($role) -or -not $Canonical.agents.ContainsKey($role)) { continue }
        $ownedPattern = '(?i)(?:gradle.*(?:test|build|check)|runGameTestServer|runServer|runClient|featureReadiness)'
        $source = @($Canonical.agents[$role].permissions | Where-Object { $_.action -eq 'shell' -and $_.resource -match $ownedPattern })
        $allowed = @($source | ForEach-Object { ([string]$_.resource) + '|' + ([string]$_.effect) })
        $existing = @($config.agents[$role].permissions | Where-Object { $_.action -eq 'shell' -and $_.resource -match $ownedPattern })
        foreach ($rule in $existing) {
            $key = ([string]$rule.resource) + '|' + ([string]$rule.effect)
            $knownLegacy = @('*gradlew*build*|deny','*gradlew*check*|deny','*gradlew*test*|deny','*gradlew*test*--tests*|allow')
            if ($key -notin $allowed -and $key -notin $knownLegacy) { throw "Unknown custom Gradle shell rule in $role; refusing to replace: $($rule.resource)" }
        }
        $rules = @($config.agents[$role].permissions)
        $first = [Array]::FindIndex($rules, [Predicate[object]]{ param($r) $r.action -eq 'shell' -and $r.resource -match $ownedPattern })
        $kept = @($rules | Where-Object { -not ($_.action -eq 'shell' -and $_.resource -match $ownedPattern) })
        $insert = @($kept | Where-Object { $_.action -eq 'shell' -and $_.resource -match 'workflow\.py.*submit' } | Select-Object -First 1)
        if ($insert.Count) {
            $at = [Array]::IndexOf($kept, $insert[0])
            $before = if ($at -gt 0) { @($kept[0..($at-1)]) } else { @() }
            $after = if ($at -lt ($kept.Count - 1)) { @($kept[($at+1)..($kept.Count-1)]) } else { @() }
            $config.agents[$role].permissions = $before + $source + @($kept[$at]) + $after
        }
        else { $config.agents[$role].permissions = $kept + $source }
    }
    return ,[System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $config) + "`n")
}

function Get-ShellPolicyFidelityHeader([byte[]] $Bytes, [byte[]] $Canonical) {
    $current = Get-TextData $Bytes; $canonicalData = Get-TextData $Canonical
    $blockPattern = '(?ms)^  bash:\r?\n.*?(?=^  [^ \r\n][^\r\n]*:|\z)'
    $old = [regex]::Match($current.Text, $blockPattern); $new = [regex]::Match($canonicalData.Text, $blockPattern)
    if (-not $old.Success -or -not $new.Success) { throw 'Fidelity bash permission block missing.' }
    $canonicalRules = @{}; foreach ($line in [regex]::Matches($new.Value,'(?m)^    "([^"]+)": (allow|deny)$')) { $canonicalRules[$line.Groups[1].Value] = $line.Groups[2].Value }
    foreach ($lineText in [regex]::Split($old.Value, '\r?\n')) {
        if ($lineText -eq '  bash:') { continue }
        if ([string]::IsNullOrWhiteSpace($lineText) -or $lineText -match '^\s*#') { continue }
        $parsed = [regex]::Match($lineText,'^    "([^"]+)": (allow|deny)$')
        if (-not $parsed.Success) { throw "Unrecognized fidelity bash policy entry; refusing to replace: $lineText" }
        $resource = $parsed.Groups[1].Value; $effect = $parsed.Groups[2].Value
        $legacyEffect = $resource -match '^\*(?:runGameTestServer|runServer|runClient|featureReadiness)\*$' -and $effect -eq 'deny'
        $knownLegacyGradle = @('*gradlew*build*','*gradlew*check*','*gradlew*test*','*gradlew*test*--tests*') -contains $resource
        $allowedOldEffect = $legacyEffect -or ($knownLegacyGradle -and (($resource -eq '*gradlew*test*--tests*' -and $effect -eq 'allow') -or ($resource -ne '*gradlew*test*--tests*' -and $effect -eq 'deny')))
        $canonicalMatch = $canonicalRules.ContainsKey($resource) -and $canonicalRules[$resource] -eq $effect
        $legacyMatch = ($knownLegacyGradle -or $legacyEffect) -and $allowedOldEffect
        if (-not ($canonicalMatch -or $legacyMatch)) { throw "Unknown custom fidelity bash policy; refusing to replace: $resource ($effect)" }
    }
    return ConvertTo-TextBytes ($current.Text.Substring(0,$old.Index) + $new.Value + $current.Text.Substring($old.Index+$old.Length)) $current
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

function Get-TailoredProjectConfig([byte[]] $Bytes, [string] $SharedRoot, [string] $WorkspaceParent, [int] $TargetNumber, $CanonicalPolicy = $null) {
    if ($CanonicalPolicy) { $Bytes = Get-ShellPolicyConfig $Bytes $CanonicalPolicy }
    $config = (Get-TextData $Bytes).Text | ConvertFrom-Json -AsHashtable
    $shared = $SharedRoot.Replace('\', '/') + '/**'
    $boundRoot = Join-Path $WorkspaceParent ('MoonStation14-agent-{0:D2}' -f $TargetNumber)
    $managedConfigPaths = @('opencode.jsonc','.opencode/agents/integration-lead.md','.opencode/agents/fidelity-reviewer.md') | ForEach-Object { (Join-Path $boundRoot $_).Replace('\','/') }
    $roles = @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')
    foreach ($role in $roles) {
        if (-not $config.agents.ContainsKey($role)) { continue }
        $config.agents[$role].permissions = @($config.agents[$role].permissions | Where-Object {
            -not ($_.action -eq 'external_directory' -and $_.resource -match '/MoonStation14(?:-agent-[^/]+)?/\*\*$')
        })
        Add-Rule $config.agents[$role] 'external_directory' $shared 'allow'
        Add-Rule $config.agents[$role] 'edit' $shared 'deny'
        foreach ($managedConfig in $managedConfigPaths) {
            Add-Rule $config.agents[$role] 'edit' $managedConfig 'deny'
        }
    }
    if ($TargetNumber -eq 5) {
        $mainReports = Join-Path $WorkspaceParent 'MoonStation14/build/agent-workflow'
        Add-Rule $config.agents['integration-lead'] 'external_directory' ($mainReports.Replace('\', '/') + '/**') 'allow'
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

function Get-WorkflowMcpEntry([string] $SharedRoot, [string] $RepoRoot, [string] $Profile, [bool] $Disabled = $false) {
    $adapter = Join-Path $SharedRoot 'tooling/agent_workflow/workflow_mcp.py'
    $python = Join-Path $SharedRoot '.venv-workflow-mcp/Scripts/python.exe'
    return [ordered]@{
        type = 'local'
        command = @($python,$adapter,'--repo',$RepoRoot,'--profile',$Profile)
        cwd = $RepoRoot
        environment = [ordered]@{}
        disabled = $Disabled
        codemode = $false
        timeout = [ordered]@{ startup = 15000; catalog = 15000; execution = 300000 }
        protocol = 'legacy'
    }
}

function Get-NormalizedWorkflowMcpJson($Entry) {
    $normalized = [ordered]@{}
    foreach ($key in $Entry.Keys) { $normalized[$key] = $Entry[$key] }
    if ($normalized.command -is [System.Collections.IList]) {
        $command = @($normalized.command)
        foreach ($index in @(0,1,3)) {
            if ($index -lt $command.Count -and $command[$index] -is [string]) { $command[$index] = $command[$index].Replace('\','/').TrimEnd('/').ToLowerInvariant() }
        }
        $normalized.command = $command
    }
    if ($normalized.cwd -is [string]) { $normalized.cwd = $normalized.cwd.Replace('\','/').TrimEnd('/').ToLowerInvariant() }
    return ConvertTo-CanonicalConfigJson $normalized
}

function Test-KnownRetiredWorkflowCliRule([string] $Role, $Permission, [string] $RepoRoot) {
    if ($Permission.action -cne 'shell' -or $Permission.effect -cne 'allow') { return $false }
    $known = @(
        @{role='orchestrate-ms14';resource='python tooling/agent_workflow/workflow.py submit *'},
        @{role='orchestrate-ms14';resource='python tooling/agent_workflow/workflow.py --repo * submit *'},
        @{role='3d-modeler';resource='python tooling/agent_workflow/workflow.py submit *'},
        @{role='3d-modeler';resource='python tooling/agent_workflow/workflow.py --repo * submit *'},
        @{role='integration-lead';resource='*workflow.py*gate-validate*'},
        @{role='integration-lead';resource='*workflow.py*gate-clear-blocker*'},
        @{role='integration-lead';resource='*workflow.py*gate-report*';leadOnly=$true},
        @{role='integration-lead';resource='*workflow.py*gate-deliver*';leadOnly=$true}
    )
    $isLeadRoot = (Split-Path -Leaf $RepoRoot) -ceq 'MoonStation14-agent-05'
    return (@($known | Where-Object { $_.role -ceq $Role -and $_.resource -ceq $Permission.resource -and (-not $_.leadOnly -or $isLeadRoot) }).Count -gt 0)
}

function Get-ExpectedMcpPermissionTuples([string] $Profile, [string] $Role) {
    $expected = @(@{action='ms14_workflow_*';resource='*';effect='deny'})
    if ($Profile -eq 'author' -and $Role -ceq 'orchestrate-ms14') {
        $expected += @(@{action='ms14_workflow_submit';resource='*';effect='allow'},@{action='ms14_workflow_status';resource='*';effect='allow'})
    }
    elseif ($Profile -eq 'lead' -and $Role -ceq 'integration-lead') {
        $expected += @(@{action='ms14_workflow_status';resource='*';effect='allow'},@{action='ms14_workflow_validate';resource='*';effect='allow'},@{action='ms14_workflow_review';resource='*';effect='allow'},@{action='ms14_workflow_finish';resource='*';effect='allow'})
    }
    return ,$expected
}

function Test-ExpectedMcpPermissionTuple([string] $Profile, [string] $Role, $Permission) {
    $expected = Get-ExpectedMcpPermissionTuples $Profile $Role
    foreach ($rule in $expected) {
        if ($rule.action -ceq $Permission.action -and $rule.resource -ceq $Permission.resource -and $rule.effect -ceq $Permission.effect) { return $true }
    }
    return $false
}

function Get-WorkflowMcpConfig([byte[]] $Bytes, [string] $SharedRoot, [string] $RepoRoot, [string] $Profile) {
    if ($Profile -notin @('author','lead')) { throw "Invalid workflow MCP profile '$Profile'." }
    $config = (Get-TextData $Bytes).Text | ConvertFrom-Json -AsHashtable
    if (-not ($config -is [System.Collections.IDictionary]) -or -not ($config.agents -is [System.Collections.IDictionary])) { throw 'Cannot configure MCP in malformed OpenCode project config.' }
    if ($config.Contains('mcp') -and -not ($config.mcp -is [System.Collections.IDictionary])) { throw 'Cannot merge workflow MCP into malformed top-level mcp config.' }
    if (-not $config.Contains('mcp')) { $config.mcp = [ordered]@{} }
    $protectedConfigPaths = @()
    if ((Split-Path -Leaf ([System.IO.Path]::GetFullPath($RepoRoot))) -match '^MoonStation14-agent-0[1-5]$') {
        $protectedConfigs = @('opencode.jsonc','.opencode/agents/integration-lead.md','.opencode/agents/fidelity-reviewer.md')
        $protectedConfigPaths = @($protectedConfigs | ForEach-Object { (Join-Path $RepoRoot $_).Replace('\','/') })
    }
    $entry = Get-WorkflowMcpEntry $SharedRoot $RepoRoot $Profile
    if ($config.mcp.Contains('timeout') -and -not ($config.mcp.timeout -is [System.Collections.IDictionary])) { throw 'Cannot merge workflow MCP into malformed root mcp.timeout.' }
    if ($config.mcp.Contains('servers') -and -not ($config.mcp.servers -is [System.Collections.IDictionary])) { throw 'Cannot merge workflow MCP into malformed root mcp.servers.' }
    $servers = if ($config.mcp.Contains('servers')) { $config.mcp.servers } else { [ordered]@{} }
    $desired = Get-NormalizedWorkflowMcpJson $entry
    $prepared = Get-NormalizedWorkflowMcpJson (Get-WorkflowMcpEntry $SharedRoot $RepoRoot $Profile $true)
    if ($config.mcp.Contains('ms14_workflow')) {
        if ($servers.Contains('ms14_workflow')) { throw 'Conflicting flat and root mcp.servers ms14_workflow entries; refusing migration.' }
        $legacy = Get-NormalizedWorkflowMcpJson $config.mcp.ms14_workflow
        if ($legacy -cne $desired -and $legacy -cne $prepared) { throw 'Unowned flat mcp.ms14_workflow entry; refusing migration.' }
        $config.mcp.Remove('ms14_workflow')
    }
    if ($servers.Contains('ms14_workflow')) {
        $existing = Get-NormalizedWorkflowMcpJson $servers.ms14_workflow
        if ($existing -cne $desired -and $existing -cne $prepared) { throw 'Unowned or incompatible mcp.servers.ms14_workflow entry; refusing to replace it.' }
    }
    else { $servers['ms14_workflow'] = $entry }
    $servers['ms14_workflow'] = $entry
    $config.mcp.servers = $servers
    $toolsByRole = @{}
    foreach ($role in @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')) { $toolsByRole[$role] = @() }
    if ($Profile -eq 'author') { $toolsByRole['orchestrate-ms14'] = @('submit','status') }
    else { $toolsByRole['integration-lead'] = @('status','validate','review','finish') }
    foreach ($role in $toolsByRole.Keys) {
        if (-not $config.agents.Contains($role)) { continue }
        $permissions = @($config.agents[$role].permissions)
        $managedRules = @($permissions | Where-Object { $_.action -like 'ms14_workflow*' })
        foreach ($r in $managedRules) {
            if (-not (Test-ExpectedMcpPermissionTuple $Profile $role $r)) { throw "Unowned or conflicting workflow MCP permission on role ${role}: $($r.action) $($r.resource) $($r.effect)" }
        }
        $permissions = @($permissions | Where-Object { -not (Test-ExpectedMcpPermissionTuple $Profile $role $_) })
        $permissions = @($permissions | Where-Object { -not ($_.action -eq 'edit' -and $_.resource -in $protectedConfigPaths -and $_.effect -eq 'deny') })
        $unknownCliAllow = @($permissions | Where-Object { $_.action -eq 'shell' -and $_.effect -eq 'allow' -and $_.resource -match '(?i)workflow(?:_entry|_runtime)?\.py' -and -not (Test-KnownRetiredWorkflowCliRule $role $_ $RepoRoot) })
        if ($unknownCliAllow.Count) { throw "Unowned direct workflow CLI shell allowance on role $role; refusing fallback: $($unknownCliAllow[0].resource)" }
        $permissions = @($permissions | Where-Object { -not (Test-KnownRetiredWorkflowCliRule $role $_ $RepoRoot) })
        $shellRouteDenies = @('*workflow.py*','*workflow_entry.py*','*workflow_runtime.py*')
        $permissions = @($permissions | Where-Object { -not ($_.action -eq 'shell' -and $_.effect -eq 'deny' -and $_.resource -in $shellRouteDenies) })
        foreach ($resource in $shellRouteDenies) { $permissions += [ordered]@{ action='shell'; resource=$resource; effect='deny' } }
        $permissions += [ordered]@{ action='ms14_workflow_*'; resource='*'; effect='deny' }
        foreach ($tool in $toolsByRole[$role]) { $permissions += [ordered]@{ action=('ms14_workflow_' + $tool); resource='*'; effect='allow' } }
        foreach ($managedConfig in $protectedConfigPaths) {
            if (-not @($permissions | Where-Object { $_.action -eq 'edit' -and $_.resource -eq $managedConfig -and $_.effect -eq 'deny' }).Count) {
                $permissions += [ordered]@{ action='edit'; resource=$managedConfig; effect='deny' }
            }
        }
        $config.agents[$role].permissions = $permissions
    }
    return ,[System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $config) + "`n")
}

function Get-McpUnownedConfigView([byte[]] $Bytes, [string] $RepoRoot, [string] $SharedRoot, [string] $Profile, [bool] $PreserveEmptyServers = $false) {
    $config = (Get-TextData $Bytes).Text | ConvertFrom-Json -AsHashtable
    if ($config.mcp -is [System.Collections.IDictionary]) {
        $desired = Get-NormalizedWorkflowMcpJson (Get-WorkflowMcpEntry $SharedRoot $RepoRoot $Profile)
        $prepared = Get-NormalizedWorkflowMcpJson (Get-WorkflowMcpEntry $SharedRoot $RepoRoot $Profile $true)
        if ($config.mcp.Contains('ms14_workflow')) {
            $legacy = Get-NormalizedWorkflowMcpJson $config.mcp.ms14_workflow
            if ($legacy -ceq $desired -or $legacy -ceq $prepared) { $config.mcp.Remove('ms14_workflow') }
        }
        if ($config.mcp.servers -is [System.Collections.IDictionary] -and $config.mcp.servers.Contains('ms14_workflow')) {
            $serverEntry = Get-NormalizedWorkflowMcpJson $config.mcp.servers.ms14_workflow
            if ($serverEntry -ceq $desired -or $serverEntry -ceq $prepared) {
                $config.mcp.servers.Remove('ms14_workflow')
                if ($config.mcp.servers.Count -eq 0 -and -not $PreserveEmptyServers) { $config.mcp.Remove('servers') }
            }
        }
        if ($config.mcp.Count -eq 0) { $config.Remove('mcp') }
    }
    $protectedPaths = @('opencode.jsonc','.opencode/agents/integration-lead.md','.opencode/agents/fidelity-reviewer.md') | ForEach-Object { (Join-Path $RepoRoot $_).Replace('\','/') }
    $profile = if ((Split-Path -Leaf $RepoRoot) -ceq 'MoonStation14-agent-05') { 'lead' } else { 'author' }
    $shellRoutes = @('*workflow.py*','*workflow_entry.py*','*workflow_runtime.py*')
    foreach ($role in @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')) {
        if (-not $config.agents.Contains($role)) { continue }
        $isManagedRole = $role -in @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')
        $permissions = @($config.agents[$role].permissions | Where-Object {
            $managedTool = $_.action -like 'ms14_workflow*' -and (Test-ExpectedMcpPermissionTuple $profile $role $_)
            $managedEdit = $_.action -eq 'edit' -and $_.effect -eq 'deny' -and $_.resource -in $protectedPaths
            $managedRoute = $_.action -eq 'shell' -and $_.effect -eq 'deny' -and $_.resource -in $shellRoutes
            $retiredCli = Test-KnownRetiredWorkflowCliRule $role $_ $RepoRoot
            -not ($managedTool -or ($isManagedRole -and ($managedEdit -or $managedRoute -or $retiredCli)))
        })
        $config.agents[$role].permissions = $permissions
    }
    return ConvertTo-CanonicalConfigJson $config
}

function Test-McpOnlySemanticChange([byte[]] $OldBytes, [byte[]] $NewBytes, [string] $RepoRoot, [string] $SharedRoot, [string] $Profile) {
    $oldConfig = (Get-TextData $OldBytes).Text | ConvertFrom-Json -AsHashtable
    $keepEmptyServers = $oldConfig.mcp.servers -is [System.Collections.IDictionary] -and $oldConfig.mcp.servers.Count -eq 0
    return (Get-McpUnownedConfigView $OldBytes $RepoRoot $SharedRoot $Profile $keepEmptyServers) -ceq (Get-McpUnownedConfigView $NewBytes $RepoRoot $SharedRoot $Profile $keepEmptyServers)
}

function Assert-McpPlanUnchanged($Plans) {
    foreach ($plan in $Plans) {
        if ((Get-FileHashValue $plan.Path) -ne $plan.OldHash) { throw "MCP target changed since planning; no files written: $($plan.Target)/$($plan.Relative)" }
    }
}

function Get-McpLeadProfile([byte[]] $Bytes) {
    $data = Get-TextData $Bytes
    $text = $data.Text
    $newline = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $legacy = @(
        '    "*workflow.py*--help*": allow',
        '    "*workflow.py*status*": allow',
        '    "*workflow.py*gate-preflight*": allow',
        '    "*workflow.py*gate-validate*": allow',
        '    "*workflow.py*gate-report*": allow',
        '    "*workflow.py*gate-clear-blocker*": allow',
        '    "*workflow.py*candidate-review*": allow',
        '    "*workflow.py*gate-finish*": allow',
        '    "*workflow.py*gate-deliver*": allow',
        '    "*workflow.py*gate-scope-review*": allow',
        '    "*workflow.py*manual-tests*": allow',
        '    "*workflow.py*manual-notify*": allow'
    )
    $managed = @('  ms14_workflow_status: allow','  ms14_workflow_validate: allow','  ms14_workflow_review: allow','  ms14_workflow_finish: allow')
    $hadFinalNewline = $text.EndsWith($newline)
    $body = if ($hadFinalNewline) { $text.Substring(0,$text.Length - $newline.Length) } else { $text }
    $lines = [System.Collections.Generic.List[string]]::new()
    $seenLegacy = @{}; $seenManaged = @{}
    foreach ($line in ($body -split "`r?`n")) {
        if ($legacy -ccontains $line) { $seenLegacy[$line] = $true; continue }
        if ($line -match '^\s*"\*workflow\.py\*.*": allow$') { throw "Unrecognized workflow CLI permission in lead profile: $line" }
        if ($managed -ccontains $line) { $seenManaged[$line] = $true; continue }
        if ($line -match '^\s*ms14_workflow_[^:]+:') { throw "Unrecognized workflow MCP permission in lead profile: $line" }
        $lines.Add($line)
    }
    if ($seenLegacy.Count -ne 0 -and $seenLegacy.Count -ne $legacy.Count) { throw 'Lead profile has a partial legacy workflow CLI permission set; refusing migration.' }
    if ($seenManaged.Count -ne 0 -and $seenManaged.Count -ne $managed.Count) { throw 'Lead profile has a partial MCP permission set; refusing migration.' }
    $anchor = '  webfetch: allow'
    if (@($lines | Where-Object { $_ -ceq $anchor }).Count -ne 1) { throw 'Lead profile requires one exact webfetch permission anchor for MCP permission insertion.' }
    $insertAt = $lines.IndexOf($anchor)
    $lines.InsertRange($insertAt,[string[]]$managed)
    $updated = $lines -join $newline
    if ($hadFinalNewline) { $updated += $newline }
    return ConvertTo-TextBytes $updated $data
}

function Get-McpLeadProfileUnownedText([byte[]] $Bytes) {
    $text = (Get-TextData $Bytes).Text
    $legacy = @('    "*workflow.py*--help*": allow','    "*workflow.py*status*": allow','    "*workflow.py*gate-preflight*": allow','    "*workflow.py*gate-validate*": allow','    "*workflow.py*gate-report*": allow','    "*workflow.py*gate-clear-blocker*": allow','    "*workflow.py*candidate-review*": allow','    "*workflow.py*gate-finish*": allow','    "*workflow.py*gate-deliver*": allow','    "*workflow.py*gate-scope-review*": allow','    "*workflow.py*manual-tests*": allow','    "*workflow.py*manual-notify*": allow')
    $managed = @('  ms14_workflow_status: allow','  ms14_workflow_validate: allow','  ms14_workflow_review: allow','  ms14_workflow_finish: allow')
    $newline = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $hadFinalNewline = $text.EndsWith($newline)
    $body = if ($hadFinalNewline) { $text.Substring(0,$text.Length - $newline.Length) } else { $text }
    $lines = @($body -split "`r?`n" | Where-Object { $_ -cnotin $legacy -and $_ -cnotin $managed })
    $result = $lines -join $newline
    if ($hadFinalNewline) { $result += $newline }
    return $result
}

function Test-McpLeadProfileSemanticChange([byte[]] $OldBytes, [byte[]] $NewBytes) {
    return (Get-McpLeadProfileUnownedText $OldBytes) -ceq (Get-McpLeadProfileUnownedText $NewBytes)
}

function Get-TailoredIntegrationProfile([byte[]] $Bytes, [string] $WorkspaceParent, [int] $TargetNumber) {
    $data = Get-TextData $Bytes
    $nl = if ($data.Text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $mainGrant = '    "C:/Users/William/Documents/Minecraft Mod Dev Files/MoonStation14/**": allow'
    $text = $data.Text.Replace($mainGrant + "`r`n", '').Replace($mainGrant + "`n", '')
    if ($TargetNumber -eq 5) {
        $assetAnchor = '    "C:/Users/William/Documents/Minecraft Mod Dev Files/Asset Building Workspace/**": allow'
        if (-not $text.Contains($assetAnchor)) { throw 'Integration profile lacks its approved asset-workspace grant.' }
        $mainReports = (Join-Path $WorkspaceParent 'MoonStation14/build/agent-workflow').Replace('\', '/')
        $reportsGrant = '    "' + $mainReports + '/**": allow'
        if (-not $text.Contains($reportsGrant)) { $text = $text.Replace($assetAnchor, $assetAnchor + $nl + $reportsGrant) }
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

function Get-NeutralCoordinatorEntrypoint([byte[]] $Bytes) {
    $data = Get-TextData $Bytes
    $text = $data.Text
    $anchor = 'if __name__ == "__main__":' + "`n" + '    raise SystemExit(_MODULE.main())'
    if (-not $text.Contains($anchor)) { $anchor = 'if __name__ == "__main__":' + "`r`n" + '    raise SystemExit(_MODULE.main())' }
    if (-not $text.Contains($anchor)) { throw 'Workflow entrypoint lacks the known main guard; refusing coordinator specialization.' }
    $nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $replacement = 'if __name__ == "__main__":' + $nl +
        '    if not any(arg == "--asset-workspace" or arg.startswith("--asset-workspace=") for arg in sys.argv[1:]):' + $nl +
        '        sys.argv[1:1] = ["--asset-workspace", str(Path(__file__).resolve().parents[3] / "Asset Building Workspace")]' + $nl +
        '    raise SystemExit(_MODULE.main())'
    return ConvertTo-TextBytes ($text.Replace($anchor, $replacement)) $data
}

function Get-NeutralCoordinatorConfig([byte[]] $ConfigBytes, [byte[]] $LeadProfile, [byte[]] $FidelityProfile, [string] $SharedRoot, [string] $WorkspaceParent) {
    $tailored = Get-TailoredProjectConfig $ConfigBytes $SharedRoot $WorkspaceParent 5
    $config = (Get-TextData $tailored).Text | ConvertFrom-Json -AsHashtable
    $config.agents = [ordered]@{
        'integration-lead' = $config.agents['integration-lead']
        'fidelity-reviewer' = $config.agents['fidelity-reviewer']
    }
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $config) + "`n")
    return ,$bytes
}

function Write-NeutralCoordinatorStage([string] $MainRoot, [string] $SharedRoot, [byte[]] $Entrypoint, [byte[]] $ConfigBytes, [byte[]] $LeadProfile, [byte[]] $FidelityProfile) {
    $stage = Join-Path $MainRoot 'build/reports/neutral-cutover/bindings'
    $workspaceParent = Split-Path -Parent $MainRoot
    $outputs = [ordered]@{
        'AGENTS.md' = [System.Text.UTF8Encoding]::new($false).GetBytes("# Neutral integration coordinator`n`nThis directory coordinates serialized integration gates; it is not a Git worktree or feature-author checkout. Follow the shared [workflow guide]($SharedRoot/docs/development-workflow/README.md), [coordinator guide]($SharedRoot/docs/development-workflow/coordinator.md), and [integration-lead contract]($SharedRoot/docs/development-workflow/roles/integration-lead.md).`n`nUse the installed CLI and its help. Review candidates read-only in their author worktrees; coding gate receipts and scoped-review evidence are written to the CLI-computed author report paths. Do not edit candidate sources, create another queue, run unrequested expensive jobs, accept, merge, commit, or release. The caller controls readiness-contract scope and serialized validation; preserve that authority.`n")
        'opencode.jsonc' = Get-NeutralCoordinatorConfig $ConfigBytes $LeadProfile $FidelityProfile $SharedRoot $workspaceParent
        '.opencode/agents/integration-lead.md' = [System.Text.UTF8Encoding]::new($false).GetBytes(((Get-TextData (Get-TailoredIntegrationProfile $LeadProfile $workspaceParent 5)).Text))
        '.opencode/agents/fidelity-reviewer.md' = [System.Text.UTF8Encoding]::new($false).GetBytes(((Get-TextData (Get-TailoredFidelityProfile $FidelityProfile)).Text))
        'tooling/agent_workflow/workflow.py' = Get-NeutralCoordinatorEntrypoint $Entrypoint
    }
    foreach ($relative in $outputs.Keys) {
        $path = Join-Path $stage ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
        [System.IO.File]::WriteAllBytes($path, $outputs[$relative])
        '{0}  sha256:{1}' -f (Join-Path $stage $relative), (Get-Sha256 $outputs[$relative])
    }
    return $stage
}

$mainRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if ($TestFixtureRoot) {
    if ($Apply -and -not ($ShellPolicyOnly -or $WorkflowMcpOnly -or $TestFixtureRoot)) { throw '-TestFixtureRoot is dry-run-only except for isolated publisher fixture deployments.' }
    $fixtureRoot = [System.IO.Path]::GetFullPath($TestFixtureRoot)
    $approvedTemp = [System.IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'Temp\opencode'))
    if (-not $fixtureRoot.StartsWith($approvedTemp + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Test fixture root must be inside the approved OpenCode temp directory.'
    }
    $mainRoot = Join-Path $fixtureRoot 'MoonStation14'
}
if ((Split-Path -Leaf $mainRoot) -ne 'MoonStation14') { throw "Run publisher from MAIN MoonStation14; resolved $mainRoot" }
if ($CoordinatorPackage) {
    if (-not $PackageStage) { throw '-CoordinatorPackage requires an explicit private -PackageStage.' }
    if (-not $PreviousReceipt -or -not (Test-Path -LiteralPath $PreviousReceipt -PathType Leaf)) { throw '-CoordinatorPackage requires an existing -PreviousReceipt.' }
    $previous = Get-Content -LiteralPath $PreviousReceipt -Raw | ConvertFrom-Json
    $packagePython = Join-Path $previous.shared_root '.venv-workflow-mcp/Scripts/python.exe'
    if ($TestFixtureRoot -and -not (Test-Path -LiteralPath $packagePython -PathType Leaf)) {
        $realMain = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
        $realShared = Join-Path (Split-Path -Parent $realMain) 'MoonStation14-shared'
        $packagePython = Join-Path $realShared '.venv-workflow-mcp/Scripts/python.exe'
    }
    if (-not (Test-Path -LiteralPath $packagePython -PathType Leaf)) { throw "Pinned coordinator publisher Python missing: $packagePython" }
    if ($Apply -or $BindWorktrees -or $WorkflowMcpOnly -or $ShellPolicyOnly -or $StageNeutral05 -or $TargetWorktrees) {
        throw '-CoordinatorPackage is stage-only and cannot be combined with publication/activation modes.'
    }
    if ($CoordinatorPackageApply -and $CoordinatorPackageRollback) { throw 'Choose package apply or rollback, not both.' }
    if (($CoordinatorPackageApply -or $CoordinatorPackageRollback) -and -not $CoordinatorPackageReceipt) { throw 'Package apply/rollback requires -CoordinatorPackageReceipt.' }
    if (($CoordinatorPackageApply -or $CoordinatorPackageRollback) -and (-not $CoordinatorPackagePointerPath -or $ExpectedCoordinatorPointerSha256 -notmatch '^[0-9a-fA-F]{64}$')) { throw 'Package apply/rollback requires explicit publication pointer path and expected SHA-256.' }
    $stagePath = [System.IO.Path]::GetFullPath($PackageStage)
    $approvedStage = [System.IO.Path]::GetFullPath((Join-Path $mainRoot 'build/agent-workflow/workflow-redesign-plan/wr10/stage'))
    $privateTemp = [System.IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'Temp/opencode'))
    if ($stagePath -cne $approvedStage -and -not $stagePath.StartsWith($privateTemp + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'PackageStage must be the approved WR10 build stage or a private OpenCode temp child.'
    }
    $builder = Join-Path $PSScriptRoot 'workflow_coordinator_package.py'
    if (-not (Test-Path -LiteralPath $builder -PathType Leaf)) { throw 'Coordinator package builder missing.' }
    if ($CoordinatorPackageApply -or $CoordinatorPackageRollback) {
        $packageArgs = @($builder,'--stage',$stagePath,'--previous-receipt',[System.IO.Path]::GetFullPath($PreviousReceipt),'--output-receipt',[System.IO.Path]::GetFullPath($CoordinatorPackageReceipt),'--publication-pointer',[System.IO.Path]::GetFullPath($CoordinatorPackagePointerPath),'--expected-pointer-sha256',$ExpectedCoordinatorPointerSha256.ToLowerInvariant())
        if ($CoordinatorPackageApply) { $packageArgs += '--apply-package' } else { $packageArgs += @('--rollback-receipt',[System.IO.Path]::GetFullPath($CoordinatorPackageRollback)) }
        & $packagePython @packageArgs
        if ($LASTEXITCODE -ne 0) { throw "Coordinator package apply/rollback failed ($LASTEXITCODE). Inspect partial-hold evidence before retry." }
        return
    }
    $builderArgs = @('--stage',$stagePath,'--previous-receipt',[System.IO.Path]::GetFullPath($PreviousReceipt))
    if ($BindingPlan) {
        if (-not (Test-Path -LiteralPath $BindingPlan -PathType Leaf)) { throw 'Explicit -BindingPlan file is missing.' }
        $bindingPlanPath = [System.IO.Path]::GetFullPath($BindingPlan)
        $privateRoot = [System.IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'Temp/opencode'))
        if (-not $bindingPlanPath.StartsWith($privateRoot + [System.IO.Path]::DirectorySeparatorChar,[System.StringComparison]::OrdinalIgnoreCase)) { throw 'Binding plan must be a private OpenCode temp file.' }
        Assert-NoReparsePath $privateRoot $bindingPlanPath
        $builderArgs += @('--binding-plan',$bindingPlanPath)
    }
    & $packagePython $builder @builderArgs
    if ($LASTEXITCODE -ne 0) { throw "Coordinator package staging failed ($LASTEXITCODE)." }
    $packageManifest = Get-Content -LiteralPath (Join-Path $stagePath 'manifest.json') -Raw | ConvertFrom-Json
    if ($packageManifest.bindings.status -eq 'planned-explicit-targets') {
        $bindingManifest = Get-Content -LiteralPath (Join-Path $stagePath $packageManifest.bindings.manifest) -Raw | ConvertFrom-Json
        foreach ($property in $bindingManifest.files.PSObject.Properties) {
            $entry = $property.Value
            $targetRoot = if ($entry.target -eq 'main') { $mainRoot } else { Join-Path (Split-Path -Parent $mainRoot) $entry.target }
            $live = Join-Path $targetRoot $entry.path
            Assert-NoReparsePath ([System.IO.Path]::GetFullPath($targetRoot)) ([System.IO.Path]::GetFullPath($live))
            $currentHash = Get-FileHashValue $live
            Assert-ReceiptMatch $previous $entry.target $entry.path $currentHash
            if ($currentHash -cne $entry.old_sha256 -or (Get-FileHashValue (Join-Path $stagePath $entry.backup_path)) -cne $entry.backup_sha256 -or
                (Get-FileHashValue (Join-Path $stagePath $entry.staged_path)) -cne $entry.new_sha256) {
                throw "Staged binding plan hash/backup conflict: $($entry.target)/$($entry.path)"
            }
        }
    }
    return
}
if ($BindingPlan) { throw '-BindingPlan is only valid with -CoordinatorPackage.' }
$workspaceParent = Split-Path -Parent $mainRoot
$sharedRoot = [System.IO.Path]::GetFullPath((Join-Path $workspaceParent 'MoonStation14-shared'))
$sourceBytes = @{}; $sourceHashes = @{}
foreach ($relative in $script:SharedSources) {
    $path = Join-Path $mainRoot ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing shared workflow source: $relative" }
    $sourceBytes[$relative] = [System.IO.File]::ReadAllBytes($path)
    $sourceHashes[$relative] = Get-Sha256 $sourceBytes[$relative]
}
$policyConfig = (Get-TextData $sourceBytes['opencode.jsonc']).Text | ConvertFrom-Json -AsHashtable
Assert-GradleShellPolicy $policyConfig
$previous = $null
if ($PreviousReceipt) {
    if (-not (Test-Path -LiteralPath $PreviousReceipt -PathType Leaf)) { throw "Previous receipt not found: $PreviousReceipt" }
    $previous = Get-Content -LiteralPath $PreviousReceipt -Raw | ConvertFrom-Json
}
if ($ShellPolicyOnly) {
    if ($BindWorktrees -or $StageNeutral05 -or $WorkflowMcpOnly) { throw '-ShellPolicyOnly cannot be combined with another publisher mode.' }
    if (-not $TargetWorktrees -or @($TargetWorktrees | Where-Object { $_ -lt 1 -or $_ -gt 5 }).Count -or @($TargetWorktrees | Select-Object -Unique).Count -ne $TargetWorktrees.Count) { throw '-ShellPolicyOnly requires unique -TargetWorktrees values from 1 through 5.' }
    $plans = @(); $canonicalHeader = $sourceBytes['.opencode/agents/fidelity-reviewer.md']
    foreach ($n in $TargetWorktrees) {
        $name = 'MoonStation14-agent-{0:D2}' -f $n; $root = Join-Path $workspaceParent $name
        if (-not (Test-Path -LiteralPath $root -PathType Container) -or (Get-Item -LiteralPath $root -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint) { throw "Assigned worktree missing or linked: $root" }
        foreach ($relative in @('opencode.jsonc','.opencode/agents/fidelity-reviewer.md')) {
            $path = Join-Path $root $relative
            if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing shell-policy target: $path" }
            Assert-NoReparsePath ([System.IO.Path]::GetFullPath($root)) ([System.IO.Path]::GetFullPath($path))
            $old = [System.IO.File]::ReadAllBytes($path)
            $new = if ($relative -eq 'opencode.jsonc') { Get-ShellPolicyConfig $old $policyConfig } else { Get-ShellPolicyFidelityHeader $old $canonicalHeader }
            $plans += [pscustomobject]@{ Target=$name; Path=$path; Relative=$relative; OldHash=(Get-Sha256 $old); Bytes=$new; Action=$(if ((Get-Sha256 $old) -eq (Get-Sha256 $new)) {'up-to-date'} else {'replace'}) }
        }
    }
    $stage = Join-Path $env:LOCALAPPDATA ('Temp\opencode\shell-policy-stage-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $stage -Force | Out-Null
    foreach ($p in $plans) { $out = Join-Path $stage (Join-Path $p.Target $p.Relative); New-Item -ItemType Directory -Path (Split-Path -Parent $out) -Force | Out-Null; [System.IO.File]::WriteAllBytes($out,$p.Bytes); '{0}  sha256:{1}' -f $out,(Get-Sha256 $p.Bytes) }
    $manifest = Join-Path $stage 'manifest.json'
    [System.IO.File]::WriteAllText($manifest,(@{ main_root=$mainRoot; targets=@($TargetWorktrees); files=@($plans | ForEach-Object { @{target=$_.Target;path=$_.Relative;old_sha256=$_.OldHash;new_sha256=(Get-Sha256 $_.Bytes);action=$_.Action} }) } | ConvertTo-Json -Depth 10),[System.Text.UTF8Encoding]::new($false))
    "Manifest: $manifest"; "Staged shell-policy files: $($plans.Count); changes: $(@($plans | Where-Object Action -ne 'up-to-date').Count)."
    if (-not $Apply) { 'Dry run only; no worktree files were written.'; return }
    foreach ($p in $plans) { if ((Get-FileHashValue $p.Path) -ne $p.OldHash) { throw "Target changed since planning: $($p.Target)/$($p.Relative)" } }
    $changes = @($plans | Where-Object Action -ne 'up-to-date'); if (-not $changes.Count) { 'Publication already current; no backups or writes performed.'; return }
    $backupRoot = Join-Path $env:LOCALAPPDATA ('Temp\opencode\workflow-publish-' + [guid]::NewGuid().ToString('N')); New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
    $previousMode = $null
    if ($PreviousReceipt) { if (-not (Test-Path -LiteralPath $PreviousReceipt -PathType Leaf)) { throw "Previous receipt not found: $PreviousReceipt" }; $previousMode = Get-Content -LiteralPath $PreviousReceipt -Raw | ConvertFrom-Json }
    $modeFiles = @()
    if ($previousMode) { $modeFiles += @($previousMode.files | Where-Object { -not ($_.target -in @($plans.Target) -and $_.path -in @($plans.Relative)) }) }
    $modeFiles += @($plans | ForEach-Object {@{target=$_.Target;path=$_.Relative;old_sha256=$_.OldHash;new_sha256=(Get-Sha256 $_.Bytes);deleted=$false}})
    $receipt = @{created_utc=[DateTime]::UtcNow.ToString('o');main_root=$mainRoot;shared_root=$(if ($previousMode) {$previousMode.shared_root} else {$null});source_hashes=$(if ($previousMode) {$previousMode.source_hashes} else {@{}});files=$modeFiles}
    [System.IO.File]::WriteAllText((Join-Path $backupRoot 'publish-receipt.json'),($receipt | ConvertTo-Json -Depth 20),[System.Text.UTF8Encoding]::new($false))
    foreach ($p in $changes) { $backup=Join-Path $backupRoot (Join-Path $p.Target $p.Relative); New-Item -ItemType Directory -Path (Split-Path -Parent $backup) -Force | Out-Null; [System.IO.File]::WriteAllBytes($backup,[System.IO.File]::ReadAllBytes($p.Path)) }
    foreach ($p in $changes) { [System.IO.File]::WriteAllBytes($p.Path,$p.Bytes) }
    "Published $($changes.Count) shell-policy changes. Backup and receipt: $backupRoot"; return
}
if ($WorkflowMcpOnly) {
    if ($BindWorktrees -or $StageNeutral05 -or $ShellPolicyOnly -or $TargetWorktrees) { throw '-WorkflowMcpOnly is an isolated binding mode and cannot be combined with other selectors.' }
    if (-not (Test-Path -LiteralPath (Join-Path $sharedRoot 'tooling/agent_workflow/workflow_mcp.py') -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $sharedRoot '.venv-workflow-mcp/Scripts/python.exe') -PathType Leaf)) {
        throw 'Workflow MCP activation requires the published adapter and isolated SDK environment.'
    }
    if (-not $TestFixtureRoot) {
        $sdkPython = Join-Path $sharedRoot '.venv-workflow-mcp/Scripts/python.exe'
        $sdkVersion = (& $sdkPython -c "from importlib.metadata import version; print(version('mcp'))" 2>$null).Trim()
        if ($LASTEXITCODE -ne 0 -or $sdkVersion -cne '1.26.0') { throw "Workflow MCP activation requires pinned SDK mcp==1.26.0; found '$sdkVersion'." }
    }
    if (-not $previous) { throw '-WorkflowMcpOnly requires the existing shared-publication receipt.' }
    $mcpPlans = @(); $mcpTargets = @([pscustomobject]@{Name='main';Root=$mainRoot;Profile='author'})
    foreach ($n in 1..5) {
        $name = 'MoonStation14-agent-{0:D2}' -f $n; $root = Join-Path $workspaceParent $name
        if (-not (Test-Path -LiteralPath $root -PathType Container) -or (Get-Item -LiteralPath $root -Force).Attributes -band [System.IO.FileAttributes]::ReparsePoint) { throw "Assigned worktree missing or linked: $root" }
        $mcpTargets += [pscustomobject]@{Name=$name;Root=$root;Profile=$(if ($n -eq 5) {'lead'} else {'author'})}
    }
    foreach ($target in $mcpTargets) {
        $path = Join-Path $target.Root 'opencode.jsonc'
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing MCP config target: $path" }
        Assert-NoReparsePath ([System.IO.Path]::GetFullPath($target.Root)) ([System.IO.Path]::GetFullPath($path))
        $old = [System.IO.File]::ReadAllBytes($path)
        $new = Get-WorkflowMcpConfig $old $sharedRoot ([System.IO.Path]::GetFullPath($target.Root)) $target.Profile
        $oldHash = Get-Sha256 $old; $newHash = Get-Sha256 $new
        if ($oldHash -ne $newHash -and -not ($target.Name -eq 'main' -and $oldHash -eq $sourceHashes['opencode.jsonc'])) {
            $receiptVerified = $false
            try { Assert-ReceiptMatch $previous $target.Name 'opencode.jsonc' $oldHash; $receiptVerified = $true } catch { }
            if (-not $receiptVerified -and -not (Test-McpOnlySemanticChange $old $new ([System.IO.Path]::GetFullPath($target.Root)) $sharedRoot $target.Profile)) {
                throw "Unreceipted config contains changes outside the managed MCP merge; refusing to alter it: $path"
            }
        }
        $mcpPlans += [pscustomobject]@{Target=$target.Name;Path=$path;Relative='opencode.jsonc';OldHash=$oldHash;NewHash=$newHash;Bytes=$new;Action=$(if ($oldHash -eq $newHash) {'up-to-date'} else {'replace'})}
    }
    $leadTarget = @($mcpTargets | Where-Object Name -eq 'MoonStation14-agent-05')[0]
    $leadRelative = '.opencode/agents/integration-lead.md'
    $leadPath = Join-Path $leadTarget.Root $leadRelative
    if (-not (Test-Path -LiteralPath $leadPath -PathType Leaf)) { throw "Missing canonical lead profile: $leadPath" }
    Assert-NoReparsePath ([System.IO.Path]::GetFullPath($leadTarget.Root)) ([System.IO.Path]::GetFullPath($leadPath))
    $leadOld = [System.IO.File]::ReadAllBytes($leadPath)
    $leadNew = Get-McpLeadProfile $leadOld
    $leadOldHash = Get-Sha256 $leadOld; $leadNewHash = Get-Sha256 $leadNew
    if ($leadOldHash -ne $leadNewHash -and $leadOldHash -ne $sourceHashes['.opencode/agents/integration-lead.md']) {
        $leadReceiptVerified = $false
        try { Assert-ReceiptMatch $previous $leadTarget.Name $leadRelative $leadOldHash; $leadReceiptVerified = $true } catch { }
        if (-not $leadReceiptVerified -and -not (Test-McpLeadProfileSemanticChange $leadOld $leadNew)) { throw "Unreceipted lead profile contains changes outside the managed MCP permission migration; refusing to alter it: $leadPath" }
    }
    $mcpPlans += [pscustomobject]@{Target=$leadTarget.Name;Path=$leadPath;Relative=$leadRelative;OldHash=$leadOldHash;NewHash=$leadNewHash;Bytes=$leadNew;Action=$(if ($leadOldHash -eq $leadNewHash) {'up-to-date'} else {'replace'})}
    foreach ($plan in $mcpPlans) { '{0,-24} {1,-12} {2} old:{3} new:{4}' -f $plan.Target,$plan.Action,$plan.Relative,$plan.OldHash,$plan.NewHash }
    $mcpChanges = @($mcpPlans | Where-Object Action -ne 'up-to-date')
    $ownershipRepairs = @()
    foreach ($plan in $mcpPlans) {
        $rows = @($previous.files | Where-Object { $_.target -eq $plan.Target -and $_.path -eq $plan.Relative })
        if ($plan.OldHash -ne $plan.NewHash -or $rows.Count -ne 1 -or $rows[0].deleted -or $rows[0].new_sha256 -cne $plan.OldHash) { $ownershipRepairs += $plan }
    }
    "Workflow MCP only; changed managed files: $($mcpChanges.Count); ownership rows to reconcile: $($ownershipRepairs.Count); profile: author on MAIN/01-04, lead on 05."
    if (-not $Apply) { 'Dry run only; no configs or role profiles were written.'; return }
    if (-not $mcpChanges.Count -and -not $ownershipRepairs.Count) { 'Workflow MCP bindings and receipt ownership already current; no backups or writes performed.'; return }
    Assert-McpPlanUnchanged $mcpPlans
    $backupRoot = Join-Path $env:LOCALAPPDATA ('Temp\opencode\workflow-publish-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
    $managedPaths = @($mcpPlans | ForEach-Object { "$($_.Target)|$($_.Relative)" })
    $files = @($previous.files | Where-Object { "$($_.target)|$($_.path)" -notin $managedPaths })
    $files += @($mcpPlans | ForEach-Object {@{target=$_.Target;path=$_.Relative;old_sha256=$_.OldHash;new_sha256=$_.NewHash;deleted=$false}})
    $receipt = @{created_utc=[DateTime]::UtcNow.ToString('o');main_root=$mainRoot;shared_root=$sharedRoot;source_hashes=$previous.source_hashes;files=$files}
    [System.IO.File]::WriteAllText((Join-Path $backupRoot 'publish-receipt.json'),($receipt | ConvertTo-Json -Depth 30),[System.Text.UTF8Encoding]::new($false))
    foreach ($plan in $mcpChanges) {
        $backup = Join-Path $backupRoot (Join-Path $plan.Target $plan.Relative)
        New-Item -ItemType Directory -Path (Split-Path -Parent $backup) -Force | Out-Null
        [System.IO.File]::WriteAllBytes($backup,[System.IO.File]::ReadAllBytes($plan.Path))
    }
    foreach ($plan in $mcpChanges) { [System.IO.File]::WriteAllBytes($plan.Path,$plan.Bytes) }
    if (-not $mcpChanges.Count) { "Reconciled $($ownershipRepairs.Count) MCP receipt ownership rows; 0 configuration files written. Receipt: $backupRoot" }
    else { "Applied $($mcpChanges.Count) scoped workflow MCP files; reconciled $($ownershipRepairs.Count) ownership rows. Backup and receipt: $backupRoot" }
    return
}
if ($StageNeutral05) {
    $stagePath = Write-NeutralCoordinatorStage $mainRoot $sharedRoot $sourceBytes['tooling/agent_workflow/workflow_entry.py'] $sourceBytes['opencode.jsonc'] $sourceBytes['.opencode/agents/integration-lead.md'] $sourceBytes['.opencode/agents/fidelity-reviewer.md']
    "Neutral coordinator bindings staged (not installed): $stagePath"
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
    $destination = if ($script:SourceMap.ContainsKey($relative)) { $script:SourceMap[$relative] } else { $relative }
    $plans.Add([pscustomobject]@{ Target = 'shared'; Path = (Join-Path $sharedRoot ($destination.Replace('/', [System.IO.Path]::DirectorySeparatorChar))); Relative = $destination; Action = 'publish'; Bytes = $sourceBytes[$relative]; Delete = $false })
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
        $newConfig = Get-TailoredProjectConfig $oldConfig $sharedRoot $workspaceParent $target.Number $policyConfig
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
if ($previous) {
    $managedPlanKeys = @($plans | ForEach-Object { "$($_.Target)|$($_.Relative)" })
    $receiptFiles += @($previous.files | Where-Object { "$($_.target)|$($_.path)" -notin $managedPlanKeys })
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
