# Door bump-open domain seam

The reference checkout was verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`Airlock -> BaseStructure` composes `Door`; `BaseStructure` has no parent.
Pinned `Content.Shared/Doors/Components/DoorComponent.cs` initializes
`BumpOpen = true`; `SharedDoorSystem.HandleCollide` checks that field, closed or
denying phase, and the contacting entity's `DoorBumpOpener` tag before `TryOpen`.
The resolved human chain is `MobHuman -> [AppearanceHuman,
BaseSpeciesMobOrganic] -> [MobBloodstream, MobRespirator, MobAtmosStandard,
MobFlammable, BaseSpeciesMob]`; `BaseSpeciesMob` includes the tag at
`Resources/Prototypes/Body/species_base.yml:155-160`. The task shorthand
`FullMobHuman` is not a prototype ID in this pinned checkout. Locally
the supported mapping is `AirlockData.Door.bumpOpen` (default true in the codec,
resolved from base_airlock) and the typed `DoorBumpOpenerComponent` on the human
character profile. No all-living-entity fallback is used.

`AirlockBumpSystem.beforeMoveContacts` nominates the nearest ordinary-world
swept contact plane at `Entity.move` HEAD. `afterWorldMove` validates actual final
leaf contact at RETURN before delegating to quiet activation: normal power,
bolt and phase rules remain authoritative, while no denial sound/state feedback
is generated. Horizontal swept collision prevents simultaneous gravity from
letting the supporting floor occlude grounded door contact; closed airlocks count
as blockers, and a foreground obstacle (or unloaded chunk boundary) wins over a
door farther along the move. Unloaded chunks are never queried or force-loaded.
A weak actor-local root/revision/readiness memo tracks only the currently touching
nearest blocking plane. Distinct door roots at that plane are deduplicated and
collected before any activation mutates a door, so a single move can open both
seam-adjacent doors; farther doors remain masked by the nearest door or opaque
obstacle. The contact set is replaced after each move, rearming only on exit or
root state/readiness change. World/frame memo episodes are independent so one
route cannot reset the other's continuous contact.

Native-frame movement delegates to this same activation owner through
`afterNativeMove` after fresh native carry. Selected blocker observations nominate
bounded coincident door pieces; each captured root/controller identity and final
current-pose SDK shape contact must revalidate. This covers a raised floor edge
being selected instead of the physically touching hatch, without proximity opening.
Owned bodies and generic living travel have separate, nonduplicating server
dispatch seams. See `docs/shuttle-relocation-sprint/BUMP-FEEDBACK.md` for paths,
verification status and remaining boundaries. Access electronics, welding and
remote activation are deliberately not added.
