# Stripping audit and mapping

## Reference

Equipment bugfix note: the inert local `utility_case` resolves physical size
`Ginormous`, rather than `Small`. Pinned SS14 `ToolboxElectrical` inherits
`ToolboxBase`'s `Item.Size = Ginormous`; the local starter has no toolbox storage
behavior, and this size correction does not add a storage component. The equipment
size validator reuses the existing shared `ItemFootprintProfile.ItemSize` enum.
Existing
`EquipmentFitPolicy` consumes resolved physical size for pocket admission, so
the case is denied pockets while suit-storage eligibility remains governed by
the existing outerwear permission/dependency rules.

Local SS14 checkout verified at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Findings below establish upstream
behavior, not a claim that the Minecraft runtime already implements it.

Relevant human ancestry: `MobHuman` inherits `AppearanceHuman` and
`BaseSpeciesMobOrganic`; the organic/base species branch reaches `BaseSpeciesMob`,
which inherits `StripableInventoryBase` and adds `Stripping`. The inventory base
composes Inventory, InventorySlots, Strippable and the stripping BUI. Human uses
the `human` inventory template; Hands' `CanBeStripped` defaults true.

Sources: `Resources/Prototypes/Body/Species/human.yml`,
`Resources/Prototypes/Body/species_base.yml`,
`Resources/Prototypes/InventoryTemplates/inventorybase.yml`, and
`human_inventory_template.yml` in that inventory-template directory.

## Resolved data to map

| Upstream data | Verified default / human override | Local responsibility |
|---|---|---|
| Stripping | Actor marker, no delay fields | Typed character capability |
| Strippable hand delay | 4 seconds | Typed target composition |
| Hands.CanBeStripped | true | Resolved hand capability |
| Slot.stripTime | 4 seconds by default | Existing inventory-template slot data |
| Human 3-second slots | shoes, eyes, ears, pockets, suit storage | Human template |
| Human 6-second slots | jumpsuit, outer clothing, ID, belt, back | Human template |
| Slot.stripHidden | false; human pockets true | Template + strip-view presentation |
| Clothing.StripDelay | additive, default 0 | Existing resolved clothing composition |
| Critical/dead target | multiply duration by 1/2 or 1/3 | Existing typed MobState consumer |

Use the existing prototype merge/serialization machinery and test inherited
defaults and overrides. Preserve current fit/dependencies: pockets/ID need the
jumpsuit; suit storage needs qualifying outerwear. `simple_coat` still has no
AllowSuitStorage. No hardcoded slot-name timing table in the stripping service.

Timing references: `Content.Shared/Inventory/InventoryTemplatePrototype.cs`,
`Content.Shared/Strip/Components/StrippableComponent.cs`,
`Content.Shared/Hands/Components/HandsComponent.cs`,
`Content.Shared/Clothing/EntitySystems/ClothingSystem.cs:OnItemStripped`, and
`Content.Shared/Mobs/Systems/MobStateSystem.Subscribers.cs:OnGettingStripped`.
Strip-time events apply item, actor, then target modifiers; each stage clamps
`initial * multiplier + additive` to at least zero. Only supported live modifiers
belong in this slice; do not invent stealth/skill components with no consumers.

## Actions and DoAfter rules

`Content.Shared/Strip/SharedStrippableSystem.cs` supplies `StartStripInsertInventory`,
`StartStripRemoveInventory`, `StartStripInsertHand`, `StartStripRemoveHand`,
`OnStrippableDoAfterRunning`, and `OnStrippableDoAfterFinished`.

All four paths use every-tick attempts, BreakOnDamage, BreakOnMove and NeedHand.
`Content.Shared/DoAfter/DoAfterArgs.cs` defaults: distance 1.5, movement threshold
0.3, damage threshold 1 in a single event, BreakOnHandChange true and
BreakOnDropItem true. Removals explicitly allow hand changes; insertion retains
the exact active item. NeedHand means at least one hand, not an empty hand.

Movement cancellation checks actor displacement and change in actor-target
distance; this is not unconditional cancellation on any target movement. Do not
substitute ingestion's movement policy. The timed runtime currently supplies
actor displacement and damage; target-distance and hand semantics need a narrow
consumer/shared-runtime extension with regression coverage.

Duplicate condition is SameTool, not SameTarget. Distinct source items may have
concurrent removals; repeat action on the same source cancels/blocks its duplicate.
All starts and completions recheck source identity, access, droppability and
equip/unequip constraints. Inventory removals use PickupOrDrop; target-hand
removals drop then attempt pickup. Full actor hands do not universally forbid
removal. Supporting garment removal recursively clears dependent slots and
drops their items by the target.

