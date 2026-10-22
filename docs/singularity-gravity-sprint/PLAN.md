# Singularity gravity and player core

## Scope
Follow-up to owner-accepted `docs/singuloosing-sprint/HANDOFF.md`.

- Smooth sustained attraction through existing shared movement and prediction owners.
- Running directly away from a stationary source gains ground outside the intended capture boundary, approximately the previous player-lethal horizon. Source movement is a separate hazard.
- A smaller player-lethal core permits visible inward travel and swallowing. Preserve terrain consumption reach, finite feeding, natural drain and death/credit admission.
- Attraction can lift grounded bodies when strong enough. Preserve uncertain capture, including drifting below an overhead source; no scripted rescue, guaranteed death or shortened capture timeout.
- Investigate near-field effect visibility when facing away. Wrapping is optional where incompatible with the accepted world-geometry renderer; do not restore rejected screen-image warping.

## Ordered work
1. Establish resolved upstream composition and local movement/rendering integration points. Verify units, friction, ordinary gravity, prediction and player-body ownership.
2. Implement and trajectory-test shared smooth attraction and distinct player core using resolved prototype data. Extend existing gravity/contact fixtures; leave terrain waves and machinery unchanged.
3. Verify/fix near-field rear-facing source admission and compatible lens response, preserving the accepted rendering approach.
4. Independent review, serialized integration gate and concise owner test. Automated readiness is not owner acceptance.

## Boundaries
No player-seeking wander change is included. No motion locks, artificial feeding/power, unrelated cleanup, commits, merge or release. This plan is not a completed implementation or validation claim.

## Established composition and initial tuning
See [PARITY.md](PARITY.md) for pinned, source-backed resolved composition, local typed-profile/runtime mapping, and explicit parity limits. Upstream checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

Upstream gravity is an elapsed-time-scaled 0.5-second impulse with unnormalized displacement divided by distance squared: its magnitude falls as **1/distance**, not inverse-square. Radial acceleration is 10 × level, range is 2 × (level + 1), and the common horizon radius is level − 0.5. Terrain consumption has its own independent 0.5-second cadence. Level-1 random movement is intentional.

The local supported typed gameplay profile and shared movement kernel remain the owners; no unsupported upstream components are being added. Proposed player-core scale is 0.35 of the general horizon. The selected first continuous-force candidate multiplies the upstream acceleration by 15 × (general horizon radius / level), yielding the same nominal acceleration at the old horizon across stages. These are deliberate Minecraft adaptations, not SS14 defaults. Profile resolution, inherited defaults, live consumers and actual movement adapters must be tested.

The isolated resolved-human motor experiment (5.625 blocks/s sprint) showed outward progress at the old horizon across levels 1–6, capture from 0.6 times that radius, and overhead lift with this candidate. That is calibration evidence, not live-server or owner acceptance; point trajectories do not prove full-body contact or network feel.

## Expanded warning-range adaptation (implementation pending validation)
User-authorized feel adaptation: retain the upstream base radius (the typed `gravityRangeAtLevel`) and inverse-distance kernel, but multiply its effective pull/query radius by nested resolved `gravityRamp.rangeMultiplier` (3.0), with full strength reached at `gravityRamp.fullStrengthRadiusScale` (2.0) times the unexpanded base radius. The ramp's inner boundary is deliberately base-relative, not horizon-relative: the same upstream base geometry drives the force ramp without introducing a second per-level table, and the expanded band extends one full base-radius width beyond that inner point for warning/drift before full pull. A shared smoothstep envelope ramps from zero at the outer radius to full pull at the inner boundary. Stage-1/4 base/effective ranges are 4/12 and 10/30 blocks. The player-core absolute cap is 0.35 blocks; this and the range/ramp are Minecraft adaptations, intentionally not SS14 parity. Focused unit tests pass; native GameTests and manual tuning/feel remain pending; no owner acceptance is claimed.

The trajectory regression's 80 neutral ticks followed by 60 outward-run ticks at 20 Hz (four seconds plus three seconds) are only a reproducible validation input schedule for measurable modeled drift and subsequent runout. They do not define a warning-band timer or automatic escape: the warning band is continuous and spatial, and no timed state transition is implemented or implied. This modeled trajectory does not establish native feel/perceptibility.

Adapter units are distinct from force integration: the shared kernel returns a change in blocks/second, native Minecraft velocity requires conversion to blocks/tick, and the character motor/predictor consumes blocks/second directly. Player-core classification must recognize the committed lifecycle character owner as well as the experimental harness owner; registration/Ready alone must not be confused with a real connected-client test.

## Rear-facing rendering decision
The accepted shader explicitly handles the forward source-relative half-space; removing its rear-side guard would make its radial division invalid. Source collection already retains rear sources. The existing core is a finite billboard, not an enclosing volume. There is no existing engulfment overlay to reuse. Per the owner's allowance for the current rendering approach, rear-facing wraparound is deferred rather than introducing a new overlay or replacing the accepted lens. No claim is made that the present lens distorts a view directly away from the source.

## Deferred pre-existing readiness gap
The first gate ran `prediction,interaction`: interaction passed, but prediction was correctly UNVERIFIED because its required `BodyHandClientPolicyTest.java` is empty in both the worktree and `HEAD`. The owner explicitly deferred this unrelated issue. No hand behavior, readiness catalog or contract is changed, and general prediction readiness is not claimed to pass. Subsequent scoped gates retain interaction readiness and require gravity-specific resolved-profile, shared-kernel, movement and native evidence separately.

## Native lifecycle validation boundary

Authenticated lifecycle native checks depend on a fresh disposable world when repeating the same source against a reused validation store. Persisted `ACTIVE` profiles can correctly trigger fail-closed `RECOVERY_REQUIRED` at startup, preventing hydration/spawn; do not weaken assertions or production recovery behavior to accommodate stale fixture state. Preserve failed fixture evidence and rerun with a fresh disposable world.

## Operator gravity-only fixture

`/ms14 singularity_fixture gravity <stage>` is a debug setup, not SS14 prototype parity or powered/caged commissioning. It resolves the live `moonstation14:singularity` gameplay profile and uses its thresholds/drain data, then spawns the registered `SingularityEntity` through its explicit fixture initializer. For stages below 6 it initializes about 30 seconds of resolved natural drain below the next threshold; stage 6 uses a finite amount above its threshold. The entity retains ordinary wandering, gravity, horizon and drain: no stage lock, velocity freeze, silent feed, PA, generator, power, cage, or readiness claim. Stages 5/6 have zero configured drain, and terrain feeding can increase energy. Ownership is tracked by requesting player and exact UUID for status/replacement/clear. Spawn is 4 blocks beyond the selected stage's effective pull radius and 3 blocks above the caller: stage 1 starts 16 blocks forward, stage 4 at 34, and stage 6 at 46. This buffer cannot guarantee safety against wandering, especially at stage 6. No world clearing or edits occur. This removes the six-minute commissioning prerequisite for gravity-only observations.
