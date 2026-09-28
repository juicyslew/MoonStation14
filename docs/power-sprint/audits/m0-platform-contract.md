# M0 platform contract audit — power sprint

**Scope:** evidence audit for the owner-approved power sprint, pinned to Minecraft 1.21.1 / NeoForge 21.1.224. This document makes no code change and does not certify untested behavior. Local repository evidence is distinguished from API-contract verification and from recommendations.

## Executive result

Use a sparse, persisted **chunk attachment** for future face-wire records, keyed by local X/Z, absolute Y, and face (plus voltage if the tier contract allows one cable per face). Mutate it only through a server-side service. Do not use item attachments or a BE per cable. M1 resolves floor finish differently: a dedicated station-floor block uses a small bounded `tile_finish=NONE|STEEL` BlockState enum, preserving the same registered block and position with vanilla persistence and synchronization. This is a narrow exception for the floor block's own presentation state, not a generic tile bit on hosts. There is no separate tile chunk bit, custom tile packet, or custom tile renderer. Future top-face cable records remain in their independent sparse cable attachment and are hidden when the floor's finish is not `NONE`. Chunk attachment persistence is proven as an existing local pattern; power cable lifecycle, client sync and render behavior are **not** established until their own implementation/tests exist.

Existing NeoForge `BlockEvent.EntityPlaceEvent`, `BlockEvent.BreakEvent`, `BlockEvent.NeighborNotifyEvent` and chunk load/unload events are usable invalidation signals (already compiled and used locally by atmosphere hooks). They are not a complete interception mechanism for arbitrary world mutation: notably do not assume that `Level#setBlockAndUpdate` causes the placement/break events. Own all power mutations through a service, use events as external-change notifications, and define fail-safe host-replacement cleanup. Chunk persistence does not itself synchronize a custom attachment to tracking clients; use a dedicated snapshot/delta payload scoped to chunk watchers, or prove a native attachment sync path with a client test. A client-side world renderer can follow the existing `MoonStation14Client` `RenderLevelStageEvent` pattern, but arbitrary oriented cable geometry, render ordering and chunk seam handling remain hypotheses until client-tested.

GameTest support is present and actively used by the `gametest` source set: `@GameTestHolder`, `@GameTest(template = "empty", timeoutTicks = ...)`, `GameTestHelper`-style fixtures and `helper.succeed()`/assertions can be seen in current tests. The repo configures a `gameTestServer` run. This confirms the project has runnable in-engine server tests, not that GameTest alone proves client rendering or tracking-client synchronization.

## Evidence and certainty

| Claim | Evidence | Status |
|---|---|---|
| Target is MC 1.21.1, NeoForge 21.1.224, Java 21 | `gradle.properties` lines 10–21; `Instructions.md` §2 also records Java/build source-set configuration | Verified repository configuration |
| Sparse chunk attachments can be serialized and read without materializing missing values | `ModDataAttachments.ATMOSPHERE_CHUNK` is registered with `.serialize(AtmosphereChunkData.CODEC, AtmosphereChunkData::hasPersistedState)` and no sync codec (`ModDataAttachments.java:112–117`); `AtmosphereService` uses `LevelChunk.getExistingDataOrNull`, `getData`, and `setUnsaved(true)` (`AtmosphereService.java:111–114, 783–795`) | Verified local implementation pattern; not independent source-contract proof for every semantic |
| Chunk attachment is not automatically client-synchronized in the current atmosphere design | Attachment registration omits `.sync(...)`; atmosphere uses a separate network module (`AtmosphereVisualServerHooks`, `AtmosphereVisualNetworking`) | Verified current project architecture |
| A correct chunk owner and non-forcing loaded lookup exist locally | `AtmosphereService.loadedChunk` calls `level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false)` (`AtmosphereService.java:799–802`); its chunk-load handling defers the lookup because load can precede FULL (`:252–269`) | Verified local pattern. Copy no atmos policy/data; power can use a tiny independent helper |
| Hook examples exist for load/unload and place/break/neighbor notification | `AtmosphereEventHooks.java:28–64`; registered through a NeoForge event bus | Verified local call sites. Completeness for all vanilla/modded writes is not established |
| Arbitrary replacement paths exist | SteelItem and CrowbarItem use `level.setBlockAndUpdate(...)` (`SteelItem.java:42`, `CrowbarItem.java:51`); BE and puddle code call `level.setBlock(..., 3)` | Verified local use. These usages demonstrate code-driven mutation paths, not the events those calls emit |
| Client custom payload and chunk tracking patterns exist | `AtmosphereVisualNetworking.registerPayloadHandlers` installs play-to-client payloads (`:16–24`); `AtmosphereVisualServerHooks` observes `ChunkWatchEvent.Sent/UnWatch`, sends snapshots and calls `PacketDistributor.sendToPlayersTrackingChunk` (`:75–105, 169–178`) | Verified local pattern; independent implementation and client tests required |
| Client world-space custom rendering pattern exists | `MoonStation14Client` listens to `RenderLevelStageEvent` and draws atmosphere geometry (`:115 onward`); class is client-only (`:63–66`) | Verified local event usage. Cable-face/model rendering is not proven by it |
| Current mod has no station substrate/floor blocks to reuse | `ModBlocks.java` registers steel wall/girder, magic, jug, puddle and atmosphere devices (`:29–67`); no eligible station-floor contract is present | Verified in current registry file |
| Existing BE approach targets devices, not every cable | `ModBlockEntities.java:31–43` registers jug, puddle and atmosphere-device BEs; `MS14Provider.update(BlockEntity, ...)` marks changed and sends block update (`MS14Provider.java:97–107`) | Verified local pattern; BE persistence/sync option is not necessarily wrong but is needlessly dense for wire |
| GameTests are available | `src/gametest/java/...` includes many `@GameTestHolder(MoonStation14.MOD_ID)` classes and `@GameTest(template = "empty", timeoutTicks = ...)`; `build.gradle` defines a `gametest` source set and `gameTestServer` run | Verified repo setup; actual tests were not run for this documentation-only task |

