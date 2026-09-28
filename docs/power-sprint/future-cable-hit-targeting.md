# Future work: geometry-based cable cutting

**Status: deferred design note only. This document does not authorize implementation.**

This records how SS14 distinguishes cables that occupy the same host face and a future direction for replacing MoonStation14's temporary offhand-coil tier selector with geometry-based targeting. It is not a request to change code, assets, or other sprint documents.

## SS14 reference behavior

The following behavior is based on the referenced SS14 source paths and prototype definitions:

- `Resources/Prototypes/Entities/Structures/Power/cables.yml` defines `CablePhysBase` with `Clickable` and `InteractionOutline`. Its physics is anchored and static, with `canCollide: false`. There is no explicit fixture or clickable bounds definition there. Thus the cable is not selected by treating a solid physics AABB as its hit target.
- On the client, `GameplayStateBase.GetClickableEntities` and `Content.Client/Clickable/ClickableSystem.CheckClick` use visible sprite/RSI click maps to query click candidates. A cable is a separate entity, so the chosen candidate has that cable entity's UID. A sprite's clickable pixel region is not a physics AABB.
- Cable prototypes use different draw depths: HV uses `ThickWire`, while MV and LV use `ThinWire`. When cable sprites overlap, visible sprite pixels and their draw depth/order provide the basis for resolving which individual cable entity is clicked. The exact per-pixel hit-mask and tie-breaking behavior has not been independently verified in a live game; do not claim more specific pixel semantics than the source establishes.
- On the server, `Content.Server/Power/EntitySystems/CableSystem.cs` receives the target cable entity UID in `OnInteractUsing`. It checks the held tool's cutting quality and runs the default one-second `UseTool` do-after. On successful completion it checks electrification, drops/deletes the targeted cable entity as appropriate. Target identity is therefore preserved through to the server interaction rather than inferred from a broad shared physics volume.
- A cable hidden by `SubFloorHide` has its clickable sprite hidden and `BlockInteractions=true`. A T-ray reveal may show the sprite, but revealing it alone does not remove the interaction block or authorize cutting through the intact tile. The tile must be removed before ordinary cutting is permitted.

These are reference behaviors, not a literal ECS port or a claim of Minecraft API compatibility.

## MoonStation14 current state and mismatch

MoonStation14 stores cables as records identified by `(host BlockPos, face, tier)` and draws procedural client quads. The tiers are records on the same host face, not individual Minecraft cable entities with separate engine hitboxes. Current cutter interaction uses the mainhand cable cutter; when a face has multiple tiers and the clicked face cannot determine the tier, a matching offhand cable coil selects it. A lone tier can be cut without that selector.

The offhand selector is a **temporary ambiguity workaround**, not SS14-equivalent targeting and not a desired permanent interaction contract. It asks the player to identify a tier through inventory instead of determining which visible cable lane was clicked. No cable renderer, storage, hit shape, or tool behavior is authorized to change under this note.

## Deferred design direction

If separately approved, replace the selector with client-side selection against the visible cable geometry, returning the exact `(host position, face, tier)` candidate to a server-validated interaction. A ray/quad intersection is the preferred direction if the renderer can provide stable selectable geometry; a conservative narrow hit shape is an alternative if exact quad picking is impractical. Selection should account for the same tier-offset lanes used by the renderer, their face-local orientation, visibility, and depth/overlap priority, so co-located LV/MV/HV cables are distinguishable without broad host-block targeting.

The client selection is only an input hint. The server must revalidate reach, loaded state, permissions, record existence, host/face validity, concealment/visibility rules, and tool eligibility before starting and completing the cut. A client must not be able to spoof a distant, covered, hidden, or nonexistent tier. The normal mainhand cable cutter should suffice; do not require an offhand coil or add a tier-specific cutting tool. An interruptible approximately one-second action may be considered for SS14 parity, but timing and interruption semantics require owner agreement before implementation.

Covered top-face cable remains non-cuttable while a floor tile blocks interactions. A future T-ray visualization by itself must not grant cutting access beneath an intact tile; any exception would need a separate explicit design and approval.

## Future acceptance questions and tests

Before implementation, agree with the owner on the picking contract, overlap priority, server request shape, and whether the SS14-like timed action is wanted. Then test at least:

- All six host faces and their rotations, including reversed/negative-direction runs and face-local lane transforms.
- LV, MV, and HV on one face, each selected independently; overlap edges and corners; three cables in all relevant directions; deterministic depth priority where lanes overlap.
- Genuine corners, neighboring hosts, and chunk seams, ensuring the picked record matches the visible segment rather than a nearby host or another tier.
- Floor-cover concealment: covered cable cannot be cut; removing the tile permits cutting the exact same retained record. A future T-ray reveal alone still does not permit cutting under the tile.
- Forged/stale tier and host requests, excessive reach, unloaded chunks, missing/replaced hosts, permission failures, and attempted visibility bypasses are rejected server-side.
- Two clients observing the same cables can each target the intended tier; selection has bounded client/server cost with dense cable layouts and no unbounded per-frame or per-interaction search.
- If a timed action is approved: correct tool quality, interruption/cancellation, electrification recheck, and no removal before successful completion.

Until the owner separately authorizes this work, the offhand selector remains the existing temporary behavior. No implementation or manual-game behavior is claimed by this proposal.
