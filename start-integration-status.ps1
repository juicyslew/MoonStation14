param([switch]$Status, [switch]$Open)
$ErrorActionPreference = 'Stop'
$python = Join-Path $PSScriptRoot 'tooling/agent_workflow/workflow_service.py'
$record = Join-Path $PSScriptRoot '.opencode/workflow/current-installation.json'
if (-not (Test-Path -LiteralPath $record)) { throw 'Current workflow installation record is missing.' }
$manifest = Get-Content -LiteralPath $record -Raw | ConvertFrom-Json
$receiptBytes = [System.IO.File]::ReadAllBytes([string]$manifest.receipt)
$sha = [System.Security.Cryptography.SHA256]::Create()
try { $digest = ([BitConverter]::ToString($sha.ComputeHash($receiptBytes))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
if ($digest -ne [string]$manifest.receipt_sha256) { throw 'Current workflow installation receipt hash mismatch.' }
$receipt = Get-Content -LiteralPath $manifest.receipt -Raw | ConvertFrom-Json
$shared = $receipt.shared_root
$interpreter = Join-Path $shared '.venv-workflow-coordinator/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $interpreter)) { throw 'Installed workflow interpreter is missing.' }
$arguments = @($python, '--repo', $PSScriptRoot)
if ($Status) { $arguments += '--status' }
if ($Open) { $arguments += '--open' }
& $interpreter @arguments
exit $LASTEXITCODE
