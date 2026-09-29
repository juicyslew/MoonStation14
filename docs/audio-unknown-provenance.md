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
| **Imported — file-specific license unresolved** | SS14 `Resources/Audio/Machines/machine_switch.ogg` at [`c9df5ef5d675b0d1d226828bddf6b78c28502d91`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Audio/Machines/machine_switch.ogg); 7,307 bytes, SHA-256 `e39e881d52afc399f50ac4ed9b79084736a3025cf10cde51460f106a2bace55d` | [`src/main/resources/assets/moonstation14/sounds/apc/machine_switch.ogg`](../src/main/resources/assets/moonstation14/sounds/apc/machine_switch.ogg) and [adjacent notice](../src/main/resources/assets/moonstation14/sounds/apc/machine_switch.ogg.license.txt). Verbatim byte copy; source/destination SHA-256 match. | No entry for `machine_switch.ogg` in pinned [`Resources/Audio/Machines/attributions.yml`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Audio/Machines/attributions.yml). Pinned [README](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/README.md) says most assets CC-BY-SA unless otherwise, not a file-specific attribution. License/author/original source URL: **UNRESOLVED — not MIT; do not assume CC for this file**. | Seek file-specific evidence or rights-holder permission; document response. Replace/remove asset and its registration if terms cannot be established for intended distribution. |

When an unresolved entry is cleared, retain a brief dated disposition (evidence
and exact license, or replacement/removal and affected path) here, and update
or remove the adjacent notice and media as appropriate. Do not mark a planned
entry imported before the file and notice exist.