Further references: `Content.Shared/DoAfter/SharedDoAfterSystem.Update.cs`,
`SharedDoAfterSystem.cs:IsDuplicate`, and
`Content.Shared/Inventory/InventorySystem.Equip.cs`.

## Opening, visibility and deliberate presentation changes

No dedicated strip keybind was established. Upstream opens through its Strip
verb, complex `ActivateInWorldEvent`, or dragging a strippable target onto the
actor. Actor Stripping, hands/access/interaction checks apply; self-opening is
excluded. Ordinary activation rejects combat mode; the verb explicitly allows
opening in combat. Local normal activation is E, routed through existing world
interaction code; no generic verb system or world-character dragging is required
for this panel slice. Record any unsupported combat-gate correspondence explicitly.

Upstream hidden-pocket substitution applies only to occupied pockets. It masks
the ordinary UI, but upstream notes the real entity may still reach clients.
The port instead sends occupancy/hidden presentation, not private item identity,
in an authorized viewer-specific strip snapshot. This small projection difference
does not change the ability to remove an occupied hidden slot. Never reuse the
public appearance broadcast or target owner action credentials for the strip UI.

The requested three-column compact draggable panel is an explicit layout
adaptation. Reuse `EquipmentPanelLayout`/`HandBarLayout` metrics and coordinates,
`PointerOverlayWindow`, `PointerOverlayRegistry`, `WindowGeometryController`, and
the existing `StoragePopupScreen` movement-friendly host. Upstream's larger BUI
layout does not override the owner's requirements.

Client references: `Content.Client/Inventory/StrippableBoundUserInterface.cs` and
`Content.Client/Strip/StrippableSystem.cs`. No new audio requirement was established.

## Implemented correspondence and deliberate boundaries

Resolved local human composition maps upstream `Stripping` to character capability,
`Strippable` hand delay 4 seconds, and `Hands.CanBeStripped=true` to hand eligibility
only (not inventory eligibility). Inventory template slot stripTime defaults to 4
seconds; human overrides 3/6 seconds and hidden pockets. Clothing delay is additive
default 0; target critical/dead modifiers are half/third. These values are consumed
from resolved prototypes, not a stripping-service slot-name table. A computed zero
delay still takes a minimum one tick. Sources and defaults are listed above.

The ordinary SS14 1.5 reach is translated through the shared flat Minecraft +1.5
block offset, producing a 3.0-block action range with physical-eye loaded LOS;
this is a deliberate dimensional/occlusion adaptation, not exact SS14 geometry.
Actor displacement and radial actor-target distance delta 0.3 cancel; a single
damage event threshold is 1. Actor movement/cancellation semantics are covered by
the focused tests. Pickup removal tries active hand, another empty hand, then the
normal actor world-root drop path. Dependent equipment drops belong to the target.
The window closes when physical roots exceed the same 3.0-block action range; this
is an explicit requested local UX policy, not verified generic upstream BUI behavior.
Within-range line-of-sight occlusion can still leave the view unavailable. Shared
normal E6-block AIM ceiling does not
widen stripping's mapped 3.0-block root reach. Real and spectator carriers are excluded.

No unsupported combat-mode, cuff/restraint, or skill system is claimed or added.
The authorized positive real-connected-player packet route remains manual: authority
rejects FakePlayer and there is no accepted native fixture for a connected client;
negative public-route authority and production consumer paths have native coverage.

Existing `PointerOverlayRegistry`, `WindowGeometryController`,
`EquipmentPanelLayout`, `ItemUiRenderer`, and `EquipmentPanelRenderer` are reused;
the authoritative LiveHands/gateway, read-only preflight, and compensated cross-body
transfer paths are extended rather than copied. No parallel screen or transfer
authority was introduced. The owner closed the sprint on 2026-10-09; connected
graphical walking/dragging and real two-player packet effects were not
assistant-validated. No claim of full upstream parity is made. `HANDOFF.md`
records the durable closed-sprint state and remaining validation boundaries.

Implementation review fixes: full-hand actor remains a valid removal candidate;
occupied-source fixture corrected; push/range/client-carrier selection addressed;
shared painting compact bounds dependencies addressed. The generic BUI close
policy, exact native connected-player acceptance, and unsupported branches remain
explicit gaps. `BACKEND-CONTRACT.md` documents the protocol boundary.
