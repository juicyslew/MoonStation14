# Basic material stack limits

Verified against SS14 commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
The local `ModItems` entries are registered Minecraft items (not SS14 entity
prototypes); their `Item.Properties.stacksTo` values are the live stack-limit
consumer. This change alters only maximum stack capacity, not item counts,
recipe quantities, footprints, loose-object profiles, or tool/container limits.

| Local item | Previous → current max | Upstream stack prototype / resolved max |
|---|---:|---|
| steel | 8 → 30 | Stack `Steel` → `BaseSheetStack`, 30 |
| glass | 8 → 30 | Stack `Glass` → `BaseSheetStack`, 30 |
| plastic | 8 → 30 | Stack `Plastic` → `BaseSheetStack`, 30 |
| cloth | 8 → 30 | Stack `Cloth` → `BaseMediumStack`, 30 |
| wood | 8 → 30 | Stack `WoodPlank` → `BaseMediumStack`, 30 |
| paper | 8 → 30 | Stack `Paper` → `BaseSheetStack`, 30 |
| metal rod | 30 → 30 | `MetalRod` → `BaseSheetStack`, 30 (unchanged) |

Upstream sources: `Resources/Prototypes/Stacks/base_stacks.yml` defines
`BaseSheetStack` and `BaseMediumStack` maxCount 30; sheet entities and stack IDs are in
`Resources/Prototypes/Entities/Objects/Materials/Sheets/{metal,glass,other}.yml`
and `Resources/Prototypes/Stacks/{Sheets/metal,Sheets/glass,Sheets/other,Sheets/organic,cloth,organic,other}.yml`.
Paper material corresponds to `SheetPaper → SheetOtherBase → BaseItem`, with
`Stack.stackType: Paper`; the stack prototype is in `Stacks/Sheets/organic.yml`.
It is distinct from writable `Paper` item behavior. The local mapping covers the
requested basic crafting materials, not every item. Metal rods already matched
the upstream limit.
SteelItem previously overrode the caller's properties to 8; it now preserves
the registration's explicit 30 while retaining its tool behavior.
