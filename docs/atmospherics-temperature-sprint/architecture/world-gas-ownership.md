# World Gas and Temperature Ownership

## Status and ownership

The experimental world-cell runtime is server-authoritative, but acceptance is incomplete. `enableAtmospherics` is COMMON config, sampled at `ServerAboutToStartEvent` before spawn chunks load, and defaults to `false`. Four registered placeholder test devices, producer/sink APIs, and focused pure tests are implemented; **M3 acceptance remains OPEN** pending automated server-world tests and owner smoke. When disabled, the service performs no reads, writes, or processing. The four registered blocks are inert, though their server-side block-entity ticker makes a cheap enabled check each tick. This subsystem does not own entity lungs, respiration, oxygen saturation, or body temperature. `Oxygenate` and `AdjustTemperature` remain unsupported.

| Concern | Current owner | Boundary / limitation |
| --- | --- | --- |
| Enable and dimension ambient policy | `Config` → `MoonStation14` server-about-to-start handler → `AtmosphereService.configureAtServerStart` | COMMON config sampled before spawn chunks load; gate defaults off; restart required. Empty vacuum-dimension list means all dimensions default to breathable air. No safe saved-world migration policy. |
| Cell gas and thermal state | `AtmosphereService` + sparse `AtmosphereChunkData` attachment | Missing override derives dimension ambient; no room object, aggregate, or identity. |
| Passability / adjacency | `AtmosphereTopology` | Six-face block adjacency; full collision and closed doors/trapdoors/gates seal, open ones pass. Partial shapes pass; airtight face/orientation behavior unsupported. |
| Active exchange | `AtmosphereEventHooks` → `AtmosphereService` | Server-only pairwise diffusion/heat transfer; every 4 ticks up to 128 pair edges / 512 inspections. FIFO order bias and unbounded deferred queue memory remain. |
| Public world query/mutation | `AtmosphereService.INSTANCE` | `sample`, positive `addGas`, `addEnergy`, breathable producer and proportional `removeGasUpTo` sink APIs. No species-selective extraction API. |
| Device blocks | `ModBlocks` / `ModBlockEntities` → `AtmosphereTestDeviceBlockEntity` | Four registered placeholders; server-only; fixed rate while gate is on; inert while off except for cheap ticker check. Focused pure tests, no server-world acceptance yet. |
| Entity, client HUD/synchronization | No owner in this subsystem | No body effects, atmosphere HUD, or synchronized client state. |

Chunk lifecycle uses deferred, bounded-per-tick resume work: `ChunkEvent.Load` can arrive before a chunk is `FULL`, so the load handler records coordinates instead of immediately querying saved attachment data. Each server post-tick attempts at most 16 non-forced `FULL` chunk lookups, resumes saved overrides when available, and requeues unresolved coordinates. Pending coordinates are deduplicated and removed on unload. This ordering is based on targeted NeoForge source review and remains an assumption until integration GameTests verify it.

## Cell model, ambient, and room behavior

Each Minecraft block position is one fixed 1 m³ gas cell. Pressure is derived from total moles and temperature via the ideal-gas relation. Gas-specific molar heat capacities define cell thermal energy. Default ambient is 21% oxygen / 79% nitrogen at 293.15 K and 101.325 kPa. A dimension listed in COMMON config `atmosphereVacuumDimensions` instead has zero-gas nominal 2.7 K ambient. The default `[]` means every dimension—including the End and custom dimensions—uses breathable air. Both settings are sampled at server start; restart is required.

Example `config/moonstation14-common.toml` settings:

```toml
enableAtmospherics = true
atmosphereVacuumDimensions = ["minecraft:the_end"]
```

The vacuum dimension list is optional; omit it or set `[]` to retain air in all dimensions. Both config settings are sampled at `ServerAboutToStartEvent`, before spawn chunks load; restart is required. Prefer an isolated new world/dimension for experiments. Do not change ambient policy for a saved world without an owner-approved migration decision: every cell without an explicit override can be reinterpreted.

Chunk attachment data stores sparse per-cell overrides. A cell without an override inherits implicit dimension ambient until changed; each changed cell is an independent override. A 3D construction therefore consists of individual block cells, not an explicit room object, room identity, aggregate, or automatic fill. Gas and thermal energy exchange through open six-neighbor faces only; diagonal corner contact is not connected. Pressure is derived, not independently diffused. Heat transfers via gas only. Empty vacuum has zero gas heat capacity and thermal energy, so it cannot act as a cold heat sink. A newly sealed space initially reads ambient wherever overrides are absent; sealing in vacuum does not auto-fill. Wall placement/removal can currently create or destroy implicit ambient matter, and conservation policy is unresolved.

## Devices

The registered placeholder blocks are an air producer, gas sink, heater, and cooler. Their placeholder blockstate/model/item JSON references vanilla textures only. They are server-only and fixed-rate while the gate is enabled. At placement the device front faces opposite the player's look direction, toward the interior. The producer adds breathable 21% oxygen / 79% nitrogen at 1 mol/s, capped at 202.65 kPa. The sink removes up to 1 mol/s proportionally from the target mixture. The heater adds 2,000 J/s; the cooler removes at most 2,000 J/s and floors at 2.7 K. Focused pure device tests exist; M3 acceptance still requires server-world automated tests and owner smoke.

