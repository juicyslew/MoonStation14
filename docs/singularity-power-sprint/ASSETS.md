# Singularity preview art

Imported the six singularity images from the pinned SS14 checkout at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, `Resources/Textures/Structures/Power/Generation/Singularity/singularity_{1..6}.rsi/`. The upstream files are 3x3 sprite sheets with 8/16 animation frame metadata; only the top-left frame is copied into the native Minecraft PNG to intentionally avoid swirl animation. Retained upstream `meta.json` as adjacent `.meta.json` provenance records; they are not runtime animation metadata. Upstream metadata attributes the art to vgstation13 commit `8eef5a676f66551bd0fb40d506486a6b3b2b0f1a`, CC-BY-SA-3.0. No author identity beyond that metadata is asserted.

`/ms14_singularity_visual_preview <1..6>` adds a client-local preview anchor at the player position; repeat the command for up to five anchors. `/ms14_singularity_visual_clear` clears them. `/ms14_singularity_visual_reduced_motion` toggles lensing (sprites remain). These anchors are only a renderer fixture: not entities, not persistent, and not a working generator or reactor. Use first person/third person, different view distances, walls, resize, and Fabulous/Fast/Fancy to manually assess sprite depth visibility and lensing. GPU presentation remains unverified by headless compilation.

## Accepted canister resource import

Imported only intact-body dependencies for current yellow `StorageCanister`
and orange `PlasmaCanister` from the user-accepted rounded R2 package. Details,
resource IDs, translation, pressure blink timing, license attribution, and
the explicit lack of runtime validation are recorded in
`src/main/resources/assets/moonstation14/models/block/canisters/CANISTER-ASSETS.md`;
per-file source SHA-256 values are in the adjacent `canister-import-manifest.json`.

## Accepted Power R6 machinery

The sibling `Asset Building Workspace` now records explicit acceptance of the
complete `assets/Structures/Power/candidates/terminal_support_r6/` package on
2026-10-11: 56 appearances / 86 editable projects. This supersedes historical
pending labels for R6, not approvals for other families or older revisions.

Imported 289 byte-identical runtime files for eleven existing machinery
identities: seven PA parts, emitter, containment generator, singularity startup
generator and radiation collector. Native resources retain the
`block/power_collection/<asset-id>/...` IDs. All imported hashes and scope passed
independent resource review. Credits, source/acceptance evidence, editable-master
locations and dependency hashes are retained under
`src/main/resources/assets/moonstation14/power-reactor-provenance/`; see
`POWER-ASSETS.md` and `power-reactor-import-manifest.json` in the asset namespace.

Resource import is not proof of live binding or native/GPU rendering. Whole-state
exports are alternatives, not composable Cartesian-product layers; integration
must keep pressure, tank presence, charge and connection indicators independent
without drawing a machine body twice. Collector motion contracts describe the
0.8-second transitions and prohibit transforming an already-posed whole snapshot
again. Live fields and singularity sprites remain separate renderer effects.

Live machinery bindings and CPU/native checks are now implemented; see
`MODEL-BINDINGS.md`, `COLLECTOR-MODEL.md` and the current `VALIDATION.md`
checkpoint. Integration-only adapters retain original geometry, UVs and source
pixels while separating independent indicators; original R6 files are unchanged.
Collector panels use raw unposed geometry and one clip transform, with swept
render bounds covering all poses. Logical placement/collision remains host-owned;
model dimensions do not silently add multi-block ownership or change beam rules.

No accepted portable plasma-tank model was established in this package; its
existing item artwork is retained. Accepted rounded R2 canisters and
surface-mapping R3 radiation suits remain separate, unchanged imports.
