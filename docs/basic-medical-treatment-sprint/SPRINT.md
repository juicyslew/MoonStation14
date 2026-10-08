# Basic Medical Treatment + Health Analyzer

## Status and goal

**Closed by owner acceptance on 2026-10-07.** First aid, the persistent analyzer,
basic syringes and requested inventory/UI/audio follow-ups are complete. Start
with `HANDOFF.md` for current architecture, decisions, paths and known gaps.
No commit or push was requested.

Final validation: 2,596 unit tests passed; focused fall/audio 3/661 selected and
all passed; full native 649/661 passed with 12 unclassified failures. See
`VALIDATION.md` and `../critical-death-prone-sprint/FALL-AUDIO.md` for evidence.
Owner acceptance does not establish automated authenticated-player request,
client-rendering or audible-playback coverage. FakePlayer authority was not
weakened. Historical requirements and earlier results below are retained as
context, not a declaration of current pending sprint work.

Upstream reference verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
`C:/Users/William/Documents/SS14-dev/space-station-14`.
Relevant existing handoffs: critical-death-prone, timed-interactions-ingestion,
blood-bleeding, hands-inventory-program, grid-storage, machine-ui-visual-kit.

## Scope and verified composition

### Basic supplies

Resolve `BaseItem → BaseHealingItem → Ointment / Brutepack / Gauze` locally,
including inherited healing defaults and their single-unit children. Supported
local components must have live consumers; do not copy unsupported YAML fields
merely to resemble upstream. Stack prototypes start at 10; children start at 1.

| Supply | Per completed application |
|---|---|
| Ointment | Heat/Cold/Shock −5 each, Caustic −1.5 |
| Brutepack | Blunt/Piercing/Slash −5 each |
| Gauze | Slash −5, Piercing −10; bleeding modifier −10 |

Healing is restricted to compatible Biological damage composition, not entity
class names. Base application delay is 3 seconds. Self-treatment uses the actual
upstream damage/critical-threshold penalty: `delay × max(1, 1 + damage / critical
threshold × (multiplier − 1))`, multiplier 3; missing critical threshold uses
`delay × multiplier`. Resolve the patient's actual threshold, not a fixed 100.

Reuse the visible timed-operation runtime. These healing actions break on
movement and explicitly **do not break on damage**; do not inherit ingestion's
damage-interruption settings. Admission and completion require real controlled
body/hand authority, exact source and target identity, reach/LOS, relevant injury
or treatable bleeding, and current source availability. Consume one unit only
on successful application; rejection/cancellation/no applicable effect consumes
nothing. Preserve the audited repeat/duplicate behavior, not a new auto-repeat
rule: successful treatment repeats while applicable injury and supply remain,
recalculating self-delay each time. Gauze need not stop all bleeding in one application.
Both treatment and scanning use the initial active hand/item, a 0.3-tile user
movement threshold, and 1.5-tile ongoing target/tool distance; neither breaks on
damage. These differ from ingestion's permitted hand transfers.

Use negative typed damage deltas through `DamageSystem`, not native health or
group-sequential `applyEvenHealthChange`. Ordinary healing continues to respect
resolved mob thresholds and their revival policy; default retained Dead bodies
must not revive. No new wound/anatomy simulation.

### Handheld health analyzer

Resolve `BaseItem → HandheldHealthAnalyzer`; relevant runtime composition is
HealthAnalyzer, ItemToggle, ActivatableUI and UserInterface. **No power-cell or
battery component is present in this prototype.**

- Scan delay 0.8 seconds, live subject range 2.5 tiles, update interval 1 second.
- Reuse hand authority, item targeting and the timed runtime; audit the scanner's
  actual DoAfter arguments separately from treatment arguments.
- Server owns scanner identity, linked subject, active/inactive state and updates.
  Opening/displaying a window never grants treatment or scan authority.
- Read typed damage and mob state, live body temperature, and usable blood fraction.
  Blood uses reference-reagent availability, not total reagent volume. Existing
  `BloodReducer.usableFraction` and resolved `BloodstreamPolicy` provide that rule.
- Display identity/species, portrait, Active/Inactive, Alive/Critical/Dead,
  temperature in °C/K, blood percentage, total damage, positive grouped/type
  damage sorted by descending group total, bleeding and unrevivable alerts.
  Missing supported state displays Unknown; do not invent physiology values.
- Use the pinned scrollable handheld layout. A read-only comparison of current
  upstream `HealthAnalyzerControl.xaml` and code-behind found the same layout;
  no newer alternate design was established. The owner's screenshot is an older
  PDA-style reference, not a requirement to build PDA cartridges.

