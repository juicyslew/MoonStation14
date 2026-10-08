# Audio with unresolved file-specific provenance

This is the running list required by the [media reuse and attribution policy](media-use-and-attribution.md).
List **every imported audio file** whose file-specific license or attribution
remains unresolved, with a link to its adjacent notice. Planned files may be
listed provisionally, but must be explicitly marked **not imported**; add the
actual destination and notice, verify copied bytes, and update the status in
the same change that imports them. Do not use this list to reclassify assets
with already verified per-file metadata, such as the
[emote sounds](third-party-emote-audio.md).

| Status | Audio / upstream source | Local destination and adjacent notice | Evidence and open question | Next action |
|---|---|---|---|---|
| **Imported - file-specific license unresolved** | SS14 `Resources/Audio/Machines/machine_switch.ogg` at [`c9df5ef5d675b0d1d226828bddf6b78c28502d91`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Audio/Machines/machine_switch.ogg); 7,307 bytes, SHA-256 `e39e881d52afc399f50ac4ed9b79084736a3025cf10cde51460f106a2bace55d` | [`src/main/resources/assets/moonstation14/sounds/apc/machine_switch.ogg`](../src/main/resources/assets/moonstation14/sounds/apc/machine_switch.ogg) and [adjacent notice](../src/main/resources/assets/moonstation14/sounds/apc/machine_switch.ogg.license.txt). Verbatim byte copy; source/destination SHA-256 match. | No entry for `machine_switch.ogg` in pinned [`Resources/Audio/Machines/attributions.yml`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Audio/Machines/attributions.yml). Pinned [README](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/README.md) says most assets CC-BY-SA unless otherwise, not a file-specific attribution. License/author/original source URL: **UNRESOLVED - not MIT; do not assume CC for this file**. | Seek file-specific evidence or rights-holder permission; document response. Replace/remove asset and its registration if terms cannot be established for intended distribution. |
| **Imported - file-specific license unresolved** | SS14 `Resources/Audio/Machines/disposalflush.ogg`; 37,814 bytes, SHA-256 `82ed14d487928ca0e569c5e147394d520b82e85f1dbe22da490f9e6023cca744` | [`sounds/disposal/flush.ogg`](../src/main/resources/assets/moonstation14/sounds/disposal/flush.ogg) and [notice](../src/main/resources/assets/moonstation14/sounds/disposal/flush.ogg.license.txt) | No file-specific entry in pinned Machines attribution manifest. | Resolve rights or remove clip and declaration. |
| **Imported - file-specific license unresolved** | SS14 `Resources/Audio/Effects/clang.ogg`; 19,932 bytes, SHA-256 `861bf3e6ddff30f7c066037ae286caeda3ed7b010745c5c8a8d9c2bb5c994fc8` | [`sounds/disposal/pipe_clang.ogg`](../src/main/resources/assets/moonstation14/sounds/disposal/pipe_clang.ogg) and [notice](../src/main/resources/assets/moonstation14/sounds/disposal/pipe_clang.ogg.license.txt) | No file-specific entry in pinned Effects attribution manifest. | Resolve rights or remove clip and declaration. |
| **Imported - file-specific license unresolved** | SS14 `Resources/Audio/Effects/trashbag1.ogg` at [`c9df5ef5d675b0d1d226828bddf6b78c28502d91`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Audio/Effects/trashbag1.ogg); SHA-256 `77adcfb6ba63abf4e4646a26a922f26a9ce5509c5ab6d468df41996fa31fbcc2` | [`sounds/disposal/insert.ogg`](../src/main/resources/assets/moonstation14/sounds/disposal/insert.ogg) and [adjacent notice](../src/main/resources/assets/moonstation14/sounds/disposal/insert.ogg.license.txt); source/destination SHA-256 match. | No file-specific entry in pinned Effects attribution manifest. Author, original source, and license remain **UNKNOWN / UNRESOLVED**; no blanket license inferred. | Resolve rights or remove clip and registration. |
| **Imported - file-specific license unresolved** | SS14 `Resources/Audio/Items/drink.ogg` at [`c9df5ef5d675b0d1d226828bddf6b78c28502d91`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Audio/Items/drink.ogg) | [`src/main/resources/assets/moonstation14/sounds/ss14/items/drink.ogg`](../src/main/resources/assets/moonstation14/sounds/ss14/items/drink.ogg) and [adjacent notice](../src/main/resources/assets/moonstation14/sounds/ss14/items/drink.ogg.license.txt). Verbatim byte copy; source/destination SHA-256 match (`6c09f978706f20302eafbd79a03ac25cc719fb34dfe1443251a026eda6867b1f`). | File-specific author, license, and original source were not found in pinned upstream evidence. **Author and license unresolved; this record is not a permission or license claim.** | Seek file-specific evidence or rights-holder permission; replace/remove if terms cannot be established for intended distribution. |
| **Imported - file-specific license unresolved** | SS14 `Resources/Audio/Items/ratchet.ogg` at pinned revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; SHA-256 `64c5112f85c4ba1df4a80623c2171c9d344c27cf7ee531d58574c6162079ea4b` | [`src/main/resources/assets/moonstation14/sounds/ss14/tools/ratchet.ogg`](../src/main/resources/assets/moonstation14/sounds/ss14/tools/ratchet.ogg) and [adjacent notice](../src/main/resources/assets/moonstation14/sounds/ss14/tools/ratchet.ogg.license.txt); byte-for-byte verified. | No matching file entry in pinned `Resources/Audio/Items/attributions.yml`; author/license/original source unresolved. | Resolve file-specific rights or remove clip and registration if terms cannot be established. |
| **Imported - file-specific license unresolved** | SS14 `Resources/Audio/Items/crowbar.ogg` at pinned revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; SHA-256 `feae4d0be300a01494df5fadcaf466990b46b36b5227d09e31e6c9461835e9db` | [`src/main/resources/assets/moonstation14/sounds/ss14/tools/crowbar.ogg`](../src/main/resources/assets/moonstation14/sounds/ss14/tools/crowbar.ogg) and [adjacent notice](../src/main/resources/assets/moonstation14/sounds/ss14/tools/crowbar.ogg.license.txt); byte-for-byte verified. | No matching file entry in pinned `Resources/Audio/Items/attributions.yml`; author/license/original source unresolved. | Resolve file-specific rights or remove clip and registration if terms cannot be established. |
| **Imported - file-specific license unresolved** | SS14 `Resources/Audio/Items/screwdriver.ogg` at pinned revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; SHA-256 `5c9aff23dd10a3e8699e75d504603f80f914304135151fe78049aa98e15077b6` | [`src/main/resources/assets/moonstation14/sounds/ss14/tools/screwdriver.ogg`](../src/main/resources/assets/moonstation14/sounds/ss14/tools/screwdriver.ogg) and [adjacent notice](../src/main/resources/assets/moonstation14/sounds/ss14/tools/screwdriver.ogg.license.txt); byte-for-byte verified. | No matching file entry in pinned `Resources/Audio/Items/attributions.yml`; author/license/original source unresolved. | Resolve file-specific rights or remove clip and registration if terms cannot be established. |
| **Imported - file-specific license unresolved** | SS14 `Resources/Audio/Items/screwdriver2.ogg` at pinned revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; SHA-256 `07e8f9c55556ae90ed756e57ddacbf44ab7ba435adb0bc850941ba0f4fc04c2c` | [`src/main/resources/assets/moonstation14/sounds/ss14/tools/screwdriver2.ogg`](../src/main/resources/assets/moonstation14/sounds/ss14/tools/screwdriver2.ogg) and [adjacent notice](../src/main/resources/assets/moonstation14/sounds/ss14/tools/screwdriver2.ogg.license.txt); byte-for-byte verified. | No matching file entry in pinned `Resources/Audio/Items/attributions.yml`; author/license/original source unresolved. | Resolve file-specific rights or remove clip and registration if terms cannot be established. |
| **Imported - file-specific license unresolved** | SS14 `Resources/Audio/Items/welder.ogg` at pinned revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; SHA-256 `79aeecd39b7695d0a08c5516b3fc38ea84385dd863388055133d422299d368a0` | [`src/main/resources/assets/moonstation14/sounds/ss14/tools/welder.ogg`](../src/main/resources/assets/moonstation14/sounds/ss14/tools/welder.ogg) and [adjacent notice](../src/main/resources/assets/moonstation14/sounds/ss14/tools/welder.ogg.license.txt); byte-for-byte verified. | No matching file entry in pinned `Resources/Audio/Items/attributions.yml`; author/license/original source unresolved. | Resolve file-specific rights or remove clip and registration if terms cannot be established. |
| **Imported - file-specific license unresolved** | SS14 `Resources/Audio/Items/welder2.ogg` at pinned revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; SHA-256 `865efd6ada43bf70f85fbfdc6fdfb160973fd702dab7c1d2aeed78f99774e887` | [`src/main/resources/assets/moonstation14/sounds/ss14/tools/welder2.ogg`](../src/main/resources/assets/moonstation14/sounds/ss14/tools/welder2.ogg) and [adjacent notice](../src/main/resources/assets/moonstation14/sounds/ss14/tools/welder2.ogg.license.txt); byte-for-byte verified. | No matching file entry in pinned `Resources/Audio/Items/attributions.yml`; author/license/original source unresolved. | Resolve file-specific rights or remove clip and registration if terms cannot be established. |

