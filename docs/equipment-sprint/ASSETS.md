# Equipment artwork and provenance

Pinned Space Station 14 source: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
All 11 source icons below are extracted unchanged except explicit grayscale
recoloring of the jumpsuit and shoes. Each output icon has an adjacent
`.png.attribution.txt` preserving source RSI/state, license, and copyright.

| Local item | SS14 source RSI / state | Local adaptation boundary |
|---|---|---|
| `grey_cap` | `Clothing/Head/Soft/greysoft.rsi`, `icon` | No flipping feature |
| `grey_jumpsuit` | `Clothing/Uniforms/Jumpsuit/color.rsi`, `icon` | Gray tint `#b3b3b3` |
| `simple_coat` | `Clothing/OuterClothing/Coats/labcoat.rsi`, `icon` | Visual stand-in only; no labcoat behavior |
| `black_gloves` | `Clothing/Hands/Gloves/Color/black.rsi`, `icon` | No synthetic-glove properties |
| `black_shoes` | `Clothing/Shoes/color.rsi`, `icon` | Gray tint `#3f3f3f` per SS14 prototype |
| `glasses` | `Clothing/Eyes/Glasses/glasses.rsi`, `icon` | No vision correction |
| `red_tie` | `Clothing/Neck/Ties/redtie.rsi`, `icon` | No tie-storage state/function |
| `black_bandana` | `Clothing/Head/Bandanas/black.rsi`, `icon_mask` | `ClothingMaskBandBlack` art; no folding/blocker behavior |
| `earpieces` | `Clothing/Ears/Headsets/base.rsi`, `icon` | Cosmetic only; no radio |
| `blank_id_card` | `Objects/Misc/id_cards.rsi`, `default` | No access/record behavior |
| `utility_case` | `Objects/Tools/Toolboxes/toolbox_yellow.rsi`, `icon` | Inert local case; not a container or pressure tank |

All these RSI metadata files specify CC-BY-SA-3.0. Exact copyright notices are
retained verbatim in per-file attribution records. The two recolors preserve
source alpha and multiply each source RGB channel by the listed tint.

Local PNG SHA-256 (after the documented tint where applicable):

| File | SHA-256 |
|---|---|
| `grey_cap.png` | `5587878C6E9E3AE045C164C7CA38390FBDFDA8B36265ACA24A28859EF06AB37F` |
| `grey_jumpsuit.png` | `C35EAF5992C5673FB52206825D9CB7EF9827424E47B53BABC5172A8407786EF2` |
| `simple_coat.png` | `1CD1656B517EC33D10A04F49C25CDF491DF494A89C99C8B52DE7EA9B07B25148` |
| `black_gloves.png` | `C428FCCE5810E9F2DB67EC8ED8C6AB69C520AA179FA382F63D3EEAF70BDA6EE3` |
| `black_shoes.png` | `47F851F0532B115F1207E834D539B16181AB0B24CD210D2AD24B81716135AD3D` |
| `glasses.png` | `F46BD3C2A91FBD7620A65E7323A69DA434EAE207640A6257829CD665E810F522` |
| `red_tie.png` | `74B7178D271FA5AAA970D8EB1B466D3C0EE0489A7ACC5F292744CBDBDE614B2D` |
| `black_bandana.png` | `6525D78602DAE843A8A2671C58BC546629E03D2DAB257F7BAA7A7647AF093C71` |
| `earpieces.png` | `9C788B36480E92A95E47D4D24A14C2C77FA026C38CB61B584A2D7F7648745538` |
| `blank_id_card.png` | `726F69645042AACCED17092C3FF712698BD9415F16FDB93585C75BD30B38D23F` |
| `utility_case.png` | `B815473DCE2A9CD8F5C03B573C4D8B15A440971781D90A74B68132D752FE0789` |

## Inventory interface artwork

Copied from SS14 `Resources/Textures/Interface/Default/Slots/`. The toggle source
is `toggle.png`, which `Content.Client/UserInterface/Systems/Inventory/Widgets/InventoryGui.xaml`
references as `Slots/toggle`. Slot files land at
`textures/gui/equipment/<slotid>.png`; `toggle.png` is exposed as
`textures/gui/equipment/inventory_toggle.png`.

| Local path ID | Source filename |
|---|---|
| `shoes` | `shoes.png` |
| `uniform` | `uniform.png` |
| `suit` | `suit.png` |
| `gloves` | `gloves.png` |
| `neck` | `neck.png` |
| `mask` | `mask.png` |
| `glasses` | `glasses.png` |
| `ears` | `ears.png` |
| `head` | `head.png` |
| `pocket` | `pocket.png` (shared by both pocket slots) |
| `suit_storage` | `suit_storage.png` |
| `id` | `id.png` |
| `belt` | `belt.png` |
| `back` | `back.png` |
| `inventory_toggle` | `toggle.png` |

Every interface PNG has a neighboring attribution record. The upstream
`Interface/Default/meta.json` gives CC-BY-NC-SA-3.0 and credits goonstation at
commit `e77d85d9c1d93aa32da7702737ceeac2b56738ac`, slightly modified by
PixelTK, with ID slots by YoungThugSS14. Preserve its non-commercial restriction.
The imported toggle provides one arrow state only. HUD collapse mirrors that
existing texture at render time to indicate the opposite action; no derivative
PNG or independent attribution/authorship claim is introduced.

## Native generated item models

Each `models/item/<id>.json` uses `minecraft:item/generated` and binds layer0 to
`moonstation14:item/<id>`. This is the stable texture/model contract for all 11
native item ids; the flat generated-item rendering is a temporary held-item
placeholder, not a worn-body mesh. Replacement anchor guidance is in
[`MODELLER.md`](MODELLER.md). The items do not imply sensors, radio, access,
folding, armor, or storage behavior. Existing bag and belt assets are reused
unchanged; no pouch art/item is added. No audio or medical assets/count behavior
are changed.

Separate eleven-piece authored worn-mesh resource import and provenance:
[`authored-wearables/ASSETS.md`](authored-wearables/ASSETS.md).
