# Singularity animation asset handoff

Upstream checkout was verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Original sprite sheets and `meta.json` remain untouched. Imported original RSI
direction 1 frames in row-major order as separate PNGs under
`assets/moonstation14/textures/singularity/animated/level_<n>/frame_<00..15>.png`.
Levels 1–5 have 8 frames each; level 6 has 16. Every frame is delayed 0.1 s,
so each animation cycles at 0.8 s (levels 1–5) or 1.6 s (level 6). Each PNG
retains the corresponding existing visual profile frame width: 32, 96, 160,
224, 288, 352 px. No repainting or synthesis was used.

`animation-manifest.json` records upstream PNG/meta byte SHA-256, crop rectangle,
source frame index, dimensions, per-frame delay, and imported PNG SHA-256. Source
art attribution is vgstation13 commit
`8eef5a676f66551bd0fb40d506486a6b3b2b0f1a`, CC-BY-SA-3.0. `SingularityFrameTiming`
is a pure sequence-driven selector: renderer integration should supply the
resolved level's manifest delays and elapsed game time plus partial tick; do not
duplicate hardcoded delays. Frames are at `animated/level_<n>/frame_00.png`...
`frame_07.png` for levels 1–5 and through `frame_15.png` for level 6.

Pixel-by-pixel verification against source crops passed for all 56 frames.
