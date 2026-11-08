# Derived gun ROOT bindings

This file records integration-only ROOT metadata changes separately from the frozen artist import receipt. No OBJ, MTL, PNG, UV, or display-pose content is authored here.

## Native item-model properties

- `weapon_pistol_mk58`: `bolt_open` and `magazine_absent` select the four R4 artist aliases; absent-magazine predicates follow present-magazine predicates.
- `weapon_rifle_lecter`: `ammo_fill` (rounds in the magazine divided by 25; chamber excluded) and `bolt_open` select magazine levels 0–4; `magazine_absent` selectors are last.
- `magazine_pistol`: `ammo_fill` selects empty, levels 1–4 and full using the existing 10-round fractions.
- `magazine_rifle`: a zero-threshold not-full baseline is superseded by the full alias at `ammo_fill >= 1` (last matching override wins).
- `weapon_laser_gun`, `weapon_energy_shotgun`, `weapon_laser_svalinn`: five charge aliases selected from whole-shot `ammo_fill`; Svalinn's `cell_absent` selector is last and independent of residual fill.
- `power_cell_small`: raw `charge` thresholds choose empty at zero, partial above zero, and full at one.
- `speedloader_magnum`: seven appearance levels use six-round `ammo_fill`; the full appearance uses the co-located artist base alias, while levels 0–5 use their supplied `_fill_N` aliases.
- `weapon_sniper_mosin`: `solution_present` selects the supplied `weapon_sniper_mosin_solution_present` model alias. The alias and dispatch ROOT both retain the selective shared wrapper. Tint is limited to artist-authored neutral mask UV [.13671875,.26953125]..[.173046875,.27484375], not the default metal UV chart [.40234375,.00390625]..[.438671875,.00921875].

The solution property reads the stack-owned REAGENT component; the standard item-stack component sync therefore drives the client property and color consumer after refill/dose. Upstream `SolutionContainerVisualsComponent.ChangeColor` defaults true; `SharedSolutionContainerSystem.UpdateAppearance` writes fill fraction and solution color, and `UpdateChemicals` refreshes it. Client coloring is fill-layer-only. Reference: pinned SS14 `Content.Shared/Chemistry/EntitySystems/SharedSolutionContainerSystem.cs` and `Content.Shared/Chemistry/Components/SolutionContainerVisualsComponent.cs`; parity notes in `PARITY.md`.

All referenced artist variants are co-located under `models/item/guns`, avoiding ROOT self-reference. These are derived native binding edits, not changes to source package or byte-exact import receipts.
