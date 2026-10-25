# Singuloosing — handoff

## STATUS
Owner accepted and closed this sprint after successful manual use. Candidate `singuloosing-20261014-r45` (`c8f3e497270c`) passed the technical gate. Changes remain uncommitted; closure does not authorize a merge, release, installation, or deletion of failure evidence.

## ARCHITECTURE
The fixture automatically starts real containment emitters, field generators, finite-fuel bootstrap power and the PA. Its one-shot generator may be consumed normally; commissioning follows the exact successfully spawned singularity UUID, not continued generator existence. Normal PA particles feed the formed source.

SINGULOOSING uses a dedicated narrow real cage: relative field-generator bounds X=5..9, Y=4..6, Z=8..11. COMPACT and WIDE remain distinct. Collision planes are at `FieldGeometry.center(blockCoordinate)`, not block boundaries. The stage-1 center's accessible cross-section is inset by its horizon radius. The three resolved PA beams cover that cross-section; the starter at (7,5,9) is strictly inside, not on a face. Face interiors and all twelve edge paths must remain clear.

Commissioning targets stage 3 plus two normal waves of margin (680 energy with current profiles), then leaves LEVEL_2 running. There is no ongoing energy governor, fake feeding, or forced motion. A 100 W lamp plus 5 kW beacon requires 5.1 kW export. Startup has a 7,200-tick deadline (six minutes at 20 TPS). Coordinate-free build/status/breach select registered fixtures; breach removes an owned corner, currently (9,4,8).

## IMPORTANT FILES
- `docs/singuloosing-sprint/OPERATOR.md`: concise owner commands and validation note.
- `src/main/java/com/juicyslew/moonstation14/ms14/singularity/fixture/`: `SingularityFixtureService`, `SingularityFixtureLayout`, `SingularityFixtureOperatorKit`, `SingularityFixturePower` own coordination, geometry, access/beam clearance and actual power construction.
- `src/main/java/com/juicyslew/moonstation14/entities/SingularityEntity.java`: destructive horizon, safe removal/credit, energy, saved horizon phase and unrestricted 3D movement.
- `src/main/java/com/juicyslew/moonstation14/entities/ParticleAcceleratorParticleEntity.java`: authoritative swept contact/direct food; optional instance-local diagnostic receipts default off.
- `src/main/java/com/juicyslew/moonstation14/block/block_entity/ParticleAcceleratorPartBlockEntity.java`: real firing and shared `emissionOrigin`.
- `src/test/java/com/juicyslew/moonstation14/ms14/singularity/fixture/SinguloosingBeamCoverageTest.java`: resolved-prototype beam coverage and nonzero-origin regression.
- `src/gametest/java/com/juicyslew/moonstation14/ms14/singularity/fixture/`: commissioning, terrain-wing and live-consumption tests.
- `docs/singularity-power-sprint/HANDOFF.md`: prior lens/power architecture and accepted decisions.

## SS14 CORRESPONDENCE
Reference revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Singularity starts at energy 180/level 1 and naturally random-walks; level 1 is not stationary. Normal PA food per particle at levels 0/1/2/3 is 10/20/30/60. Preserve finite fuel, resolved prototypes, real allocation and natural drain. Dirt/grass yield 0.01 versus ordinary terrain's 1. Beacon attraction is power-dependent; beacon food adds 120, not 121 or stack-scaled credit. Destruction credits only successfully removed matter and uses existing machinery/cable lifecycle owners. Saved horizon timing survives reload; unsafe food arithmetic is rejected before removal.

## DECISIONS / WHY
- Preserve accepted geometry-based lens and transient escape bias; no altitude lock or permanent governor.
- Stage-1 wandering can dodge a beam bank in a taller cage. Use valid physical containment geometry to cover the reachable volume, not injected energy or suppressed movement.
- Keep PA emitter placement identities separate from containment-emitter identities when observing waves.
- Emitter/field-generator blocks use non-occluding rendering while retaining physical shapes. Stair headroom and all three actual PA corridors are cleared by the kit planner.
- Shared field bounds are absolute and translated once. Positive/negative-origin regressions guard against double offsets.
- Native fixtures own sufficient surrounding chunks; some power fixtures use a one-chunk halo. This is fixture loading, not permission to bypass UNKNOWN power topology.

## KNOWN GAPS
Connected-client and detailed visual coverage remain limited despite owner success. Existing unsupported boundaries include nested holders, unmarked block entities, corpse retention, timed anchoring, multi-hand carrying, held-speed modifiers and contraband. Opt-in diagnostics retain bounded recent evidence; source removal time is the first polling observation, not an exact death timestamp.

## VALIDATION
R45: JUnit 3561/3561; interaction readiness 60/60; focused native 27/27 selected of 926; blocker native 45/45 selected of 926; full native 926/926; assemble artifact verified. Independent reviews covered geometry, bounds translation, beam coverage and corrected native assertions. Owner reported the fixture worked well. Reports and failure evidence remain under `build/agent-workflow/singuloosing-sprint/`; do not reuse old native worlds with retained ACTIVE lifecycle state.

## NEXT WORK
Separate `singularity-gravity-sprint`: smooth attraction with meaningful overhead lift; smaller player-lethal core without shrinking terrain consumption; running away from a stationary source should gain ground outside the intended capture boundary. Preserve emergent uncertain capture, including drifting beneath a source. Verify near-field lens visibility when facing away; wrapping is desirable only where compatible with the current rendering approach. These are new adaptations, not completed work in this sprint.
