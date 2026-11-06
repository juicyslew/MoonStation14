# Stock Sable-only control (R28)

This independent nested Gradle project tests stock Sable without MoonStation integration. It uses the root wrapper, ModDevGradle 2.0.141, Minecraft 1.21.1 / NeoForge 21.1.228 and the official Sable artifact with its bundled dependencies. It must not load the root mod, its mixins, access transformer or gameplay systems. Only the explicitly allowlisted pure observation code described below is reused as generated neutral-package sources. Production remains at two substeps; no ten-step adoption is proposed.

Run only with a new empty project-local directory, for example from the repository root:

```powershell
.\gradlew.bat -p tooling/stock-sable-control runGameTestServer -Pms14GameTestDir=build/gametest-shuttle-feedback-08-stock-only-r28
```

The property path is relative to the nested project, not the repository root. A pre-launch validation dependency requires an explicit path under its `build/` and rejects a non-empty path before ModDev preparation. Never point it at any R25/R26/R27 fixture or report directory. The root project/build configuration is not altered.

**Status / evidence boundary:** the isolated project uses exact pinned Modrinth coordinate `maven.modrinth:sable:fg9dTRz9` (non-transitive). The companion API coordinate matches the root build's `compileOnly` declaration; runtime companion code is supplied by the official Sable release's Jar-in-Jar, avoiding a separately-added companion mod candidate. Its runtime allowlist reflects the pinned R27 log's observed mod IDs, including `sablecompanion` and `veil` (not guessed underscore variants). `StockControlIsolation.verify()` fails closed unless the loaded Minecraft, NeoForge, and Sable versions are exactly 1.21.1, 21.1.228, and 2.0.6; it also asserts no MoonStation mod/resources/mixin config, logs mod/mixin inventories, and validates the Sable jar digest before a fixture may proceed. Registration reflects over the fixture class and requires exactly two annotated cases before logging the computed `2/2` preflight. No native test has run and there is no collision result. Required artifact SHA-256: `9c199157843bf953a47dff625fc5bb41c01a90538d35ad18e6996bbcb8a617c2`.

Pure observer code is generated into this project's build tree from `HullPhysicalSnapshot.java`, `HullTerrainGeometry.java`, `FrameBoundsTransform.java`, and the existing `HullImpactTraceState.java`. The trace latch body is reused unchanged; only its package declaration is relocated and its two `HullTerrainImpactProbe.Observation` type references are replaced with `StockHullImpactGameTests.Observation`, the neutral fixture observation contract. The other three helpers receive only package relocation and the existing `FrameBoundsTransform` import relocation. The structure template is likewise copied into the generated `sable_control` namespace. No root gameplay class or existing fixture runner is linked. The integrated `HullTerrainImpactProbe`/runner imports MS14 fixture, scene, and world-observation systems, so a separate minimal physical adapter is necessary. Setup failures are inconclusive.

## Physical control and interpretation

- Exactly two cases: public stock assembly followed by an upward 20 or 80 m/s
  velocity delta, at confirmed two substeps. No body, controller, cargo, power or
  atmosphere owner is present; no pose correction, rotation freeze or native
  collision modification is made.
- Vanilla stone occupies the original 8×7×13 shell boundary and internal
  partition coordinates, with glass at `(0,2,4)`: 452 cells/parts. It replaces
  MS14 steel-wall/airlock states and is **not** R16's state fingerprint.
  Pre/post-transfer snapshots must match; mass/COM, actual collision parts,
  grounded pose/pivot/bounds and admitted native velocity are evidence.
- Separate 64×64 pads have top y=−47 and four stone layers; test origins are
  x=4400/4600, z=3600. Only queried physical-world and global storage-footprint
  chunks are retained, with bounded FULL/entity-ticking readiness checks.
- The shared oracle latches >0.10 m penetration on two consecutive samples,
  lost coverage, changed hull or whole-slab escape. It preserves history and
  bounded follow-through; penetrating trajectories are genuine GameTest failures.
  Passing requires nonvacuous ascent/descent/contact and ten naturally settled
  samples. Cleanup must complete before success, with failure/timeout fallback.

A valid stock-only penetration establishes that MS14 is unnecessary for that
vanilla-shell symptom, not that Sable is defective or violates a speed guarantee.
A pass does not exonerate MS14: any attribution needs this **same vanilla fixture**
with MS14 present. Setup/isolation/coverage failures are inconclusive. See
`STOCK-ASSEMBLY-CONTROL.md` for the version-pinned documentation search and
original R16/R17 limitations. No flight/body lifecycle, process-restart, client,
production mitigation, readiness or M1 acceptance is established by this control.

## Pre-submission validation

Focused `compileJava processResources` passed after the final cleanup correction.
Independent read-only build/isolation and physical-control reviews passed,
including comparison of the generated failure tracker with its original body.
No full MS14 suite, native control, client or packaging check was run by authors.
The single R28 gate check is the first intended native use of its fresh fixture;
all runtime exclusion, artifact identity and trajectory claims remain unverified
until its bound log and receipt are inspected. Diagnostic completion is not
readiness or owner acceptance, including when a native assertion fails usefully.
