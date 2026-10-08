# Medical asset import

Imported directly from pinned SS14 checkout `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Treatment icons use the first static RSI state (`ointment`, `brutepack`, `gauze`);
the upstream states have no frame animation. In-hand source sprites are retained
alongside the icons where present. Analyzer `idle`/`active` source states and the
upstream out-of-range image are retained for native client presentation. This
import retains source material independently of renderer state. The integrated
analyzer window uses the out-of-range image and damage-group icons; native item
models use the supply/analyzer icons. Imported active and in-hand source frames
are retained, but their import alone does not establish every animation path.

Medical RSI metadata attributes artwork under CC-BY-SA-3.0 and records original
sources/authors (including PoorMansDreams, SeamLesss, and TiniestShark); see the
adjacent `source-meta.json` copied from the RSI. Analyzer status-icon RSI uses
the same license and attributes its icons to /tg/station commit
`20ae083f140ac5b4da7e8bc40f95349001b6c086`; analyzer device sprites retain their
upstream metadata. Out-of-range image is from SS14 `Interface/Misc`.

Pinned-source asset verification (`c9df5ef5d675b0d1d226828bddf6b78c28502d91`):
`textures/gui/medical/unknown.png` is byte-identical to
`Resources/Textures/Objects/Devices/health_analyzer.rsi/unknown.png`
(SHA-256 `ACF1DFD40C85711E19B2AB837FC41F30281CC8330FE5B54DF2EC08823723DAC5`);
`textures/item/medical/analyzer/icon.png` is byte-identical to
`Resources/Textures/Objects/Specific/Medical/healthanalyzer.rsi/icon.png`
(SHA-256 `E0B590294AD25A596CA4EEB8EF1D293F7CBC616D004BE36E4CEFB1219DB366C2`);
`textures/gui/medical/out_of_range.png` is byte-identical to
`Resources/Textures/Interface/Misc/health_analyzer_out_of_range.png`
(SHA-256 `83B6C2C5C2F3CD76568BF839693A339DBDE6FB4F7CFDCF6BEF567B531AD78332`).
All are 32x32; no frame/direction extraction or other transform was applied.
`unknown.png` belongs to the analyzer device RSI, so its adjacent
`device-icons-meta.json` records that RSI's CC-BY-SA-3.0 metadata. The separate
`health_icons-meta.json` attribution is for status/damage icons, not the unknown
analyzer-device state.

Medical audio attribution was checked individually in SS14
`Resources/Audio/Items/Medical/attributions.yml`: `healthscanner.ogg` is
CC-BY-4.0, taken from FM Synthesis / FreqMan on freesound.org
(32683). No entries there attribute `ointment_begin/end.ogg` or
`brutepack_begin/end.ogg`; no credit/license has been inferred. They are recorded
as unknown provenance pending upstream attribution clarification. No scanner
start sound was imported or registered; SS14 prototype does not configure one.

The registered sound IDs are `medical/ointment_begin`, `medical/ointment_end`,
`medical/brutepack_begin`, `medical/brutepack_end`, and `medical/healthscanner`.
