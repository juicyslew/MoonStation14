# Armor wearable-model request — approved and imported

Status (2026-11-04): Request `thrown-combat-basic-armor-wearables-20261103-r1`,
produced by `gpt-6.1-sol-basic-armor-r1`, is approved and imported through the managed
asset workflow. The owner separately authorized the exact delivery digest. Six
runtime files match that delivery; resolved armor prototypes and the production
client catalog now bind them. See `ASSETS.md` for hashes, attribution, material
routing and remaining GPU-review boundaries. The historical request brief below
does not modify the frozen submitted context; no duplicate request was created.

Requester worktree: `MoonStation14-agent-02`.

Please check whether approved versions of these two wearable meshes already exist and reuse them if so. Otherwise provide:

1. `armor_vest` - `ClothingOuterArmorBasic`
2. `basic_helmet` - `ClothingHeadHelmetBasic`

Each deliverable must be an authored body-worn mesh export with required textures, canonical export path, and complete attribution/provenance/approval manifest. Bind to the accepted human WIDE/Steve rig attachments, following `docs/equipment-sprint/authored-wearables/binding_contract.json` and the established wearable export contract. The local runtime consumes `equipment_item.clothing.worn_model`; supply canonical mesh paths, `body_shape: WIDE`, and appropriate head/torso attachment bindings. Report existing approved source/export and provenance rather than recreating it.

Upstream pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Correct source references are `Resources/Prototypes/Entities/Clothing/Head/helmets.yml` (`ClothingHeadHelmetBasic`) and `Resources/Prototypes/Entities/Clothing/OuterClothing/armor.yml` (`ClothingOuterArmorBasic`). Verify prototype texture/RSI references and names against those pinned YAMLs at delivery review. Corresponding imagery is under `Resources/Textures/Clothing/Head/` and `Resources/Textures/Clothing/OuterClothing/`.

The published request context is frozen and may retain earlier source-path/prototype typos. This local correction is not a request update or communication to the claimant; do not issue a duplicate request while r1 is claimed. Verify the corrected source references during delivery review.

Artwork only: no armor stats, physical hitboxes, or mechanics were changed. No
placeholder or unapproved wearable models were added. Existing inventory icons
remain unchanged; approved body-worn models replace the prior visuals.
