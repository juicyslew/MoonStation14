# Shuttle / station-grid feasibility

**Superseded planning outline.** Execution follows
`docs/shuttle-relocation-sprint/Instructions.md` and its owner clarification:
Sable 2.0.6 is a required dependency, not an opt-in probe. The older 2.0.3 findings
and licensing hold below are historical, not the current implementation contract.

## Goal

Determine whether Sable can be the authoritative grid engine for MoonStation14,
before implementing gravity coverage or shuttle travel. This is an evaluation,
not production adoption, FTL implementation or a zero-G sprint.

The owner rejected fixed station bounds. Construction must expand a station grid;
removal must reduce it. Lunar terrain must remain map terrain, and docking must
not implicitly merge independent grids. Future free-standing stations are relevant,
but are not promised by this evaluation.

## Established baseline

- Local target: Minecraft 1.21.1, NeoForge 21.1.224, Java 21,
  ModDevGradle 2.0.141 (`build.gradle`, `gradle.properties`).
- Sable release: `2.0.3+mc1.21.1`, Modrinth version `1L6XJqnY`, published
  2026-06-17. Source tag `mc1.21.1-2.0.3-neoforge` resolves to
  `17d29f5b99d50e3cc13b536b23f9181ade99f14f`.
  Listed artifact: `sable-neoforge-1.21.1-2.0.3.jar`, 12,889,000 bytes,
  SHA-1 `f3ba9a497122ba29d9e94ebe6f9a3a3aaa5fa756`.
- Release build pins NeoForge **21.1.228**, versus local **21.1.224**.
  This does not establish the declared runtime minimum or require a project
  upgrade. Inspect artifact metadata and test the existing version first if its
  requirements permit it. Windows native presence/loading is not yet verified.
- Published coordinate: `dev.ryanhcode.sable:sable-neoforge-1.21.1:2.0.3`.
  POM runtime declarations are not sufficient to distinguish mandatory mods,
  optional integrations and embedded dependencies; inspect the actual artifact.
- Sable describes sub-levels containing chunks, entities and block entities with
  separate positions/orientations. Its README explicitly warns about intrusive
  mixins. Artifact availability is not compatibility evidence.
- SS14 at `c9df5ef5d675b0d1d226828bddf6b78c28502d91` distinguishes editable,
  transformable grids from stations that group grids. Tile disconnection splits
  grids; station membership can persist across the split. Gravity generators act
  on their direct grid parent, not every grid in their station.
- The port currently has no station/grid ownership service. Its generated starter
  layout, cable graphs and finite gas ownership are not substitutes for one.

## Ordered evaluation gates

1. **Release/API and licensing:** pin a reproducible release, inspect requirements,
   creation/anchoring/splitting/transform APIs and Windows native support.
2. **Isolated boot:** test a dedicated fixture environment without modifying normal
   owner runs, configuration, worlds or production dependencies.
3. **Static grid ownership:** create a small authored platform/room excluding lunar
   terrain; prove block addition/removal changes grid occupancy. Initial authored
   cells are fixture setup, not permanent coverage bounds.
4. **Independent grids:** prove separation, splitting and supported attachment or
   docking behavior without treating physical contact as shared ownership.
5. **Port integration:** real characters, loose items and corpses; collision and
   interaction coordinates; stable block-entity identity; real power and finite
   atmosphere consumers; save/reload and server/client tracking.
6. **Decision:** document demonstrated behavior, blockers and required integration
   work. Do not claim prediction, client rendering or FTL from headless evidence.

Reuse existing tagged GameTests, isolated run-directory properties, authoritative
movement/collision adapters, actual powered-airlock fixtures and finite gas
consumers. Do not build a second physics, power, atmosphere or ownership framework
just to make the probe pass. Keep the experimental movement path's existing
enablement policy; do not silently enable it.

## License gate

Sable's current repository uses PolyForm Shield 1.0.0. Its noncompete clause covers
the software and products supplied by the licensor or affiliates; free products
are not automatically exempt. This is not a permissive MIT-compatible assumption
or legal clearance. Production adoption/redistribution needs separate review or
clarification. No Sable implementation is copied into this project's MIT sources.

## Evidence sources

- https://modrinth.com/mod/sable/version/2.0.3+mc1.21.1
- https://github.com/ryanhcode/sable
- https://github.com/ryanhcode/sable/wiki
- https://github.com/ryanhcode/sable/blob/main/LICENSE.md
- SS14 `StationSystem`, Robust `GridFixtureSystem`, `Tile`, and server
  `GravitySystem`/`GravityGeneratorSystem` at the pin above.

## Current status

Release-tag API inspection and artifact provenance/requirements checks underway.
The first prototype should reuse existing tagged fixtures and explicitly isolated
GameTest/server/client run directories. Ordinary movement enablement and owner
launch settings remain unchanged. No engine dependency, gameplay change or
prototype execution yet. No production go/no-go decision has been made.
