$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'connected_join.ps1'
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw "connected_join.ps1 parse failed: $($errors[0])" }
foreach ($name in 'Convert-CimCreationDate','Test-ProcessIdentity','Get-ProcessObservation','Get-IdentityKey','Stop-OwnedTree') {
    $function = $ast.Find({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq $name }, $true)
    if (!$function) { throw "Missing production function $name" }
    Invoke-Expression $function.Extent.Text
}
$script:identity = [pscustomobject]@{ Id=17384; Created=[datetime]::new(2026,1,1,0,0,10,[DateTimeKind]::Utc); IsRoot=$true; ParentId=1; ParentCreated=[datetime]::new(2026,1,1); RootId=17384; RootCreated=[datetime]::new(2026,1,1,0,0,10,[DateTimeKind]::Utc) }
$script:owned = @{ owned=$script:identity }
function Get-IdentityKey($i) { 'owned' }
function Get-CimInstance {
    if ($script:observations.Count) { $script:lastObservation = $script:observations.Dequeue() }
    if ($script:lastObservation -is [System.Management.Automation.ErrorRecord]) { throw $script:lastObservation }
    if ($script:lastObservation -eq 'QUERY_ERROR') { throw [UnauthorizedAccessException]::new('CIM query denied') }
    return $script:lastObservation
}
function Stop-Process { $script:kills++; if ($script:stopMode -eq 'notfound') { $e=[System.Management.Automation.ErrorRecord]::new([InvalidOperationException]::new('Cannot find process identifier'), 'ProcessNotFound', [System.Management.Automation.ErrorCategory]::ObjectNotFound, $null); throw $e }; throw [UnauthorizedAccessException]::new('Access denied') }
function Start-Sleep {}
function Run-Case($mode,$after,$expected,$expectedKills) {
    $script:observations = [collections.generic.queue[object]]::new()
    $script:observations.Enqueue([pscustomobject]@{ProcessId=17384;CreationDate=$script:identity.Created})
    if ($after -eq 'gone') {$script:observations.Enqueue($null)}
    elseif ($after -eq 'queryerror') {$script:observations.Enqueue('QUERY_ERROR')}
    else {$created=if($after -eq 'reused'){$script:identity.Created.AddSeconds(1)}else{$script:identity.Created};$script:observations.Enqueue([pscustomobject]@{ProcessId=17384;CreationDate=$created}); if($after -eq 'still'){$script:observations.Enqueue($null)}}
    $script:stopMode=$mode; $script:kills=0; $script:cleanupOutcomes=@{owned=@{state='initial'}}
    $failed=$false; try { Stop-OwnedTree } catch { $failed=$true }
    if ($failed -ne ($expected -eq 'fail') -or $script:kills -ne $expectedKills) { throw "Race case mode=$mode after=$after failed=$failed kills=$($script:kills) outcome=$($script:cleanupOutcomes.owned.state) stopErr=$($script:cleanupOutcomes.owned.error)" }
}
Run-Case notfound gone pass 1
Run-Case notfound reused pass 1
Run-Case notfound still fail 1
Run-Case notfound queryerror fail 1
Run-Case denied gone fail 1
Run-Case denied reused fail 1
Run-Case denied queryerror fail 1
'CONNECTED_JOIN_CLEANUP_RACE_TESTS_PASS cases=7'
