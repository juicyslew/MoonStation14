$ErrorActionPreference = 'Stop'

$publisher = Join-Path $PSScriptRoot 'publish_workflow.ps1'
$publisherText = [System.IO.File]::ReadAllText($publisher)
$tokens = $null; $parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($publisher, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | ForEach-Object ToString | Out-String) }
$needed = @('Get-Sha256','Get-FileHashValue','Get-TextData','ConvertTo-TextBytes','ConvertTo-CanonicalConfigJson','Get-CanonicalConfigHash','Add-Rule','Assert-GradleShellPolicy','Get-ShellPolicyConfig','Get-ShellPolicyFidelityHeader','Get-WorkflowSection','Set-WorkflowSection','Assert-NoReparsePath','Get-ReceiptHash','Assert-ReceiptMatch','Get-TailoredProjectConfig','Get-TailoredIntegrationProfile','Get-TailoredFidelityProfile','Get-NeutralCoordinatorEntrypoint','Get-NeutralCoordinatorConfig','Get-WorkflowMcpEntry','Get-NormalizedWorkflowMcpJson','Test-KnownRetiredWorkflowCliRule','Get-ExpectedMcpPermissionTuples','Test-ExpectedMcpPermissionTuple','Get-WorkflowMcpConfig','Test-McpOnlySemanticChange','Get-McpUnownedConfigView','Assert-McpPlanUnchanged','Get-McpLeadProfile','Get-McpLeadProfileUnownedText','Test-McpLeadProfileSemanticChange')
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
# Retain the pre-MCP canonical shell grants in this fixture seed so normal binding
# migration and its CLI policy assertions remain covered after MAIN switched to MCP.
foreach ($role in @('orchestrate-ms14','3d-modeler')) {
    foreach ($resource in @('python tooling/agent_workflow/workflow.py submit *','python tooling/agent_workflow/workflow.py --repo * submit *')) {
        if (-not @($baseConfig.agents[$role].permissions | Where-Object { $_.action -eq 'shell' -and $_.resource -ceq $resource -and $_.effect -eq 'allow' }).Count) {
            $baseConfig.agents[$role].permissions += [ordered]@{action='shell';resource=$resource;effect='allow'}
        }
    }
}
Assert-GradleShellPolicy $baseConfig
$mcpSeed = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $baseConfig -Depth 100)
$mcpSeed.mcp.servers.Remove('ms14_workflow')
$mcpSeed.mcp.timeout = [ordered]@{ startup=9000; catalog=10000; execution=12000 }
$mcpSeed.mcp.servers['existing-user-server'] = [ordered]@{ type='local'; command=@('keep-me') }
$mcpSeed.agents['orchestrate-ms14'].permissions += @([ordered]@{action='shell';resource='*';effect='allow'})
$mcpBytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $mcpSeed))
$mcpShared = 'C:/fixture/MoonStation14-shared'; $mcpRepo = 'C:/fixture/MoonStation14-agent-01'
$mcpAuthor = Get-WorkflowMcpConfig $mcpBytes $mcpShared $mcpRepo 'author'
$mcpAuthorObject = (Get-TextData $mcpAuthor).Text | ConvertFrom-Json -AsHashtable
if ($mcpAuthorObject.model.model -cne $mcpSeed.model.model -or -not $mcpAuthorObject.mcp.servers.Contains('existing-user-server')) { throw 'Workflow MCP merge changed model configuration or another MCP server.' }
$baseImplementShell = ConvertTo-CanonicalConfigJson @($mcpSeed.agents['implement'].permissions | Where-Object { $_.action -eq 'shell' -and $_.resource -notin @('*workflow.py*','*workflow_entry.py*','*workflow_runtime.py*') })
$mcpImplementShell = ConvertTo-CanonicalConfigJson @($mcpAuthorObject.agents['implement'].permissions | Where-Object { $_.action -eq 'shell' -and $_.resource -notin @('*workflow.py*','*workflow_entry.py*','*workflow_runtime.py*') })
if ($baseImplementShell -cne $mcpImplementShell) { throw 'Workflow MCP merge altered unrelated implement shell permissions.' }
$mcpCommand = @($mcpAuthorObject.mcp.servers.ms14_workflow.command)
if ($mcpCommand[-1] -cne 'author' -or $mcpCommand[2] -cne '--repo' -or $mcpAuthorObject.mcp.servers.ms14_workflow.protocol -cne 'legacy') { throw 'Workflow MCP command/profile/protocol is not bound to the selected author worktree.' }
if ($mcpAuthorObject.mcp.servers.ms14_workflow.timeout.startup -ne 15000 -or $mcpAuthorObject.mcp.servers.ms14_workflow.timeout.catalog -ne 15000 -or $mcpAuthorObject.mcp.servers.ms14_workflow.timeout.execution -ne 300000) { throw 'Workflow MCP transport timeout bounds changed from the owner-fixed policy.' }
if ($mcpAuthorObject.mcp.timeout.execution -ne 12000 -or -not $mcpAuthorObject.mcp.servers.Contains('existing-user-server') -or $mcpAuthorObject.mcp.Contains('ms14_workflow')) { throw 'Workflow MCP merge did not preserve root timeout/servers structure or migrated the wrong flat path.' }
foreach ($nullCase in @('mcp','servers')) {
    $nullConfig = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $mcpSeed -Depth 100)
    if ($nullCase -eq 'mcp') { $nullConfig.mcp = $null } else { $nullConfig.mcp.servers = $null }
    $nullBytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $nullConfig))
    $nullBeforeHash = Get-Sha256 $nullBytes
    $nullRejected = $false
    try { Get-WorkflowMcpConfig $nullBytes $mcpShared $mcpRepo 'author' | Out-Null } catch { $nullRejected = $true }
    if (-not $nullRejected -or (Get-Sha256 $nullBytes) -cne $nullBeforeHash) { throw "Malformed mcp.$nullCase null value was silently normalized or modified." }
}
$rootSchema = Get-Content (Join-Path $mainRoot 'build/agent-workflow/phantom-wait-fix/opencode-schema.json') -Raw | ConvertFrom-Json -AsHashtable
$mcpRootSchema = $rootSchema.components.schemas['Config.InfoEncoded'].properties.mcp
if ($mcpRootSchema.additionalProperties -ne $false -or $mcpRootSchema.properties.servers.type -cne 'object' -or $mcpRootSchema.properties.timeout.type -cne 'object' -or $mcpRootSchema.properties.Contains('ms14_workflow') -or $mcpRootSchema.properties.servers.additionalProperties.anyOf[0].'$ref' -cne '#/components/schemas/Mcp.LocalConfigEncoded') { throw 'MCP output does not match retained OpenCode Config.InfoEncoded root schema.' }
$encodedMcp = $mcpAuthorObject.mcp
if (-not $encodedMcp.servers.ms14_workflow -or $encodedMcp.Contains('ms14_workflow')) { throw 'Generated MCP entry is not nested under root mcp.servers.' }
$flatLegacyConfig = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $mcpSeed -Depth 100)
$flatLegacyConfig.mcp.ms14_workflow = Get-WorkflowMcpEntry $mcpShared $mcpRepo 'author' $true
$migratedLegacy = (Get-TextData (Get-WorkflowMcpConfig ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $flatLegacyConfig))) $mcpShared $mcpRepo 'author')).Text | ConvertFrom-Json -AsHashtable
if ($migratedLegacy.mcp.Contains('ms14_workflow') -or $migratedLegacy.mcp.servers.ms14_workflow.disabled) { throw 'Exact publisher-owned flat MCP entry was not migrated to the schema-correct servers map.' }
$bothPaths = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $flatLegacyConfig -Depth 100)
$bothPaths.mcp.servers.ms14_workflow = Get-WorkflowMcpEntry $mcpShared $mcpRepo 'author'
$bothRefused = $false; try { Get-WorkflowMcpConfig ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $bothPaths))) $mcpShared $mcpRepo 'author' | Out-Null } catch { $bothRefused = $true }
if (-not $bothRefused) { throw 'Workflow MCP migration accepted conflicting flat and servers-map entries.' }
$authorToolRules = @($mcpAuthorObject.agents['orchestrate-ms14'].permissions | Where-Object { $_.action -like 'ms14_workflow*' })
if (@($authorToolRules | Where-Object { $_.action -eq 'ms14_workflow_submit' -and $_.resource -eq '*' -and $_.effect -eq 'allow' }).Count -ne 1 -or
    @($authorToolRules | Where-Object { $_.action -eq 'ms14_workflow_status' -and $_.resource -eq '*' -and $_.effect -eq 'allow' }).Count -ne 1 -or
    @($authorToolRules | Where-Object { $_.effect -eq 'allow' -and $_.action -notin @('ms14_workflow_submit','ms14_workflow_status') }).Count) { throw 'Author MCP permissions must allow only submit/status.' }
