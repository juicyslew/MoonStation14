# Tesla power sprint

## Goal

Deliver the playable SS14-style Tesla power loop: commissioning, contained energy-ball operation, electrical discharge, Tesla-coil generation, grounding protection and a hazardous containment failure. Reuse the singularity/PA/containment/power infrastructure and pinned SS14 sprites/sounds wherever their responsibilities match. Preserve the accepted singularity behavior.

Owner authorized autonomous implementation and model requests. Automated readiness is not owner acceptance. The previously deferred death-before-ghost revival bug is outside this sprint.

## Ordered work

1. **Establish composition and reuse.** Verify the pinned upstream revision, resolve each relevant prototype's ancestry/components/defaults, trace actual consumers and media provenance, and map supported behavior to local typed prototypes. Identify existing shared owners before adding code. Record deliberate Minecraft adaptations separately.
2. **Implement the authoritative Tesla loop.** Add resolved gameplay data and live runtime consumers for ball lifecycle, movement and discharges, including relevant target eligibility and grounding behavior. Reuse existing containment, damage/status, entity and networking services; do not copy the singularity runtime wholesale.
3. **Integrate machinery and real power.** Connect commissioning to the ordinary PA path, and coils to the existing electrical network with finite received energy. Add generator/coil/grounding-rod machines using existing registration, interaction and construction conventions. No artificial permanent power or hidden fixture feeding.
4. **Integrate presentation.** Import the exact upstream animation/audio with attribution, reuse rendering/network-effect owners, and request missing machine models through the established modeler route if needed. Keep media in canonical resource/source locations, not docs. Unwired art is not delivered functionality.
5. **Provide runnable fixtures and validate.** Add concise setup/status/breach instructions and focused profile-resolution, shared-owner and native-loop regressions. Run cheap development checks, independent review, then the coordinator's serialized readiness/integration gate. Fix actionable findings before presenting owner-testing steps.

## Acceptance boundaries

- Values and gameplay rules follow the verified pinned SS14 systems unless a documented Minecraft adaptation is necessary.
- Relevant inheritance/default resolution is exercised by both tests and live consumers; unsupported components are not introduced as decorative YAML mimicry.
- Tesla and singularity share suitable existing services without competing power, PA, containment, damage or networking authorities.
- Commissioning, useful coil output, grounding preference/protection and an unsafe breach are observable through normal runtime paths.
- SS14 visuals/audio are live and attributed; any model or connected-client boundary not verified is stated honestly.
- Fixtures are disposable, stage/commissioning shortcuts are explicit, and prior native worlds with persisted lifecycle state are not silently reused.

## Established architecture

- Verified upstream pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. `TeslaEnergyBall` and `TeslaMiniEnergyBall` inherit `BaseEnergyBall`; only the main ball has `TeslaEnergyBall` energy/growth and `EventHorizon`. Minis must not consume or reproduce.
- Preserve server-owned target priority/probability/resistance chaining, not an indiscriminate nearest-player lightning attack. Main shooter is range 7/depth 3/authored max 4 (exclusive random upper bound yields 1–3 initial bolts) every 3–5 seconds; inherited mini shooter is range 3/depth 2/one bolt every 4–10 seconds. First shot waits the maximum interval. Coils are priority 4/probability .5/resistance 10; rods priority 3/probability 1/resistance 10; supported mob composition supplies priority 2. Selection does not add a wall line-of-sight rule absent upstream.
- Main-ball contact consumption is distinct from periodic horizon sweeps: upstream disables periodic tile/entity scans but retains collision consumption. No natural Tesla energy drain. Energy adjustments above 100 spawn one mini and subtract 100; below -100 collapses the main ball. Minis expire after 120 seconds.
- Reuse `SingularityGeneratorStartup` and the generator's containment/PA admission behavior through a narrow shared generator contract, not a second copy of PA firing. Reuse `FieldContainmentRuntime` for loaded-world six-face geometry and swept contact. Minecraft three-dimensional containment and collision are explicit adaptations.
- Coils hold at most 5 MJ, gain 5 MJ per accepted strike and offer at most 350 kW to the existing HV graph. Add finite-supplier settlement to the shared power owner if needed; do not create electricity merely by publishing an offer or drain unrelated APC stores. Grounding rods have no power output.
- Reuse `ElectrocutionSystem.applySourceShock`, status/damage owners and existing item/machine lifecycle removal. Resolve lightning eligibility and parameters through supported typed data. Do not infer insulation from Minecraft armor; unsupported conductivity/equipment boundaries must be documented rather than silently claiming SS14 insulation parity.
- Zero-length selected segments preserve target response/chaining but emit no beam, hazard, or living shock: pinned `Content.Server/Lightning/LightningSystem.cs:79-99` retains target selection/events, while `BeamSystem.cs:162-164` rejects coincident endpoints. No source exclusion or visited-set is added.
- Typed ball profiles represent the upstream base/main/mini composition and defaults, including genuinely absent optional behaviors. Machine data represents separate generator/coil/rod responsibilities. Live consumers must read resolved catalogs. Keep visual/audio state separate from authoritative energy and damage.
- Accepted Power R6 exports already contain Tesla generator, coil and rod models; import these exact families rather than commissioning duplicate art. Use pinned SS14 ball/mini animations and Tesla/lightning audio with retained attribution. No existing art is to be rebuilt or corrected without authorization.

