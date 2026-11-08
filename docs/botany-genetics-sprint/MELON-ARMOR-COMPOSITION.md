# Melon output armor boundary

## Requirement and verified source

The two melon hats are distinct Knife-butcher outputs, not fruit/slices. They inherit `ClothingHeadBase → Clothing → BaseItem`: HEAD, Small, special-digestion Edible, food10u/Fiber10. Hat leaves add their own sprite/worn RSI and coefficient-only Armor: watermelon Blunt0.95; holy watermelon Caustic0.95. No head-hit localization follows from this.

Pin: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, Robust `3136118b5338ef2d9580178caf5c723e65eb76e7`. Sources: clothing head/base/hats YAML; `ArmorComponent`, `SharedArmorSystem`, `DamageableSystem.API/Events`, `DamageSpecifier.ApplyModifierSet`, `InventorySystem.Relay`, and `Content.Shared/FixedPoint/FixedPoint2.cs:53–66`.

Source order: target resistances, then sequential worn inventory armor, then global modifiers. `ignoreResistances` skips armor. Negative healing is unchanged; coefficient-only armor modifies positive values without turning damage into healing. Nonpocket worn inventory slots relay; held/nested items do not. Each item quantizes back to source cents before the next: float×100, signed epsilon0.00001, integer truncation toward zero, not ordinary rounding. Two0.95 applications to5 yield4.75 then4.51.

## Local decision

Extend the existing typed `EquipmentItemData` composition with optional coefficient-only Armor, using its existing merge strategy, prototype loading and aggregate sync. Do not create another item catalog, arbitrary resistance source, damage pipeline or head-specific special case. Do not expose flat reduction, price or examination fields without consumers. Coefficients must be canonical, finite and nonnegative; unusual values are local validation policy, not established source-deserializer parity.

One shared equipment-armor consumer resolves `LiveHands.equipmentSnapshot` and declared inventory order, selects slots matching the source `~POCKET` mask, and reads resolved equipment profiles. Extract the missing template lookup within `HandCapability` and reuse it for its existing equipment-slot resolver; do not duplicate character→inventory traversal. Missing/incompatible inventories and unresolved slots are neutral. No existing MAIN item gains armor in this work.

Slot filtering means source mask intersection, not a blanket POCKET veto: `InventorySystem.Relay.cs:139–143` uses `InventorySlotEnumerator`, whose `NextItem` (`InventorySystem.Slots.cs:312–327`) admits any slot intersecting `~POCKET`. Thus POCKET-only and empty flags are excluded, but mixed HEAD|POCKET is included. The parent verified these source lines directly after review raised the opposite interpretation; retain a mixed-flag regression with inclusion expected.

Reuse `DamageSystem` as the sole mutation/reconciliation boundary. For typed changes, apply armor before splitting positives/negatives and creating the existing pending transaction, so post-damage proportions use modified typed values and zeroed positives do not lose healing. At native incoming damage, use the existing source classifier and conversion, but skip matching pending typed transactions to prevent double application; native armor-bypass sources skip equipment armor. Preserve spectator guards, vanilla mitigation/cancellation, pending-stack ownership, final ledger reconciliation, blood and feedback events. Never apply armor again in `observePost`.

This is a current output dependency, not a general armor/locational-combat framework. Existing floats/vanilla-health conversion remain local adaptations; do not refactor unrelated damage behavior. No client-side predicted armor mutation is introduced.

## Evidence and scope

Implementation is sequenced: first the optional equipment composition/merge and pure coefficient kernel with focused tests (no live armor data); then the shared worn-inventory resolver and typed/native damage wiring, followed by transaction/native-fixture coverage. An unwired data/kernel phase is not a completed armor consumer or feature-readiness milestone.

Required focused checks: codec/inheritance/default absence and aggregate sync; canonical immutable coefficient maps; sequential cents, mixed types/healing/bypass and zero coefficients; actual slot-flag filtering/order, held/nested neutrality; typed/native exactly-once application and existing cancellation/mitigation reconciliation. Author native fixtures using existing body/equipment/damage owners, compile but do not execute them locally. Native/readiness checks remain whole-feature workflow-owned; passing JVM helpers is not live armor proof.

Melon item registration, authentic worn art, equipped output acquisition and owner acceptance remain separate unfinished work. CarvedPumpkin construction/light is not implemented by this armor decision.