foreach ($tool in @('submit','status','validate','review','finish')) {
    $matches = @($authorToolRules | Where-Object { $_.action -in @('ms14_workflow_*',('ms14_workflow_' + $tool)) -and $_.resource -eq '*' })
    $actual = 'ask'; foreach ($rule in $matches) { $actual = $rule.effect }
    $expected = if ($tool -in @('submit','status')) {'allow'} else {'deny'}
    if ($actual -cne $expected) { throw "Author role expected $expected for $tool, got $actual." }
}
foreach ($role in @('implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')) {
    if (@($mcpAuthorObject.agents[$role].permissions | Where-Object { $_.action -eq 'ms14_workflow_*' -and $_.resource -eq '*' -and $_.effect -eq 'deny' }).Count -ne 1) { throw "Workflow MCP access is not denied for helper role $role." }
}
foreach ($role in @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')) {
    $permissions = @($mcpAuthorObject.agents[$role].permissions | Where-Object { $_.action -eq 'edit' })
    foreach ($protectedPath in @('C:/fixture/MoonStation14-agent-01/opencode.jsonc','C:/fixture/MoonStation14-agent-01/.opencode/agents/integration-lead.md','C:/fixture/MoonStation14-agent-01/.opencode/agents/fidelity-reviewer.md')) {
        $effect='allow'; foreach ($rule in $permissions) { if ($protectedPath -like $rule.resource) { $effect=$rule.effect } }
        if ($effect -ne 'deny') { throw "Agent role $role may edit protected workflow config $protectedPath." }
    }
    foreach ($authorPath in @('C:/fixture/MoonStation14-agent-01/build/agent-workflow/sprint/gate-plan.json','C:/fixture/MoonStation14-agent-01/src/main/java/example/Feature.java')) {
        $effect='allow'; foreach ($rule in $permissions) { if ($authorPath -like $rule.resource) { $effect=$rule.effect } }
        if ($effect -ne 'allow') { throw "Config guard overblocked author work path $authorPath for $role." }
    }
    $mainEdit='allow'; foreach ($rule in $permissions) { if ('C:/fixture/MoonStation14/opencode.jsonc' -like $rule.resource) { $mainEdit=$rule.effect } }
    if ($mainEdit -ne 'allow') { throw 'Worktree policy accidentally denied the privileged MAIN process-development config.' }
}
foreach ($role in @('orchestrate-ms14','3d-modeler','integration-lead')) {
    if (@($mcpAuthorObject.agents[$role].permissions | Where-Object { $_.action -eq 'shell' -and $_.effect -eq 'allow' -and $_.resource -match '(?i)workflow\.py.*(?:submit|status|gate-request|gate-preflight|gate-validate|gate-report|gate-finish|gate-deliver|gate-scope-review|gate-clear-blocker|candidate-review|manual-tests|manual-notify)' }).Count) {
        throw "MCP binding retained a CLI workflow write/read allowance for $role."
    }
    if (@($mcpAuthorObject.agents[$role].permissions | Where-Object { $_.action -eq 'shell' -and $_.effect -eq 'allow' -and $_.resource -match '(?i)workflow\.py' -and $_.resource -notmatch '(?i)workflow\.py.*--help' }).Count) { throw "MCP binding retained an unclassified workflow CLI allowance for $role." }
}
$authorShellRules = @($mcpAuthorObject.agents['orchestrate-ms14'].permissions | Where-Object action -eq 'shell')
if (@($authorShellRules | Where-Object { $_.resource -eq '*' -and $_.effect -eq 'allow' }).Count -ne 1) { throw 'MCP policy unexpectedly removed an inherited broad shell rule instead of adding explicit route denies.' }
foreach ($case in @(
    @{command='python tooling/agent_workflow/workflow.py submit ID';expected='deny'},
    @{command='python tooling/agent_workflow/workflow_runtime.py';expected='deny'},
    @{command='python tooling/agent_workflow/workflow_entry.py';expected='deny'},
    @{command='.\gradlew compileJava';expected='allow'},
    @{command='git status --short';expected='allow'}
)) {
    $effect='ask'; foreach ($rule in $authorShellRules) { if ($case.command -like $rule.resource) { $effect=$rule.effect } }
    if ($effect -cne $case.expected) { throw "Workflow CLI policy expected $($case.expected) for $($case.command), got $effect." }
}
$mcpAgain = Get-WorkflowMcpConfig $mcpAuthor $mcpShared $mcpRepo 'author'
if ((Get-Sha256 $mcpAuthor) -cne (Get-Sha256 $mcpAgain)) { throw 'Workflow MCP binding is not idempotent.' }
$mcpLeadSeed = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $mcpSeed -Depth 100)
foreach ($role in @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')) {
    $mcpLeadSeed.agents[$role].permissions = @($mcpLeadSeed.agents[$role].permissions | Where-Object { $_.action -notlike 'ms14_workflow*' })
    foreach ($rule in (Get-ExpectedMcpPermissionTuples 'lead' $role)) { $mcpLeadSeed.agents[$role].permissions += [ordered]@{action=$rule.action;resource=$rule.resource;effect=$rule.effect} }
}
$mcpLeadBytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $mcpLeadSeed))
$mcpLead = (Get-TextData (Get-WorkflowMcpConfig $mcpLeadBytes $mcpShared 'C:/fixture/MoonStation14-agent-05' 'lead')).Text | ConvertFrom-Json -AsHashtable
$leadToolRules = @($mcpLead.agents['integration-lead'].permissions | Where-Object { $_.action -like 'ms14_workflow*' })
if (@($leadToolRules | Where-Object { $_.effect -eq 'allow' -and $_.resource -eq '*' -and $_.action -in @('ms14_workflow_status','ms14_workflow_validate','ms14_workflow_review','ms14_workflow_finish') }).Count -ne 4 -or
    @($leadToolRules | Where-Object { $_.effect -eq 'allow' }).Count -ne 4) { throw 'Lead MCP permissions must expose only status/validate/review/finish.' }
foreach ($tool in @('submit','status','validate','review','finish')) {
    $matches = @($leadToolRules | Where-Object { $_.action -in @('ms14_workflow_*',('ms14_workflow_' + $tool)) -and $_.resource -eq '*' })
    $actual = 'ask'; foreach ($rule in $matches) { $actual = $rule.effect }
    $expected = if ($tool -in @('status','validate','review','finish')) {'allow'} else {'deny'}
    if ($actual -cne $expected) { throw "Lead role expected $expected for $tool, got $actual." }
}
$customMcp = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $mcpSeed -Depth 100)
$customMcp.mcp.ms14_workflow = [ordered]@{ type='remote'; url='https://unowned.invalid' }
$customRefused = $false
try { Get-WorkflowMcpConfig ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $customMcp))) $mcpShared $mcpRepo 'author' | Out-Null } catch { $customRefused = $true }
if (-not $customRefused) { throw 'Workflow MCP merge overwrote an unowned custom server entry.' }
$wrongPrepared = Get-WorkflowMcpEntry $mcpShared $mcpRepo 'author' $true
foreach ($field in @('root','cwd','shared','interpreter','adapter','extra')) {
    $configCase = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $mcpSeed -Depth 100)
    $copyEntry = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $wrongPrepared -Depth 20)
    switch ($field) {
        'root' { $copyEntry.command[3] = 'C:/fixture/other-repo' }
        'cwd' { $copyEntry.cwd = 'C:/fixture/other-cwd' }
        'shared' { $copyEntry.command[0] = 'C:/fixture/other-shared/.venv-workflow-mcp/Scripts/python.exe'; $copyEntry.command[1] = 'C:/fixture/other-shared/tooling/agent_workflow/workflow_mcp.py' }
        'interpreter' { $copyEntry.command[0] = 'C:/fixture/other-python.exe' }
        'adapter' { $copyEntry.command[1] = 'C:/fixture/other-workflow-mcp.py' }
        'extra' { $copyEntry.custom_extension = 'unowned' }
    }
