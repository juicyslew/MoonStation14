$ErrorActionPreference = 'Stop'

$publisher = Join-Path $PSScriptRoot 'publish_workflow.ps1'
$tokens = $null; $parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($publisher, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | ForEach-Object ToString | Out-String) }
$needed = @('Get-Sha256','Get-FileHashValue','Get-TextData','ConvertTo-TextBytes','ConvertTo-CanonicalConfigJson','Get-CanonicalConfigHash','Add-Rule','Get-WorkflowSection','Set-WorkflowSection','Assert-NoReparsePath','Get-ReceiptHash','Assert-ReceiptMatch','Get-TailoredProjectConfig','Get-TailoredIntegrationProfile','Get-TailoredFidelityProfile')
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
$authorProfile = (Get-TextData (Get-TailoredIntegrationProfile $integrationSource $workspaceParent 1)).Text
$centralProfile = (Get-TextData (Get-TailoredIntegrationProfile $integrationSource $workspaceParent 5)).Text
if ($authorProfile -match 'MoonStation14-agent-0[1-4]' -or $authorProfile -match 'Documents/Minecraft Mod Dev Files/MoonStation14/\*\*') {
    throw 'Noncentral integration profile has a peer-worktree grant.'
}
foreach ($number in 1..4) {
    if ($centralProfile -notmatch ('MoonStation14-agent-{0:D2}/\*\*' -f $number)) { throw "Central profile is missing assigned tree {0:D2}." -f $number }
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
    'tooling/agent_workflow/workflow.py','tooling/agent_workflow/validation_evidence.py','tooling/agent_workflow/gate_dispatch.py',
    'docs/development-workflow/README.md','docs/development-workflow/CLI.md','docs/development-workflow/coordinator.md',
    'docs/development-workflow/source-fidelity.md','docs/development-workflow/request-template.json',
    'docs/development-workflow/review-report.md','docs/development-workflow/HANDOFF.md',
    'docs/development-workflow/roles/integration-lead.md','docs/development-workflow/roles/fidelity-reviewer.md',
    '.opencode/agents/integration-lead.md','.opencode/agents/fidelity-reviewer.md',
    'tooling/agent_workflow/workflow_entry.py','AGENTS.md','opencode.jsonc'
)
$deleteOnBind = @(
    'tooling/agent_workflow/validation_evidence.py','tooling/agent_workflow/gate_dispatch.py',
    'tooling/agent_workflow/test_workflow.py','tooling/agent_workflow/test_validation_evidence.py',
    'tooling/agent_workflow/test_gate_dispatch.py','docs/development-workflow/CLI.md',
    'docs/development-workflow/coordinator.md','docs/development-workflow/source-fidelity.md',
    'docs/development-workflow/request-template.json','docs/development-workflow/review-report.md',
    'docs/development-workflow/HANDOFF.md'
)
try {
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
    Push-Location $fixtureRoot
    try { $run = & $python.Source $shimPath --repo kept-relative caller-arg }
    finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw "Fixture shim returned $LASTEXITCODE" }
    $result = ($run | Out-String | ConvertFrom-Json)
    Assert-Equal ([System.IO.Path]::GetFullPath($fixtureRoot)) $result.cwd 'Shim changed caller working directory.'
    if (($result.argv -join ' ') -cne '--repo kept-relative caller-arg') { throw 'Shim changed CLI arguments.' }
}
finally {
    if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}

'Shared publisher fixtures passed: one shared runtime, wrapper context preservation, AGENTS prefix/suffix, scoped grants, and receipt conflict handling.'
