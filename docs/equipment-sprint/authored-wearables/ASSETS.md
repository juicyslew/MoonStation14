# Authored wearable resource import

Imported the eleven-item standard-player collection from the external workspace
without modifying it. Mesh JSON and PNGs were copied byte-for-byte. Meshes live
in the custom-resource namespace `assets/moonstation14/wearables/meshes/`; these
are not vanilla Minecraft model JSON. OBJ files are retained as reference
interchange data, not a runtime parser requirement. MTL texture-path rewrites,
where needed, are confined to the installed copies and point to the canonical
installed PNGs.

`import-manifest.json` records each source/destination hash, equality status,
and MTL derivative status. The original README, attribution, source manifest,
export manifest and binding contract are preserved alongside it. PNG sidecars
carry the selected upstream RSI/state credits, license and source-image hash.
The source assets and reconstructed painted art remain credited as documented
in the preserved source material; no new authorship is asserted here.

The export is limited to the manifest's eleven existing standard-player assets.
The source README says wide/Steve arms only; alternate species/narrow arms are
not validated. The export explicitly leaves geometry destination null because
the renderer adapter is absent. The resource copies are therefore not live
renderer integration or in-game validation. No inventory/held art, prototypes,
item fields, ID-card/case icons, or external assets were changed. No audio was
imported.
