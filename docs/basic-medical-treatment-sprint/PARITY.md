# Medical item composition parity (work unit 1)

Verified against SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Source: `Resources/Prototypes/Entities/Objects/base_item.yml`,
`Specific/Medical/healing.yml`, and `Specific/Medical/healthanalyzer.yml`.

Resolved ancestry is `BaseItem → BaseHealingItem → Ointment / Brutepack / Gauze`,
with `Ointment1 → Ointment`, `Brutepack1 → Brutepack`, and `Gauze1 → Gauze`
children each overriding Stack count to 1. `BaseHealingItem` inherits BaseItem
and supplies item sprite/size and Healing application defaults; each healing item allows Biological,
Ointment applies Heat/Cold/Shock −5 and Caustic −1.5, Brutepack Blunt/Piercing/
Slash −5, and Gauze Slash −5/Piercing −10 with bleed modifier −10.
Treatment delay/self multiplier resolve to 3 seconds/3. HandheldHealthAnalyzer
directly parents BaseItem (not BaseHealingItem), and composes HealthAnalyzer,
ItemToggle, ActivatableUI and UserInterface; it has no cell/battery component.
Its policy defaults are scan delay .8, scan range 2.5, update interval 1,
UI range 2.

The local `medical_item` family is now a typed composed record: item is required,
stack is optional, and healing and analyzer/UI/toggle policies are optional
and mutually constrained. Abstract BaseItem has neither behavior policy;
BaseHealingItem inherits it and adds Biological healing defaults (3-second delay,
self penalty 3); concrete supplies add their typed negative damage deltas and
sounds. Analyzer is independently composed from BaseItem. Base-item physics,
held visuals, UI machinery and toggle runtime remain Minecraft/native behavior,
not placeholder components. Typed patient `Damageable` composition carries the
verified biological container on Human/Pig; treatment consumes this descriptor
rather than matching entity class names. Human also has the scanner-relevant
`HumanoidProfile` species field; Pig does not. See `NEWPATIENT-COMPOSITION.md`.
`ModMedicalItems.createStack`
maps resolved Ointment/Brutepack/Gauze (and `*1`) / analyzer prototypes to
registered Minecraft items and takes the initial count from resolved Stack
composition (normal MC item stack limits stay unchanged).

The four registered Minecraft item IDs for the parallel asset handoff are
`ointment`, `brutepack`, `gauze`, and `handheld_health_analyzer`.

SS14 item/UI sprites and medical sounds have been imported with adjacent metadata.
See `ASSETS.md` for provenance, including the scanner sound's CC-BY-4.0 credit.
Upstream credits for the four treatment sounds remain unresolved and are recorded
in `docs/audio-unknown-provenance.md`; no license or author was fabricated.

## Minecraft input adaptation

Medical hand capability is selected from the resolved client `medical_item` catalog,
not item-class or hardcoded-item checks. The existing `Z` in-hand input retains
self-treatment for healing supplies; analyzer activation opens its UI, while the
resolved ItemToggle `onUse` policy optionally toggles scanning (the pinned
HandheldHealthAnalyzer has `onUse: false`).
Because the first-person camera cannot nominate its own body by clicking it,
`Shift+Z` with an analyzer sends the same exact-body scan intent for the controlled
body. Primary click on a separately ray-resolved supported living body nominates its
UUID; Ctrl-click pull remains higher priority, and ordinary block/item use remains
unchanged when no medical body is aimed. This is an input adaptation, not a change
to SS14 scan or treatment authority/range rules.
