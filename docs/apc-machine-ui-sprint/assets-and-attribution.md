# APC machine UI — assets and attribution

## Scope and rule

M2 reuses one existing SS14 APC sprite as a static 2D screen image. This APC image import includes no SS14 UI/code, logos, fonts, sound, models, or newly drawn/copy-traced sprites. Omit entity-preview animation; the static image is sufficient. Audio reuse, if added separately, follows the [media reuse and attribution policy](../media-use-and-attribution.md) and [unresolved-audio running list](../audio-unknown-provenance.md).

SS14 is a design reference, not an instruction to copy its XAML, C# implementation, localization, layout code, or artwork. Never copy upstream code. Asset reuse is a separate decision from code reference and requires the exact source-file provenance, license, attribution text, and any modifications to be recorded before adding the asset.

## Reference checked

- Local upstream checkout: `C:\Users\William\Documents\SS14-dev\space-station-14`.
- APC layout reference: `Content.Client/Power/APC/UI/ApcMenu.xaml` and related files in that directory.
- APC visual source: `Resources/Textures/Structures/Power/apc.rsi` and adjacent `meta.json`.

## Imported asset and attribution

- Result: `src/main/resources/assets/moonstation14/textures/gui/apc/apc.png`.
- Exact source: `C:\Users\William\Documents\SS14-dev\space-station-14\Resources\Textures\Structures\Power\apc.rsi\static.png` (upstream repository `https://github.com/space-wizards/space-station-14`).
- Source metadata identifies license `CC-BY-SA-3.0` and copyright/attribution: “Taken from tgstation at commit https://github.com/tgstation/tgstation/commit/9c7d509354ee030300f63c701da63c17928c3b3b and heavily modified by EmoGarbage404 (github)”. This import does not claim authorship.
- Transformation: byte-for-byte copy; no crop, resize, palette/color, or alpha modification. `static.png` is a single 32×32 frame, so no sheet extraction/animation conversion was needed.
- Shipped notice: `src/main/resources/assets/moonstation14/textures/gui/apc/apc.png.license.txt` carries the attribution and a CC-BY-SA-3.0 legal-code link.
- License: [Creative Commons Attribution-ShareAlike 3.0 Unported](https://creativecommons.org/licenses/by-sa/3.0/legalcode). Preserve the notice and license when redistributing; downstream adaptation obligations apply under CC-BY-SA-3.0.

## Reuse record requirements for any additional asset

Before copying or format conversion, document:

1. upstream repository and exact immutable commit;
2. exact original path(s), including RSI metadata and source texture(s);
3. original creator(s), copyright holder(s), license file/notice, and applicable attribution;
4. every conversion/edit (for example, RSI sheet/frame extraction to Minecraft PNG, scaling, palette/alpha changes), tool/settings if material, and resulting asset path;
5. a license/attribution notice shipped with or referenced by the converted asset, preserving CC-BY-SA-3.0 requirements and the original attribution chain.

Do not assume a license for artwork from a code license or a nearby file. Do not remove metadata, creator attribution, or license context during conversion. Have compatibility with the project's distribution/license reviewed before import. Keep the asset visually faithful only to the extent permitted; no asset should imply controls/telemetry the local APC does not support.

The one imported image and its shipped attribution notice are recorded above. Any additional asset must be recorded here with full provenance and its shipped notice in the same change.
