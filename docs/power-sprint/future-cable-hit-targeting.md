# Future work: geometry-based cable cutting

**Status: visible-lane server selection implemented under the current wirecutter bugfix request.**

This records upstream context and remaining future parity work. The current owner-authorized bugfix implements server-side selection from the admitted visible lane; it does not authorize the deferred timing or electrical behavior below.

## SS14 reference behavior

The following behavior is based on the referenced SS14 source paths and prototype definitions:

- `Resources/Prototypes/Entities/Structures/Power/cables.yml` defines `CablePhysBase` with `Clickable` and `InteractionOutline`. Its physics is anchored and static, with `canCollide: false`. There is no explicit fixture or clickable bounds definition there. Thus the cable is not selected by treating a solid physics AABB as its hit target.
- On the client, `GameplayStateBase.GetClickableEntities` and `Content.Client/Clickable/ClickableSystem.CheckClick` use visible sprite/RSI click maps to query click candidates. A cable is a separate entity, so the chosen candidate has that cable entity's UID. A sprite's clickable pixel region is not a physics AABB.
- Cable prototypes use different draw depths: HV uses `ThickWire`, while MV and LV use `ThinWire`. When cable sprites overlap, visible sprite pixels and their draw depth/order provide the basis for resolving which individual cable entity is clicked. The exact per-pixel hit-mask and tie-breaking behavior has not been independently verified in a live game; do not claim more specific pixel semantics than the source establishes.
- On the server, `Content.Server/Power/EntitySystems/CableSystem.cs` receives the target cable entity UID in `OnInteractUsing`. It checks the held tool's cutting quality and runs the default one-second `UseTool` do-after. On successful completion it checks electrification, drops/deletes the targeted cable entity as appropriate. Target identity is therefore preserved through to the server interaction rather than inferred from a broad shared physics volume.
- A cable hidden by `SubFloorHide` has its clickable sprite hidden and `BlockInteractions=true`. A T-ray reveal may show the sprite, but revealing it alone does not remove the interaction block or authorize cutting through the intact tile. The tile must be removed before ordinary cutting is permitted.

These are reference behaviors, not a literal ECS port or a claim of Minecraft API compatibility.

## MoonStation14 current state and mismatch

MoonStation14 stores cables as records identified by `(host BlockPos, face, tier)` and draws procedural client quads. The tiers are records on the same host face, not individual Minecraft cable entities with separate engine hitboxes. Both ordinary and body-owned wirecutter paths now ask the shared server selector to resolve the admitted hit against visible cable geometry and remove only its selected tier. Null/missed hits deny even when only one tier exists. A matching offhand coil is not required and cannot override the clicked lane.

The former offhand selector was a **temporary ambiguity workaround** and is superseded by the current bugfix's shared server geometry selection. Selection is instantaneous; SS14-like one-second timing remains deferred, as do electrocution and other future behavior. This work claims no GPU/visual or connected-gameplay acceptance.

## Deferred design direction

Future parity work may consider a client-side selection hint, but server validation remains authoritative. The current server selector uses shared quad geometry, face-local orientation, visibility, and draw priority to resolve a clicked lane without broad host-block targeting.

The server revalidates admission, loaded state, permissions, record existence, host/face validity, concealment/visibility rules, and held-tool ownership before cutting. A client cannot nominate a tier. The normal mainhand cable cutter suffices. An interruptible approximately one-second action may be considered for SS14 parity, but timing and interruption semantics remain deferred pending owner agreement.

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

Visible-lane selection is implemented, while timing and electrical parity remain deferred. No visual/GPU or manual-game acceptance is claimed by this note.
