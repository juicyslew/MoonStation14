[CmdletBinding()]
param(
    [string] $SharedRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\MoonStation14-shared')),
    [string] $Python = 'python'
)

$ErrorActionPreference = 'Stop'
$shared = [System.IO.Path]::GetFullPath($SharedRoot)
$requirements = Join-Path $shared 'tooling/agent_workflow/requirements-workflow-mcp.txt'
$adapter = Join-Path $shared 'tooling/agent_workflow/workflow_mcp.py'
$venv = Join-Path $shared '.venv-workflow-mcp'
$venvPython = Join-Path $venv 'Scripts/python.exe'

if (-not (Test-Path -LiteralPath $shared -PathType Container)) { throw "Shared runtime is missing: $shared" }
if (-not (Test-Path -LiteralPath $requirements -PathType Leaf)) { throw "Pinned MCP requirements are missing: $requirements" }
if (-not (Test-Path -LiteralPath $adapter -PathType Leaf)) { throw "Published workflow MCP adapter is missing: $adapter" }
$requirementLines = @(Get-Content -LiteralPath $requirements | Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and -not $_.Trim().StartsWith('#') })
if ($requirementLines.Count -ne 1 -or $requirementLines[0].Trim() -cne 'mcp==1.26.0') {
    throw 'Workflow MCP requirements must pin exactly mcp==1.26.0; refusing an unreviewed dependency change.'
}
if (-not (Test-Path -LiteralPath $venvPython -PathType Leaf)) {
    & $Python -m venv $venv
    if ($LASTEXITCODE -ne 0) { throw "Python venv creation failed with exit $LASTEXITCODE" }
}
& $venvPython -m pip install --disable-pip-version-check -r $requirements
if ($LASTEXITCODE -ne 0) { throw "Pinned MCP SDK installation failed with exit $LASTEXITCODE" }
$installed = (& $venvPython -c "from importlib.metadata import version; print(version('mcp'))").Trim()
if ($LASTEXITCODE -ne 0 -or $installed -cne '1.26.0') { throw "Expected MCP SDK 1.26.0, found '$installed'." }
& $venvPython -c "import mcp.server, mcp.server.stdio; print('MCP SDK ' + '$installed' + ' ready')"
if ($LASTEXITCODE -ne 0) { throw 'Installed MCP SDK failed import verification.' }
"Shared root: $shared"
"Workflow MCP interpreter: $venvPython"