### Pinned platform artifacts and signature confidence

The local Gradle cache contains `neoforge-21.1.224-sources.jar`, `neoforge-21.1.224-userdev.jar` and `neoforge-21.1.224-universal.jar` under `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\net.neoforged\neoforge\21.1.224\`. NeoForm intermediate artifacts also exist under `%USERPROFILE%\.gradle\caches\neoformruntime\intermediate_results\` (including sources-with-NeoForge and compiled-with-NeoForge outputs). This audit did not extract/read those archives or run a compile probe. Therefore **no precise NeoForge/Minecraft method declaration is claimed as independently source-verified here**. Exact signatures that must be checked from those pinned mapped artifacts before code relies on them include `Block#onRemove`, `Block#updateShape`, `Level#setBlock`/`setBlockAndUpdate`, chunk attachment serialization callbacks, event cancellation/ordering, `ChunkWatchEvent` accessors, attachment sync tracking, and render/model registration APIs. Existing source call sites prove compilation in this repository, not semantic guarantees for all lifecycle routes.

## Recommended storage contract

### One chunk-owned sparse record

Use a power-specific attachment type (do not repurpose `ATMOSPHERE_CHUNK` or its codec) on `LevelChunk`, containing only non-default entries. Suggested value shape:

```text
schemaVersion
localX: 0..15, absoluteY, localZ: 0..15 ->
    zero or more { face: Direction, tier: HV|MV|APC, cable-kind/id }
```

Use deterministic ordering and a versioned codec; reject invalid local coordinates, face/tier values, duplicates and unsupported schema versions. Empty chunk state must be omitted from serialization. Read with `getExistingDataOrNull` so renderer/topology reads do not create persistent data. On the first actual mutation call `chunk.getData(...)`; when the value changes call `chunk.setUnsaved(true)`. Coalesce equal writes and remove empty entries so cable removal returns to sparse state. Decide and enforce whether multiple tiers/cable types can share the same face; do not accidentally encode an implicit bridge. Floor tile finish is not part of this attachment.

**Why chunk attachment:** position-and-face topology naturally partitions by chunk, one attachment avoids one ticking BE per segment, and the project already persists sparse chunk-owned data this way. Floor finish uses the floor's native bounded property instead, while preserving its block identity. Server authority belongs in the cable service; graph/index state remains transient/rebuildable and never triggers chunk loads. Chunk attachment APIs and on-disk lifecycle are used locally, but this exact proposed codec and size limit are not yet tested.

**Why not blockstates for cables:** blockstates are unsuitable for sparse many-position directional records and potentially have a large state-space; adding properties to arbitrary Minecraft host blocks is impossible without replacing/modifying them. However, M1 intentionally uses one small bounded presentation property on its own dedicated station-floor block: applying/removing finish edits that block's BlockState, not a second same-cell block. This exception does not extend to cable identity, other hosts, or sparse records. The exact floor host is registered/typed, not “any full cube”.

**Why not BEs:** a BE per cable is explicitly disallowed by the performance goal. A single BE per substrate could store the tile and all faces, but it adds thousands of block entities/ticking/BE update-packet policy and still does not solve arbitrary host integration. Reconsider only if measured chunk attachment limitations or an owner-approved host model demonstrate need. Existing device BEs are specific device implementations, not evidence wire requires BEs.

### Tile identity and visibility

