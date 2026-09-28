# M6–M9 implementation status audit

**Status: source review only; all milestone acceptance gates remain OPEN.** This records the current prototype code after worker changes, not a runtime or owner-acceptance result. M6–M9 do not close any M0–M5 gate. No game was launched and no owner-only visual/gameplay smoke or 20-player benchmark was run for this audit. See the separate [M10 breach and overlay regression audit](m10-breach-and-overlay-regressions.md) for newer outflow and visual regression work.

## Findings by milestone

| Milestone | Code status | Acceptance status |
| --- | --- | --- |
| M6 sky | Client-only Overworld special effects registered; black procedural star sky, white sun and blue Earth placeholder; `moonSky` local config defaults false and is independent of server `enableAtmospherics`. Ordinary time/daylight/skylight are untouched by design. | **OPEN.** No in-game render verification; config independence and appearance need owner verification. A visual smoke check requires enabling `moonSky` on the client; this is separate from server atmospherics. |
| M7 generators/analyzer | Nine pure-gas producers plus mixed-air producer, shared block-entity/device implementation, creative-tab entries, and generic all-present-gases analyzer formatting are present. | **OPEN.** Source/pure tests are not device interaction or server-world acceptance. The nine producers currently cause no chemical/physiological harm. |
| M8 gas visuals | Five gas overlay policy, bounded chunk-watch snapshot/delta sync, client cache, and procedural client render path present. | **OPEN.** Delivery and runtime rendering/performance have not been verified. No copied SS14 sprites. |
| M9 thermal/exposure | Character-policy thermal profile, typed attachment/provider bridge, one-second thermal exposure and `DamageSystem` heat/cold damage, and `AdjustTemperature` handler present for supported entities. | **OPEN / PARTIAL.** Air composition and general gas physiology are not active; server-world character and damage behavior unverified. |

### M6 — Overworld sky

`moonSky` is a client-local config option whose default is false. It is distinct from and does not reflect server `enableAtmospherics`; the client can opt into the visual without server simulation being enabled. Mod-bus registration of the Overworld effects exists. The renderer draws black and procedurally generated stars, a white sun and a blue Earth placeholder. This visual is not physical sunlight or darkness: world time, daylight progression, block light, skylight and atmospherics remain untouched. No in-game render verification was performed, so celestial correctness, visual artifacts and runtime stability remain unknown. No notable art asset was added.

### M7 — Producers and analyzer

The implementation registers nine single-species producers (O2, N2, CO2, plasma, tritium, water vapor, ammonia, nitrous oxide, frezon) alongside the pre-existing mixed breathable-air producer. They use shared atmosphere block-entity/device handling and appear in the custom creative tab. Producer cadence is once per 20 ticks (one second), maximum dose 20 mol/s, injection temperature 293.15 K, with a partial dose calculated to stay at or below 202.65 kPa. The analyzer formatter enumerates all present gases, including oxygen and nitrogen, using significant digits.

These rules/registrations are implementation claims from current code, not proof of server-world placement, use, persistence, or default-off behavior in a launched world. Game-world interaction acceptance remains outstanding.

### M8 — Gas visual synchronization/rendering

The visible set is exactly plasma, tritium, water vapor, ammonia and frezon, based on the SS14 `gasOverlaySprite` set. Nitrous oxide has no overlay. SS14's 2.5 m³ reference threshold/maximum amounts are volume-scaled to 1 m³: default gases use 0.1/2 mol, ammonia 0.8/2.8 mol, and frezon 0.24/4.8 mol. The original rounded 20-level quantization is used, so just-over-threshold concentrations may still have alpha 0. The historical M12 widened 40 mol/block cap has been superseded. The client renderer uses procedural translucent quads and approximate prototype UI-color tints; it selects nearest cells first and favors tritium only at equal distance, considers cells within 48 blocks, and caps the visual batch at 2,000 cells. Three crossed planes are limited to a maximum 0.85-opacity four-block column, and their quads remain inset to a maximum 0.48-block radius. A HUD warning indicates when bounded cache eviction makes the view incomplete. These are visual indicators only: direct tritium visuals currently cause no chemical harm, and gas composition does not trigger physiology.