## Window persistence and lifetime

The window is draggable and remains drawn when the mouse is recaptured. Cursor
capture and window visibility are separate states, as in storage. Explicit close
closes the window; it does not invent a scanner-stop rule.

| Event | Required behavior |
|---|---|
| Mouse recaptured | Window remains visible, position retained |
| Subject beyond 2.5 tiles | Keep last readings; show inactive/out-of-range; retain link |
| Subject returns in range | Resume live readings |
| User beyond 2 tiles from scanner's resolved host position | Close bound window |
| Scanner toggled off, dropped, inserted into storage | Stop scan and clear link; inactive window may remain within UI range |
| Select another hand | No new hand-only auto-close rule |
| Subject deleted / scanner removed / authority reset | Clear invalid sessions; no stale authoritative actions |

SS14's generic UI range is user-to-scanner, not scanner-to-patient. Stored/held
stack host positions must resolve through existing inventory/world ownership;
do not measure against the detached Minecraft carrier. Scanner link, stream state
and window lifetime must remain distinct.

## Ordered work units

1. **Resolved composition and contracts.** Add a narrow medical prototype catalog
   using existing prototype resolution/reload infrastructure; local items currently
   have feature catalogs rather than a universal entity/item prototype registry.
   Register supplies/scanner, storage footprints and SS14 assets. Specify effective
   treatment results, scanner snapshots, exact identities and lifetime predicates.
   Test inherited defaults/overrides and actual runtime lookups before UI work.
2. **Authoritative gameplay.** Treatment/bleeding completion and stack consumption;
   scanner targeting, delayed scan, periodic updates and host transitions. Reuse
   `BodyHandRequestService`/`BodyHeldItemActions` held-use dispatch and hand tokens.
   Add a body-target route only where existing requests cannot express it. Extend
   timed-operation capabilities only for verified consumer needs.
3. **Persistent analyzer presentation and integration.** Separate scanner workspace
   and server state from screen/input state. Integrate a thin render/input delegate
   with storage's shared pointer entry and `StoragePopupScreen`; draw the analyzer
   in gameplay HUD mode too. Do not introduce a competing mouse-capture lifecycle
   or a speculative universal-window framework. Preserve storage windows and
   existing shortcuts. Add SS14 icons, sprites, sounds and attribution.
4. **Acceptance.** Independent parity/architecture review, focused tests and a short
   connected checklist. Do not fix unrelated existing UI, power or despawn gaps.

## Validation requirements

- Prototype inheritance/defaults, reload and live resolved consumers, including
  nondefault values and unsupported/missing composition rejection.
- Actual self/other treatment, correct typed healing/bleed reduction, source count,
  no-op/cancel/replacement/storage/range rejection, movement versus damage breaks,
  Critical recovery and default Dead non-revival. Preserve worn storage and hands.
- Scanner snapshots/unknown fields, blood reference accounting, live update cadence,
  exact subject identity, out-of-range resume and dropped/stored host transitions.
- Window persists across pointer recapture; explicit close, drag, scrolling,
  resizing/GUI scale and coexistence with storage behave correctly.
- Full lightweight suite and tagged native fixtures in an isolated ignored `build/`
  directory. Run/report the full native suite if integration changes are broad;
  distinguish known deferred failures from new regressions. Tags are not isolation.
- Concise connected checks for real input/rendering; no repeated diagnostic launches.
  Keep logs under ignored `build/validation-logs/` and preserve useful failure evidence.

## Upstream source index

- `Resources/Prototypes/Entities/Objects/{base_item.yml,Specific/Medical/healing.yml,Specific/Medical/healthanalyzer.yml}`.
- `Content.Shared/Medical/Healing/{HealingComponent,HealingSystem}.cs`.
- `Content.Server/Medical/{HealthAnalyzerSystem.cs,Components/HealthAnalyzerComponent.cs}`.
- `Content.Client/HealthAnalyzer/UI/{HealthAnalyzerControl.xaml,HealthAnalyzerControl.xaml.cs,HealthAnalyzerBoundUserInterface.cs}`.
- `Content.Shared/UserInterface/{ActivatableUIComponent,ActivatableUISystem}.cs`;
  Robust `UserInterfaceComponent.InterfaceData` and `SharedUserInterfaceSystem`.
- `Content.Shared/Body/Systems/SharedBloodstreamSystem.cs:GetBloodLevel`.
- Medical and health-analyzer RSI metadata provide CC-BY-SA-3.0 attribution.
  Verify sound provenance during import; unknown attribution belongs in
  `docs/audio-unknown-provenance.md`.
