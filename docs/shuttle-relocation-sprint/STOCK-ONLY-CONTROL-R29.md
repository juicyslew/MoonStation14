# Stock-only R29: corrected composition admission and shutdown evidence

## Scope and reuse

R28's two cases failed **our incorrect mass premise**, not a collision assertion:
see `STOCK-ONLY-CONTROL-R28-RESULT.md`. Preserve that run and never reuse its world.
R29 retains the same 451-stone/one-glass shell, public stock assembly, separate
20/80 m/s launches and strict shared trajectory oracle described in
`STOCK-ONLY-CONTROL-R28.md`. No engine, physics-property, gravity, substep or
production/owner-state setting is changed.

Expected mass now comes from existing public stock
`PhysicsBlockPropertyHelper.getMass(level, pos, state)` for each authored cell,
not a guessed one-unit-per-block rule or the actual tracker reused as its own
expectation. Capture records the 451/1 composition and each state's resolved
mass, friction, restitution, volume and optional inertia. Pre/post-transfer
captures must match; actual tracker mass must equal the independent sum both
after assembly and at grounded admission. Runtime values remain to be observed.
This is still not the R16 material/state/mass composition.

Source correspondence at Sable pin `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`:
- `common/src/main/java/dev/ryanhcode/sable/physics/config/block_properties/PhysicsBlockPropertyHelper.java`: public resolved property getters, zero mass for nonsolid states.
- `PhysicsBlockPropertyTypes.java` in that folder: fallback mass 1, friction 1, restitution 0, volume 1 and null inertia; data can override these.
- `common/src/main/java/dev/ryanhcode/sable/api/physics/mass/MassTracker.java`: sums the same getter for solid states and uses mass in COM/inertia construction.
- `common/src/main/resources/data/sable/physics_block_properties/heavy.json` and `tags/block/heavy.json`: mass-2 heavy category includes `#c:stones`; complete runtime membership/property resolution is captured rather than guessed.
- `PhysicsBlockPropertiesDefinitionLoader.applyToBlocks/applyAll`: missing optional `create:flywheel` logs an empty selection and the outer loop continues; that warning does not itself prove the other definitions were skipped.

These are version-pinned source references, not a reproducible source/binary
equivalence claim. The actual launched JAR must again match the official digest.

## Freshness and shutdown diagnostics

The guard now depends from the actual ModDev task `prepareGameTestServerRun`:
`validateFreshGameDirectory` → `prepareGameTestServerRun` → `runGameTestServer`.
The previous wrong task name is evidenced by R28's raw task log. A requested
dry-run was permission-denied and was not retried or bypassed. Static dependency
inspection found no concrete defect, but execution order remains unverified;
the fresh native log must show guard execution before preparation.

The existing test-only `GameTestProgressObserver` keeps its exact-server event
ownership; its sampler delegates to extracted pure `GameTestShutdownStackCapture`.
The standalone project generates a neutral-package copy of that fifth allowlisted
helper. `StockControlShutdownDiagnostics.arm(h)` **is called** at fixture entry
after isolation verification. Matching public NeoForge stopping/stopped events
start/cancel one daemon: 15 seconds to sample 1, another 15 to sample 2, at most
128 threads/64 frames each, server thread first, bounded strings and truncation
markers. No world/chunk/physics queries or process/deadline changes occur off-thread.
No MS14 observer or gameplay class is loaded into the stock-only runtime.

This may identify a future blocked operation; it neither explains nor fixes R28's
incomplete shutdown. Normal cancellation would be non-reproduction, not a fix.

## Validation and native boundary

Focused standalone `compileJava processResources` passed. Independent physical
review passed the resolved-property correction; build/capture review found no
concrete source defect, while explicitly withholding task-graph execution proof.
Root `compileGameTestJava` was permission-denied and not retried; it remains an
explicit lead-owned check before the new native run. No root full suite, native
run, client, packaging or owner-ready validation was performed by authors.

Fresh fixture: `tooling/stock-sable-control/build/gametest-shuttle-feedback-08-stock-only-r29`.
Fresh raw log: `build/agent-workflow/shuttle-relocation-sprint/reports/feedback08-stock-only-r29-native.log`.
Expected complete control catalogue: 2/2, not the MS14 suite. Require isolation,
hash/version/substep/composition/grounded admission before interpreting outcomes.
Ordinary trajectory failures remain failures. Inspect shutdown stacks or actual
per-dimension completion/normal exit separately. No stock-only flight conclusion,
Sable defect/speed guarantee, integration attribution, readiness or M1 acceptance
exists yet.
