# Tesla sprint handoff

## STATUS

Complete; owner accepted and requested sprint closure. Latest implementation gate: `run_73ddc75af4b0ab8d9b8dc6ea`, generation 1, PASS, acknowledged. This includes the final mini follow-lag change. No commit or push was performed by the assistant.

## ARCHITECTURE

- `ModTeslaProfiles` registers typed ball/machine catalogs in `PrototypeRuntime`. Live entities and machines consume resolved inheritance/defaults, not standalone JSON or hardcoded substitutes.
- `TeslaEnergyBallEntity` owns main/mini movement, energy, lifetime, contact and shooting. `TeslaMovementPolicy` supplies deterministic 3D orbit targets and bounded pursuit. Minis follow the current floor-adjusted orbit point with **zero velocity feed-forward**; existing response, speed and acceleration produce follow lag without extra target-history state.
- `TeslaLightningSystem`/selection own targeting/chaining; server beam lifecycle owns persistent segment-entry shocks. Lightning target eligibility does not imply block-consumption eligibility or blast immunity.
- Coils use `TeslaMachineBlockEntity`, `PowerLiveLoop` and `PowerRuntimeNode` for finite storage and actual allocated HV export. Rods have no battery.
- Startup reuses singularity-generator/PA admission and receiver paths. Shared reactor contact/block-consumption services keep living/PA geometry separate from terrain removal.
- Structural damage uses shared persisted `MachineryIntegrity`; ordinary Examine reads it through the existing contributor pipeline. Timed welder repair reuses shared held-authority/fuel services.
- Tesla/grenade accepted explosions share one presentation owner. Ambient loops use `SingularityAmbientClient` and its existing lifecycle policy, including range re-entry/profile replacement.

## IMPORTANT FILES

- `src/main/java/com/juicyslew/moonstation14/ms14/tesla/`: profiles, movement, lightning, machine repair and `fixture/TeslaFixtureService.java`.
- `src/main/java/com/juicyslew/moonstation14/entities/TeslaEnergyBallEntity.java`: live ball behavior.
- `src/main/resources/data/moonstation14/moonstation14/tesla_ball/`: inherited main/mini tuning; neighboring Tesla machine catalog provides target/damage/power data.
- `src/test/java/com/juicyslew/moonstation14/ms14/tesla/`: profile resolution, motion, energy, battery and lightning policy coverage.
- `src/gametest/java/com/juicyslew/moonstation14/ms14/tesla/`: native orbit, ownership, startup, targeting and power fixtures. Ordinary Examine coverage is in `ms14/examine/TeslaDamageExamineGameTests.java`.
- `PARITY.md`: detailed composition/source references and deliberate departures. `ASSETS.md`: canonical media/model paths and attribution. `OPERATOR.md`: disposable-world commands. `PLAN.md` is historical planning, not current acceptance status.

## SS14 CORRESPONDENCE

Reference revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Audited energy-ball ancestry is `BaseEnergyBall` → main/mini; coil and rod are independent roots. See `PARITY.md` for inherited components and actual upstream consumers.

- Main/mini lightning ranges are 7/3. Main fires 1–3 initial bolts; minis fire one. Rods absorb hits without chaining/explosion. Coil capacity is 5 MJ, export 350 kW.
- Minis have no ordinary wall/field collision in SS14; local minis likewise pass through walls and containment. Their separate contact-shock behavior remains an explicit adaptation.
- Mini lifetime is age-only 120 seconds; minis neither feed nor reproduce. Main growth is strictly above 100 energy, spawning one mini per adjustment; no natural drain.
- SS14 chasing is 2D. Continuously changing 3D orbital planes, radial variation, smooth positional pursuit and 3D jumps are deliberate Minecraft adaptations.

## DECISIONS / WHY

- Orbit targets retain radius 2–8 and radial/angular periods 14/10 seconds. Resolved speed 7, acceleration 8 and response 1.5 remain unchanged. Removing instantaneous main/orbit velocity feed-forward prevents welded-looking synchronized turns. Valid main ownership survives jumps; invalid owners use bounded reacquisition.
- Main visual/collider radii are 2.0/.75; mini .90/.65. Main living/lethal/PA contact remains .65; block-only consumption uses .85. Do not merge these geometries.
- Production fixture has four rods: original collector-lane rod plus three supplemental rods covering the contained MAIN source bounds. Three eligible rods can absorb its maximum initial volley. This is **not** universal protection from minis, beams, contact or incidental explosions; emitters remain damageable.
- Seven canonical finished PA parts resist incidental blast removal without blocking propagation, matching audited nondamageable upstream composition. Fixed HV Source has no verified exact SS14 counterpart; retain its existing direct-target exclusion and ordinary blast vulnerability.
- Coil/rod Examine uses resolved optional damage-message keys and six SS14 WindowMessages bands. No duplicate damage counter or crack overlay was introduced.
- Field reserve remains upstream-derived: roughly 200 seconds from full charge to below connection threshold without new hits; either charged endpoint can retain a link.

## KNOWN GAPS

Equipment insulation/conductivity, ChemMaster frame replacement, substation secondary aftermath, intermediate-floor/no-vacuum blast distinctions, crack overlays, full SS14 explosion fire/light/intensity audio/camera shake and explosion combining remain incomplete. EMP departure pulse is visual-only. No deterministic native bounce-lag assertion was added; experiential smoothness is not established by automated tests. Owner closure is acceptance, not a claim of independently captured GPU/audio or multiclient evidence.

## VALIDATION

Final managed gate passed focused unit selectors, interaction/prediction readiness, selected native GameTests, build/package and provenance checks on source digest `f10bb08b008bd55bcf121479ec7ff202f011c25172ce5e5fd8d1cb59dfd765b6`. Plan: 30 unit selectors and ten focused native tags; this was not the full GameTest suite. Focused worker movement/profile checks passed 11 JVM tests plus compilation. Existing native coverage includes actual 3D near/far orbit, bounded motion, retained ownership and jump-gap closure. Independent source review passed.

Evidence is retained by the workflow coordinator; ignored scratch envelope is `build/agent-workflow/tesla-sprint/owner-mini-follow-lag-1/`. Documentation-only closure edits postdate the validated implementation digest.

## NEXT WORK

No remaining sprint implementation task. For future changes, start with the parity map and the existing shared owners above. Do not reintroduce velocity feed-forward or horizontal-only orbiting without revisiting the accepted follow-lag behavior.
