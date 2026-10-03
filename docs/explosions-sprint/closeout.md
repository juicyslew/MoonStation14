# Explosions sprint — closeout

The implementation sprint is **owner-designated complete** and ready for owner review/merge. This does not claim owner connected-client acceptance, numeric 3D parity or balance, or a fully green GameTest suite.

## Delivered

- Server-authoritative `ExGrenade` cadence/fuse; body-hand prime and throw; overlapping pickup; bounce/slide without impact detonation.
- Bounded destructive block handling and typed entity damage, with atmosphere-topology, power, puddle-support, and speech-occlusion integration.
- Prototype-driven source `380/3/10` selecting local `Default` damage/break/`temperature_kelvin: 800` metadata only. This is provisional, not SS14 numeric parity or calibrated 3D balance. **SS14 hotspot exposure/heat deposition is not implemented**, despite the metadata.
- Near/far explosion sounds with attribution/license retained, cocoa-pod item model, and bounded particles.

## Verification and remaining acceptance

Latest reported coordinator validation: ` .\gradlew.bat compileJava compileGameTestJava test --rerun-tasks --no-daemon` — 1,752 JUnit tests, zero failures. Isolated `build/gametest-grenade-source-prototype-20261001-01` — 368 GameTests; one required unrelated failure, `leasedpigandvillagerkeepvanillawalkanimation`; no grenade, explosion, or pickup failures. This is **not** a full green GameTest result.

Owner follow-up: connected-client retest of bounce, stacked pickup, cocoa-pod presentation, and near/far audio with carrier parked away from the camera; provisional blast tuning review. Container contents may be lost when blocks are set to AIR; no drops are guaranteed. Generic/modular grenades are deferred. See [M3](m3-player-grenade.md) for behavior, bounds, and acceptance procedure.

## Merge handoff

All changes remain uncommitted for the owner. At handoff, the working tree already showed 54 tracked `assets/` deletions outside grenade paths. Their intent is unknown; do not assume they are intentional or include them as grenade changes. This closeout does not modify or attribute those files.
