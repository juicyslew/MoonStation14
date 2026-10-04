# Atmosphere and temperature foundations

## STATUS

Reconstructed 2026-10-03. Closed by bounded owner decision on 2026-09-28, not completion of all historical gates. Original default-off opt-in solver is delivered. Later combustion, physiology and powered ventilation extend it; their records supersede original absent-feature descriptions for those slices.

## ARCHITECTURE

Server-startup enableAtmospherics gate; sparse chunk data owns finite claims. AtmosphereService bounds simulation; GasMixture tracks species and thermal energy. LINDA mixes species; bounded Monstermos routes donor surplus toward patch average while carrying species/energy. Space flow and immutable boundaries are distinct. Local room cells are 1 m³.

## IMPORTANT FILES

`closure.md`, `current-handoff.md`, `audits/m16-server-gametest-and-acceptance-evidence.md`; `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/{AtmosphereService,AtmosphereChunkData,AtmosphereTopology}.java`. Successors: combustion/blood handoffs and `../powered-ventilation-sprint/HANDOFF.md`.

## SS14 CORRESPONDENCE

Parity-inspired solver, not full SS14 equivalence. Bounded patches/queues and excited-group handling are deliberate local differences. Atmosphere temperature alone is not general chemical/physiological temperature.

## DECISIONS / WHY

Retain normal Monstermos; cached average-and-nudge was rejected over gradient/provenance concerns. Unknown samples are not vacuum. Client-local moonSky is independent of server atmosphere. Closure preserves historical reports without promoting them to acceptance.

## KNOWN GAPS

Historical finite zero-mole rows and post-final-change owner door smoke were deferred. Persistent unload/restart, full connected visual sync, representative performance and complete solver parity were not certified. Patches above 800 cells can act as separate rooms. Later combustion/respiration/device implementations do not retroactively prove these gates.

## VALIDATION

Historical coordinator rerun test/build passed; isolated batch completed 154 required GameTests, not 154 atmosphere-specific tests. No fresh suites or client review performed.

## NEXT WORK

Original closure authorizes no resumption. For new work, start with latest finite-owner/device integration and its failure evidence, then separately scope remaining solver/persistence questions.