When an unresolved entry is cleared, retain a brief dated disposition (evidence
and exact license, or replacement/removal and affected path) here, and update
or remove the adjacent notice and media as appropriate. Do not mark a planned
entry imported before the file and notice exist.

## Body-fall samples

The following upstream audio files have no matching per-file entry in the pinned
checkout's `Resources/Audio/Effects/attributions.yml`. Their author and license
are unverified; no per-file author or license claim is made here. Reference
checkout: Space Station 14 at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

| Upstream path | SHA-256 |
|---|---|
| `Resources/Audio/Effects/bodyfall1.ogg` | `E2C3ED1CE6300D96ED4BA757205F4233D5274DB171815C5AACA09A1DB9ACB73E` |
| `Resources/Audio/Effects/bodyfall2.ogg` | `934D7BC860E876705B09E62CFEACD050160143A9F0CF2BE4357B38EFD23184BD` |
| `Resources/Audio/Effects/bodyfall3.ogg` | `00583958A08EEC9550B93D9AF52691CEFDA49E467062AD70E92DE07EA031E381` |
| `Resources/Audio/Effects/bodyfall4.ogg` | `A22D748BD0ED41E7D53EB3DAF649DA528369880C9ABE295620DF1F13A1601109` |


## Airlock provenance records retained from the airlocks sprint

