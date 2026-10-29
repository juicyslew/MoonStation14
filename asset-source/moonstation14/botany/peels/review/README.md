# Banana + mimana peel R1 — review and handoff records

The immutable complete asset pair is [source_peels_r1](../../candidates/source_peels_r1/README.md), with the [fully expanded gallery](../../candidates/source_peels_r1/index.html). Open either embedded-texture `.bbmodel` under that package's `authoring/` directory.

- `package_identity.json`: frozen package/archive hashes.
- `source_peels_r1.zip`: complete portable package, not a runtime-resource-only ZIP.
- `independent_final_review.json`: independent final artwork/integrity recommendation, once recorded. Recommendation is not owner acceptance.
- `delivery_receipt.json` and `final_delivery_audit.json`: actual completed queue handoff, once generated; absence means that milestone is not complete.

The runtime-only flat manifest is the core package's `export/manifest.json`, relative to that package root. It maps **12 files**: both ordinary item bindings, OBJ wrappers, OBJ/MTL/PNG dependencies and visual-only loose profile candidates. Source/support files are intentionally separate from game resources. Never copy authoring, screenshots or archive into runtime resource paths.

One consistent three-dimensional skin volume serves loose, held and inventory rendering; existing worn head assets are untouched. The native floor/hand presentation remains a proposal pending actual native/game verification. Physical and gameplay fields are preserved. No reference-repository installation, import authorization, new renderer, mechanics or dependency work is included.

Read-only saved-file check from workspace root:
```powershell
python tooling/Objects/Botany/Peels/delivery.py verify
```

Preserve the immutable package, archives, guards and manual edits. Retrieve saved files, not work/pilots or automatic regeneration. Future authorized artwork changes require a separate revision. No automatic Blockbench launch.
