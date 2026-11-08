# Seed extractor and grinder model assets

Transferred the two families from the provisionally usable Machine perspective R2 package:
`Asset Building Workspace/assets/Structures/Machines/candidates/perspective_r2/export/`.
The export manifest's `files` records (not its transfer text) selected model and texture
entries in the `seed_extractor` and `grinder` families; destination paths and SHA-256
values are retained in that manifest. All selected entries were copied byte-for-byte,
including OBJ/MTL meshes and animated texture `.mcmeta` files. Source license and
provenance notices are in that package's `source/manifest.json` and
`source/attribution.json`; source terms include CC0/CC-BY-SA and must accompany
redistribution.

The grinder's default OBJ JSON uses NeoForge's built-in `neoforge:obj` loader, as used
by existing machine OBJ integration; this adds no dependency. The existing block
model IDs are stable item parents and blockstate targets: they now inherit the default
machine appearance. State-specific grinder/extractor exports are installed for later
appearance binding, but this change does not add state selection or gameplay behavior.
Only these two model families were transferred. The source candidate is provisionally
usable, not a claim of formal asset approval or GPU/in-game acceptance.