### Attributed airlock sounds

The following files were copied from pinned SS14 revision
`c9df5ef5d675b0d1d226828bddf6b78c28502d91` into
`src/main/resources/assets/moonstation14/sounds/airlock/`:

- `airlock_open.ogg` and `airlock_deny.ogg` are attributed in
  `Resources/Audio/Machines/attributions.yml` to /tg/station, CC-BY-SA-3.0:
  [source](https://github.com/tgstation/tgstation/tree/5f5002b21253354c20ea224be3f604d79299b37e/sound/machines).
- `airlock_close.ogg` is attributed there to CEV-Eris (commit
  `789ed19064ac42376d2355192cd1069a45a310f1`), modified by mirrorcult,
  CC-BY-SA-3.0:
  [source](https://github.com/discordia-space/CEV-Eris/commit/789ed19064ac42376d2355192cd1069a45a310f1).

Upstream metadata establishes these licenses; no independent upstream audio
README/license text for the original creators was identified during this asset
pass. Retain upstream attribution and do not imply vanilla Minecraft sound
provenance.

### Airlock sounds with unresolved provenance

- `boltsup.ogg` and `boltsdown.ogg` are unchanged copies from pinned SS14
  `Resources/Audio/Machines/`. `Content.Shared/Doors/Components/DoorBoltComponent.cs`
  identifies them as bolt up/down sounds. The pinned machines attribution file
  has no entries for either filename, and no separate audio README/license
  attribution was located. Provenance and license remain unresolved; do not
  claim CC-BY-SA or distribute until clarified.
- `panel_open.ogg` and `panel_close.ogg` are unchanged copies of pinned SS14
  `Resources/Audio/Machines/screwdriveropen.ogg` and `screwdriverclose.ogg`,
  selected by `WiresPanelComponent` defaults and consumed after successful
  `SharedWiresSystem` panel completion. Neither source filename appears in the
  machines attribution file; license and provenance remain unresolved. Do not
  claim CC-BY-SA or distribute until clarified.
- `sounds/tools/wirecutter.ogg` is an unchanged copy of pinned
  `Resources/Audio/Items/wirecutter.ogg` (6,474 bytes). No matching filename
  entry was found in `Resources/Audio/Items/attributions.yml`; author, license,
  and provenance are unresolved. Do not claim a license or distribute until
  clarified. At pinned `WiresSystem.UpdateWires`, successful-eligible Cut and
  Mend branches call `Tool.PlayToolSound`; `SharedToolSystem.PlayToolSound`
  plays resolved `Tool.UseSound` when non-null. Pulse does not call it. Local
  airlock cut/mend feedback follows the resolved held-tool `useSound` and
  accepted state mutation.
- Airlock shock audio uses pinned SS14 `Resources/Audio/Effects/sparks1.ogg`
  through `sparks4.ogg`, copied to
  `assets/moonstation14/sounds/airlock/sparks/`. The pinned
  `Resources/Prototypes/SoundCollections/sparks.yml` identifies these as the
  sparks collection; no filename attribution was found in the effects
  attribution file. Author, license, and provenance remain unresolved; do not
  represent these sounds as licensed or cleared.

### Attributed electrification sounds

`airlock_electrify_on.ogg` and `airlock_electrify_off.ogg` are unchanged copies
from pinned SS14 `Resources/Audio/Machines/`. The pinned machines attribution
file identifies them as CC0-1.0 by ScarKy0, source
[PR #32012](https://github.com/space-wizards/space-station-14/pull/32012).
