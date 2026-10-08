# Health analyzer item animation

The analyzer item model keeps the upstream `icon` sprite on layer 0 and puts
the upstream `analyzer` overlay on layer 1. The overlay is animated continuously;
the pinned RSI declares no active/inactive appearance binding. No item state or
gameplay predicate is involved.

Source: SS14 checkout pinned at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
`Resources/Textures/Objects/Specific/Medical/healthanalyzer.rsi`. Its `meta.json`
declares a 32x32 frame size and twelve analyzer frames, with the first eleven
at 0.1s and the last at 0.5s. The source `analyzer.png` is a 128x96 atlas;
frames were copied row-major (four columns, three rows) into a 32x384 vertical
strip without resampling, compositing, or alpha changes. Minecraft animation
metadata assigns two ticks to each of frames 0-10 and ten ticks to frame 11.
The existing adjacent `source-meta.json` retains the RSI CC-BY-SA-3.0 and
copyright attribution.

The generated item model is used by the standard Minecraft item renderer, so
the layered model/animated texture applies in world, HUD/grid and hand item
render contexts. The overlay is not unshaded: this project's native generated
item-model path has no verified emissive-layer support. It therefore receives
ordinary Minecraft item lighting as a deliberate rendering adaptation; no
custom renderer or active-state toggle was added.
