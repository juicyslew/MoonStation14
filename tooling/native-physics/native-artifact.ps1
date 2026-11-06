param(
    [Parameter(Mandatory=$true)][string]$JavaSource,
    [Parameter(Mandatory=$true)][string]$Lz4Jar,
    [Parameter(Mandatory=$true)][string]$OfficialOuterJar,
    [Parameter(Mandatory=$true)][string]$WindowsDll,
    [Parameter(Mandatory=$true)][string]$OutputJar,
    [Parameter(Mandatory=$true)][string]$ProvenanceJson,
    [Parameter(Mandatory=$true)][string]$BuildReceipt,
    [switch]$VerifyOnly,
    [switch]$ValidateInputsOnly,
    [switch]$CheckContractFixtures
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Hash([string]$p) { $h=[Security.Cryptography.SHA256]::Create(); $s=[IO.File]::OpenRead($p); try { ([BitConverter]::ToString($h.ComputeHash($s))).Replace('-','').ToLowerInvariant() } finally { $s.Dispose(); $h.Dispose() } }
function SourceHash([string]$p) { $t=[IO.File]::ReadAllText($p).Replace("`r`n","`n"); $b=[Text.UTF8Encoding]::new($false).GetBytes($t); $h=[Security.Cryptography.SHA256]::Create(); try { ([BitConverter]::ToString($h.ComputeHash($b))).Replace('-','').ToLowerInvariant() } finally { $h.Dispose() } }
function Check([string]$p,[string]$expected,[string]$label) {
  if (!(Test-Path -LiteralPath $p -PathType Leaf) -or (Hash $p) -ne $expected.ToLowerInvariant()) { throw "$label missing or hash mismatch: $p" }
}
function Validate-UnitCounts($unit,[string]$summaryLine) {
  if ($unit.status -ne 'tests-passed' -or $unit.command_exit_code -ne 0 -or $unit.source_revision -ne $rapier.revision -or $unit.passed -lt 2 -or $unit.failed -ne 0 -or $unit.ignored -lt 0 -or $unit.filtered -lt 1 -or $unit.patch_sha256 -ne $rapier.patch_sha256 -or $unit.patched_source_sha256 -ne $rapier.patched_source_sha256) { throw 'Managed unit proof is not current and passing' }
  $pattern='^test result: (?<state>ok)\.\s+(?<passed>\d+) passed;\s+(?<failed>\d+) failed;\s+(?<ignored>\d+) ignored;.*?\s+(?<filtered>\d+) filtered out(?:;.*)?$'
  if ($summaryLine -notmatch $pattern -or [int]$Matches.passed -ne $unit.passed -or [int]$Matches.failed -ne $unit.failed -or [int]$Matches.ignored -ne $unit.ignored -or [int]$Matches.filtered -ne $unit.filtered) { throw 'Managed unit log summary is missing, malformed, or differs from proof counts' }
}
function Assert-ProvenanceMetadata($p) {
  if($p.build_receipt_sha256 -ne (Hash $BuildReceipt) -or $p.rust_unit_evidence_sha256 -ne (Hash $unitPath) -or $p.pack_helper_sha256 -ne (Hash $JavaSource) -or $p.wrapper_sha256 -ne (Hash $PSCommandPath) -or $p.lz4_dependency.coordinate -ne 'at.yawk.lz4:lz4-java:1.11.0' -or $p.lz4_dependency.sha256 -ne (Hash $Lz4Jar) -or $p.provenance_json_dependency.coordinate -ne $official.provenance_json_dependency.coordinate -or $p.provenance_json_dependency.sha256 -ne $official.provenance_json_dependency.sha256) { throw 'Provenance receipt/proof/tool binding mismatch' }
  if($p.sable_source.commit -ne $sable.commit -or $p.sable_source.patchSha256 -ne $sable.patchSha256 -or $p.sable_source.patchedSha256 -ne $sable.patchedSha256 -or $p.rapier_source.revision -ne $rapier.revision -or $p.rapier_source.patch_sha256 -ne $rapier.patch_sha256 -or $p.rapier_source.patched_source_sha256 -ne $rapier.patched_source_sha256) { throw 'Provenance canonical source pin/patch metadata mismatch' }
  if($p.rust_unit_proof.patch_sha256 -ne $unit.patch_sha256 -or $p.rust_unit_proof.patched_source_sha256 -ne $unit.patched_source_sha256 -or $p.rust_unit_proof.passed -ne $unit.passed -or $p.rust_unit_proof.failed -ne $unit.failed -or $p.rust_unit_proof.filtered -ne $unit.filtered) { throw 'Provenance managed unit proof mismatch' }
}
if (!(Test-Path -LiteralPath $BuildReceipt -PathType Leaf)) { throw 'Build receipt missing' }
$receipt = Get-Content -Raw -LiteralPath $BuildReceipt | ConvertFrom-Json
$rapier = Get-Content -Raw (Join-Path $PSScriptRoot 'rapier-source.json') | ConvertFrom-Json
$sable = Get-Content -Raw (Join-Path $PSScriptRoot 'sable-source.json') | ConvertFrom-Json
$official = Get-Content -Raw (Join-Path $PSScriptRoot 'official-artifact.json') | ConvertFrom-Json
if ($official.sable_source_pin -ne $sable.commit) { throw 'Official artifact pin does not match the pinned Sable source' }
Check $OfficialOuterJar $official.sha256 'Official outer Sable artifact'
$gsonPattern=Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1/*/gson-2.10.1.jar'
$gsonJar=Get-ChildItem -Path $gsonPattern -File -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if (!$gsonJar) { throw 'Pinned cached Gson 2.10.1 build-time verifier dependency missing' }
Check $gsonJar $official.provenance_json_dependency.sha256 'Pinned Gson JSON parser (build-time only)'
if ($receipt.status -ne 'built' -or $receipt.cargo_exit_code -ne 0 -or $receipt.pe_machine -ne 'AMD64 (0x8664)' -or $receipt.target -ne 'x86_64-pc-windows-msvc') { throw 'Receipt is not a successful Windows x64 built DLL receipt' }
if ($receipt.sable_source_pin -ne $sable.commit -or $receipt.rapier_source_pin -ne $rapier.revision -or $receipt.sable_patch_sha256 -ne $sable.patchSha256 -or $receipt.rapier_patch_sha256 -ne $rapier.patch_sha256 -or $receipt.sable_patched_source_sha256 -ne $sable.patchedSha256 -or $receipt.rapier_patched_source_sha256 -ne $rapier.patched_source_sha256) { throw 'Receipt source pins/patch/source hashes differ from current canonical manifests' }
$dllPath = Join-Path $root $receipt.artifact_path
Check $dllPath $receipt.artifact_sha256 'Built DLL'
if ((Hash $WindowsDll) -ne $receipt.artifact_sha256) { throw 'Selected DLL does not match receipt-bound built DLL' }
$unitPath = Join-Path $root $receipt.managed_rust_unit_evidence_path
Check $unitPath $receipt.managed_rust_unit_evidence_sha256 'Managed unit proof'
$unit = Get-Content -Raw $unitPath | ConvertFrom-Json
$log = Join-Path $root $unit.test_log
Check $log $unit.test_log_sha256 'Managed unit test log'
$summaries=@(Get-Content $log | Where-Object { $_ -match '^test result:' })
if ($summaries.Count -ne 1) { throw 'Managed test log must contain exactly one summary' }
Validate-UnitCounts $unit $summaries[0]
$private = Join-Path $root 'build/native-physics/sable-dll-source'
if ((SourceHash (Join-Path $private $sable.file)) -ne $sable.patchedSha256) { throw 'Patched Sable source hash mismatch' }
if ((SourceHash (Join-Path $root 'build/native-physics/rapier-patched/src/dynamics/rigid_body.rs')) -ne $rapier.patched_source_sha256) { throw 'Patched Rapier source hash mismatch' }
Check (Join-Path $root 'tooling/native-physics/patches/sable-effective-mass-refresh.patch') $sable.patchSha256 'Sable patch'
Check (Join-Path $root 'tooling/native-physics/patches/rapier-principal-frame.patch') $rapier.patch_sha256 'Rapier patch'
Check (Join-Path $private 'sable_rapier/src/main/rust/Cargo.lock') $receipt.cargo_lock_candidate_sha256 'Production Cargo.lock'
if ($CheckContractFixtures) {
  $cases=0; $rejected=0
  $valid=$unit | ConvertTo-Json -Depth 8 | ConvertFrom-Json
  Validate-UnitCounts $valid $summaries[0]; $cases++
  foreach($change in @(@{k='filtered';v=19},@{k='ignored';v=1},@{k='passed';v=3},@{k='failed';v=1},@{k='command_exit_code';v=7},@{k='status';v='ERROR'})) {
    $bad=$unit | ConvertTo-Json -Depth 8 | ConvertFrom-Json; $bad.($change.k)=$change.v
    try { Validate-UnitCounts $bad $summaries[0]; throw "fixture accepted $($change.k) mismatch" } catch { if($_.Exception.Message -like 'fixture accepted*'){throw}; $rejected++ }; $cases++
  }
  $tmp=Join-Path ([IO.Path]::GetTempPath()) ('native-receipt-fixture-'+[guid]::NewGuid()+'.txt'); Set-Content $tmp 'changed'
  try {
    foreach($label in @('DLL','patch','production lock','unit proof')) {
      try { Check $tmp (Hash $log) "fixture changed $label"; throw "fixture accepted changed $label" }
      catch { if($_.Exception.Message -like 'fixture accepted*'){throw}; $rejected++ }; $cases++
    }
  } finally { Remove-Item $tmp -Force }
  if($rejected -ne 10){throw "Contract fixture rejection count $rejected/10"}; Write-Host 'Receipt proof fixtures: accepted=1 rejected=10 PASS (status, counts/filtered, and changed DLL/patch/lock/proof bindings)'
}
foreach ($candidate in @($OutputJar,$ProvenanceJson)) {
  $full=[IO.Path]::GetFullPath($candidate).TrimEnd('\')
  $artifactRoot=[IO.Path]::GetFullPath((Join-Path $root 'build/native-physics/artifacts')).TrimEnd('\')+'\'
  if (!$full.StartsWith($artifactRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Candidate outputs must remain under ignored build/native-physics/artifacts' }
  if ($full -eq [IO.Path]::GetFullPath($OfficialOuterJar) -or $full -eq [IO.Path]::GetFullPath($WindowsDll) -or $full -eq [IO.Path]::GetFullPath($BuildReceipt)) { throw 'Candidate output would overwrite an input' }
}
if ([IO.Path]::GetFullPath($OutputJar) -eq [IO.Path]::GetFullPath($ProvenanceJson)) { throw 'Candidate JAR and provenance paths must be distinct' }
if (!(Test-Path $WindowsDll)) { throw 'Windows DLL missing' }
$bytes=[IO.File]::ReadAllBytes((Resolve-Path $WindowsDll)); if($bytes.Length -lt 256 -or $bytes[0] -ne 0x4d -or $bytes[1] -ne 0x5a){throw 'DLL is not PE'}
$peOffset=[BitConverter]::ToInt32($bytes,0x3c); if($peOffset -lt 64 -or $peOffset+6 -gt $bytes.Length -or $bytes[$peOffset] -ne 0x50 -or $bytes[$peOffset+1] -ne 0x45 -or [BitConverter]::ToUInt16($bytes,$peOffset+4) -ne 0x8664){throw 'DLL is not AMD64 Windows PE'}
if (!(Test-Path $Lz4Jar) -or (Hash $Lz4Jar) -ne '535c5578cab5dcd0a438e202df80091632b873c0370c25d9b1c1ad1d73577207') { throw 'Pinned LZ4 jar missing or hash mismatch' }
$classes = Join-Path ([IO.Path]::GetTempPath()) ('native-artifact-' + [guid]::NewGuid())
New-Item -ItemType Directory $classes | Out-Null
try {
  $classpath="$Lz4Jar;$gsonJar"
  & javac -cp $classpath -d $classes (Resolve-Path $JavaSource) (Resolve-Path (Join-Path $PSScriptRoot 'NativeArtifactTest.java'))
  if ($LASTEXITCODE) { throw 'javac failed' }
  if ($CheckContractFixtures) {
    $fixtureDir=Join-Path $root 'build/native-physics/provenance-json-roundtrip-fixture'
    Remove-Item -Recurse -Force $fixtureDir -ErrorAction SilentlyContinue
    & java -cp "$classes;$classpath" NativeArtifactTest $fixtureDir
    if ($LASTEXITCODE) { throw 'Synthetic verifier fixture batch failed' }
    $fixtureProof=Join-Path $fixtureDir 'proof.json'
    (Get-Content -Raw $fixtureProof | ConvertFrom-Json | ConvertTo-Json -Depth 12) | Set-Content -Encoding utf8 $fixtureProof
    & java -cp "$classes;$classpath" NativeArtifact --verify (Join-Path $fixtureDir 'official.jar') (Join-Path $fixtureDir 'native.dll') (Join-Path $fixtureDir 'candidate.jar') $fixtureProof (Join-Path $fixtureDir 'receipt.json')
    if ($LASTEXITCODE) { throw 'Gson verification failed after PowerShell ConvertFrom/To-Json UTF-8 roundtrip' }
    Write-Host 'Provenance JSON roundtrip fixture: PowerShell parse/pretty-print/UTF-8 write -> same Java verifier PASS'
  }
  & java -cp "$classes;$classpath" NativeArtifact --inspect (Resolve-Path $OfficialOuterJar)
  if ($LASTEXITCODE) { throw 'Official outer JAR payload/structure validation failed' }
  if ($VerifyOnly) {
    if (!(Test-Path $OutputJar) -or !(Test-Path $ProvenanceJson)) { throw 'VerifyOnly requires candidate and provenance files' }
    Assert-ProvenanceMetadata (Get-Content -Raw $ProvenanceJson | ConvertFrom-Json)
    & java -cp "$classes;$classpath" NativeArtifact --verify (Resolve-Path $OfficialOuterJar) (Resolve-Path $WindowsDll) (Resolve-Path $OutputJar) (Resolve-Path $ProvenanceJson) (Resolve-Path $BuildReceipt)
    if ($LASTEXITCODE) { throw 'Fresh payload/provenance verification failed' }; return
  }
  if ($ValidateInputsOnly) { Write-Host "Receipt and official outer JAR validation PASS; unit counts $($unit.passed)/$($unit.failed)/$($unit.ignored)/$($unit.filtered), AMD64 DLL and pinned LZ4 PASS; no candidate emitted."; return }
  & java -cp "$classes;$classpath" NativeArtifact (Resolve-Path $OfficialOuterJar) (Resolve-Path $WindowsDll) $OutputJar $ProvenanceJson $BuildReceipt $unit.passed $unit.failed $unit.filtered
  if ($LASTEXITCODE) { throw 'NativeArtifact packaging failed' }
  $proof=[ordered]@{ build_receipt_sha256=(Hash $BuildReceipt); rust_unit_evidence_sha256=(Hash $unitPath); sable_source=$sable; rapier_source=$rapier; lz4_dependency=[ordered]@{ coordinate='at.yawk.lz4:lz4-java:1.11.0'; sha256=(Hash $Lz4Jar) }; provenance_json_dependency=$official.provenance_json_dependency; pack_helper_sha256=(Hash $JavaSource); wrapper_sha256=(Hash $PSCommandPath); rust_unit_proof=$unit; workflow_context='R39 package-check FAIL does not alter R38 managed ERROR; receipt records Cargo exit 0; cleanup currently confirmed; R36 unit proof is reused, not a new managed PASS.' }
  $prov=Get-Content -Raw $ProvenanceJson | ConvertFrom-Json
  foreach($key in $proof.Keys){$prov | Add-Member -NotePropertyName $key -NotePropertyValue $proof[$key] -Force}
  $prov | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 $ProvenanceJson
  Assert-ProvenanceMetadata (Get-Content -Raw $ProvenanceJson | ConvertFrom-Json)
  & java -cp "$classes;$classpath" NativeArtifact --verify (Resolve-Path $OfficialOuterJar) (Resolve-Path $WindowsDll) (Resolve-Path $OutputJar) (Resolve-Path $ProvenanceJson) (Resolve-Path $BuildReceipt)
  if ($LASTEXITCODE) { throw 'Fresh payload/provenance verification failed' }
} finally { Remove-Item -Recurse -Force $classes -ErrorAction SilentlyContinue }
