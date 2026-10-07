# Audio provenance notes

* Airlock audio copied from pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`
  into `src/main/resources/assets/moonstation14/sounds/airlock/`:
  `airlock_open.ogg` and `airlock_deny.ogg` are attributed in
  `Resources/Audio/Machines/attributions.yml` to /tg/station, CC-BY-SA-3.0,
  source `https://github.com/tgstation/tgstation/tree/5f5002b21253354c20ea224be3f604d79299b37e/sound/machines`.
  `airlock_close.ogg` is attributed there to CEV-Eris (commit
  `789ed19064ac42376d2355192cd1069a45a310f1`), modified by mirrorcult,
  CC-BY-SA-3.0; source `https://github.com/discordia-space/CEV-Eris/commit/789ed19064ac42376d2355192cd1069a45a310f1`.
  Upstream metadata establishes these licenses; no independent upstream audio
  README/license text for the original creators was identified during this
  asset pass. Retain upstream attribution and do not imply vanilla Minecraft
  sound provenance.
* `boltsup.ogg` and `boltsdown.ogg` were copied unchanged from pinned SS14
  `Resources/Audio/Machines/`; `Content.Shared/Doors/Components/DoorBoltComponent.cs`
  identifies them as bolt up/down sounds. The pinned
  `Resources/Audio/Machines/attributions.yml` has no entries for either filename,
  and no separate audio README/license attribution was located. Provenance and
  license remain unresolved: do not claim CC-BY-SA or distribute until clarified.
* `panel_open.ogg` and `panel_close.ogg` are unchanged copies of pinned SS14
  `Resources/Audio/Machines/screwdriveropen.ogg` and `screwdriverclose.ogg`,
  respectively, selected by `WiresPanelComponent` defaults and consumed after
  successful `SharedWiresSystem` panel completion. No entries for either source
  filename occur in `Resources/Audio/Machines/attributions.yml`; license and
  provenance remain unresolved. Do not claim CC-BY-SA or distribute until clarified.
* `sounds/tools/wirecutter.ogg` is an unchanged copy of pinned
  `Resources/Audio/Items/wirecutter.ogg` (6,474 bytes). No matching filename entry
  was found in `Resources/Audio/Items/attributions.yml`; author, license, and
  provenance are unresolved. Do not claim a license or distribute until clarified.
  At pinned `WiresSystem.UpdateWires`, successful-eligible Cut and Mend branches
  call `Tool.PlayToolSound`; `SharedToolSystem.PlayToolSound` plays the resolved
  `Tool.UseSound` when non-null. Pulse does not call it. Local airlock cut/mend
   feedback follows the resolved held-tool `useSound` and accepted state mutation.
* Airlock shock sound uses the pinned SS14 `Resources/Audio/Effects/sparks1.ogg`
  through `sparks4.ogg` collection path copied to
  `assets/moonstation14/sounds/airlock/sparks/`. The pinned
  `Resources/Prototypes/SoundCollections/sparks.yml` identifies these as the
  sparks collection; no filename attribution was found in
  `Resources/Audio/Effects/attributions.yml`. Author, license, and provenance
  remain unresolved; preserve this ledger entry and do not represent the sounds
  as licensed/cleared.
* `airlock_electrify_on.ogg` and `airlock_electrify_off.ogg` are unchanged
  copies from pinned SS14 `Resources/Audio/Machines/`; the pinned machines
  attribution file identifies them as CC0-1.0 by ScarKy0, source
  `https://github.com/space-wizards/space-station-14/pull/32012`.
