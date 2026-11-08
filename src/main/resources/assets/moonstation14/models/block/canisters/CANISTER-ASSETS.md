Accepted canister render resources (artifact-only import)
========================================================

Source: `Asset Building Workspace/assets/Structures/Storage/Canisters/candidates/rounded_r2`
R2 is user accepted as-is (`user_acceptance.json`: complete24-master package,
2026-10-09; accepted package output SHA-256
`437a2c38669935ef8bcb870e32fc3a53a6b23d77b5a1012df233044f62de8b11`). This
import copies bytes from that saved export; no asset was regenerated or altered.
Source artwork attribution/licensing: derived SS14 artwork, CC-BY-SA-3.0;
tgstation/Frezon/Vermidia attribution is retained in the accepted package's
`source/ATTRIBUTION.md`. This notice and `canister-import-manifest.json` retain
that provenance and the exact imported-file checksums.

Imported intact assembly resources for the current yellow StorageCanister
(initially unlocked) and orange PlasmaCanister (initially locked): shared body,
head and connector, one-of pressure 0..3 indicator pieces, locked/unlocked
hardware, optional tank-inserted indicator, and the animated low-pressure-zero
texture metadata. Blue structural masks are retained as separate OBJ
components. No full-state inspection model is combined with these components;
unsupported labels, damage/integrity/glow, broken models and proposal-only
blockstates are intentionally omitted.

Block-corner OBJ coordinates must be translated by `[-0.5, 0, -0.5]` exactly
once when later drawn at entity center (16 OBJ units/block); do not scale again.
Imported package's accepted visible hull is 10.25 units (0.640625 block) wide,
head reaches 16 units (one block). The later Minecraft entity height 1.0 is an
adaptation based on the accepted shape, not a 2x scale. Portable OBJ exports'
top-origin V convention is retained unchanged. Integrity glow layers are not
included in this slice.

Resource IDs for the future renderer: `moonstation14:block/canisters/yellow`
and `moonstation14:block/canisters/orange`, with components under their
`components/` directories. Pressure threshold bins are `<10`, `<101.325`,
`<1519.875`, otherwise3 kPa. Pressure0 texture loops at four ticks per frame,
4 ticks on / 4 ticks off. These assets/art acceptance do not imply block/item
registration, gameplay, native OBJ-loader, GPU, or runtime validation.
