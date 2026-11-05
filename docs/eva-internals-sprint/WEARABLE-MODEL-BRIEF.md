# EVA worn-model replacement request

This commissions art-only replacement of the existing rejected/unaccepted EVA wearable placeholders. Keep the current six runtime IDs and renderer contract; this request does not authorize code, slot/framework changes, icon replacement, or import.

## Scope and fit

Replace `eva_helmet`, `eva_suit`, `breathing_mask`, `breathing_mask_lowered`, `oxygen_tank`, and `emergency_oxygen_tank` together as a coherent, professionally finished and SS14-faithful visual family. Fit the canonical WIDE humanoid body and existing authored-wearable anchors. Keep head, torso, each arm, and each leg articulated independently; cleanly join suit panels without seams, clipping, or occlusion. Helmet needs a deep shell and readable clear visor. Raised mask belongs at mouth/head; lowered mask must visibly sit at lower neck/torso. Tanks should read as faceted oxygen cylinders and use only existing supported worn placement (back, suit storage, or belt). Do not render equipment in unsupported slots or introduce a new slot system.

Held/inventory presentation is separate. Preserve the existing five inventory icons; they are not evidence of worn-model completion. No unrelated wearable changes.

## Source and workflow

Use the SS14 source pinned to `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; relevant visual references are `Resources/Textures/Clothing/Head/Helmets/eva.rsi`, `Clothing/OuterClothing/Suits/eva.rsi`, `Clothing/Mask/breath.rsi`, and the actual tank RSI(s) with adjacent metadata. Verify source revision and exact tank paths before relying on them. Preserve per-file attribution/license in the delivered package. The source art is reference, not a request to extract sprites into the wrong pipeline.

Follow `docs/equipment-sprint/MODELLER.md`, `ASSETS.md`, and `authored-wearables/binding_contract.json`: use existing `AuthoredWearableMesh`/`EquipmentRenderPlan` compatibility and canonical anchors. Deliver the complete runtime group through the canonical asset workflow with editable project/source, provenance, manifest and review-image paths. Do not put binary models or image bundles in docs; do not install/import assets as part of authoring.

## Required review views

Provide front, side, and back fit views on the WIDE humanoid, plus walking/arm-and-leg articulation views. Show raised and lowered breathing-mask states distinctly. Views should make fit, articulation, clipping, seams, depth and occlusion inspectable before delivery. These are review evidence, not acceptance.
