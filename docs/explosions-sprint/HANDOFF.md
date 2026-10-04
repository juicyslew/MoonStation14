# Explosions and fixed grenade

## STATUS

Reconstructed 2026-10-03. Owner-designated implementation complete; closure does not establish connected-client acceptance, calibrated 3D balance, or a fully green historical server suite. Historical uncommitted/asset-deletion warnings describe that closeout, not the current checkout.

## ARCHITECTURE

Fixed disposable ExGrenade uses controlled-body LIVE_HANDS actions: N primes; M separately throws. Server fuse/explosion services own detonation, typed damage, bounded block mutation, and atmosphere topology, power, puddle-support, and speech-occlusion integration. Source and explosion settings come from resolved local prototypes.

## IMPORTANT FILES

- `closeout.md`, `m3-player-grenade.md`, `m2-verification.md`.
- `src/main/java/com/juicyslew/moonstation14/ms14/explosion/server/ServerExplosionService.java` and `explosion/prototype/ModExplosions.java`.
- `src/main/resources/data/moonstation14/moonstation14/explosive_source/ex_grenade.json` and `explosion/default.json`.

## SS14 CORRESPONDENCE

Upstream explosion systems/prototypes are the reference. SS14 grenade values are 120/3/10; local 380/3/10 is provisional. Local 800 K metadata does **not** deposit heat, expose hotspots, or apply fire stacks. Fixed fuse cadence is not upstream modular TimerTrigger behavior. Sound attribution is retained.

## DECISIONS / WHY

Impacts bounce/slide without detonation or fuse reset. Work is bounded: tolerance 2, visited cap 256, no chunk loading. Generic/modular grenade and throw dispatch are outside this slice.

## KNOWN GAPS

Connected physics/model/audio and carrier-camera sound perception, numeric balance, and disconnected fuse semantics remain unverified. Setting blocks to AIR can lose container contents; drops/protection are not guaranteed.

## VALIDATION

Historical coordinator evidence: 1,752 JUnit tests passed; isolated 368 GameTests had one unrelated required animation failure (`leasedpigandvillagerkeepvanillawalkanimation`), with no grenade/explosion/pickup failures. This is not a full green server result. No suites rerun for reconstruction.

## NEXT WORK

Owner-connected bounce, stacked pickup, cocoa-pod model, near/far audio, and tuning review. Audit current ownership/container semantics before expanding destructive effects.