$configCase.mcp.servers.ms14_workflow = $copyEntry
    $refused = $false
    try { Get-WorkflowMcpConfig ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $configCase))) $mcpShared $mcpRepo 'author' | Out-Null } catch { $refused = $true }
    if (-not $refused) { throw "Prepared MCP config with unowned $field binding was accepted." }
}
$validPrepared = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $mcpSeed -Depth 100)
$validPrepared.mcp.servers.ms14_workflow = Get-WorkflowMcpEntry $mcpShared $mcpRepo 'author' $true
$validActivated = (Get-TextData (Get-WorkflowMcpConfig ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $validPrepared))) $mcpShared $mcpRepo 'author')).Text | ConvertFrom-Json -AsHashtable
if ($validActivated.mcp.servers.ms14_workflow.disabled) { throw 'Exact target-bound disabled MCP template was not activated.' }
$outsideChange = (Get-TextData (Get-WorkflowMcpConfig $mcpBytes $mcpShared $mcpRepo 'author')).Text | ConvertFrom-Json -AsHashtable
$outsideChange.model = [ordered]@{model='unowned/custom-change'}
$outsideChangeBytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $outsideChange))
if (Test-McpOnlySemanticChange $mcpBytes $outsideChangeBytes $mcpRepo $mcpShared 'author') { throw 'Semantic ownership comparison unexpectedly ignored unrelated model drift.' }
$mainShared = [System.IO.Path]::GetFullPath((Join-Path $workspaceParent 'MoonStation14-shared'))
$mainActivation = (Get-TextData (Get-WorkflowMcpConfig ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $baseConfig))) $mainShared $mainRoot 'author')).Text | ConvertFrom-Json -AsHashtable
if ($mainActivation.mcp.servers.ms14_workflow.disabled -or $mainActivation.mcp.servers.ms14_workflow.command[3] -cne $mainRoot) { throw 'Exact canonical MAIN MCP template did not activate with its trusted root binding.' }
$customCli = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $mcpSeed -Depth 100)
$customCli.agents['orchestrate-ms14'].permissions += @([ordered]@{action='shell';resource='python tooling/agent_workflow/workflow.py *';effect='allow'})
$customCliRefused = $false
try { Get-WorkflowMcpConfig ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $customCli))) $mcpShared $mcpRepo 'author' | Out-Null } catch { $customCliRefused = $true }
if (-not $customCliRefused) { throw 'Workflow MCP migration accepted an unclassified CLI fallback rule.' }
$nearMatchCli = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $mcpSeed -Depth 100)
$nearMatchCli.agents['orchestrate-ms14'].permissions += @([ordered]@{action='shell';resource='*workflow.py*status*--summary *';effect='allow'})
$nearMatchRefused = $false
try { Get-WorkflowMcpConfig ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $nearMatchCli))) $mcpShared $mcpRepo 'author' | Out-Null } catch { $nearMatchRefused = $true }
if (-not $nearMatchRefused) { throw 'Workflow MCP migration accepted an unowned near-match to a retired CLI allowance.' }
$badMcpRules = @(
    @{role='orchestrate-ms14';action='ms14_workflow_submit';resource='*';effect='deny'},
    @{role='implement';action='ms14_workflow_validate';resource='*';effect='allow'},
    @{role='orchestrate-ms14';action='ms14_workflow_submit';resource='*';effect='ask'},
    @{role='orchestrate-ms14';action='ms14_workflow_future';resource='*';effect='allow'}
)
foreach ($badRule in $badMcpRules) {
    $badPermissionConfig = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $mcpSeed -Depth 100)
    $badPermissionConfig.agents[$badRule.role].permissions += @([ordered]@{action=$badRule.action;resource=$badRule.resource;effect=$badRule.effect})
    $badRuleRefused = $false
    try { Get-WorkflowMcpConfig ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $badPermissionConfig))) $mcpShared $mcpRepo 'author' | Out-Null } catch { $badRuleRefused = $true }
