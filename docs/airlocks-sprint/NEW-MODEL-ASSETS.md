# Airlock default model asset import

Imported the default-airlock export manifest's 44 resources to the manifest's
`src/main/resources/assets/moonstation14/...` destinations. This is an asset
installation was followed by the renderer integration indexed in
`../airlock-model-mini-sprint/IMPLEMENTATION.md`. No `.bbmodel` authoring files
were packaged.

## Contents and renderer notes

The set contains 11 state models, six reusable component models, five effect
models, and one item model (23 JSON models total), plus 16 PNGs and five
animation-metadata files. State models are `closed`, `open`, `unpowered`, `panel_open`,
`open_panel`, `bolted`, `welded`, `emergency`, `denied`, `bolted_panel`, and
`welded_panel`; supporting item model: `models/item/airlock_default.json`.
Reusable pieces are frame, left/right leaves, maintenance cover/wires, and weld
overlay. Effects are sparks, sparks_broken, sparks_damaged, sparks_open, and
electrified. The intended footprint is 1x2 blocks; component geometry is
reusable, not a new general-purpose Blockbench loader format.

Each model binds its own `moonstation14:block/airlock/default/<state>` atlas
under `#atlas`; use the matching same-stem PNG, not the older airlock atlas.
UV coordinates are authored for these shared state atlases, so retain the
exported PNG dimensions/pixels and do not substitute per-component atlases or
rescale UVs. Models are vanilla block-model JSON with element `from`/`to` boxes
and per-face UVs; no element rotations are defined. Effect atlases include
`.png.mcmeta` animation metadata. Renderer integration must explicitly select
the state model/texture, compose component models, and decide effect layers;
these assets do not register states or implement animation/gameplay.

The integrated renderer registers supported component/state models and applies
existing controller motion and appearance. Unsupported gameplay modes remain
inactive; importing an asset is not implementation of its corresponding system.

For renderer choice, `closed` is the ordinary powered/closed artwork;
`unpowered` is the powerless presentation; `open` and `denied` are distinct
appearances. Panel, bolted, welded, emergency, and combined open/panel or
bolted/panel variants are separately exported visual states. Sparks variants
and electrified are optional effect layers, not automatic state transitions.
This differs from the earlier flat sprite mapping in `ASSETS.md`; integrate
these candidates deliberately rather than silently replacing that renderer.

## Provenance

Source workspace:
`C:\Users\William\Documents\Minecraft Mod Dev Files\Asset Building Workspace\assets\Structures\Doors\Airlocks\default`.
The delivered `ATTRIBUTION.md`, `DELIVERY.md`, `export/manifest.json`, and
`source/manifest.json` identify this as the SS14 neutral/default adaptation,
CC-BY-SA-3.0, based on `Resources/Textures/Structures/Doors/Airlocks/Standard/basic.rsi`
at SS14 revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, artwork from
tgstation commit `c6e3401f2e7e1e55c57060cdf956a98ef1fefc24`. Source attribution
also identifies the electricity effect as made by Aleksh#7552 / Alekshhh for
SS14. The export records reconstruction changes (cuboids, Minecraft 1x2
proportions, hidden surfaces/slots, palette art, translation clips, separated
visual channels, isolated VFX cutouts); it is an adaptation, not unchanged
upstream art. Authoring files and external workspace material remain external.

The source manifest's resolved source hashes are preserved there, and the
installed bytes are copied directly from the manifest-listed export paths.
