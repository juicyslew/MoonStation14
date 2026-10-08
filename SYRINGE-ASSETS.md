# Syringe item and asset provenance

The syringe is a Tiny, one-cell item with one empty vessel and no preloaded reagent. Its 15-unit vessel size, initial draw mode, and 5-unit selected dose are resolved from the `syringe` medical-item prototype and its inherited policy; a stack with no `SyringeStackData` reads those defaults rather than baking them into native item properties.

The item sprite source is the pinned SS14 checkout at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, `Resources/Textures/Objects/Specific/Chemistry/syringe.rsi`, composed by `Resources/Prototypes/Entities/Objects/Specific/Chemistry/chemistry.yml` (`BaseSyringe`, lines 438-475). The copied RSI metadata is retained verbatim (`CC-BY-SA-3.0`; original CEV-Eris attribution and subsequent bluespace/cryo attribution are in `meta.json`). This adaptation copies the ordinary syringe `syringe_base0` and `syringe1/2` states only and uses them as generated Minecraft item layers; bluespace/cryo/mini/projectile/in-hand states are not imported. The syringe item itself has no invented use sound.

Minecraft model predicates are `moonstation14:fill` (0..1 vessel fraction) and `moonstation14:syringe_mode` (draw 0, inject 1). The reagent layer uses the existing reagent mixture tint hook. The footprint inherits `moonstation14:tiny`, preserving the upstream single-cell classification.