if (-not $badRuleRefused) { throw "Workflow MCP migration accepted conflicting/unowned permission tuple $($badRule.role): $($badRule.action) $($badRule.resource) $($badRule.effect)." }
foreach ($route in @('*workflow_entry.py*submit*','*workflow_runtime.py*status*')) {
    $customRoute = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $mcpSeed -Depth 100)
    $customRoute.agents['orchestrate-ms14'].permissions += @([ordered]@{action='shell';resource=$route;effect='allow'})
    $routeRefused = $false
    try { Get-WorkflowMcpConfig ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $customRoute))) $mcpShared $mcpRepo 'author' | Out-Null } catch { $routeRefused = $true }
    if (-not $routeRefused) { throw "Workflow MCP migration accepted an unowned direct-entry CLI allowance: $route" }
}
}
$broadPolicy = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $baseConfig -Depth 100)
$broadPolicy.agents['implement'].permissions = @($broadPolicy.agents['implement'].permissions) + @([ordered]@{ action = 'shell'; resource = '*gradlew*test*'; effect = 'deny' })
$broadRejected = $false
try { Assert-GradleShellPolicy $broadPolicy } catch { $broadRejected = $true }
if (-not $broadRejected) { throw 'Gradle policy guard accepted a broad substring deny that can block safe compile commands.' }
$baseConfig.model = [ordered]@{ providerID = 'fixture-provider'; model = 'unchanged-model' }
$baseConfig.agents['implement'].request = [ordered]@{ body = [ordered]@{ fixtureDefault = 'preserve' } }
$baseBytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $baseConfig))
$shellConfig = Get-ShellPolicyConfig $baseBytes $baseConfig
Assert-GradleShellPolicy ((Get-TextData $shellConfig).Text | ConvertFrom-Json -AsHashtable)
$oldTarget = (Get-TextData $baseBytes).Text | ConvertFrom-Json -AsHashtable
foreach ($role in $oldTarget.agents.Keys) { $oldTarget.agents[$role].permissions = @($oldTarget.agents[$role].permissions | Where-Object { -not ($_.action -eq 'shell' -and (($_.resource -match '(?i)gradle' -and $_.resource -match '(?i)(?:test|build|check)') -or $_.resource -match '(?i)(?:runGameTestServer|runServer|runClient|featureReadiness)')) }) }
$oldTargetBytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $oldTarget))
$ordinaryBound = Get-TailoredProjectConfig $oldTargetBytes $shared $workspaceParent 4 $baseConfig
Assert-GradleShellPolicy ((Get-TextData $ordinaryBound).Text | ConvertFrom-Json -AsHashtable)
$ordinaryConfig = (Get-TextData $ordinaryBound).Text | ConvertFrom-Json -AsHashtable
$ordinaryEdit = @($ordinaryConfig.agents['orchestrate-ms14'].permissions | Where-Object action -eq 'edit')
$peerConfigPath = (Join-Path $workspaceParent 'MoonStation14-agent-04/opencode.jsonc').Replace('\','/')
if (@($ordinaryEdit | Where-Object { $_.resource -eq $peerConfigPath -and $_.effect -eq 'deny' }).Count -ne 1 -or
    @($ordinaryEdit | Where-Object { $_.resource -match 'MoonStation14/opencode\.jsonc' }).Count) { throw 'Bound config-edit deny escaped its assigned worktree or failed to protect that worktree.' }
$localConfig = (Get-TextData $shellConfig).Text | ConvertFrom-Json -AsHashtable
$localConfig.model.fixtureUnrelated = 'preserve'
$localConfig.agents['implement'].permissions += @([ordered]@{action='shell';resource='git status';effect='allow'})
$unknownGradle = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $localConfig -Depth 100)
$unknownGradle.agents['implement'].permissions += @([ordered]@{action='shell';resource='*gradle* test bespoke';effect='deny'})
$unknownRejected = $false
try { Get-ShellPolicyConfig ([System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $unknownGradle))) $baseConfig | Out-Null } catch { $unknownRejected = $true }
if (-not $unknownRejected) { throw 'Shell policy deployment discarded an unknown custom Gradle rule.' }
$fidelityCanonical = [System.IO.File]::ReadAllBytes((Join-Path $mainRoot '.opencode/agents/fidelity-reviewer.md'))
$fidelityReplacement = Get-ShellPolicyFidelityHeader $fidelityCanonical $fidelityCanonical
if ((Get-Sha256 $fidelityReplacement) -cne (Get-Sha256 $fidelityCanonical)) { throw 'Fidelity shell policy replacement changed an already canonical header.' }
$legacyFidelityPath = Join-Path $mainRoot 'build/agent-workflow/agent04-shell-permission-fix/baseline/MoonStation14-agent-04/.opencode/agents/fidelity-reviewer.md'
if (Test-Path -LiteralPath $legacyFidelityPath -PathType Leaf) {
    $legacyFidelity = [System.IO.File]::ReadAllBytes($legacyFidelityPath)
    $legacyUpdated = Get-ShellPolicyFidelityHeader $legacyFidelity $fidelityCanonical
    $legacyUpdatedAgain = Get-ShellPolicyFidelityHeader $legacyUpdated $fidelityCanonical
    if ((Get-Sha256 $legacyUpdated) -cne (Get-Sha256 $legacyUpdatedAgain)) { throw 'Recognized legacy fidelity policy replacement is not idempotent.' }
    $legacyText = (Get-TextData $legacyFidelity).Text
    foreach ($badRule in @(@('*gradlew*build*','allow'),@('*gradlew*check*','allow'),@('*gradlew*test*','allow'),@('*gradlew*test*--tests*','deny'))) {
        $needle = '    "' + $badRule[0] + '": ' + $(if ($badRule[1] -eq 'allow') {'deny'} else {'allow'})
        $replacement = '    "' + $badRule[0] + '": ' + $badRule[1]
        $badLegacyText = $legacyText.Replace($needle,$replacement)
        $refused = $false
        try { Get-ShellPolicyFidelityHeader ([System.Text.UTF8Encoding]::new($false).GetBytes($badLegacyText)) $fidelityCanonical | Out-Null } catch { $refused = $true }
        if (-not $refused) { throw "Fidelity updater accepted opposite legacy effect for $($badRule[0])." }
    }
}
$fidelityText = (Get-TextData $fidelityCanonical).Text
foreach ($badEntry in @("    '*custom*': deny",'    custom: deny','    "custom": ask')) {
    $badText = $fidelityText.Replace('  bash:' + "`n", '  bash:' + "`n" + $badEntry + "`n")
    $badRejected = $false
    try { Get-ShellPolicyFidelityHeader ([System.Text.UTF8Encoding]::new($false).GetBytes($badText)) $fidelityCanonical | Out-Null } catch { $badRejected = $true }
    if (-not $badRejected) { throw "Fidelity policy accepted unrecognized bash entry: $badEntry" }
}
$minimal = [ordered]@{agents=[ordered]@{'integration-lead'=$baseConfig.agents['integration-lead'];'fidelity-reviewer'=$baseConfig.agents['fidelity-reviewer']}; model=$baseConfig.model}
$minimalBytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $minimal))
$minimalPolicy = Get-ShellPolicyConfig $minimalBytes $baseConfig
$minimalResult = (Get-TextData $minimalPolicy).Text | ConvertFrom-Json -AsHashtable
if (($minimalResult.agents.Keys | Sort-Object) -join ',' -cne 'fidelity-reviewer,integration-lead') { throw 'Shell policy replacement synthesized missing roles.' }
$firstSubmitConfig = (Get-TextData $baseBytes).Text | ConvertFrom-Json -AsHashtable
$firstSubmitConfig.agents['implement'].permissions = @([ordered]@{action='shell';resource='python tooling/agent_workflow/workflow.py submit *';effect='allow'})
$firstSubmitBytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $firstSubmitConfig))
$firstSubmitOut = Get-ShellPolicyConfig $firstSubmitBytes $baseConfig
$firstSubmitResolved = (Get-TextData $firstSubmitOut).Text | ConvertFrom-Json -AsHashtable
if (@($firstSubmitResolved.agents['implement'].permissions | Where-Object resource -eq 'python tooling/agent_workflow/workflow.py submit *').Count -ne 1) { throw 'First-position submit permission was duplicated or lost.' }
$tailored = Get-TailoredProjectConfig $baseBytes $shared $workspaceParent 1 $baseConfig
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
        if ($matches.Count -ne 1) {
            throw "Canonical author profile lacks the unique submit permission for $role."
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
$gradleCases = @(
    @{ command = '.\gradlew compileJava compileTestJava compileGametestJava --max-workers=1 --no-daemon'; expected = 'allow' },
    @{ command = './gradlew compileJava'; expected = 'allow' },
    @{ command = 'gradlew.bat test --tests X'; expected = 'allow' },
    @{ command = '"C:\Gradle\gradle.exe" :sub:test --tests=X'; expected = 'allow' },
    @{ command = 'gradle compileJava :test --tests X'; expected = 'allow' },
    @{ command = 'gradlew runGameTestServer test --tests X'; expected = 'deny' },
    @{ command = '.\gradlew runGameTestServer test --tests com.example.FocusedTest'; expected = 'deny' },
    @{ command = 'gradlew compileJava test'; expected = 'deny' },
    @{ command = 'gradlew compileGametestJava'; expected = 'allow' },
    @{ command = 'gradlew testClasses gametestClasses buildEnvironment checkstyleMain --build-cache -Ppath=build/gametest'; expected = 'allow' }
)
foreach ($role in @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')) {
    $rules = @($resolved.agents[$role].permissions | Where-Object action -eq 'shell')
    foreach ($case in $gradleCases) {
        $effect = 'allow'
        foreach ($rule in $rules) { if ($case.command -like $rule.resource) { $effect = $rule.effect } }
        $expected = $case.expected
        if ($role -eq 'integration-lead' -and $expected -eq 'allow' -and $case.command -match '\btest\b') { $expected = 'deny' }
        if ($effect -cne $expected) { throw "Gradle policy fixture expected $expected for ${role}: $($case.command), got $effect" }
    }
}
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
$legacyLeadLines = @(
    '    "*workflow.py*--help*": allow','    "*workflow.py*status*": allow','    "*workflow.py*gate-preflight*": allow',
    '    "*workflow.py*gate-validate*": allow','    "*workflow.py*gate-report*": allow','    "*workflow.py*gate-clear-blocker*": allow',
    '    "*workflow.py*candidate-review*": allow','    "*workflow.py*gate-finish*": allow','    "*workflow.py*gate-deliver*": allow',
    '    "*workflow.py*gate-scope-review*": allow','    "*workflow.py*manual-tests*": allow','    "*workflow.py*manual-notify*": allow'
)
$legacyLeadText = $integrationText
foreach ($mcpPermission in @('  ms14_workflow_status: allow','  ms14_workflow_validate: allow','  ms14_workflow_review: allow','  ms14_workflow_finish: allow')) { $legacyLeadText = $legacyLeadText.Replace($mcpPermission + "`n",'') }
$legacyAnchor = '  webfetch: allow'
$legacyLeadText = $legacyLeadText.Replace($legacyAnchor,($legacyLeadLines -join "`n") + "`n" + $legacyAnchor)
$legacyLeadText = $legacyLeadText.Replace('model: openai/gpt-6.1-sol','model: user/custom-lead-model').Replace('reasoningEffort: medium','reasoningEffort: high').Replace('description: Permanent, bounded integration/readiness coordinator for the four-worker team.','description: Custom lead description; preserve me.')
$legacyLeadText = $legacyLeadText.Replace('Read and follow the shared role contract','Custom body paragraph. Preserve exactly.`n`nRead and follow the shared role contract')
$legacyLeadBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($legacyLeadText)
$mergedLeadBytes = Get-McpLeadProfile $legacyLeadBytes
$againLeadBytes = Get-McpLeadProfile $mergedLeadBytes
if (-not (Test-McpLeadProfileSemanticChange $legacyLeadBytes $mergedLeadBytes) -or [Convert]::ToBase64String($againLeadBytes) -cne [Convert]::ToBase64String($mergedLeadBytes)) {
    $aText=(Get-TextData $mergedLeadBytes).Text; $bText=(Get-TextData $againLeadBytes).Text
    throw "Lead-profile MCP transformation changed unrelated text or was not idempotent (semantic=$(Test-McpLeadProfileSemanticChange $legacyLeadBytes $mergedLeadBytes), lengths=$($aText.Length)/$($bText.Length), suffix=$($aText.Substring([Math]::Max(0,$aText.Length-25)) -replace "`n",'<LF>')/$($bText.Substring([Math]::Max(0,$bText.Length-25)) -replace "`n",'<LF>'))."
}
$mergedLeadText = (Get-TextData $mergedLeadBytes).Text
foreach ($preserve in @('model: user/custom-lead-model','reasoningEffort: high','description: Custom lead description; preserve me.','Custom body paragraph. Preserve exactly.')) { if (-not $mergedLeadText.Contains($preserve)) { throw "Lead-profile MCP transformation lost unrelated content: $preserve" } }
if (@($mergedLeadText -split "`n" | Where-Object { $_ -in @('  ms14_workflow_status: allow','  ms14_workflow_validate: allow','  ms14_workflow_review: allow','  ms14_workflow_finish: allow') }).Count -ne 4 -or $mergedLeadText -match '(?m)^\s+"\*workflow\.py\*.*": allow$') { throw 'Lead-profile MCP migration did not replace all known CLI entries with exact tool permissions.' }
$badLegacyLead = $legacyLeadText.Replace('  webfetch: allow', '    "*workflow.py*unknown-operation*": allow' + "`n" + '  webfetch: allow')
$badLegacyRejected = $false; try { Get-McpLeadProfile ([System.Text.UTF8Encoding]::new($false).GetBytes($badLegacyLead)) | Out-Null } catch { $badLegacyRejected = $true }
if (-not $badLegacyRejected) { throw 'Lead-profile MCP migration accepted an unknown workflow CLI permission.' }
$badMcpLead = $legacyLeadText.Replace($legacyAnchor,'  ms14_workflow_unknown: allow' + "`n" + $legacyAnchor)
$badMcpRejected = $false; try { Get-McpLeadProfile ([System.Text.UTF8Encoding]::new($false).GetBytes($badMcpLead)) | Out-Null } catch { $badMcpRejected = $true }
if (-not $badMcpRejected) { throw 'Lead-profile MCP migration accepted an unknown MCP permission.' }
foreach ($command in @('gate-finish','gate-deliver')) {
    if ($integrationText -match ('(?m)^\s+"\*workflow\.py\*' + [regex]::Escape($command) + '\*": allow$')) {
        throw "Integration profile must not retain CLI write allowance '$command'."
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
if ($centralProfile -match '(?m)^\s+"\*workflow\.py\*(?:gate-report|gate-deliver|sprint-cleanup)\*": allow$') {
    throw 'Central integration profile must not retain normal CLI workflow write allowances.'
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
if (@($neutralPermissions | Where-Object { $_.action -eq 'shell' -and $_.resource -like '*workflow.py*gate-report*' -and $_.effect -eq 'allow' }).Count -gt 0 -or
    @($neutralPermissions | Where-Object { $_.action -eq 'shell' -and $_.effect -eq 'allow' -and $_.resource -eq '*' }).Count -gt 0 -or
    @($neutralPermissions | Where-Object { $_.action -eq 'shell' -and $_.resource -match 'sprint-cleanup' -and $_.effect -eq 'allow' }).Count -gt 0) {
    throw 'Neutral integration CLI permissions must not retain workflow writes, arbitrary shell or cleanup.'
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
    'tooling/agent_workflow/owner_attention.py','tooling/agent_workflow/owner_feedback.py','tooling/agent_workflow/gate_progress.py','tooling/agent_workflow/validation_evidence.py','tooling/agent_workflow/admission.py','tooling/agent_workflow/gate_dispatch.py','tooling/agent_workflow/gate_execution.py','tooling/agent_workflow/workflow_runtime.py','tooling/agent_workflow/workflow_mcp.py','tooling/agent_workflow/requirements-workflow-mcp.txt',
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
    if ($planOutput -notmatch 'shared\s+create\s+tooling/agent_workflow/workflow_mcp.py' -or
        $planOutput -notmatch 'shared\s+create\s+tooling/agent_workflow/requirements-workflow-mcp.txt') {
        throw 'Workflow MCP adapter and pinned SDK requirements are missing from the shared payload.'
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
$contractDocs = @(
    [System.IO.File]::ReadAllText((Join-Path $mainRoot 'docs/development-workflow/README.runtime.md')),
    [System.IO.File]::ReadAllText((Join-Path $mainRoot 'docs/development-workflow/CLI.md')),
    [System.IO.File]::ReadAllText((Join-Path $mainRoot 'docs/development-workflow/roles/integration-lead.md'))
) -join "`n"
if ($contractDocs -match '--(?:wait-timeout|server|central-worktree)' -or $contractDocs -match 'gate-request ID .*--wait-timeout') {
    throw 'Removed caller-controlled timeout/server/worktree flags remain in published contract examples.'
}
if ($contractDocs -notmatch 'job ID' -or $contractDocs -notmatch 'MUST NOT retry') { throw 'Gate job ownership contract is missing.' }

# End-to-end ShellPolicyOnly deployment stays entirely inside this unique fake workspace.
$modeRoot = Join-Path $env:LOCALAPPDATA ('Temp\opencode\shell-policy-e2e-' + [guid]::NewGuid().ToString('N'))
$modeMain = Join-Path $modeRoot 'MoonStation14'
$modeStage = $null; $modeBackup = $null
try {
    foreach ($relative in $sharedSources) {
        $src = Join-Path $mainRoot ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        $dst = Join-Path $modeMain ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        New-Item -ItemType Directory -Path (Split-Path -Parent $dst) -Force | Out-Null
        [System.IO.File]::WriteAllBytes($dst,[System.IO.File]::ReadAllBytes($src))
    }
    $outside = @{}
    foreach ($number in 1..5) {
        $peer = Join-Path $modeRoot ('MoonStation14-agent-{0:D2}' -f $number)
        foreach ($relative in @('opencode.jsonc','.opencode/agents/fidelity-reviewer.md','.opencode/agents/integration-lead.md','AGENTS.md','tooling/agent_workflow/workflow.py')) {
            $dst = Join-Path $peer $relative; New-Item -ItemType Directory -Path (Split-Path -Parent $dst) -Force | Out-Null
            $src = Join-Path $mainRoot $relative
            if ($number -eq 4 -and $relative -in @('opencode.jsonc','.opencode/agents/fidelity-reviewer.md')) {
                $baseline = Join-Path $mainRoot ('build/agent-workflow/agent04-shell-permission-fix/baseline/MoonStation14-agent-04/' + $relative)
                if (Test-Path -LiteralPath $baseline -PathType Leaf) { $src = $baseline }
            }
            $bytes = [System.IO.File]::ReadAllBytes($src)
            if ($relative -eq 'opencode.jsonc' -or $relative -eq '.opencode/agents/fidelity-reviewer.md') { $outside["$number/$relative"] = Get-Sha256 $bytes }
            [System.IO.File]::WriteAllBytes($dst,$bytes)
        }
    }
    $selectedConfig = Join-Path $modeRoot 'MoonStation14-agent-04/opencode.jsonc'
    $previousPath = Join-Path $modeRoot 'previous-receipt.json'
    $previousDoc = @{created_utc='fixture';main_root=$modeMain;shared_root=(Join-Path $modeRoot 'MoonStation14-shared');source_hashes=@{'kept-source.md'='abc'};files=@(@{target='shared';path='kept.txt';old_sha256='old';new_sha256='new';deleted=$false},@{target='MoonStation14-agent-02';path='keep.md';old_sha256='old';new_sha256='new';deleted=$false})}
    [System.IO.File]::WriteAllText($previousPath,($previousDoc | ConvertTo-Json -Depth 20),[System.Text.UTF8Encoding]::new($false))
    $dry = (& $publisher -ShellPolicyOnly -TargetWorktrees 4 -TestFixtureRoot $modeRoot -PreviousReceipt $previousPath | Out-String); if ($LASTEXITCODE) { throw 'Shell-only fixture dry-run failed.' }
    if ($dry -notmatch 'Dry run only' -or $dry -notmatch 'manifest.json') { throw 'Shell-only dry run did not stage a manifest.' }
    $manifestPath = [regex]::Match($dry,'Manifest: (.+)').Groups[1].Value.Trim(); $modeStage = Split-Path -Parent $manifestPath
    $manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
    if (@($manifest.files).Count -ne 2 -or @($manifest.files | Where-Object target -ne 'MoonStation14-agent-04').Count) { throw 'Shell-only manifest contains unexpected targets/files.' }
    $otherHash = $outside['3/opencode.jsonc']; $drySelectedHash = Get-FileHashValue $selectedConfig
    $apply = (& $publisher -ShellPolicyOnly -TargetWorktrees 4 -TestFixtureRoot $modeRoot -PreviousReceipt $previousPath -Apply | Out-String); if ($LASTEXITCODE) { throw 'Shell-only fixture apply failed.' }
    if ($apply -notmatch 'Backup and receipt: (.+)') { throw 'Shell-only apply omitted backup/receipt path.' }
    $modeBackup = $Matches[1].Trim(); $receiptPath = Join-Path $modeBackup 'publish-receipt.json'
    $receipt = Get-Content $receiptPath -Raw | ConvertFrom-Json
    if (@($receipt.files).Count -ne 4 -or @($receipt.files | Where-Object { -not $_.old_sha256 -or -not $_.new_sha256 }).Count) { throw 'Shell-only receipt hashes/files are incomplete.' }
    if (-not (@($receipt.files | Where-Object path -eq 'kept.txt').Count) -or $receipt.source_hashes.'kept-source.md' -ne 'abc' -or $receipt.shared_root -ne $previousDoc.shared_root) { throw 'Shell-only receipt lost previous publisher ownership/source metadata.' }
    $normalDry = (& $publisher -PreviousReceipt $receiptPath -TestFixtureRoot $modeRoot | Out-String); if ($LASTEXITCODE -or $normalDry -notmatch 'Dry run only') { throw 'Normal publisher rejected the merged shell-policy receipt.' }
    if ((Get-FileHashValue $selectedConfig) -eq $drySelectedHash -or (Get-FileHashValue (Join-Path $modeRoot 'MoonStation14-agent-03/opencode.jsonc')) -ne $otherHash) { throw 'Shell-only apply failed selection isolation.' }
    foreach ($relative in @('AGENTS.md','tooling/agent_workflow/workflow.py','.opencode/agents/integration-lead.md')) { if ((Get-FileHashValue (Join-Path $modeRoot "MoonStation14-agent-04/$relative")) -ne (Get-FileHashValue (Join-Path $mainRoot $relative))) { throw "Shell-only apply changed unrelated file $relative." } }
    $repeat = (& $publisher -ShellPolicyOnly -TargetWorktrees 4 -TestFixtureRoot $modeRoot -PreviousReceipt $receiptPath -Apply | Out-String); if ($LASTEXITCODE -or $repeat -notmatch 'no backups or writes') { throw 'Repeated shell-only apply was not a zero-change run.' }
    'ShellPolicyOnly fixture CLI evidence:'; $dry.TrimEnd(); $apply.TrimEnd(); $repeat.TrimEnd()
    $bad = (Get-Content $selectedConfig -Raw | ConvertFrom-Json -AsHashtable); $bad.agents['implement'].permissions += @([ordered]@{action='shell';resource='*gradle* test bespoke';effect='deny'})
    [System.IO.File]::WriteAllText($selectedConfig,(ConvertTo-Json -InputObject $bad -Depth 100),[System.Text.UTF8Encoding]::new($false)); $badHash = Get-FileHashValue $selectedConfig
    $rejected = $false; try { & $publisher -ShellPolicyOnly -TargetWorktrees 4 -TestFixtureRoot $modeRoot -Apply | Out-Null } catch { $rejected = $true }
    if (-not $rejected -or (Get-FileHashValue $selectedConfig) -ne $badHash) { throw 'Unknown Gradle rule was not refused before writes.' }
    foreach ($argsBad in @(@('-TargetWorktrees',0),@('-TargetWorktrees',4,'-BindWorktrees'))) {
        $rejected = $false; try { & $publisher -ShellPolicyOnly @argsBad -TestFixtureRoot $modeRoot -Apply | Out-Null } catch { $rejected = $true }
        if (-not $rejected) { throw 'Invalid shell-only selection/conflicting mode flag was accepted.' }
    }
}
finally {
    if ($modeStage -and (Test-Path -LiteralPath $modeStage)) { Remove-Item -LiteralPath $modeStage -Recurse -Force }
    if ($modeBackup -and (Test-Path -LiteralPath $modeBackup)) { Remove-Item -LiteralPath $modeBackup -Recurse -Force }
    if (Test-Path -LiteralPath $modeRoot) { Remove-Item -LiteralPath $modeRoot -Recurse -Force }
}

# MCP-only fixture apply/reapply is isolated to a GUID temp root; preserve unrelated config and receipt ownership.
$mcpFixtureRoot = Join-Path $env:LOCALAPPDATA ('Temp\opencode\workflow-mcp-fixture-' + [guid]::NewGuid().ToString('N'))
$mcpFixtureMain = Join-Path $mcpFixtureRoot 'MoonStation14'; $mcpFixtureBackup = $null
try {
    foreach ($relative in $sharedSources) {
        $src = Join-Path $mainRoot ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        $dst = Join-Path $mcpFixtureMain ($relative.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        New-Item -ItemType Directory -Path (Split-Path -Parent $dst) -Force | Out-Null
        [System.IO.File]::WriteAllBytes($dst,[System.IO.File]::ReadAllBytes($src))
    }
    $fakeShared = Join-Path $mcpFixtureRoot 'MoonStation14-shared'
    $fakeAdapter = Join-Path $fakeShared 'tooling/agent_workflow/workflow_mcp.py'
    $fakePython = Join-Path $fakeShared '.venv-workflow-mcp/Scripts/python.exe'
    New-Item -ItemType Directory -Path (Split-Path -Parent $fakeAdapter) -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path -Parent $fakePython) -Force | Out-Null
    [System.IO.File]::WriteAllText($fakeAdapter,'# fixture adapter',[System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText($fakePython,'fixture sdk interpreter',[System.Text.UTF8Encoding]::new($false))
    $configBytes = [System.IO.File]::ReadAllBytes((Join-Path $mcpFixtureMain 'opencode.jsonc'))
    $sourceConfig = (Get-TextData $configBytes).Text | ConvertFrom-Json -AsHashtable
    $sourceConfig.mcp.servers['retained-server'] = [ordered]@{type='local';command=@('keep-existing')}
    $sourceConfig.mcp.servers.Remove('ms14_workflow')
    $sourceConfig.mcp.ms14_workflow = Get-WorkflowMcpEntry $fakeShared $mcpFixtureMain 'author' $true
    $mainConfigBytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $sourceConfig) + "`n")
    [System.IO.File]::WriteAllBytes((Join-Path $mcpFixtureMain 'opencode.jsonc'),$mainConfigBytes)
    $mcpRows = @(); $mcpRoots = @([pscustomobject]@{Name='main';Root=$mcpFixtureMain})
    foreach ($number in 1..5) {
        $name = 'MoonStation14-agent-{0:D2}' -f $number; $root = Join-Path $mcpFixtureRoot $name
        New-Item -ItemType Directory -Path $root -Force | Out-Null
        $targetConfig = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $sourceConfig -Depth 100)
        $targetConfig.mcp.Remove('ms14_workflow')
        $targetConfig.mcp.servers.Remove('ms14_workflow')
        if ($number -eq 5) {
            foreach ($role in @('orchestrate-ms14','implement','investigate','ss14-expert','review','3d-modeler','integration-lead','fidelity-reviewer')) {
                $targetConfig.agents[$role].permissions = @($targetConfig.agents[$role].permissions | Where-Object { $_.action -notlike 'ms14_workflow*' })
                foreach ($rule in (Get-ExpectedMcpPermissionTuples 'lead' $role)) { $targetConfig.agents[$role].permissions += [ordered]@{action=$rule.action;resource=$rule.resource;effect=$rule.effect} }
            }
        }
        $configBytes = [System.Text.UTF8Encoding]::new($false).GetBytes((ConvertTo-CanonicalConfigJson $targetConfig) + "`n")
        $config = Join-Path $root 'opencode.jsonc'; [System.IO.File]::WriteAllBytes($config,$configBytes)
        $receiptBaselineHash = Get-FileHashValue $config
        if ($number -in @(1,2,3,5)) {
            $drifted = ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject $targetConfig -Depth 100)
            $drifted.model = [ordered]@{model='user/custom-model';provider='user-provider'}
            $drifted.mcp['user-server'] = [ordered]@{type='local';command=@('user-server')}
            $drifted.agents['orchestrate-ms14'].permissions += @([ordered]@{action='edit';resource='custom/plan.md';effect='allow'})
            $drifted.agents['orchestrate-ms14'].user_extension = [ordered]@{preserve='baseline'}
            [System.IO.File]::WriteAllText($config,(ConvertTo-CanonicalConfigJson $drifted) + "`n",[System.Text.UTF8Encoding]::new($false))
        }
        $mcpRows += @{target=$name;path='opencode.jsonc';new_sha256=$receiptBaselineHash;deleted=$false}
        $mcpRoots += [pscustomobject]@{Name=$name;Root=$root}
    }
    $leadProfilePath = Join-Path $mcpFixtureRoot 'MoonStation14-agent-05/.opencode/agents/integration-lead.md'
    New-Item -ItemType Directory -Path (Split-Path -Parent $leadProfilePath) -Force | Out-Null
    $leadProfileBytes = $legacyLeadBytes
    $leadProfileReceiptHash = Get-FileHashValue (Join-Path $mcpFixtureMain '.opencode/agents/integration-lead.md')
    [System.IO.File]::WriteAllBytes($leadProfilePath,$leadProfileBytes)
    $mcpRows += @{target='MoonStation14-agent-05';path='.opencode/agents/integration-lead.md';new_sha256=$leadProfileReceiptHash;deleted=$false}
    $mcpFixtureOwnedPlans = @($mcpRoots | ForEach-Object { [pscustomobject]@{Target=$_.Name;Relative='opencode.jsonc';Path=(Join-Path $_.Root 'opencode.jsonc')} })
    $mcpFixtureOwnedPlans += [pscustomobject]@{Target='MoonStation14-agent-05';Relative='.opencode/agents/integration-lead.md';Path=$leadProfilePath}
    $mainConfigPath = Join-Path $mcpFixtureMain 'opencode.jsonc'
    $mcpRows += @{target='main';path='opencode.jsonc';new_sha256=(Get-FileHashValue $mainConfigPath);deleted=$false}
    $mcpRows += @{target='shared';path='retained.txt';new_sha256='retained-hash';deleted=$false}
    $mcpRows += @{target='shared';path='tooling/agent_workflow/workflow_mcp.py';new_sha256=(Get-FileHashValue $fakeAdapter);deleted=$false}
    $mcpRows += @{target='MoonStation14-agent-02';path='preserved-custom-owner.txt';new_sha256='custom-owner-hash';deleted=$false}
    $mcpPrevious = Join-Path $mcpFixtureRoot 'previous-receipt.json'
    [System.IO.File]::WriteAllText($mcpPrevious,(@{main_root=$mcpFixtureMain;shared_root=$fakeShared;source_hashes=@{retained='source'};files=$mcpRows} | ConvertTo-Json -Depth 20),[System.Text.UTF8Encoding]::new($false))
    $racePath = Join-Path $mcpFixtureRoot 'concurrent-edit-fixture.txt'
    [System.IO.File]::WriteAllText($racePath,'planned baseline',[System.Text.UTF8Encoding]::new($false))
    $currentPath = Join-Path $mcpFixtureRoot 'already-current-config.txt'
    [System.IO.File]::WriteAllText($currentPath,'already current',[System.Text.UTF8Encoding]::new($false))
    $racePlans = @(
        [pscustomobject]@{Target='fixture';Relative='concurrent-edit-fixture.txt';Path=$racePath;OldHash=(Get-FileHashValue $racePath);Action='replace'},
        [pscustomobject]@{Target='fixture';Relative='already-current-config.txt';Path=$currentPath;OldHash=(Get-FileHashValue $currentPath);Action='up-to-date'}
    )
    [System.IO.File]::WriteAllText($currentPath,'concurrent edit to up-to-date target',[System.Text.UTF8Encoding]::new($false))
    $raceRejected = $false; try { Assert-McpPlanUnchanged $racePlans } catch { $raceRejected = $true }
    if (-not $raceRejected -or [System.IO.File]::ReadAllText($racePath) -cne 'planned baseline' -or [System.IO.File]::ReadAllText($currentPath) -cne 'concurrent edit to up-to-date target' -or (Test-Path (Join-Path $mcpFixtureRoot 'new-receipt.json'))) { throw 'MCP apply preflight failed to refuse a concurrent edit to an up-to-date plan without writes.' }
    $applyMcp = (& $publisher -WorkflowMcpOnly -TestFixtureRoot $mcpFixtureRoot -PreviousReceipt $mcpPrevious -Apply | Out-String)
    if ($LASTEXITCODE -or $applyMcp -notmatch 'Applied 7 scoped workflow MCP files') { throw 'MCP-only isolated fixture apply failed.' }
    if ($applyMcp -notmatch 'Backup and receipt: (.+)') { throw 'MCP-only apply omitted its receipt path.' }
    $mcpFixtureBackup = $Matches[1].Trim(); $mcpReceipt = Join-Path $mcpFixtureBackup 'publish-receipt.json'
    $mergedReceipt = Get-Content $mcpReceipt -Raw | ConvertFrom-Json
    if (-not @($mergedReceipt.files | Where-Object { $_.target -eq 'shared' -and $_.path -eq 'retained.txt' }).Count -or $mergedReceipt.source_hashes.retained -ne 'source') { throw 'MCP-only receipt discarded prior ownership metadata.' }
    foreach ($target in $mcpRoots) {
        $cfg = (Get-Content (Join-Path $target.Root 'opencode.jsonc') -Raw | ConvertFrom-Json -AsHashtable)
        if (-not $cfg.mcp.servers.Contains('retained-server')) { throw "MCP-only binding replaced another server in $($target.Name)." }
        $expectedModel = if ($target.Name -in @('MoonStation14-agent-01','MoonStation14-agent-02','MoonStation14-agent-03','MoonStation14-agent-05')) {[ordered]@{model='user/custom-model';provider='user-provider'}} else {$sourceConfig.model}
        if ((ConvertTo-CanonicalConfigJson $cfg.model) -cne (ConvertTo-CanonicalConfigJson $expectedModel)) { throw "MCP-only binding changed model settings in $($target.Name)." }
        if ($target.Name -in @('MoonStation14-agent-01','MoonStation14-agent-02','MoonStation14-agent-03','MoonStation14-agent-05')) {
            if (-not $cfg.mcp.Contains('user-server') -or $cfg.agents['orchestrate-ms14'].user_extension.preserve -cne 'baseline' -or -not @($cfg.agents['orchestrate-ms14'].permissions | Where-Object resource -eq 'custom/plan.md').Count) { throw "MCP-only merge failed to preserve unrelated baseline fields in $($target.Name)." }
        }
        $expectedProfile = if ($target.Name -eq 'MoonStation14-agent-05') {'lead'} else {'author'}
        if ($cfg.mcp.servers.ms14_workflow.command[-1] -cne $expectedProfile -or $cfg.mcp.Contains('ms14_workflow')) { throw "MCP profile/root path mismatch in $($target.Name)." }
    }
    $leadProfileText = [System.IO.File]::ReadAllText($leadProfilePath)
    if ($leadProfileText -notmatch '(?m)^  ms14_workflow_validate: allow$' -or $leadProfileText -match '(?m)^\s+"\*workflow\.py\*gate-validate\*": allow$') { throw 'Central lead profile did not migrate to MCP-only tool permission.' }
    foreach ($preserve in @('model: user/custom-lead-model','reasoningEffort: high','description: Custom lead description; preserve me.','Custom body paragraph. Preserve exactly.')) { if (-not $leadProfileText.Contains($preserve)) { throw "Applied MCP merge changed unowned lead profile content: $preserve" } }
    $mcpConfigHashes = @{}
    foreach ($plan in $mcpFixtureOwnedPlans) { $mcpConfigHashes["$($plan.Target)|$($plan.Relative)"] = Get-FileHashValue $plan.Path }
    $ordinaryApply = (& $publisher -PreviousReceipt $mcpReceipt -TestFixtureRoot $mcpFixtureRoot -Apply | Out-String)
    if ($LASTEXITCODE -or $ordinaryApply -notmatch 'Published .* managed changes' -or $ordinaryApply -notmatch 'Backup and receipt: (.+)') { throw 'Ordinary shared publication did not apply from the MCP receipt fixture.' }
    $ordinaryBackup = $Matches[1].Trim(); $ordinaryReceiptPath = Join-Path $ordinaryBackup 'publish-receipt.json'
    $ordinaryReceipt = Get-Content $ordinaryReceiptPath -Raw | ConvertFrom-Json
    foreach ($plan in $mcpFixtureOwnedPlans) {
        $rows = @($ordinaryReceipt.files | Where-Object { $_.target -eq $plan.Target -and $_.path -eq $plan.Relative })
        if ($rows.Count -ne 1 -or $rows[0].new_sha256 -cne $mcpConfigHashes["$($plan.Target)|$($plan.Relative)"] -or (Get-FileHashValue $plan.Path) -cne $rows[0].new_sha256) { throw "Ordinary publication failed to preserve current MCP ownership row $($plan.Target)/$($plan.Relative)." }
    }
    foreach ($sentinel in @(@{target='shared';path='retained.txt';hash='retained-hash'},@{target='MoonStation14-agent-02';path='preserved-custom-owner.txt';hash='custom-owner-hash'})) {
        $rows = @($ordinaryReceipt.files | Where-Object { $_.target -eq $sentinel.target -and $_.path -eq $sentinel.path })
        if ($rows.Count -ne 1 -or $rows[0].new_sha256 -cne $sentinel.hash) { throw "Normal receipt merge discarded/duplicated untouched ownership row $($sentinel.target)/$($sentinel.path)." }
    }
    $duplicateReceiptRows = @($ordinaryReceipt.files | Group-Object { "$($_.target)|$($_.path)" } | Where-Object Count -gt 1)
    if ($duplicateReceiptRows.Count) { throw 'Normal receipt merge duplicated ownership rows for planned/shared paths.' }
    $beforeOwnershipRepair = @{}; foreach ($plan in $mcpFixtureOwnedPlans) { $beforeOwnershipRepair["$($plan.Target)|$($plan.Relative)"] = Get-FileHashValue $plan.Path }
    $simulatedMissingMainRow = Join-Path $mcpFixtureRoot 'ordinary-receipt-missing-main-row.json'
    $repairSeed = Get-Content $ordinaryReceiptPath -Raw | ConvertFrom-Json
    $repairSeed.files = @($repairSeed.files | Where-Object { -not ($_.target -eq 'main' -and $_.path -eq 'opencode.jsonc') })
    [System.IO.File]::WriteAllText($simulatedMissingMainRow,($repairSeed | ConvertTo-Json -Depth 30),[System.Text.UTF8Encoding]::new($false))
    $receiptRepair = (& $publisher -WorkflowMcpOnly -TestFixtureRoot $mcpFixtureRoot -PreviousReceipt $simulatedMissingMainRow -Apply | Out-String)
    if ($LASTEXITCODE -or $receiptRepair -notmatch 'changed managed files: 0' -or $receiptRepair -notmatch 'ownership rows to reconcile: 1' -or $receiptRepair -notmatch 'Reconciled 1 MCP receipt ownership rows; 0 configuration files written') { throw 'MCP-only missing-main-row recovery did not produce a receipt-only repair.' }
    if ($receiptRepair -notmatch 'Receipt: (.+)') { throw 'MCP-only receipt repair omitted the new receipt path.' }
    $repairedReceiptPath = Join-Path $Matches[1].Trim() 'publish-receipt.json'
    $repairedReceipt = Get-Content $repairedReceiptPath -Raw | ConvertFrom-Json
    foreach ($plan in $mcpFixtureOwnedPlans) {
        if ((Get-FileHashValue $plan.Path) -cne $beforeOwnershipRepair["$($plan.Target)|$($plan.Relative)"]) { throw 'Receipt-only repair changed an MCP config/profile file.' }
    }
    $mainRows = @($repairedReceipt.files | Where-Object { $_.target -eq 'main' -and $_.path -eq 'opencode.jsonc' })
    if ($mainRows.Count -ne 1 -or $mainRows[0].new_sha256 -cne (Get-FileHashValue $mainConfigPath)) { throw 'Receipt repair did not record the actual current MAIN config hash exactly once.' }
    $repeatMcp = (& $publisher -WorkflowMcpOnly -TestFixtureRoot $mcpFixtureRoot -PreviousReceipt $repairedReceiptPath -Apply | Out-String)
    if ($LASTEXITCODE -or $repeatMcp -notmatch 'ownership rows to reconcile: 0' -or $repeatMcp -notmatch 'bindings and receipt ownership already current; no backups or writes') { throw 'MCP-only reapply after ordinary publication/receipt repair was not idempotent.' }
    $beforeMissingSdk = Get-FileHashValue $mainConfigPath
    Remove-Item -LiteralPath $fakePython -Force
    $missingSdkRejected = $false; try { & $publisher -WorkflowMcpOnly -TestFixtureRoot $mcpFixtureRoot -PreviousReceipt $repairedReceiptPath -Apply | Out-Null } catch { $missingSdkRejected = $true }
    if (-not $missingSdkRejected -or (Get-FileHashValue $mainConfigPath) -cne $beforeMissingSdk) { throw 'MCP activation did not refuse missing SDK before modifying configs.' }
    [System.IO.File]::WriteAllText((Join-Path $mcpFixtureRoot 'MoonStation14-agent-02/opencode.jsonc'),'{ malformed',[System.Text.UTF8Encoding]::new($false))
    $malformedRejected = $false; try { & $publisher -WorkflowMcpOnly -TestFixtureRoot $mcpFixtureRoot -PreviousReceipt $repairedReceiptPath | Out-Null } catch { $malformedRejected = $true }
    if (-not $malformedRejected) { throw 'MCP-only binding accepted malformed JSON config.' }
}
finally {
    if ($mcpFixtureBackup -and (Test-Path -LiteralPath $mcpFixtureBackup)) { Remove-Item -LiteralPath $mcpFixtureBackup -Recurse -Force }
    if (Test-Path -LiteralPath $mcpFixtureRoot) { Remove-Item -LiteralPath $mcpFixtureRoot -Recurse -Force }
}
'Workflow MCP fixtures passed: adapter payload, isolated scoped apply/reapply, role tool permissions, preservation, malformed/unowned refusal, and missing-SDK refusal.'

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