The removable tile is a cosmetic finish on the station substrate, not a second block or cable host. In M1, its presence is the substrate's `tile_finish` BlockState enum. The future cable render rule is: top-face cable + station floor finish `NONE` => visible; same record + finish `STEEL` => hidden; resetting finish => same record visible; wall/ceiling cable stays visible. Tile edits do not change cable records or network topology. Host replacement/removal invalidates cables under explicit policy. Keep item placement/pry permissions, item return, and floor eligibility as separate server-authoritative rules.

The separate tile item is targeted directly at the floor; it sets the floor finish property and consumes one item. A dedicated pry item resets the property and returns one tile. The vanilla blockstate model variant presents the finish; no custom overlay geometry or packet is involved.

## Lifecycle: authoritative mutation and unavoidable gaps

1. **Service-owned writes (reliable primary path):** all cable, tile and device placement/removal APIs validate server side, inspect a loaded chunk only, compare old/new state, mutate one authoritative store, mark chunk unsaved, enqueue topology or visual invalidation, and queue client deltas. Include old/new host block identity when replacing hosts. These functions are the complete contract for mod-owned changes.
2. **Vanilla player placement/removal:** local hooks `BlockEvent.EntityPlaceEvent` and `BlockEvent.BreakEvent` can invalidate around changed positions; use `ServerLevel` only and inspect before/after state at a defined event priority if lifecycle requires it. They are useful integration hooks, not a substitute for routing own writes through the service. Exact cancellation/order/signatures still require pinned source review.
3. **Neighbor notification:** `BlockEvent.NeighborNotifyEvent` is an additional topology/eligibility invalidation seam (local precedent: `AtmosphereEventHooks.java:57–60`), not a guarantee that every relevant position mutation emits it.
4. **Host replacement through `setBlockAndUpdate`:** do not claim this is covered by `EntityPlaceEvent`/`BreakEvent`. Repository steel/crowbar code uses it directly and other code uses `setBlock(..., 3)`. For power-owned code, replace via `PowerWorldMutations.replaceHost(...)` that snapshots/prunes attachments and invalidates graph before/after the actual block change. For arbitrary external mods/operator commands, hook the block lifecycle (`Block#onRemove`/replacement callback) on the power substrate/device blocks and/or reconcile through an explicit block-change observer if exact-version API supports it. A global `setBlock` interception is not available through the local evidence. If unrelated replacement cannot be observed reliably, document the host tag/block scope and require owner approval for stale-data cleanup policy before M2.
5. **Host removal/replacement:** before dropping/removing the substrate, remove all cable faces keyed there and device-local index state; dirty affected neighboring graph/visual records. Decide drops and graph dirty invalidation. A floor finish state change is emphatically not this operation.
6. **Chunk lifecycle:** on server `ChunkEvent.Load`, capture the chunk coordinate and, if callbacks can precede FULL status as current atmosphere code warns, defer bounded resolution; use nonforcing `getChunk(..., ChunkStatus.FULL, false)`. On unload discard transient indexes, dirty queues and client watcher state for that chunk, but keep persisted attachment data untouched. Level/server unload clears all per-level transient state. Loading a chunk must restore/re-index from attachment snapshots; seam checks must inspect only ordinarily loaded neighbors.

**Stop condition:** before implementing M2, provide a verified exact-version replacement hook or an enforceable no-orphan boundary (only mod-owned eligible substrates/device blocks plus routed mutation service). Do not infer world-wide arbitrary host replacement coverage from the atmosphere event hooks.

## Client synchronization and rendering without atmosphere dependency

Implement an independent power payload type/registration and server hooks rather than calling/importing atmosphere services or `AtmosphereVisual*`. A straightforward first contract is `ChunkWatchEvent.Sent` → bounded full chunk snapshot of that chunk's sparse records to that viewer; mutations while watched coalesce into chunk-scoped deltas; `UnWatch`, logout, level unload and chunk unload clear transient watcher/transfer state. The local atmosphere implementation proves a concrete precedent: watcher tracking via `ChunkWatchEvent.Sent/UnWatch`, `PacketDistributor.sendToPlayer` for snapshots and `sendToPlayersTrackingChunk(level, chunkPos, payload)` for deltas, cursor/budget-limited snapshot transfer, revisioning, and bounded resync validation (`AtmosphereVisualServerHooks.java:42–64, 75–105, 132–189`). Reuse the protocol shape if useful, not atmosphere's data/service dependencies. Also test ordering/races: initial chunk block packet versus custom snapshot, edit during snapshot, duplicate/out-of-order revisions, and chunk watch/unwatch.

Do not confuse `AttachmentType.Builder.sync(...)` (used by entity/item/device attachments in `ModDataAttachments`) with a verified “sync chunk attachment to all tracking clients” path: the current atmosphere chunk type intentionally has no `.sync`, and its world visuals require custom networking. Even if the pinned attachment API offers a chunk attachment sync feature, verify its watcher audience, initial chunk sync, mutation notifications and removal/empty-state semantics before choosing it over explicit payloads.

