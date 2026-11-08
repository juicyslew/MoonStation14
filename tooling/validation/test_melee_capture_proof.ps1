$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'melee_capture_proof.ps1')
$dir=Join-Path $env:TEMP ('melee-proof-'+[guid]::NewGuid().ToString('N')); [IO.Directory]::CreateDirectory($dir)|Out-Null
$passed=0; $failed=0
function Assert-Throws([scriptblock]$Action) { try { & $Action; throw 'Expected rejection.' } catch { if ($_.Exception.Message -eq 'Expected rejection.') { throw }; $script:passed++ } }
try {
 $bytes=[byte[]](137,80,78,71); [IO.File]::WriteAllBytes((Join-Path $dir 'motion_knife_repeat_outward.png'),$bytes)
 $hash=([BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash($bytes))).Replace('-','').ToLowerInvariant()
 $knife=[pscustomobject]@{phase='motion_knife_repeat_outward';scene='motion_knife_repeat';sample='outward';sha256=$hash;hudHidden=$true;actualItem='moonstation14:kitchen_knife';expectedItem='moonstation14:kitchen_knife';arc=[pscustomobject]@{strike=[pscustomobject]@{mode='LIGHT'}}}
  Assert-MeleeCaptureProof $knife $dir $true; $passed++
   $wide=[pscustomobject]@{phase='motion_crowbar_wall_wide';scene='motion_crowbar_wall_wide';sample='outward';sha256=$hash;hudHidden=$true;actualItem='moonstation14:crowbar';expectedItem='moonstation14:crowbar';replyStatus='HIT';serverScene=[pscustomobject]@{targetBlockX=1;targetBlockY=2;targetBlockZ=3;targetBlockStateId=4;typedNominations=@([pscustomobject]@{block=[pscustomobject]@{pos=[pscustomobject]@{x=1;y=2;z=3};expectedBlockStateID=4}})};arc=[pscustomobject]@{strike=[pscustomobject]@{mode='WIDE'}}}
   [IO.File]::WriteAllBytes((Join-Path $dir ($wide.phase+'.png')),$bytes); Assert-MeleeCaptureProof $wide $dir $true; $passed++
   $bad=$wide.PSObject.Copy(); $bad.replyStatus='MISS'; Assert-Throws { Assert-MeleeCaptureProof $bad $dir $true }
   $bad=$wide.PSObject.Copy(); $bad.serverScene.typedNominations=@([pscustomobject]@{block=[pscustomobject]@{pos=[pscustomobject]@{x=1;y=2;z=3};expectedBlockStateID=5}}); Assert-Throws { Assert-MeleeCaptureProof $bad $dir $true }
   $bad=$wide.PSObject.Copy(); $bad.serverScene.typedNominations=@([pscustomobject]@{block=[pscustomobject]@{pos=[pscustomobject]@{x=9;y=2;z=3};expectedBlockStateID=4}}); Assert-Throws { Assert-MeleeCaptureProof $bad $dir $true }
  function FlightFixture([int]$sample,[bool]$observer=$false) {
   $scene=if($observer){'motion_observer_disarm_flight'}else{'motion_disarm_flight'}; $suffix=@('outward','extended','return')[$sample]; $phase="$scene`_$suffix"; [IO.File]::WriteAllBytes((Join-Path $dir ($phase+'.png')),$bytes)
   $vec=[pscustomobject]@{x=1.0;y=0.0;z=0.0}; $org=[pscustomobject]@{x=2.0;y=3.0;z=4.0}
    $cue=[pscustomobject]@{active=($sample -lt 2);participant=$true;ageNanos=$(if($sample -eq 2){1300000000}else{100});lifetimeNanos=1200000000;itemUUID='item-1';receiverUUID='receiver-1';velocity=$vec;origin=$org}
   $receipt=[pscustomobject]@{itemUUID='item-1';actorUUID='actor-1';victimUUID='victim-1';recipientUUIDs=@('receiver-1');velocity=$vec;origin=$org}
   [pscustomobject]@{phase=$phase;scene=$scene;sample=$sample;sha256=$hash;hudHidden=$false;replyStatus='DISARM_HANDLED';flightCue=$cue;serverScene=[pscustomobject]@{expectedActorUUID='actor-1';expectedVictimUUID='victim-1';committedFlight=$receipt};arc=[pscustomobject]@{strike=[pscustomobject]@{mode='DISARM'}}}
  }
  foreach($i in @(0,1)){Assert-MeleeCaptureProof (FlightFixture $i) $dir $true; $passed++}
   Assert-MeleeCaptureProof (FlightFixture 2 $true) $dir $true; $passed++
  $badflight=FlightFixture 0; $badflight.replyStatus='HANDLED'; Assert-Throws {Assert-MeleeCaptureProof $badflight $dir $true}
  $badflight=FlightFixture 0; $badflight.flightCue.receiverUUID=''; Assert-Throws {Assert-MeleeCaptureProof $badflight $dir $true}
  $badflight=FlightFixture 0; $badflight.serverScene.committedFlight.velocity.x=0; $badflight.flightCue.velocity.x=0; Assert-Throws {Assert-MeleeCaptureProof $badflight $dir $true}
   $badflight=FlightFixture 0; $badflight.flightCue.ageNanos=1200000001; Assert-Throws {Assert-MeleeCaptureProof $badflight $dir $true}
   $badflight=FlightFixture 2 $true; $badflight.flightCue.ageNanos=2200000001; Assert-Throws {Assert-MeleeCaptureProof $badflight $dir $true}
 $bad=$knife.PSObject.Copy(); $bad.expectedItem='held-item'; Assert-Throws { Assert-MeleeCaptureProof $bad $dir $true }
 $bad=$knife.PSObject.Copy(); $bad.arc=[pscustomobject]@{strike=[pscustomobject]@{mode='WIDE'}}; Assert-Throws { Assert-MeleeCaptureProof $bad $dir $true }
 $bad=$knife.PSObject.Copy(); $bad.sha256='bad'; Assert-Throws { Assert-MeleeCaptureProof $bad $dir $true }
  Write-Output "Pure melee proof fixture tests passed=$passed failed=$failed (validator fixtures, not gameplay proof)"
} finally { Remove-Item $dir -Recurse -Force }