## Baseline

The working tree already contains the validated previous merge's travel guard and native-travel regression; preserve those edits. Tesla gameplay, shared startup/PA integration, finite electrical export, source media, render/audio bindings and eight native tests are implemented. The focused Tesla diagnostic `run_1532d6b7aad654b171a6bbb2` passed all eight native tests, including resolved mini contact and an entity-timed main-ball discharge. Compilation and 64 focused JVM tests passed for that candidate. This is not full feature-gate, GPU or owner acceptance. `PARITY.md` and `ASSETS.md` index the source-to-runtime mapping and limitations.

Subsequent r5 checks passed 51/1063 selected native fixtures, the selected JUnit checks, interaction readiness and packaging; the overall feature gate rejected the coverage-evidence mapping. The owner's client-loading crash then exposed uppercase texture identifiers. Runtime paths and shared animation UV definitions were corrected. Focused resource/classloading checks passed 17 tests. Client-startup diagnostic `run_2afa4e54838efee746358a9c` reached `TitleScreen` with no loading overlay and exited successfully in an isolated run directory. This proves startup only, not Tesla world rendering, audio playback or owner acceptance. Full feature readiness remains pending the corrected evidence submission.

## Owner-feedback revision

Owner approved PA-only vertical capture tolerance after reporting difficult 3D feeding, trapped minis, harmless coil contact after breach, and a raw examine translation key. Keep player/mob PA hit detection and reactor lethal-contact sizes unchanged. Ordered work: resolved collision/name and physical contact corrections; shared PA receiver-only vertical tolerance; input attribution and focused regressions; then focused validation and a fresh replacement gate. Previous technical PASS is not acceptance of these changes. Preserve the client-startup fix and accepted singularity gravity/lifecycle boundaries.

Owner-feedback diagnostic `run_6b7ad02b37f9bc6721221924` passed all 60 selected native tests from 1,072 discovered fixtures. This includes the new receiver-only capture, unchanged mob-hitbox, mini field/wall escape, protected-block, coil contact/cable cleanup and flat-food-credit cases. Compilation and 95 focused JVM checks passed before that diagnostic. Only the readiness selector catalog and documentation changed afterward; the full replacement feature gate remains pending. No new connected-client/world visual or audio acceptance is claimed.

## Gate criteria

1. **TESLA01_COMPOSITION:** pinned parent/default mapping, typed inheritance, supported target composition and live resolved consumers.
2. **TESLA02_BALL:** main/mini distinction, finite energy transitions, no natural drain, contact admission, chasing, timed lifetime and contained jumps/breach behavior.
3. **TESLA03_LIGHTNING:** target priorities/probability/resistance, source shock and authoritative arc dispatch; documented structural/insulation boundaries.
4. **TESLA04_STARTUP:** real PA startup through the shared one-shot generator and field owner, with existing singularity behavior retained.
5. **TESLA05_POWER:** coil/rod distinction, finite strike charge, bounded interval offers, attributed HV settlement and no electrical output from an empty coil or rod.
6. **TESLA06_PRESENTATION:** exact attributed source media and accepted machine models are wired to animation, hit states, looping/lifecycle audio and server-authored arc rendering. Connected visual/audio observation remains unclaimed until performed.
7. **TESLA07_FIXTURE:** runnable build/start/status/breach paths, original-source tracking, real isolated output load and stale-ownership refusal without mutating unrelated fixtures.
8. **TESLA08_REGRESSION_PACKAGE:** relevant cheap/native/readiness checks, preserved shared-system regressions, fresh package and attributable managed provenance evidence.
