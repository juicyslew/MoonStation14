# M6–M9: Sky, Gas Presentation, and Entity Exposure

## Status and scope

This is the architecture and implementation-status companion for ordered M6–M9 work. Implementation exists at different levels, but **all M6–M9 acceptance remains OPEN** and none of these milestones closes M0–M5. See the [implementation audit](../audits/m6-m9-implementation-status.md) for source-level findings and evidence boundaries.

The current server atmosphere gate `enableAtmospherics` remains default-off. M6 uses a separate client-local visual preference and is not an authority signal. M7 devices and M8 synchronization are gated by the server atmosphere service. M9 currently provides a limited thermal path for supported character-backed actors, not general gas physiology. There has been no runtime visual verification, owner smoke, 20-player benchmark, or executed server-world GameTest validation of these changes. Do not claim SS14 parity.

## M6 — Overworld moon sky presentation (code present; acceptance OPEN)

The client registers an Overworld `DimensionSpecialEffects` replacement on the mod event bus. When active it draws a black sky, deterministic procedural stars, a white sun and a simple blue Earth disc in the moon position. This is a texture-free placeholder renderer. The config `moonSky` is a client-local setting, defaults to `false`, and activates in the Overworld; it is independent of the server's `enableAtmospherics` option. Consequently there is no server effective-gate negotiation: a client can elect to render the cosmetic independently of server simulation configuration.

This implementation does not change world time, ordinary daylight progression, block lighting, skylight, atmosphere ambient, or gas state. The sky appearance itself has no physical sunlight/temperature effect. A source review is not visual acceptance; test renderer registration, enable/disable behavior, celestial appearance through the cycle, and vanilla light/time preservation in a running client before acceptance.

## M7 — Nine pure-gas producers and analyzer (code present; acceptance OPEN)

Nine individual producer blocks (oxygen, nitrogen, carbon dioxide, plasma, tritium, water vapor, ammonia, nitrous oxide, and frezon) coexist with the existing mixed breathable-air producer. All ten generator blocks are registered in the Moonstation creative tab. The gas producers share the existing atmosphere device block entity infrastructure; they are not ten unique runtime systems.

The shared producer rule runs on a one-second/20-tick cadence and doses up to 20 mol/s at 293.15 K. It computes projected pressure and takes a partial dose if needed to remain at or below 202.65 kPa; when already at the limit it does not produce. The shared analyzer formatter iterates all gas types and reports all nonzero measured components, including O2 and N2, using significant-digit formatting. Source/test coverage supports those rules and registrations; no server-world device interaction or owner acceptance is claimed.

## M8 — Client-only gas visuals (code present; acceptance OPEN)

Only five gases have overlays: plasma, tritium, water vapor, ammonia, and frezon. This selection follows SS14's `gasOverlaySprite` presence. Nitrous oxide has no overlay, as do oxygen, nitrogen, and carbon dioxide. The pure policy takes SS14 visual thresholds/maxima and scales them from its 2.5 m³ reference tile to Minecraft's 1 m³ gas cell; opacity is quantized to 20 levels. The renderer produces approximate tinted translucent procedural quads, not copied sprite assets or the SS14 white-alpha sprite animation. It prioritizes tritium when rendering and limits work to 2,000 cells in a 48-block range.

The server synchronization is chunk-watch scoped: snapshots begin for a watcher on `ChunkWatchEvent.Sent`, deltas target clients tracking the changed chunk, unwatch removes watcher state, and bounded queue overflow requests a fresh snapshot. Payload handling stages chunk snapshots in the client cache before replacing visible state. Server emission uses a seven-tick cadence (approximately 3 Hz), with bounded packet, chunk, and probe work. This is not a gas-mixture replication protocol or client simulation. Server/client separation has focused tests, but protocol and pure tests do not prove in-world delivery, rendering appearance, culling, visual quality, or cost. Runtime visual appearance is unverified.

### Reference and attribution boundary

The visual behavior was informed by tgstation SS14 sources including `Resources/Prototypes/Atmospherics/gases.yml` and `Content.Server/Atmos/EntitySystems/GasTileOverlaySystem.cs`, with upstream commit reference `04e43d8...`; the upstream project/source is CC-BY-SA-3.0. No actual SS14 sprites or other source assets were copied into this project. The current UI-color tints are approximations for prototype visualization, not redistributed upstream artwork. This reference note is not a claim that third-party assets are bundled or an attribution notice for redistributed assets. If future work copies an asset, record the exact file, full verified source commit, license compatibility, and required notices before shipping.

## M9 — Character thermal exposure and gas effects (partial code; acceptance OPEN)

The thermal prototype defines human body temperature as 310.15 K, specific heat as 42 J/kgK, mass as approximately 70 kg, heat threshold as 325 K, and cold threshold as 260 K. A typed body-temperature attachment is accessed through a provider bridge. The server exposure path is scheduled at one-second intervals for supported actors, resolves character identity rather than treating every Minecraft player as a character, and routes typed heat/cold damage through `DamageSystem`. `AdjustTemperature` now has a real handler for supported entities and changes stored body heat. Spectator-player exposure/controller behavior is inert.

When a valid, authoritative gas sample exists, the current thermal path models heat exchange using gas temperature and heat capacity. If no acceptable gas sample is available, stored-body threshold damage can still apply; temperature-threshold damage is therefore not proof of a gas-composition effect and may be independent of a current sample. Respiration remains a no-op. Oxygen saturation, toxic effects, tritium/fire effects, pressure/wind injury, and organ-dependent behavior are deferred. Air composition is not physiologically active: only thermal properties currently affect supported entities. Do not say that gases generally affect entities or that this fulfills full M9.

`Oxygenate` remains unsupported. Respiration, respirator filtering, and other SS14-organ-dependent functions remain explicit no-ops/deferred until their actual state owners and contracts exist. Focused pure tests do not demonstrate server-world character identity, persistence, exposure scheduling, damage integration, or reconnect behavior.

## Shared ordering, gates, and safety

Keep the order M6, M7, M8, M9. M6 visuals must not mutate physical lighting/time/atmosphere. Keep M7 and M8 server mutations/synchronization gated by `enableAtmospherics`; keep the client M6 preference distinct from server authority. Keep client render classes out of common/server load paths. M9 must continue resolving an explicit character policy and must not infer character identity solely from `Player` or `LivingEntity` class. Require automated integration evidence, owner runtime smoke for visual and gameplay behavior, and a measured 20-player performance result before acceptance. M6–M9 implementation does not erase outstanding M0–M5 topology, persistence, transactionality, performance, and server-world test gates.
