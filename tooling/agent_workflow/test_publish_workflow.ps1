$ErrorActionPreference = 'Stop'

$publisher = Join-Path $PSScriptRoot 'publish_workflow.ps1'
$publisherText = [System.IO.File]::ReadAllText($publisher)
$tokens = $null; $parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($publisher, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | ForEach-Object ToString | Out-String) }
$needed = @('Get-Sha256','Get-FileHashValue','Get-TextData','ConvertTo-TextBytes','ConvertTo-CanonicalConfigJson','Get-CanonicalConfigHash','Add-Rule','Get-WorkflowSection','Set-WorkflowSection','Assert-NoReparsePath','Get-ReceiptHash','Assert-ReceiptMatch','Get-TailoredProjectConfig','Get-TailoredIntegrationProfile','Get-TailoredFidelityProfile','Get-NeutralCoordinatorEntrypoint','Get-NeutralCoordinatorConfig')
$functions = $ast.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $needed -contains $node.Name }, $true)
if ($functions.Count -ne $needed.Count) { throw 'Publisher fixture could not find every tested helper.' }
. ([scriptblock]::Create(($functions | ForEach-Object { $_.Extent.Text }) -join "`n"))

function Assert-Equal([string] $Expected, [string] $Actual, [string] $Message) {
    if ($Expected -cne $Actual) { throw $Message }
}

$canonical = @'
# Source root
## Prototype and readiness
SOURCE-PROTO
SOURCE-READY

## Shared worker and review workflow
SHARED-POLICY

## Later source section
SHARED-LATER
'@ -replace "`n", "`r`n"
$target = @'
# Private assigned root
## Prototype and readiness
TARGET-PROTO-UNIQUE
TARGET-READY-UNIQUE

## Shared worker and review workflow
OLD-POLICY

## Later source section
TARGET-LATER-UNIQUE
'@ -replace "`n", "`r`n"
$section = Get-WorkflowSection $canonical
$updated = Set-WorkflowSection $target $section
$expected = $target.Replace("## Shared worker and review workflow`r`nOLD-POLICY`r`n`r`n## Later source section", "## Shared worker and review workflow`r`nSHARED-POLICY`r`n`r`n## Later source section")
Assert-Equal $expected $updated 'Shared AGENTS patch overwrote non-workflow instructions.'
Assert-Equal $updated (Set-WorkflowSection $updated $section) 'Shared AGENTS patch is not idempotent.'

$mainRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$workspaceParent = Split-Path -Parent $mainRoot
$shared = 'C:/fixtures/MoonStation14-shared'
$baseConfig = (Get-Content (Join-Path $mainRoot 'opencode.jsonc') -Raw | ConvertFrom-Json -AsHashtable)
$baseConfig.model = [ordered]@{ providerID = 'fixture-provider'; model = 'unchanged-model' }
$baseConfig.agents['implement'].request = [ordered]@{ body = [ordered]@{ fixtureDefault = 'preserve' } }
$baseBytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $baseConfig))
$tailored = Get-TailoredProjectConfig $baseBytes $shared $workspaceParent 1
$tailoredAgain = Get-TailoredProjectConfig $tailored $shared $workspaceParent 1
if ((Get-CanonicalConfigHash $tailored) -cne (Get-CanonicalConfigHash $tailoredAgain)) { throw 'Shared grants are not idempotent.' }
$resolved = (Get-TextData $tailored).Text | ConvertFrom-Json -AsHashtable
if ($resolved.model.model -cne 'unchanged-model' -or $resolved.agents['implement'].request.body.fixtureDefault -cne 'preserve') {
    throw 'Binding changed an unrelated model/default field.'
}
$sharedRule = $shared + '/**'
foreach ($role in @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')) {
    $read = @($resolved.agents[$role].permissions | Where-Object { $_.action -eq 'external_directory' -and $_.resource -eq $sharedRule -and $_.effect -eq 'allow' })
    $edit = @($resolved.agents[$role].permissions | Where-Object { $_.action -eq 'edit' -and $_.resource -eq $sharedRule -and $_.effect -eq 'deny' })
    if ($read.Count -ne 1 -or $edit.Count -ne 1) { throw "Shared-folder read-only permission missing for role $role." }
}
$centralPath = (Join-Path $workspaceParent 'MoonStation14-agent-05').Replace('\', '/') + '/**'
$centralRead = @($resolved.agents['orchestrate-ms14'].permissions | Where-Object {
    $_.action -eq 'external_directory' -and $_.resource -eq $centralPath -and $_.effect -eq 'allow'
})
if ($centralRead.Count -ne 1) { throw 'Primary author config lacks the exact central-05 grant.' }
$submitRules = @(
    [ordered]@{ action = 'shell'; resource = 'python tooling/agent_workflow/workflow.py submit *'; effect = 'allow' },
    [ordered]@{ action = 'shell'; resource = 'python tooling/agent_workflow/workflow.py --repo * submit *'; effect = 'allow' }
)
foreach ($role in @('orchestrate-ms14','3d-modeler')) {
    $permissions = @($resolved.agents[$role].permissions)
    $shellPermissions = @($permissions | Where-Object { $_.action -eq 'shell' })
    foreach ($rule in $submitRules) {
        $matches = @($shellPermissions | Where-Object { $_.action -eq $rule.action -and $_.resource -ceq $rule.resource -and $_.effect -eq $rule.effect })
        if ($matches.Count -ne 1 -or $shellPermissions[-1].resource -cne $submitRules[-1].resource) {
            throw "Canonical author profile lacks the unique final submit permission for $role."
        }
    }
}
foreach ($role in @('implement','investigate','ss14-expert','review','integration-lead','fidelity-reviewer')) {
    if (@($resolved.agents[$role].permissions | Where-Object { $_.action -eq 'shell' -and $_.effect -eq 'allow' -and $_.resource -like '*workflow.py*submit*' }).Count) {
        throw "Submit permission leaked to non-primary role $role."
    }
}
# Model OpenCode's cooperative wildcard rules: every matching shell rule is considered
# in order and the final matching rule wins. Include the reproduction metadata that
# previously tripped broad gradlew/test denies.
$permissionCases = @(
    @{ command = 'python tooling/agent_workflow/workflow.py --repo . submit --sprint sprint-fixture --summary "Fix permission" --check "gradlew.bat runGameTestServer -Pms14GameTestTags=fixture" --check "JVM tests"'; expected = 'allow' },
    @{ command = 'python tooling/agent_workflow/workflow.py submit --sprint sprint-fixture --summary "mentions gradlew.bat build and tests"'; expected = 'allow' },
    @{ command = 'gradlew.bat test'; expected = 'deny' },
    @{ command = '.\\gradlew.bat build'; expected = 'deny' },
    @{ command = 'python tooling/agent_workflow/workflow.py gate-validate --summary "submit gradlew.bat test"'; expected = 'deny' },
    @{ command = 'python tooling/agent_workflow/workflow.py --repo . gate-clear-blocker --summary submit'; expected = 'deny' },
    @{ command = 'python tooling/agent_workflow/workflow.py status --summary "submit gradlew.bat test"'; expected = 'deny' },
    @{ command = 'python other.py submit --summary "workflow.py gradlew.bat test"'; expected = 'deny' },
    @{ command = 'python tooling/agent_workflow/workflow.py submit --check "runServer"'; expected = 'allow' }
)
$authorPermissions = @($resolved.agents['orchestrate-ms14'].permissions | Where-Object { $_.action -eq 'shell' })
foreach ($case in $permissionCases) {
    $effect = 'deny'
    foreach ($rule in $authorPermissions) {
        if ($case.command -like $rule.resource) { $effect = $rule.effect }
    }
    if ($effect -cne $case.expected) { throw "Submit shell permission fixture expected $($case.expected), got $effect for: $($case.command)" }
}
foreach ($role in @('implement','investigate','ss14-expert','review','3d-modeler','fidelity-reviewer')) {
    if (@($resolved.agents[$role].permissions | Where-Object { $_.action -eq 'external_directory' -and $_.resource -match 'MoonStation14-agent-0[1-4]' }).Count) {
        throw "Shared binding granted peer trees to $role."
    }
}
$fidelityCodeGrants = @($resolved.agents['fidelity-reviewer'].permissions | Where-Object {
    $_.action -eq 'external_directory' -and $_.resource -match '/MoonStation14(?:-agent-[^/]+)?/\*\*$'
})
if ($fidelityCodeGrants.Count) { throw 'Fidelity reviewer received an explicit peer-code worktree grant.' }
$workspaceParent = Split-Path -Parent $mainRoot
$integrationSource = [System.IO.File]::ReadAllBytes((Join-Path $mainRoot '.opencode/agents/integration-lead.md'))
$integrationText = (Get-TextData $integrationSource).Text
foreach ($command in @('gate-finish','gate-deliver')) {
    if ($integrationText -notmatch ('(?m)^    "\*workflow\.py\*' + [regex]::Escape($command) + '\*": allow$') -or
        $integrationText -match ('(?m)^     "\*workflow\.py\*' + [regex]::Escape($command) + '\*": allow$')) {
        throw "Integration profile command '$command' must use exactly four bash-map indentation spaces."
    }
}
$authorProfile = (Get-TextData (Get-TailoredIntegrationProfile $integrationSource $workspaceParent 1)).Text
$centralProfile = (Get-TextData (Get-TailoredIntegrationProfile $integrationSource $workspaceParent 5)).Text
if ($authorProfile -match 'MoonStation14-agent-0[1-4]' -or $authorProfile -match 'Documents/Minecraft Mod Dev Files/MoonStation14/\*\*') {
    throw 'Noncentral integration profile has a peer-worktree grant.'
}
foreach ($number in 1..4) {
    if ($centralProfile -notmatch ('MoonStation14-agent-{0:D2}/\*\*' -f $number)) { throw "Central profile is missing assigned tree {0:D2}." -f $number }
}
if ($centralProfile -notmatch '(?m)^\s+"\*workflow\.py\*gate-report\*": allow$' -or
    $centralProfile -notmatch '(?m)^\s+"\*workflow\.py\*gate-deliver\*": allow$' -or
    $centralProfile -match '(?m)^\s+"\*workflow\.py\*sprint-cleanup\*": allow$') {
    throw 'Central integration profile must allow gate-report without broadening cleanup or shell access.'
}
$mainReportsProfile = (Join-Path $workspaceParent 'MoonStation14/build/agent-workflow').Replace('\', '/') + '/**'
if ($centralProfile -notmatch [regex]::Escape('"' + $mainReportsProfile + '": allow') -or
    $centralProfile -match 'Documents/Minecraft Mod Dev Files/MoonStation14/\*\*') {
    throw 'Central integration profile must grant only the MAIN managed report subtree, not the MAIN worktree.'
}
$entrypointBytes = [System.IO.File]::ReadAllBytes((Join-Path $mainRoot 'tooling/agent_workflow/workflow_entry.py'))
$coordinatorShim = Get-NeutralCoordinatorEntrypoint $entrypointBytes
$authorShim = $entrypointBytes
if ((Get-Sha256 $authorShim) -ne (Get-Sha256 ([System.IO.File]::ReadAllBytes((Join-Path $mainRoot 'tooling/agent_workflow/workflow_entry.py'))))) { throw 'Coordinator binding altered author entrypoint template.' }
$shimText = (Get-TextData $coordinatorShim).Text
if ($shimText -notmatch 'arg == "--asset-workspace"' -or $shimText -notmatch 'Asset Building Workspace' -or $shimText -notmatch 'arg.startswith\("--asset-workspace="\)') { throw 'Coordinator shim does not default queue context while preserving explicit overrides.' }
$minimalBytes = Get-NeutralCoordinatorConfig $baseBytes $integrationSource $fidelitySource $shared $workspaceParent
$minimalConfig = (Get-TextData $minimalBytes).Text | ConvertFrom-Json -AsHashtable
if (@($minimalConfig.agents.Keys | Sort-Object) -join ',' -cne 'fidelity-reviewer,integration-lead' -or $minimalConfig.model.model -cne 'unchanged-model') { throw 'Neutral config did not preserve global config while restricting entries to the two coordinator roles.' }
$neutralPermissions = @($minimalConfig.agents['integration-lead'].permissions)
if (@($neutralPermissions | Where-Object { $_.action -eq 'external_directory' -and $_.resource -eq $sharedRule -and $_.effect -eq 'edit' }).Count -gt 0) { throw 'Neutral integration lead received shared-source edit permission.' }
if (@($neutralPermissions | Where-Object { $_.action -eq 'shell' -and $_.resource -eq '*workflow.py*gate-report*' -and $_.effect -eq 'allow' }).Count -ne 1 -or
    @($neutralPermissions | Where-Object { $_.action -eq 'shell' -and $_.effect -eq 'allow' -and $_.resource -eq '*' }).Count -gt 0 -or
    @($neutralPermissions | Where-Object { $_.action -eq 'shell' -and $_.resource -match 'sprint-cleanup' -and $_.effect -eq 'allow' }).Count -gt 0) {
    throw 'Neutral integration CLI permissions must allow only the specific gate-report command, not arbitrary shell or cleanup.'
}
$fidelitySource = [System.IO.File]::ReadAllBytes((Join-Path $mainRoot '.opencode/agents/fidelity-reviewer.md'))
$fidelityProfile = (Get-TextData (Get-TailoredFidelityProfile $fidelitySource)).Text
if ($fidelityProfile -match 'Documents/Minecraft Mod Dev Files/MoonStation14/\*\*' -or $fidelityProfile -match 'MoonStation14-agent-') {
    throw 'Fidelity reviewer has an explicit code-peer grant.'
}
$centralConfig = Get-TailoredProjectConfig $baseBytes $shared $workspaceParent 5
$centralObject = (Get-TextData $centralConfig).Text | ConvertFrom-Json -AsHashtable
$expectedPeers = 1..4 | ForEach-Object { (Join-Path $workspaceParent ('MoonStation14-agent-{0:D2}' -f $_)).Replace('\', '/') + '/**' }
$integrationPeers = @($centralObject.agents['integration-lead'].permissions | Where-Object {
    $_.action -eq 'external_directory' -and $_.effect -eq 'allow' -and $_.resource -in $expectedPeers
})
if ($integrationPeers.Count -ne 4) { throw 'Central config lacks exactly the four peer-reader grants.' }
$mainReportsConfig = (Join-Path $workspaceParent 'MoonStation14/build/agent-workflow').Replace('\', '/') + '/**'
$leadExternalAllows = @($centralObject.agents['integration-lead'].permissions | Where-Object { $_.action -eq 'external_directory' -and $_.effect -eq 'allow' })
if (@($leadExternalAllows | Where-Object { $_.resource -eq $mainReportsConfig }).Count -ne 1 -or
    @($leadExternalAllows | Where-Object { $_.resource -eq ((Join-Path $workspaceParent 'MoonStation14').Replace('\', '/') + '/**') }).Count -gt 0) {
    throw 'Central config must allow MAIN/build/agent-workflow only, never MAIN root.'
}
foreach ($role in @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','fidelity-reviewer')) {
    if (@($centralObject.agents[$role].permissions | Where-Object {
        $_.action -eq 'external_directory' -and $_.resource -in $expectedPeers
    }).Count) { throw "Central config grants peer trees to non-gate role '$role'." }
}

$receipt = [pscustomobject]@{ files = @([pscustomobject]@{ target = 'agent-01'; path = 'owned.txt'; new_sha256 = 'known-hash' }) }
Assert-ReceiptMatch $receipt 'agent-01' 'owned.txt' 'known-hash'
$sourceFallbackReceipt = [pscustomobject]@{ files = @(); source_hashes = @{ 'unchanged.md' = 'source-known' } }
Assert-ReceiptMatch $sourceFallbackReceipt 'agent-01' 'unchanged.md' 'source-known'
$unknownWasRejected = $false
try { Assert-ReceiptMatch $receipt 'agent-01' 'owned.txt' 'edited-hash' } catch { $unknownWasRejected = $true }
if (-not $unknownWasRejected) { throw 'Publisher did not reject an unreceipted local file edit.' }
$deletedWasRejected = $false
$deletedReceipt = [pscustomobject]@{ files = @([pscustomobject]@{ target = 'agent-01'; path = 'unchanged.md'; new_sha256 = $null; deleted = $true }); source_hashes = @{ 'unchanged.md' = 'source-known' } }
try { Assert-ReceiptMatch $deletedReceipt 'agent-01' 'unchanged.md' 'source-known' } catch { $deletedWasRejected = $true }
if (-not $deletedWasRejected) { throw 'Deleted receipt entry incorrectly fell back to a source hash.' }

# Exercise the complete dry-run planner on isolated fixture roots; never touch the five real worktrees.
$fixtureRoot = Join-Path $env:LOCALAPPDATA ('Temp\opencode\publisher-plan-fixture-' + [guid]::NewGuid().ToString('N'))
$fixtureMain = Join-Path $fixtureRoot 'MoonStation14'
$sharedSources = @(
    'tooling/agent_workflow/gate_liveness.py','tooling/agent_workflow/agent_status.py','tooling/agent_workflow/workflow_observer.py',
    'tooling/agent_workflow/owner_attention.py','tooling/agent_workflow/owner_feedback.py','tooling/agent_workflow/gate_progress.py','tooling/agent_workflow/validation_evidence.py','tooling/agent_workflow/admission.py','tooling/agent_workflow/gate_dispatch.py','tooling/agent_workflow/workflow_runtime.py',
    'docs/development-workflow/README.runtime.md','docs/development-workflow/CLI.md','docs/development-workflow/coordinator.md',
    'docs/development-workflow/source-fidelity.md','docs/development-workflow/request-template.json',
    'docs/development-workflow/review-report.md','docs/development-workflow/HANDOFF.md',
    'docs/development-workflow/roles/integration-lead.md','docs/development-workflow/roles/fidelity-reviewer.md',
    '.opencode/agents/integration-lead.md','.opencode/agents/fidelity-reviewer.md',
    'tooling/agent_workflow/workflow_entry.py','AGENTS.md','opencode.jsonc'
)
if ($sharedSources.IndexOf('tooling/agent_workflow/gate_liveness.py') -gt $sharedSources.IndexOf('tooling/agent_workflow/gate_progress.py') -or
    $sharedSources.IndexOf('tooling/agent_workflow/agent_status.py') -gt $sharedSources.IndexOf('tooling/agent_workflow/workflow_observer.py') -or
    $sharedSources.IndexOf('tooling/agent_workflow/agent_status.py') -gt $sharedSources.IndexOf('tooling/agent_workflow/gate_progress.py') -or
    $sharedSources.IndexOf('tooling/agent_workflow/workflow_observer.py') -gt $sharedSources.IndexOf('tooling/agent_workflow/workflow_runtime.py')) {
    throw 'Shared operational dependencies must precede their consumers in the publication payload.'
}
$deleteOnBind = @(
    'tooling/agent_workflow/validation_evidence.py','tooling/agent_workflow/gate_dispatch.py',
    'tooling/agent_workflow/test_workflow.py','tooling/agent_workflow/test_validation_evidence.py',
    'tooling/agent_workflow/test_gate_dispatch.py','docs/development-workflow/CLI.md',
    'docs/development-workflow/coordinator.md','docs/development-workflow/source-fidelity.md',
    'docs/development-workflow/request-template.json','docs/development-workflow/review-report.md',
    'docs/development-workflow/HANDOFF.md'
)
try {
    $preparedRuntime = [System.IO.File]::ReadAllBytes((Join-Path $mainRoot 'tooling/agent_workflow/workflow_runtime.py'))
    $liveShim = [System.IO.File]::ReadAllBytes((Join-Path $mainRoot 'tooling/agent_workflow/workflow.py'))
    $preparedReadme = [System.IO.File]::ReadAllBytes((Join-Path $mainRoot 'docs/development-workflow/README.runtime.md'))
    $pointerReadme = [System.IO.File]::ReadAllBytes((Join-Path $mainRoot 'docs/development-workflow/README.md'))
    if ((Get-Sha256 $preparedRuntime) -eq (Get-Sha256 $liveShim) -or
        (Get-Sha256 $preparedReadme) -eq (Get-Sha256 $pointerReadme)) {
        throw 'Fixture source payloads must distinguish prepared runtime/readme from live shim/pointer.'
    }
    if ($publisherText -notmatch "'tooling/agent_workflow/workflow_runtime.py'\s*=\s*'tooling/agent_workflow/workflow.py'" -or
        $publisherText -notmatch "'docs/development-workflow/README.runtime.md'\s*=\s*'docs/development-workflow/README.md'") {
        throw 'Publisher source mapping does not route prepared payloads to shared runtime destinations.'
    }
    if ($publisherText -notmatch 'Relative = \$destination; Action = ''publish''; Bytes = \$sourceBytes\[\$relative\]') {
        throw 'Publisher plan no longer carries the mapped source payload bytes into the destination plan.'
    }
    $sourceHashes = @{}
    foreach ($relative in $sharedSources) {
        $source = Join-Path $mainRoot ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        $destination = Join-Path $fixtureMain ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        $bytes = [System.IO.File]::ReadAllBytes($source)
        [System.IO.File]::WriteAllBytes($destination, $bytes)
        $sourceHashes[$relative] = Get-Sha256 $bytes
    }
    foreach ($relative in @('tooling/agent_workflow/test_workflow.py','tooling/agent_workflow/test_validation_evidence.py','tooling/agent_workflow/test_gate_dispatch.py')) {
        $source = Join-Path $mainRoot ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        $destination = Join-Path $fixtureMain ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        [System.IO.File]::WriteAllBytes($destination, [System.IO.File]::ReadAllBytes($source))
        $sourceHashes[$relative] = Get-Sha256 ([System.IO.File]::ReadAllBytes($source))
    }
    foreach ($relative in @('tooling/agent_workflow/workflow.py','docs/development-workflow/README.md')) {
        $source = Join-Path $mainRoot ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        $destination = Join-Path $fixtureMain ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        [System.IO.File]::WriteAllBytes($destination, [System.IO.File]::ReadAllBytes($source))
    }
    $receiptRows = @()
    foreach ($number in 1..5) {
        $name = 'MoonStation14-agent-{0:D2}' -f $number
        $root = Join-Path $fixtureRoot $name
        $locals = @('tooling/agent_workflow/workflow.py','.opencode/agents/integration-lead.md','.opencode/agents/fidelity-reviewer.md','AGENTS.md','docs/development-workflow/README.md','opencode.jsonc') + $deleteOnBind
        foreach ($relative in $locals | Select-Object -Unique) {
            $destination = Join-Path $root ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
            New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
            if ($relative -eq 'AGENTS.md') {
                $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes("# Target $name`r`n## Local readiness`r`nPRESERVE-$name`r`n`r`n## Shared worker and review workflow`r`nOLD-POLICY`r`n`r`n## Later`r`nPRESERVE-LATER-$name`r`n")
            }
            else {
                $bytes = [System.IO.File]::ReadAllBytes((Join-Path $fixtureMain ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))))
            }
            [System.IO.File]::WriteAllBytes($destination, $bytes)
            if (-not (($number -eq 1) -and $relative -in @('docs/development-workflow/source-fidelity.md','docs/development-workflow/request-template.json','docs/development-workflow/review-report.md'))) {
                $receiptRows += @{ target = $name; path = $relative; new_sha256 = Get-Sha256 $bytes }
            }
        }
    }
    $receiptPath = Join-Path $fixtureRoot 'previous-receipt.json'
    [System.IO.File]::WriteAllText($receiptPath, (@{ source_hashes = $sourceHashes; files = $receiptRows } | ConvertTo-Json -Depth 20), [System.Text.UTF8Encoding]::new($false))
    $oldShimHash = Get-FileHashValue (Join-Path $fixtureRoot 'MoonStation14-agent-01/tooling/agent_workflow/workflow.py')
    $planOutput = & $publisher -BindWorktrees -PreviousReceipt $receiptPath -TestFixtureRoot $fixtureRoot | Out-String
    if ($planOutput -notmatch 'Dry run only' -or $planOutput -notmatch 'agent-01\s+remove\s+docs/development-workflow/source-fidelity.md') {
        throw 'Complete fixture dry run did not plan receipt-source fallback cleanup.'
    }
    if ($planOutput -notmatch 'shared\s+create\s+tooling/agent_workflow/workflow.py' -or
        $planOutput -notmatch 'shared\s+create\s+docs/development-workflow/README.md') {
        throw 'Prepared runtime/readme sources were not mapped to their shared destinations.'
    }
    $progressAt = $planOutput.IndexOf('tooling/agent_workflow/gate_progress.py')
    $agentStatusAt = $planOutput.IndexOf('tooling/agent_workflow/agent_status.py')
    $observerAt = $planOutput.IndexOf('tooling/agent_workflow/workflow_observer.py')
    $attentionAt = $planOutput.IndexOf('tooling/agent_workflow/owner_attention.py')
    $feedbackAt = $planOutput.IndexOf('tooling/agent_workflow/owner_feedback.py')
    $dispatchAt = $planOutput.IndexOf('tooling/agent_workflow/gate_dispatch.py')
    $runtimeAt = $planOutput.IndexOf('tooling/agent_workflow/workflow.py')
    if ($attentionAt -lt 0 -or $feedbackAt -lt 0 -or $agentStatusAt -lt 0 -or $observerAt -lt 0 -or $progressAt -lt 0 -or $dispatchAt -lt 0 -or $runtimeAt -lt 0 -or
        $agentStatusAt -ge $observerAt -or $agentStatusAt -ge $progressAt -or
        $attentionAt -ge $progressAt -or $feedbackAt -ge $progressAt -or $progressAt -ge $runtimeAt -or $dispatchAt -ge $runtimeAt) {
        throw 'Shared dependency/dispatcher payloads must be planned before the mapped workflow runtime.'
    }
    if ((Test-Path -LiteralPath (Join-Path $fixtureRoot 'MoonStation14-shared')) -or
        (Get-FileHashValue (Join-Path $fixtureRoot 'MoonStation14-agent-01/tooling/agent_workflow/workflow.py')) -ne $oldShimHash -or
        -not (Test-Path -LiteralPath (Join-Path $fixtureRoot 'MoonStation14-agent-01/docs/development-workflow/source-fidelity.md'))) {
        throw 'Dry-run fixture changed a shared or local target.'
    }
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
}

$python = Get-Command python -ErrorAction Stop
$tempRoot = Join-Path $env:LOCALAPPDATA ('Temp\opencode\workflow-shim-fixture-' + [guid]::NewGuid().ToString('N'))
$fixtureRoot = Join-Path $tempRoot 'MoonStation14-agent-01'
$runtimeRoot = Join-Path $tempRoot 'MoonStation14-shared'
try {
    $shimPath = Join-Path $fixtureRoot 'tooling/agent_workflow/workflow.py'
    $runtimePath = Join-Path $runtimeRoot 'tooling/agent_workflow/workflow.py'
    New-Item -ItemType Directory -Path (Split-Path -Parent $shimPath) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path -Parent $runtimePath) -Force | Out-Null
    [System.IO.File]::WriteAllBytes($shimPath, [System.IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'workflow_entry.py')))
    $fakeRuntime = @'
import json, os, sys
def main():
    print(json.dumps({"cwd": os.getcwd(), "argv": sys.argv[1:]}))
    return 0
'@
    [System.IO.File]::WriteAllText($runtimePath, $fakeRuntime, [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path (Split-Path -Parent $runtimePath) 'validation_evidence.py'), '# shared fixture module', [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path (Split-Path -Parent $runtimePath) 'gate_dispatch.py'), '# shared fixture module', [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path (Split-Path -Parent $runtimePath) 'owner_attention.py'), '# shared fixture module', [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path (Split-Path -Parent $runtimePath) 'owner_feedback.py'), '# shared fixture module', [System.Text.UTF8Encoding]::new($false))
    Push-Location $fixtureRoot
    try { $run = & $python.Source $shimPath --repo kept-relative caller-arg }
    finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw "Fixture shim returned $LASTEXITCODE" }
    $result = ($run | Out-String | ConvertFrom-Json)
    Assert-Equal ([System.IO.Path]::GetFullPath($fixtureRoot)) $result.cwd 'Shim changed caller working directory.'
    if (($result.argv -join ' ') -cne '--repo kept-relative caller-arg') { throw 'Shim changed CLI arguments.' }
    $centralRoot = Join-Path $tempRoot 'MoonStation14-agent-05'
    $centralShim = Join-Path $centralRoot 'tooling/agent_workflow/workflow.py'
    New-Item -ItemType Directory -Path (Split-Path -Parent $centralShim) -Force | Out-Null
    [System.IO.File]::WriteAllBytes($centralShim, $coordinatorShim)
    Push-Location $centralRoot
    try {
        $defaultRun = & $python.Source $centralShim status
        if ($LASTEXITCODE -ne 0) { throw 'Coordinator shim default-queue fixture failed.' }
        $explicitRun = & $python.Source $centralShim --asset-workspace custom-queue status
        if ($LASTEXITCODE -ne 0) { throw 'Coordinator shim explicit-queue fixture failed.' }
    }
    finally { Pop-Location }
    $defaultArgs = (($defaultRun | Out-String | ConvertFrom-Json).argv -join ' ')
    $explicitArgs = (($explicitRun | Out-String | ConvertFrom-Json).argv -join ' ')
    if ($defaultArgs -notmatch '--asset-workspace .*Asset Building Workspace status' -or $explicitArgs -cne '--asset-workspace custom-queue status') {
        throw 'Coordinator shim failed to default the existing asset workspace or preserve an explicit override.'
    }
}
finally {
    if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}

'Shared publisher fixtures passed: one shared runtime, wrapper context preservation, AGENTS prefix/suffix, scoped grants, and receipt conflict handling.'
