# Equipment worn-model contract

The item identifiers and native-generated item-model texture references in this
sprint are stable inputs for replacement artwork. These are simple starter
placeholders, not exact SS14 leaves and not evidence of full leaf-prototype
parity. Keep replacements under `assets/moonstation14/models/item/` and
`assets/moonstation14/textures/item/` unless renderer integration explicitly
requires another path.

| Item | Current item model / texture | Intended worn region / anchor |
|---|---|---|
| `grey_cap` | `models/item/grey_cap.json`, `textures/item/grey_cap.png` | head; cap crown and brim |
| `grey_jumpsuit` | `models/item/grey_jumpsuit.json`, `textures/item/grey_jumpsuit.png` | torso and legs; uniform layer |
| `simple_coat` | `models/item/simple_coat.json`, `textures/item/simple_coat.png` | torso and sleeves; outer garment layer |
| `black_gloves` | `models/item/black_gloves.json`, `textures/item/black_gloves.png` | both hands |
| `black_shoes` | `models/item/black_shoes.json`, `textures/item/black_shoes.png` | both feet |
| `glasses` | `models/item/glasses.json`, `textures/item/glasses.png` | face/eyes |
| `red_tie` | `models/item/red_tie.json`, `textures/item/red_tie.png` | front of uniform torso, neck anchor |
| `black_bandana` | `models/item/black_bandana.json`, `textures/item/black_bandana.png` | lower face; no flip state |
| `earpieces` | `models/item/earpieces.json`, `textures/item/earpieces.png` | ears; cosmetic only, no radio |
| `blank_id_card` | `models/item/blank_id_card.json`, `textures/item/blank_id_card.png` | chest/ID position; no access data |
| `utility_case` | `models/item/utility_case.json`, `textures/item/utility_case.png` | suit-storage visual, small case |

The existing `moonstation14:bag` and `moonstation14:belt` now have authored worn
models: backpack on the back/torso anchor and belt around the waist.
Keep their existing item models and storage behavior. The two pockets are slots,
not additional garments; their contents do not need worn models.

The native item models are inventory/held placeholders only. Held models should
provide sensible `ItemDisplayContext` transforms for third-person hands, ground
and GUI; first-person arms and held-item viewmodels are not rendered. Worn assets are a separate renderer deliverable: prefer
body-part-specific meshes/sprites for articulated arms and legs, with torso,
head, hand and foot anchors aligned to the humanoid skeleton. Do not implement a
generic wearable-model framework in these asset replacements.

Renderer contract: procedural worn placeholders are anchored to articulated
humanoid model parts (`head`, `body`, each arm, and each leg), never to one rigid
whole-body mesh. Replacement artwork must retain these semantic anchors so limbs
continue to follow animation. The shared presentation plan is
`ms14/equipment/client/EquipmentRenderPlan`; it resolves only the public body
appearance projection and resolved clothing data. It is consumed by the F5
third-person character layer and observer layer; there is no custom first-person
appearance pass.

Wide/Steve authored visuals use `AuthoredWearableMeshes` and
`AuthoredWearableRenderer`, selected through resolved `Clothing.worn_model` in the
shared plan. See `authored-wearables/binding_contract.json` and `ASSETS.md`.
The procedural fallback is `EquipmentPlaceholderGeometry`: cuboids
are rendered from each planned garment's resolved packed ARGB color. Its
`render(Garment, Anchor, PlayerModel, slim, PoseStack, VertexConsumer, light)`
entry point accepts one selected planned anchor and is reusable by other
perspectives; it does not inspect entity inventory or item identifiers. Baked
parts are cached, and the layer supplies slim shape from the character entity.
For leg pieces the vanilla player leg is 4x12x4 in local coordinates. Uniform
leg cuboids are slightly inflated in X/Z and terminate at y=9.5; shoe cuboids
start at y=9 and extend to y=12.25. The cuff overlap avoids exposed ankle skin,
while the shoe bottom passes the vanilla leg bottom at y=12 rather than sharing
its coplanar plane. Both left/right meshes use these bounds on the articulated
leg anchors.

For color/layout guidance: grey uniform/cap, pale coat, dark gloves/shoes,
red tie, black bandana, neutral-grey earpieces, blue blank card, grey utility
case. These colors communicate placeholders only.
