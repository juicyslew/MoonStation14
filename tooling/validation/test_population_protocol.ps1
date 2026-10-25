$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'connected_join.ps1'
$parameterPath = Join-Path $PSScriptRoot 'connected_join_parameters.ps1'
$tokens = $null; $errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw "Launcher parse failed: $($errors[0].Message)" }
$functionAst = $ast.Find({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Test-PopulationCompletion' }, $true)
if (!$functionAst) { throw 'Launcher has no Test-PopulationCompletion function.' }
$testCompletion = [scriptblock]::Create($functionAst.Extent.Text)
. $testCompletion
foreach ($name in @('Get-PopulationTerminalFailure','Format-PrimaryFailure','Get-PopulationProofPath','Copy-PopulationEvidence','Test-PopulationBootstrapProof')) { $found=$ast.Find({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name }, $true); if (!$found) { throw "Missing launcher helper $name" }; . ([scriptblock]::Create($found.Extent.Text)) }
if ((Format-PrimaryFailure 'primary-cause' @('cleanup issue')) -notmatch 'primary-cause') { throw 'Secondary failure hid primary cause.' }
if ((Get-PopulationTerminalFailure 'Animal population diagnostic aborted: obstructed' '') -notmatch 'obstructed') { throw 'Terminal abort not detected.' }
if ((Get-PopulationTerminalFailure 'MS14_ANIMAL_POPULATION_RECORDING_FAILED role=server reason=oops' '') -notmatch 'oops') { throw 'Recording failure not detected.' }
if (!(Test-PopulationBootstrapProof 'server boot MS14_STARTER_STATION_DISABLED reason=run-local property ms14.starterStation.enabled=false')) { throw 'Current server opt-out marker was not recognized.' }
if (Test-PopulationBootstrapProof 'server boot MS14_STARTER_STATION_DISABLED reason=run-local property ms14.starterStation.enabled=true') { throw 'Wrong starter-station property value accepted.' }
if (Test-PopulationBootstrapProof 'unrelated log ms14.starterStation.enabled=false') { throw 'Property text without the runtime marker accepted.' }
$bootstrapScript = Get-Content -LiteralPath $scriptPath -Raw
. $parameterPath
$serverPath = 'C:\capture files\server.json'; $clientPath = 'C:\capture files\client.json'; $outputPath = 'C:\capture files\summary.json'
[string[]]$analyzerArgs = New-PopulationAnalyzerArguments $serverPath $clientPath 'mixed' 'stationary' $true $outputPath
$flagIndexes = @([Array]::FindAll([string[]]$analyzerArgs, [Predicate[string]]{ param($arg) $arg -eq '--require-flyer-pose' }))
if ($flagIndexes.Count -ne 1 -or $analyzerArgs -notcontains '--require-flyer-pose') { throw 'Flyer-pose option was not emitted as exactly one argument token.' }
if ($analyzerArgs -notcontains $serverPath -or $analyzerArgs -notcontains $clientPath -or $analyzerArgs -notcontains $outputPath) { throw 'Analyzer argument builder did not preserve paths containing spaces.' }
[string[]]$withoutFlyer = New-PopulationAnalyzerArguments $serverPath $clientPath 'mixed' 'stationary' $false $outputPath
if ($withoutFlyer -contains '--require-flyer-pose') { throw 'Disabled flyer-pose option was emitted.' }
Assert-ConnectedJoinParameters 900 300 $true $true
Assert-ConnectedJoinParameters 120 300 $false $false
Assert-ConnectedJoinParameters 120 300 $true $false
Assert-ConnectedJoinParameters 120 300 $true $false $true 'Mixed'
foreach ($case in @(@{scenario=$false;name='';flag=$true},@{scenario=$true;name='Parrots';flag=$true})) {
    $rejected=$false; try { Assert-ConnectedJoinParameters 120 300 $case.scenario $false $case.flag $case.name } catch { $rejected=$true }
    if (!$rejected) { throw 'Invalid flyer-pose diagnostic admission accepted.' }
}
foreach ($case in @(@{wall=120;sustained=$true;scenario=$true},@{wall=900;sustained=$false;scenario=$true},@{wall=900;sustained=$true;scenario=$false},@{wall=120;sustained=$true;scenario=$false})) {
    $rejected = $false
    try { Assert-ConnectedJoinParameters $case.wall 300 $case.scenario $case.sustained } catch { $rejected = $true }
    if (!$rejected) { throw "Invalid launcher parameter policy accepted: $($case | ConvertTo-Json -Compress)" }
}
if ($bootstrapScript -notmatch 'Assert-ConnectedJoinParameters \$WallTimeoutSeconds \$PreparationTimeoutSeconds \$populationMode') { throw 'Launcher does not execute parameter admission policy.' }
if ($bootstrapScript -notmatch 'Pms14AnimalPopulationSustained=true') { throw 'Sustained launcher parameter is not wired.' }
if ($bootstrapScript -notmatch 'if \(\$populationMode -and !\(Test-PopulationBootstrapProof \$serverText\)\) \{ throw') { throw 'Population success does not require current server-log opt-out proof.' }
if ($bootstrapScript -notmatch '\$profileData\.starterStationDisabled = \$true') { throw 'Population manifest does not record verified station opt-out.' }
$proof = Join-Path ([IO.Path]::GetTempPath()) ('ms14-proof-' + [Guid]::NewGuid().ToString('N'))
$fixtureCopy = Join-Path $proof 'fixture'; $retained = Join-Path $proof 'profile'
New-Item -ItemType Directory -Path $fixtureCopy,$retained | Out-Null
try { Set-Content (Join-Path $fixtureCopy 'server.log') 'evidence'; Copy-PopulationEvidence $fixtureCopy $retained; if ((Get-Content (Join-Path $retained 'server.log') -Raw).Trim() -ne 'evidence') { throw 'Evidence copy failed.' }; if ((Get-PopulationProofPath $retained) -ne (Join-Path $retained 'join-proof.log')) { throw 'Per-case proof path is not deterministic.' } } finally { Remove-Item $proof -Recurse -Force }

$driverPath = Join-Path $PSScriptRoot '..\..\src\main\java\com\juicyslew\moonstation14\ms14\animals\diagnostics\AnimalPopulationScenarioDriver.java'
$driver = Get-Content -LiteralPath $driverPath -Raw
if ($driver -notmatch 'MS14_ANIMAL_POPULATION_COMPLETE scenario=\{\} animalCount=\{\}') {
    throw 'Java driver completion logger format changed; update launcher protocol deliberately.'
}
$initPath = Join-Path $PSScriptRoot 'connected_join.init.gradle'
$init = Get-Content -LiteralPath $initPath -Raw
if ($init -notmatch '\[server,\s*client\]\.each\s*\{\s*run\s*->\s*run\.jvmArguments\.addAll\(') {
    throw 'Population recording arguments are not applied to both server and client runs.'
}
if ($init -notmatch 'server\.jvmArguments\.add\(''-Dms14\.animalPopulationDiagnosticPlayer=JoinDiagnostic''\)') {
    throw 'Diagnostic player property must be server-only.'
}
$sustainedPropagation = [regex]::Match($init, 'def sustainedProperty = p\.findProperty\(''ms14AnimalPopulationSustained''\)([\s\S]*?)def roles')
if (!$sustainedPropagation.Success -or $sustainedPropagation.Groups[1].Value -notmatch "sustainedProperty in \['true', 'false'\]" -or
    $sustainedPropagation.Groups[1].Value -notmatch "populationSustained && populationScenario == null") {
    throw 'Init script must validate literal sustained opt-in and require a population scenario.'
}
if ($sustainedPropagation.Groups[1].Value -notmatch "if \(populationSustained\)\s+run\.jvmArguments\.add\('-Dms14\.animalPopulationSustained=true'\)") {
    throw 'Validated sustained opt-in is not propagated to actual server/client JVM launch arguments.'
}
if ($init -notmatch "captureDir == null \|\| captureDir\.toString\(\)\.trim\(\)\.isEmpty\(\)" -or
    $init -notmatch "server\.jvmArguments\.add\('-Dms14\.animalPopulationDiagnosticPlayer=JoinDiagnostic'\)") {
    throw 'Population launch must require capture directory and configure the real diagnostic player server-side.'
}
$populationBranch = [regex]::Match($init, 'if \(populationScenario != null\) \{([\s\S]*?)\n    \}\n    def roles')
if (!$populationBranch.Success -or $populationBranch.Groups[1].Value -notmatch "server\.jvmArguments\.add\('-Dms14\.starterStation\.enabled=false'\)") {
    throw 'Starter station opt-out must be scoped to the population server run.'
}
if ($populationBranch.Groups[1].Value -match "client\.jvmArguments\.add\([^\r\n]*starterStation") {
    throw 'Starter station opt-out must not be applied to the client.'
}
if ($init -notmatch 'ms14\.animalPopulationCaptureDir=\$\{capturePath\}') {
    throw 'Capture output directory JVM property is not wired from the launcher argument.'
}
$captureAst = $ast.Find({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Test-PopulationRecordingReady' }, $true)
if (!$captureAst) { throw 'Launcher has no recording-ready artifact validator.' }
. ([scriptblock]::Create($captureAst.Extent.Text))
$temp = Join-Path ([IO.Path]::GetTempPath()) ('ms14-pop-ready-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
try {
    foreach ($case in @(@{role='server';scenario='parrots';count=100;pid=1234},@{role='client';scenario='mixed';count=0;pid=2345})) {
        $fileName = "$($case.role).jfr"
        [IO.File]::WriteAllBytes((Join-Path $temp $fileName), [byte[]](1,2,3))
        @{role=$case.role;scenario=$case.scenario;actualCount=$case.count;pid=$case.pid;filename=$fileName;bytes=3} | ConvertTo-Json | Set-Content (Join-Path $temp ($case.role+'-ready.json'))
        $log = "MS14_ANIMAL_POPULATION_RECORDING_READY role=$($case.role) scenario=$($case.scenario)"
        if (!(Test-PopulationRecordingReady $log $case.role $case.scenario $case.pid $temp)) { throw "Valid $($case.role) recording-ready evidence rejected." }
        if (Test-PopulationRecordingReady $log $case.role $case.scenario ($case.pid+1) $temp) { throw 'Forged PID accepted.' }
        if (Test-PopulationRecordingReady $log $case.role $(if ($case.scenario -eq 'parrots') { 'kobolds' } else { 'parrots' }) $case.pid $temp) { throw 'Wrong scenario accepted.' }
        if (Test-PopulationRecordingReady ($log -replace 'RECORDING_READY','RECORDING_FAILED') $case.role $case.scenario $case.pid $temp) { throw 'Missing ready log marker accepted.' }
    }
    $markerPath = Join-Path $temp 'server-ready.json'
    $bad = @{role='server';scenario='parrots';actualCount=100;pid=1234;filename='..\outside.jfr';bytes=3} | ConvertTo-Json
    Set-Content $markerPath $bad
    if (Test-PopulationRecordingReady 'MS14_ANIMAL_POPULATION_RECORDING_READY role=server scenario=parrots' 'server' 'parrots' 1234 $temp) { throw 'Escaping filename accepted.' }
    @{role='server';scenario='parrots';actualCount=100;pid=1234;filename='server.jfr';bytes=0} | ConvertTo-Json | Set-Content $markerPath
    if (Test-PopulationRecordingReady 'MS14_ANIMAL_POPULATION_RECORDING_READY role=server scenario=parrots' 'server' 'parrots' 1234 $temp) { throw 'Empty recording accepted.' }
} finally { Remove-Item -LiteralPath $temp -Recurse -Force }
foreach ($case in @(@{scenario='parrots';count=100},@{scenario='kobolds';count=100},@{scenario='mixed';count=40})) {
    $line = "MS14_ANIMAL_POPULATION_COMPLETE scenario=$($case.scenario) animalCount=$($case.count)"
    if (!(Test-PopulationCompletion $line $case.scenario)) { throw "Valid completion rejected: $line" }
    if (Test-PopulationCompletion $line $(if ($case.scenario -eq 'mixed') { 'parrots' } else { 'mixed' })) { throw "Wrong scenario accepted: $line" }
    $wrongCount = if ($case.count -eq 100) { 40 } else { 100 }
    if (Test-PopulationCompletion "MS14_ANIMAL_POPULATION_COMPLETE scenario=$($case.scenario) animalCount=$wrongCount" $case.scenario) { throw "Wrong animalCount accepted: $line" }
    if (Test-PopulationCompletion "MS14_ANIMAL_POPULATION_COMPLETE scenario=$($case.scenario)" $case.scenario) { throw 'Partial completion marker accepted.' }
}
'Population completion protocol checks passed.'