The server starts chunk snapshots for `ChunkWatchEvent.Sent` watchers and sends changed-state deltas to chunk trackers, with unwatch cleanup and bounded overflow/resync behavior. Snapshots are staged on the client cache before replacement; newer deltas received while a snapshot is staged are queued and replayed after publish. Delta packetization uses non-reset payloads, so a change does not clear the chunk cache; reset semantics are reserved for snapshots. The server hook cadence is seven ticks (about 3 Hz), with additional packet/chunk/probe caps. This protocol sends quantized visual state, not authoritative mixtures. Focused client synchronization tests are reported passing after a stale selection expectation was corrected, but no end-to-end launched client/server test or visual runtime smoke is evidenced. Runtime appearance, translucency quality, culling effectiveness and rendering/network cost remain unverified. Column opacity is split per plane across a four-block column to mitigate compounding from the three rendered planes; this remains an approximation and is not in-game verified.

**Third-party boundary:** SS14's gas definitions and `GasTileOverlaySystem` are behavioral references; upstream is tgstation, CC-BY-SA-3.0, commit reference `04e43d8...`. No actual CC-BY-SA SS14 sprites were copied or redistributed. Current tints are a prototype approximation and do not recreate the white-alpha source sprites or their animation. This is not a claim of shipped third-party assets. If assets are copied later, verify the full hash and source paths and add exact required attribution/license notices before distribution.

### M9 — Entity temperature and gas effects

Current human thermal parameters are 310.15 K baseline, 42 J/kgK specific heat, approximately 70 kg mass, 325 K heat damage threshold and 260 K cold threshold. Typed temperature state is attached through a provider bridge. The server exposure path runs at one-second cadence, resolves character identity/policy, and applies heat/cold damage through `DamageSystem`. Spectator players are excluded/inert. `AdjustTemperature` has a real handler for supported character-backed actors.

When a valid gas sample exists, the thermal path couples temperature/heat capacity to body temperature. However, heat/cold threshold damage also derives from stored body temperature and can apply without a gas sample. **Gas composition is not physiologically active.** Respiration is a no-op; oxygen saturation, toxic effects, tritium/fire, pressure/wind effects and organ-dependent behavior remain deferred. `Oxygenate` remains unsupported. The implemented thermal prototype must not be represented as general “gases affect entities” functionality.

## Tests and validation boundary

Earlier successful coordinator Gradle test/build runs are historical and predate the current integrated validation. Fresh focused visual tests were reported passing by the worker; the coordinator's final integrated suite is pending. This audit did not execute tests. `compileGametestJava` and GameTest execution are not current acceptance evidence. No server process was launched. Focused unit tests and source status are not a substitute for in-game verification.

## Outstanding gates

- **M0–M5 remain open:** in particular server-world atmosphere behavior, topology/persistence/conservation decisions and tests, scale/performance, owner smoke, and a 20-player benchmark remain outstanding. See the sprint instructions and M1–M3 audits.
- **M6:** owner verifies enabled/disabled client config and actual sky appearance across time; enable `moonSky` in the client config for a visual smoke check (it is not a server atmos setting); verify daylight, block light, skylight and atmosphere are unchanged in a running world.
- **M7:** run server-world interaction tests for all ten producer entries and analyzer output, including pressure-cap partial dosing and default-off no-mutation behavior. None of the nine per-gas producers currently causes chemical/physiological harm.
- **M8:** execute client/server chunk-watch snapshot/delta/unwatch/resync tests, visually inspect all five overlays and culling/opacity behavior (including the newer M10 opacity/flicker fixes), and measure rendering/network cost at 20 players.
- **M9:** execute authoritative character identity, state persistence, cadence, `DamageSystem`, `AdjustTemperature`, spectator and disabled-gate tests in an actual server world. Keep respiration and unimplemented composition effects explicit no-ops until their owners/contracts exist.
- Do not launch a game/server or perform owner-only smoke as part of this documentation update. Coordinator integration results and owner approval are separate required evidence. M0–M9 acceptance remains OPEN; no client/server launch, 20-player benchmark, or true SS14 sprite art copy is claimed. Respiration remains a no-op.