For M2's cable geometry, use client-only rendering code registered from `MoonStation14Client`/the client event bus. Existing code establishes an `AFTER_PARTICLES` world render hook and buffer/vertex rendering (lines 115–150 onward), sufficient precedent for an initial custom face overlay, not evidence of a baked model/BER API. Build the canonical face plane and edge turn from `Direction` in pure geometry helpers, draw based on synchronized current cable state, suppress only top-face cable when a typed station floor's current `tile_finish` is not `NONE`, and check neighboring loaded chunk state only (no client chunk loads). Render layer, depth/offset, ambient occlusion, resource reload, camera transforms, block culling and exact edge continuity are still **hypotheses**; visual GameTest is not enough, schedule owner-run client validation later. Use project-owned temporary art until the required SS14 per-file import/license ledger is completed.

## Existing local seams to follow / avoid

- `MS14Provider` is a holder abstraction for `ItemStack`, `Entity`, `BlockEntity`, and `IAttachmentHolder` (`MS14Provider.java:22–90, 93–162`). `getDetached` avoids mutating absent data (`:33–48`) and `updateIfChanged` compares immutable snapshots (`:164–183`). This is not a position/chunk/world store, and there is no chunk overload or world graph policy. Do not shoehorn chunk wire records through `MS14Provider` or `MS14Bridges`.
- `ModDataAttachments.ATTACHMENT_TYPES` and `register(IEventBus)` establish registration style. Add independent power attachment registration there only when implementing; do not edit it in this audit.
- `ModBlocks` uses deferred block registration and block-item helpers; `ModBlockEntities` binds BEs to specific registered block types. No current station floor is an eligible substrate.
- `AtmosphereChunkData` demonstrates a versioned sparse `Codec`, validation, deterministic storage, omitted empty state, and immutable snapshots. It stores atmosphere cells; do not share/reuse the type.
- Atmosphere visual sync already handles bounded chunk snapshots/deltas and resync. It is a pattern only and atmosphere is separately owned; power implementation must not edit/depend on atmosphere code.

## Minimal runnable M1 slice

M1 is the first runnable, bounded proof—not a power graph prototype:

1. Register one typed station floor whose small `tile_finish` property is `NONE|STEEL`; register separate tile and dedicated pry items.
2. Implement server-authoritative item interactions that change only the expected floor property; installation consumes a tile outside creative mode, pry returns one, and the registered substrate remains unchanged.
3. Verify both visual state variants and item interactions; persistence and vanilla synchronization come from ordinary BlockState. Cable-record preservation/occlusion is a follow-on integration check when M2 introduces cable records.
4. Add focused automated verification of property bounds and interaction transitions as appropriate; do not simulate client rendering as server assertions.
5. For rendered proof, defer client visual smoke to owner-run validation; no server launch/manual test was performed during this audit.

## Stop gates and unresolved decisions

- **M1 decision:** tile is targeted with a separate tile item and removed with a dedicated pry item. Same floor block and position are retained; state change is vanilla BlockState. Installation consumes in survival; pry returns one tile. This replaces the prior chunk-tile-bit recommendation.
- **Before M2 implementation:** use mapped MC/NeoForge 21.1.224 source/jar to verify exact `LevelChunk` attachment get/set, codec serialization and dirty-save behavior; verify exact lifecycle method callback signature/call timing for `onRemove` and behavior for same-block state changes versus replacement; produce the host-replacement strategy above. Do not rely on event assumptions for `setBlockAndUpdate`.
- **Before first client claim:** exact payload handling thread/registration and tracking-client distribution must be verified with a two-client test or equivalent harness; snapshots/deltas/reconnect/chunk-watch races and bounds tested. Attachment sync may replace custom payload only after demonstrated.
- **Before render acceptance:** test face orientation, all six directions, tile occlusion, edge turn, neighboring/adjacent chunk, depth/culling/resource reload on client. `RenderLevelStageEvent` remains only an initial candidate.
- **Before M3:** topology face-pair truth table and host-removal behavior approved; unloaded boundaries remain unknown and never force-load.
- **Before importing SS14 art:** complete per-file source/revision/license/attribution/modification/notice ledger from the sprint guide.
- This audit did not decide multi-tier same-face conflicts, tile replacement rules for legacy saves, palette/geometry style, graph unknown-state behavior or any HV/MV/APC transfer/capacity policy. These remain owner/design contracts from the sprint docs, not API facts.

## Validation record

Documentation-only audit. No Gradle command, compile probe, GameTest, server launch, or manual client/game test was run. Required git checks are recorded in the implementation report; source evidence above comes from local source/config/tests and cache artifact presence, not newly executed runtime behavior.
