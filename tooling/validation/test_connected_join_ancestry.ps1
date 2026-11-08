$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'connected_join.ps1'
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw "connected_join.ps1 parse failed: $($errors[0])" }
foreach ($name in 'Convert-CimCreationDate','Test-ProcessIdentity','Get-ProcessObservation','Get-IdentityKey','Register-Root','Get-VerifiedDescendants','Track-Tree','Write-OwnedProof') {
    $function = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq $name }, $true)
    if (!$function) { throw "Missing function $name" }
    Invoke-Expression $function.Extent.Text
}
function New-Process([int]$Id,[int]$Parent,[int]$Second) {
    [pscustomobject]@{ ProcessId=$Id; ParentProcessId=$Parent; CreationDate=[datetime]::new(2026,1,1,0,0,$Second,[DateTimeKind]::Utc); Name='synthetic.exe' }
}
$root = [pscustomobject]@{ Id=10; Created=[datetime]::new(2026,1,1,0,0,10,[DateTimeKind]::Utc); IsRoot=$true }
$same = New-Process 10 1 10
if (!(Test-ProcessIdentity $same $root)) { throw 'Matching normalized identity rejected.' }
if (Test-ProcessIdentity $null $root) { throw 'Null process accepted.' }
if (Test-ProcessIdentity ( [pscustomobject]@{ ProcessId=10; CreationDate='bad' } ) $root) { throw 'Malformed creation date accepted.' }
if (Test-ProcessIdentity ( [pscustomobject]@{ ProcessId=10; CreationDate=$null } ) $root) { throw 'Missing creation date accepted.' }
if (Test-ProcessIdentity $same ([pscustomobject]@{ Id=10; Created='bad' })) { throw 'Malformed identity date accepted.' }
if (Test-ProcessIdentity (New-Process 99 1 10) $root) { throw 'Wrong PID with matching creation date accepted.' }
if (Test-ProcessIdentity (New-Process 10 1 11) $root) { throw 'Wrong creation date accepted.' }
if ((Get-ProcessObservation $null $root) -ne 'GONE') { throw 'Null CIM record was not classified GONE.' }
if ((Get-ProcessObservation $same $root) -ne 'MATCHED_OWNED') { throw 'Exact identity was not MATCHED_OWNED.' }
if ((Get-ProcessObservation (New-Process 99 1 10) $root) -ne 'REUSED') { throw 'Valid different PID was not REUSED.' }
if ((Get-ProcessObservation (New-Process 10 1 11) $root) -ne 'REUSED') { throw 'Valid different creation date was not REUSED.' }
if ((Get-ProcessObservation ([pscustomobject]@{ ProcessId=10; CreationDate='bad' }) $root) -ne 'UNKNOWN') { throw 'Malformed live creation date was not UNKNOWN.' }
if ((Get-ProcessObservation ([pscustomobject]@{ ProcessId='bad'; CreationDate=$same.CreationDate }) $root) -ne 'UNKNOWN') { throw 'Malformed live PID was not UNKNOWN.' }
if ((Get-ProcessObservation ([pscustomobject]@{ ProcessId=10; CreationDate=$null }) $root) -ne 'UNKNOWN') { throw 'Missing live creation date was not UNKNOWN.' }
if ((Get-ProcessObservation $same $null) -ne 'UNKNOWN') { throw 'Missing identity was not UNKNOWN.' }
$normal = @( (New-Process 10 1 10), (New-Process 11 10 11), (New-Process 12 11 12) )
$valid = @(Get-VerifiedDescendants -Snapshot $normal -RootIdentity $root)
if ($valid.Count -ne 3) { throw "Valid ancestry rejected (count=$($valid.Count))." }
if (@(Get-VerifiedDescendants -Snapshot @((New-Process 10 1 10),(New-Process 11 10 9)) -RootIdentity $root).Count -ne 1) { throw 'Stale child accepted.' }
if (@(Get-VerifiedDescendants -Snapshot @((New-Process 10 1 10),(New-Process 11 10 11),(New-Process 12 11 10)) -RootIdentity $root).Count -ne 2) { throw 'Stale grandchild accepted.' }
if (@(Get-VerifiedDescendants -Snapshot @((New-Process 10 1 11),(New-Process 11 10 12)) -RootIdentity $root).Count -ne 0) { throw 'Reused root PID accepted.' }
if (@(Get-VerifiedDescendants -Snapshot @((New-Process 10 1 10),(New-Process 11 10 10)) -RootIdentity $root).Count -ne 2) { throw 'Same-time parent/child rejected.' }
$missing = New-Process 11 10 11; $missing.CreationDate=$null
if (@(Get-VerifiedDescendants -Snapshot @((New-Process 10 1 10),$missing) -RootIdentity $root).Count -ne 1) { throw 'Missing creation date accepted.' }
if (@(Get-VerifiedDescendants -Snapshot @((New-Process 11 12 11),(New-Process 12 11 12)) -RootIdentity $root).Count -ne 0) { throw 'Unrelated cycle accepted.' }
$duplicates = @((New-Process 10 1 10),(New-Process 11 10 11),(New-Process 11 10 12),(New-Process 11 10 13),(New-Process 12 11 14))
if (@(Get-VerifiedDescendants -Snapshot $duplicates -RootIdentity $root).Count -ne 1) { throw 'Duplicate PID or its descendant was admitted.' }
$currentRoot = [pscustomobject]@{ Id=1; Created=[datetime]::new(2026,1,1,0,0,20,[DateTimeKind]::Utc); IsRoot=$true; Phase='server'; ObservedUtc=[datetime]::UtcNow }
$oldRoot = [pscustomobject]@{ Id=1; Created=[datetime]::new(2026,1,1,0,0,10,[DateTimeKind]::Utc); IsRoot=$true; Phase='preparation'; ObservedUtc=[datetime]::UtcNow }
$snapshotForTrack = @((New-Process 1 0 20),(New-Process 2 1 21),(New-Process 3 2 22))
function Get-Descendants($RootIdentity) { Get-VerifiedDescendants -Snapshot $script:snapshotForTrack -RootIdentity $RootIdentity }
$script:owned = @{}
$script:roots = @{}
$script:owned[(Get-IdentityKey $oldRoot)] = $oldRoot
$oldParent = [pscustomobject]@{ Id=2; Created=[datetime]::new(2026,1,1,0,0,11,[DateTimeKind]::Utc); IsRoot=$false; ParentId=1; ParentCreated=$oldRoot.Created; RootId=1; RootCreated=$oldRoot.Created; Phase='preparation'; ObservedUtc=[datetime]::UtcNow }
$script:owned[(Get-IdentityKey $oldParent)] = $oldParent
$script:roots[(Get-IdentityKey $currentRoot)] = $currentRoot
Track-Tree $currentRoot
if ($script:owned.Count -ne 4) { throw 'PID reuse overwrote captured identity records.' }
$newParent = $script:owned[(Get-IdentityKey ([pscustomobject]@{Id=2;Created=[datetime]::new(2026,1,1,0,0,21,[DateTimeKind]::Utc)}))]
$newChild = $script:owned[(Get-IdentityKey ([pscustomobject]@{Id=3;Created=[datetime]::new(2026,1,1,0,0,22,[DateTimeKind]::Utc)}))]
if (!$newParent -or $newParent.ParentCreated -ne $currentRoot.Created -or !$newChild -or $newChild.ParentCreated -ne $newParent.Created) { throw 'Tracked ancestry used stale parent identity.' }
if ($script:owned[(Get-IdentityKey $oldParent)].Created -ne $oldParent.Created) { throw 'Old parent identity was mutated.' }
if ((Get-ProcessObservation (New-Process 2 1 21) $oldParent) -ne 'REUSED' -or (Get-ProcessObservation (New-Process 2 1 21) $newParent) -ne 'MATCHED_OWNED') { throw 'Reused PID cleanup classification is not identity-specific.' }
if (@(Get-VerifiedDescendants -Snapshot $snapshotForTrack -RootIdentity $oldRoot).Count -ne 0) { throw 'Stale captured root attached to reused PID.' }
$proofDir = Join-Path ([IO.Path]::GetTempPath()) ('ms14-owned-proof-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $proofDir | Out-Null
try {
    $root | Add-Member -NotePropertyName Phase -NotePropertyValue 'preparation'
    $root | Add-Member -NotePropertyName ObservedUtc -NotePropertyValue ([datetime]::UtcNow)
    $script:owned = @{ 10=$root; 11=[pscustomobject]@{ Id=11; Created=[datetime]::new(2026,1,1,0,0,11,[DateTimeKind]::Utc); IsRoot=$false; ParentId=10; ParentCreated=$root.Created; RootId=10; RootCreated=$root.Created; Phase='server'; ObservedUtc=[datetime]::UtcNow } }
    $script:cleanupOutcomes = @{ '10'=@{ state='gone'; stopAttempted=$true; stopResult='success' } }
    $script:ownedProofPath = Join-Path $proofDir 'owned-processes.json'
    Write-OwnedProof 'cleanup-complete'
    $saved = Get-Content -LiteralPath $script:ownedProofPath -Raw | ConvertFrom-Json
    if ($saved.cleanupState -ne 'cleanup-complete' -or $saved.processes.Count -ne 2) { throw 'Ownership proof record incomplete.' }
    $child = $saved.processes | Where-Object pid -eq 11
    if ($child.phase -ne 'server' -or $child.parentPid -ne 10 -or !$child.parentCreatedUtc -or !$child.rootCreatedUtc) { throw 'Ancestry/phase proof missing.' }
    if ($saved.processes | Where-Object { $_.PSObject.Properties.Name -match 'command|environment' }) { throw 'Sensitive process details unexpectedly persisted.' }
} finally { Remove-Item -LiteralPath $proofDir -Recurse -Force }
'CONNECTED_JOIN_ANCESTRY_TESTS_PASS'
