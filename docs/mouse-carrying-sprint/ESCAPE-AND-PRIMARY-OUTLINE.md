# Escape jump frames and primary interaction outline

Status: changes awaiting a fresh integration gate. The owner confirmed r38 held
camera motion is smooth; this pass must preserve that correction.

## Escape behavior

Verified upstream revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`MobMouse` explicitly includes `CanEscapeInventory` at
`Resources/Prototypes/Entities/Mobs/NPCs/animals.yml:1882`. The complete parent
chain includes SimpleMobBase, SimpleSpaceMobBase, BaseSimpleMob, BaseMob and
BaseControllable, with the animal/medical/atmospheric branches; no parent removes
the mouse's escape capability. An initial research claim that mice lacked this
component was incorrect.

`EscapeInventorySystem` starts a five-second do-after with `NeedHand=false`,
movement cancellation (default 0.3) and damage cancellation (single event at least
1). Holding adds no random contest roll or longer duration. Incapacity can prevent
interaction/resisting; a real drop cancels the attempt. SS14 has no jump mechanic.
Carrier motion must not be confused with displacement of the contained body in
its own parent coordinate frame.

The port previously sampled `body.position - current root.position`, which can
pair a previously pinned child with a newly moved holder. A normal jump exceeds
the 0.3 threshold despite unchanged containment-local position.

`BodyContainmentService` now records the exact root position accompanying each
canonical body pin. Escape samples the body relative to that recorded root frame.
Controlled-body pinning in `GroundedHarnessWorldStep` reuses the same service.
This derived pose history is not a second ownership registry. Exact membership,
owner validity and cleanup remain authoritative. Actual child displacement,
damage, incapacity, stale custody and world drop still interrupt; thresholds,
duration and release snapshot validation are unchanged.

The canonical registered-player escape fixture now submits a production holder
jump, attests the old mixed-frame delta exceeds 0.3 while the canonical frame is
stable, and retains the same operation/start tick through original-body release.
Existing true-relative-displacement and drop regressions remain required.

## Outline routing

`BodyHandClient.resolvePrimaryWorldTarget` is consumed by ordinary clicks,
gameplay-window clicks and `selectedEntityForOutline`. It reuses
`PrimaryWorldGesture` routing priority and existing resolved medical item/actor
nomination. A medical body's priority over a pile/block is now reflected in the
outline; an occupied item without a body-target route does not invent one.

The existing entity bounding-box renderer and background-block suppression remain
the rendering owners. This is not a mouse-only highlight rule and adds no gameplay
capability or prototype field. Policy tests cover medical/pile/block priority,
medical misses, occupied-hand routes and empty-hand portable pickup.

## Boundaries

Continuous-jump observer landing presentation remains optional/unresolved.
Same-holder physical hand-transfer escape continuity is still a known parity gap;
no completed correction is included here. Do not interpret the stale snapshot
guard as an intended SS14 hand-transfer cancellation rule.

After a validated build: check five-second escape while the holder repeatedly
jumps; bruise-pack/analyzer/syringe outlines on mice and other supported bodies;
appropriate item/block fallback with unrelated held items; and r38 camera
smoothness. Policy/native checks do not establish physical input or GPU visuals.
