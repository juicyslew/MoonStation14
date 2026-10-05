# Third connected feedback

Superseded where noted by `connected-feedback-04.md`: puddle sipping is immediate, Alt+E is mapped-click/tick-owned, and generic presentation snapshots now feed the shared UI renderer. Validation below belongs to the third pass.

This supersedes the second pass's contextual Shift+E decision. Automated validation is not owner-connected acceptance.

## Controls and interaction

- Pinned SS14 defaults separate `SmartEquipBelt` (Shift+E) from `AltActivateItemInWorld` (Alt+E and Alt+left-click). The port now follows that separation: Shift+E always uses the existing belt equipment/quickstore/take gesture, including when holding liquid containers. Alternate world use performs liquid transfer or empty-hand puddle drinking. Screen/free-cursor restrictions still apply.
- The port's default Left Alt pointer toggle now occurs only on an unused tap's release. Using Alt as a keyboard/mouse modifier does not open the free cursor before an alternate gesture can run. Remapped non-Alt pointer bindings keep their normal press behavior. Normal E dispatch is tick-owned; alternate E is event-owned, preventing duplicate storage-open toggles.
- Native survival/creative inventory screens are suppressed while a committed body is controlled, preventing vanilla E from stealing the same gesture. Mod storage screens and uncontrolled native inventory are unaffected.
- Alternate in-hand activation is a separate SS14 function (Alt+Z); the port has not implemented that capability. Normal Z remains normal in-hand use, not backpack/belt equipment.
- The jug's item translation is `item.moonstation14.jug: Jug`.
- Midair catching is intentionally retained. Selected pickup still transfers the whole authoritative stack into an available hand; airborne motion is not a rejection condition.
- Generic “server denied” feedback remains diagnostic for now. A future concise reason such as “Your hand is full” should replace generic feedback for known admission failures; no universal notification framework was added.

## Jugs and puddles

- Jug break qualification requires actual contact and uses total pre-contact travel speed, rather than only the blocked velocity axes. The existing profile threshold is unchanged. Fast motion alone does not break an airborne jug.
- Filled wall impacts seek the first loaded floor/puddle below the resolved, near-side contact column. The downward scan is bounded to 512 blocks and never loads chunks. Successful spills conserve contents and discard the jug; rejected/partial admission retains authoritative contents/remainder. No falling-fluid entity simulation was added.
- Puddle outline height is `.25 / 16 = 1/64` block, not four pixels. Exact outline-ray targeting was fragile. `hands/live/PuddleTargetProbe` is shared by client nomination and authoritative drink start: bounded aim tolerance, five-block reach, loaded-only candidates, actual-shape hit ordering, and visibility to the real puddle shape. It does not enlarge the block's physical shape or trust a client position without validation. Ongoing drink access remains separate from initial aim.

## Storage footprints

`storage/grid/ItemFootprintProfile`, `ModItemFootprints`, and `ItemFootprints` resolve inherited prototype cells from `data/moonstation14/moonstation14/item_footprint/`. The catalog participates in existing server/client prototype synchronization. Unknown IDs retain the prior one-cell fallback; installed machinery/BlockItem hosts have not been audited as portable SS14 items.

| Core items | Footprint |
| --- | --- |
| Steel, glass, plastic, cloth, wood, jug, both floor tiles | 2×2 |
| Paper, chemistry bottle, waffle | 1×1 |
| Crowbar, floor-tile pry tool, cable cutter, atmosphere analyzer, grenade, all three cable coils | 1×2 |
| Backpack | 4×4 |
| Leather waist bag/belt | 6×6 |
| Pickaxe | Five-cell T: three-cell top row, two-cell central stem |

Outer item footprints do not change container capacity, stack counts, loose-object physical dimensions, or sprite-fitting rules. The pry tool uses a local crowbar-equivalent mapping. Jug 2×2 is explicitly required by the owner. Existing placements are not migrated or repacked.

Grid payload constructors/codecs validate structure without consulting a logical-side catalog; authoritative live host reads/writes validate full geometry using the server catalog. Storage mutations are gated until the relevant side's catalog is available. Snapshot decoding therefore does not depend on a server catalog on clients.

### Upstream evidence

SS14 pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `Resources/keybinds.yml`; `Content.Shared/Item/SharedItemSystem.cs:GetItemShape`; `Resources/Prototypes/item_size.yml`. Missing explicit item shape uses the resolved size's default shape.
- `Entities/Objects/Materials/Sheets/{metal,glass,other}.yml`, `Materials/materials.yml`, `Objects/Misc/{paper,tiles}.yml`.
- `Objects/Weapons/Melee/mining.yml`, `Objects/Tools/{crowbars,tools,cable_coils}.yml`, `Objects/Specific/atmos.yml`, `Objects/Weapons/Throwable/base_grenades.yml`.
- `Clothing/Back/backpacks.yml`, `Clothing/Belt/{base_clothingbelt,waist_bags}.yml`, `Objects/Specific/Chemistry/chemistry-bottles.yml`, `Objects/Consumable/Food/Baked/misc.yml`. Chemistry bottle is the 30-unit Tiny bottle, not a 50-unit drinking bottle. Waistbag inherits Ginormous outer size despite its smaller internal storage grid.

## Validation and connected recheck

All **1,907 unit tests** and **439 required GameTests** pass (`build/loose-feedback-03-unit-final.log`, `build/loose-feedback-03-gametest-recheck.log`); `git diff --check` passes. Independent reviews accepted the input dispatch/native-screen suppression and authoritative footprint/catalog integration.

Earlier runs exposed and corrected the floor-impact fixture's initial-column assumption and the thin-ray fixture's far-edge intersection. Superseded full runs also failed unrelated APC manual-reclose, timed-stun, and timed-alert fixtures; their logs and useful failure-run directories are retained. No power/status implementation changes were made to obtain the final green run. Successful and superseded disposable runs without useful unrelated failures are removed.

New puddle coverage checks that a full five-block native outline ray misses when aimed 0.19 block above the thin puddle's top from four blocks away, while the shared probe and production drink start accept it. Wall occlusion and beyond-five-block reach are negative cases. These trusted body fixtures do not establish connected authenticated-player input or packet acceptance. New collision fixtures exercise actual floor/wall motion, and selected pickup coverage explicitly uses an airborne moving target.

Connected recheck: confirm Shift+E quickstores ordinary items and jugs; use Alt+E/Alt+left-click to sip a looked-at puddle with an empty hand; throw filled jugs horizontally at floors and elevated walls; verify enlarged storage footprints and rotation. Saved key overrides may need resetting/rebinding. Connected appearance/input and paired-player acceptance remain outstanding.
