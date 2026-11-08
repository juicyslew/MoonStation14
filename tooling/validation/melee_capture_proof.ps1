function Assert-MeleeCaptureProof([object]$Phase, [string]$Directory, [bool]$MotionMode) {
    $name = [string]$Phase.phase
    $path = Join-Path $Directory ($name + '.png')
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing frame for phase=$name." }
    $bytes = [IO.File]::ReadAllBytes($path)
    if ($bytes.Length -eq 0) { throw "Empty frame for phase=$name." }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $actualHash = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
    if ([string]$Phase.sha256 -cne $actualHash) { throw "Frame hash mismatch phase=$name." }
    if (!$MotionMode) { return }
    $scene = [string]$Phase.scene
    $sample = $Phase.sample
    $requiredHidden = !($scene -in @('motion_disarm_flight','motion_observer_disarm_flight'))
    if ($null -eq $Phase.PSObject.Properties['hudHidden'] -or $Phase.hudHidden -isnot [bool] -or $Phase.hudHidden -ne $requiredHidden) { throw "HUD visibility mismatch phase=$name." }
    if ($scene -like 'motion_knife_*' -or $scene -like 'motion_crowbar_*') {
        $requiredItem = if ($scene -like 'motion_crowbar_*') { 'moonstation14:crowbar' } else { 'moonstation14:kitchen_knife' }
        if ([string]$Phase.actualItem -cne $requiredItem -or [string]$Phase.expectedItem -cne $requiredItem) { throw "Melee weapon capture identity mismatch phase=$name expected=$($Phase.expectedItem) actual=$($Phase.actualItem); required=$requiredItem." }
    }
    if ($scene -in @('motion_knife_wide','motion_knife_wide_up','motion_knife_wide_vertical_up','motion_knife_wide_down','motion_knife_wide_vertical_down','motion_crowbar_wall_wide','motion_observer_wide')) { $mode='WIDE' }
    elseif ($scene -in @('motion_disarm_first','motion_disarm_flight','motion_observer_disarm_flight')) { $mode='DISARM' }
    else { $mode='LIGHT' }
    if ([string]$Phase.arc.strike.mode -cne $mode) { throw "Strike mode mismatch phase=$name expected=$mode actual=$($Phase.arc.strike.mode)." }
    if ($scene -eq 'motion_crowbar_wall_wide') {
        if ($Phase.replyStatus -cne 'HIT') { throw "Wide crowbar wall phase was not HIT: $name." }
        $target=$Phase.serverScene; $found=$false
        foreach ($nomination in @($target.typedNominations)) {
            if ($null -eq $nomination -or $null -eq $nomination.block) { continue }
            $block=$nomination.block; $pos=$block.pos
            if ([int]$pos.x -eq [int]$target.targetBlockX -and [int]$pos.y -eq [int]$target.targetBlockY -and [int]$pos.z -eq [int]$target.targetBlockZ -and [int]$block.expectedBlockStateID -eq [int]$target.targetBlockStateId) { $found=$true; break }
        }
        if (!$found) { throw "Wide crowbar wall nomination does not prove authored block position/state: $name." }
    }
    if ($scene -in @('motion_disarm_flight','motion_observer_disarm_flight')) {
        if ($Phase.replyStatus -cne 'DISARM_HANDLED') { throw "Flight phase was not handled: $name." }
        $flight=$Phase.serverScene.committedFlight; $cue=$Phase.flightCue
        if (!$flight -or !$cue) { throw "Flight receipt/cue missing phase=$name." }
        if ($null -eq $cue.PSObject.Properties['lifetimeNanos'] -or [long]$cue.lifetimeNanos -ne 1200000000) { throw "Flight cue lifetime metadata mismatch phase=$name." }
        foreach ($v in @($flight.itemUUID,$flight.actorUUID,$flight.victimUUID,$cue.itemUUID,$cue.receiverUUID,$Phase.serverScene.expectedActorUUID,$Phase.serverScene.expectedVictimUUID)) { if ([string]::IsNullOrWhiteSpace([string]$v)) { throw "Flight identity missing phase=$name." } }
        if ([string]$flight.itemUUID -cne [string]$cue.itemUUID -or [string]$flight.actorUUID -cne [string]$Phase.serverScene.expectedActorUUID -or [string]$flight.victimUUID -cne [string]$Phase.serverScene.expectedVictimUUID) { throw "Flight actors/item mismatch phase=$name." }
        if (!$flight.recipientUUIDs -or @($flight.recipientUUIDs).Count -eq 0 -or [string]$cue.receiverUUID -cnotin @($flight.recipientUUIDs | ForEach-Object { [string]$_ })) { throw "Flight receiver is not a committed recipient phase=$name." }
        if ($sample -isnot [int] -or $sample -notin @(0,1,2)) { throw "Invalid flight sample phase=$name." }
        $suffix=@('outward','extended','return')[[int]$sample]; if (!$name.EndsWith('_'+$suffix,[StringComparison]::Ordinal)) { throw "Flight phase/sample mismatch phase=$name." }
        if ($sample -lt 2) {
            if (!$cue.active -or !$cue.participant -or [long]$cue.ageNanos -lt 0 -or [long]$cue.ageNanos -ge [long]$cue.lifetimeNanos) { throw "Flight cue missing/expired phase=$name." }
        } elseif ($cue.active -or [long]$cue.ageNanos -lt [long]$cue.lifetimeNanos -or [long]$cue.ageNanos -gt ([long]$cue.lifetimeNanos + 1000000000)) { throw "Final flight sample must be expired within its finite proof window phase=$name." }
        foreach ($axis in @('x','y','z')) {
            $fv=[double]$flight.velocity.$axis; $cv=[double]$cue.velocity.$axis; $fo=[double]$flight.origin.$axis; $co=[double]$cue.origin.$axis
            if ([double]::IsNaN($fv) -or [double]::IsInfinity($fv) -or [double]::IsNaN($fo) -or [double]::IsInfinity($fo) -or [Math]::Abs($fv-$cv) -gt 0.000001 -or ($sample -lt 2 -and [Math]::Abs($fo-$co) -gt 0.000001)) { throw "Flight vector mismatch phase=$name." }
        }
        if ([Math]::Abs([double]$flight.velocity.x)+[Math]::Abs([double]$flight.velocity.y)+[Math]::Abs([double]$flight.velocity.z) -le 0) { throw "Zero flight velocity phase=$name." }
    }
}