The owner can empty-hand right-click a device to read the gas in its front cell. This readout is server-only and gated by `enableAtmospherics`; it reports pressure, temperature, and moles for that single cell, not a room aggregate. It is not a client HUD or synchronized client state.

## Solver and topology

The active FIFO scheduler processes local pairs of adjacent cells across six block faces, transferring gas and thermal energy according to local gradients. Processing runs every fourth server game tick with caps of 128 pair edges and 512 conservatively charged cell inspections; the 8,192-position hot queue spills to deferred overflow. Pair math conserves gas/energy in an isolated pair, but repeated FIFO multi-neighbor steps are order-biased. This local pressure equalization is only an approximation, not room-wide pressure equalization or SS14 parity. Deferred overflow and deduplication memory have no total cap.

Full collision blocks and closed doors, trapdoors, and gates are treated as sealed; open variants are passable. Partial collision shapes are treated as passable. Airtightness is not face/orientation-aware, and fluid interactions are not defined. Unloaded/inaccessible chunks are closed transfer boundaries; lookups use `create=false` rather than force-loading. Seam resume and persistence are not proven by server GameTests. Block placement/break/neighbor notifications invalidate local work but do not provide conservation semantics: topology edits can create/destroy ambient matter. This is an M2 blocker.

## SS14 reference comparison (no parity claim)

SS14 map/grid atmosphere associates gas mixtures with finite grid tiles and has implicit map/space atmosphere defaults. Its active processing diffuses neighboring tile mixtures and exchanges heat. `LINDA` and `Monstermos` are SS14 solver strategies for processing that grid atmosphere; neither solver is present here. This project instead stores sparse fixed-1-m³ Minecraft block cells in chunk attachments, obtains missing-cell gas from configurable dimension ambient, and uses local active pairwise six-face exchanges. It has no map-grid atmosphere owner, room identity/aggregate, automatic filling, or SS14 LINDA/Monstermos algorithms. Shared terms do not mean equivalent behavior; this is not full SS14 parity.

Reference paths: `Content.Shared/Atmos/GasMixture.cs`, `Content.Shared/Atmos/Atmospherics.cs`, `Content.Shared/Atmos/MapAtmosphereComponent.cs`, `Content.Server/Atmos/EntitySystems/AtmosphereSystem.{GridAtmosphere,Processing,LINDA,Monstermos}.cs`, and `Resources/Prototypes/Atmospherics/gases.yml`. Review to date is targeted only.

## Ordered acceptance milestones

1. **M0 — default-off safety proof (OPEN):** verify default false, disabled service has no reads/writes/processing, and gate changes take effect only after restart. The gate and cheap device ticker check exist; acceptance proof does not. Document optional `atmosphereVacuumDimensions` (empty means air in all dimensions), restart, and migration warning. Prefer an isolated new world/dimension.
2. **M1 — server automated GameTests (OPEN):** test disabled worlds, actual world queries, persistence/lifecycle, and cross-chunk exchange. Create a sealed NxMxK cell volume crossing chunks, isolate it from dimension defaults with explicit sources, and verify six-face mixing plus stable temperature/pressure. This is not a room aggregate test.
3. **M2 — topology/persistence/conservation (OPEN):** resolve matter behavior on topology edits and test airtight faces, partial blocks, doors, fluid interactions, chunk save/load, opening/closing, loaded cross-chunk mixing, unloaded edges/resumption without force-load, and bounded queue-memory/overload behavior.
4. **M3 — four test devices and API (CODE IMPLEMENTED; ACCEPTANCE OPEN):** producer, proportional sink, heater, cooler, producer/sink service APIs, and focused pure tests are present. Rates/caps: producer +1 mol/s breathable mix capped at 202.65 kPa; sink up to -1 mol/s proportionally; heater +2,000 J/s; cooler at most -2,000 J/s with 2.7 K floor. Server-only, fixed rate, disabled by default gate. Front is opposite placement look direction. Vanilla-texture placeholder JSON only. Acceptance needs automated server-world tests and owner smoke.
5. **M4 — scale/performance (OPEN):** profile active regions, bound and measure queue memory, and benchmark at least 20 players recording solver cost and latency. No profile or benchmark exists. Owner smoke is after automated server gates and owner-only.
6. **M5 — SS14 parity matrix / further systems (OPEN):** record behaviors and local deviations with evidence; fire, pipes, respiration and other parity require separate implementation and tests. No full SS14 parity is claimed.

## Evidence and limits

`compileJava` and focused atmosphere/device tests passed. After this readout was prepared, the coordinator ran `.\gradlew.bat build --no-daemon`: **PASS**, with tasks reported `UP-TO-DATE`. This confirms the build task completed but was not a fresh `compileGametestJava` verification. These checks do not prove server-world behavior. M0-M5 remain OPEN (M3 code implemented, acceptance open). No integration GameTests, server launch, owner smoke, profile, or 20-player benchmark is evidenced. Manual execution is owner-only; see [M1 implementation status](../audits/m1-implementation-status.md).
