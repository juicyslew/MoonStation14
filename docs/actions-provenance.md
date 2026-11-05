# Action icon provenance

Imported byte-for-byte from the pinned Space Station 14 checkout at revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`:

- `src/main/resources/assets/moonstation14/textures/interface/actions/harm_off.png` from `Resources/Textures/Interface/Actions/harmOff.png`
- `src/main/resources/assets/moonstation14/textures/interface/actions/harm.png` from `Resources/Textures/Interface/Actions/harm.png`
- `src/main/resources/assets/moonstation14/textures/interface/actions/scream.png` from `Resources/Textures/Interface/Actions/scream.png`

Temporary, project-authored icon (not an SS14 asset):

- `src/main/resources/assets/moonstation14/textures/interface/actions/remove_attached_item.png` is a geometric cyan outward-arrow with a dark outline, authored for temporary action recognizability. No external source or third-party artwork was used; custom art may replace it later.

All are 32×32 PNGs. Upstream `Resources/Textures/Interface/Emotes/attributions.yml` attributes `scream.png` to lzk228 (based on a sprite by Sarahon), under CC-BY-SA-3.0, source `https://github.com/lzk228`. The inspected upstream attribution metadata contains no entry for `harm.png` or `harmOff.png`; their authors and applicable license are unclear and are not inferred. Upstream lists no additional license text for these files in that metadata.

Internals and mask action icons are byte-for-byte source assets at
`textures/interface/actions/{internals_on,internals_off,internals_low,mask_on,mask_off}.png`; each
has adjacent attribution metadata. Mask-off (`blocked.png`) carries the upstream
CC-BY-NC-SA-3.0 restriction. `sounds/internals.ogg` is the pinned SS14 internals
sound with adjacent provenance metadata and the upstream CC-BY-SA-3.0 attribution.
