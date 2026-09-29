# G0 — upstream storage geometry observations

**Scope:** targeted source inspection only; this is not an implementation spec, a port of the upstream algorithm, or a claim of Minecraft prototype parity.

**Target checkout:** `C:\Users\William\Documents\SS14-dev\space-station-14`

**Revision:** `c9df5ef5d675b0d1d226828bddf6b78c28502d91`

## Observed upstream behavior

| Source | Observation |
|---|---|
| `Content.Shared/Storage/StorageComponent.cs` | `Grid` is a list of `Box2i` storage regions. Stored-item data includes each item's location and rotation. A storage region's rectangle should not be mistaken for a statement that every cell in an arbitrary region list is necessarily one contiguous filled grid. |
| `Content.Shared/Item/ItemComponent.cs` | Item shape is optional and represented as a list of `Box2i`. |
| `Content.Shared/Item/ItemSizePrototype.cs` | Item-size prototypes provide `DefaultShape`. |
| `SharedItemSystem.GetAdjustedItemShape` | Provides adjusted item shape for rotation and normalization. |
| `SharedStorageSystem.TryGetAvailableGridSpace` | Multi-box footprints are considered at 0°, 90°, 180°, and 270°; rectangular footprints have fast paths. This records supported orientations/observed behavior, not the precise algorithm to reproduce. |
| `SharedStorageSystem.ItemFitsInGridLocation` | Fit checking checks occupied cells, rather than treating an item's bounding box as filled. |

These observations establish neither a specific Minecraft data model nor parity for any item-size or backpack prototype. See the pinned checkout's sources above for implementation details; do not infer behavior beyond these recorded findings.

## Proposed sprint contract (separate from observations)

- Use a bounded occupancy-mask model for item footprints, with all four cardinal rotations and deterministic normalization. Equivalent rotations of regular rectangles may be deduplicated; explicit irregular masks remain optional and require owner approval.
- Model storage as a bounded domain plus an explicit mask of approved cells. Never treat a bounding box as filled unless the represented shape actually occupies all those cells.
- Keep search and geometry bounded by owner-approved limits. The sprint is not required to copy upstream's exact algorithm, storage representation, admission rules, or prototype behavior.

## Remaining G0 decisions

Owner approval is still required for quantitative grid/footprint/item-count/request-work bounds, irregular-mask scope, nesting, and acceptance of the hands contract. This audit does not mark G0 passed and does not establish prototype parity.
