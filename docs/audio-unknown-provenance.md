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
| **Imported ΓÇö file-specific license unresolved** | SS14 `Resources/Audio/Items/drink.ogg` at [`c9df5ef5d675b0d1d226828bddf6b78c28502d91`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Audio/Items/drink.ogg) | [`src/main/resources/assets/moonstation14/sounds/ss14/items/drink.ogg`](../src/main/resources/assets/moonstation14/sounds/ss14/items/drink.ogg) and [adjacent notice](../src/main/resources/assets/moonstation14/sounds/ss14/items/drink.ogg.license.txt). Verbatim byte copy; source/destination SHA-256 match (`6c09f978706f20302eafbd79a03ac25cc719fb34dfe1443251a026eda6867b1f`). | File-specific author, license, and original source were not found in pinned upstream evidence. **Author and license unresolved; this record is not a permission or license claim.** | Seek file-specific evidence or rights-holder permission; replace/remove if terms cannot be established for intended distribution. |
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