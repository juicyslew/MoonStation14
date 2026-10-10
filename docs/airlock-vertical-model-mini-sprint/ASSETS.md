# Accepted vertical standard-basic resource import

## Source and scope

Copied unmodified from the frozen, owner-accepted 49-profile package at
`C:/Users/William/Documents/Minecraft Mod Dev Files/Asset Building Workspace/assets/Structures/Doors/Airlocks/all_variants_revision2/`.
Acceptance is recorded in `user_acceptance.json`; hashes are frozen in
`accepted_output_hashes.json`. No exporter/generator was run and neither source
art nor source-side hash records were modified. This import is only the standard
basic skin dependency set, not a gameplay-door batch.

Package model IDs retain their source namespace and path:
`moonstation14:block/airlock/vertical_all/standard_basic/...`.
The opaque closed pose is retained as a separate item candidate at
`moonstation14:item/airlock_vertical_standard_basic`; it does not replace the
existing item pointer pending renderer integration.

## Imported parts and intentional omissions

Imported model components: `fixed`, `header_lamps`, `upper_leaf`, `lower_leaf`,
`upper_infill`, `reader_cover`, `reader_housing`, and `maintenance_wires`.
`pocket_upper`/`pocket_lower` are intentionally omitted because their accepted
four-block installation envelope is outside the chosen two-block rendering
boundary. `weld_bead` and welding art are unsupported and omitted. The accepted
closed opaque pose, base atlas, and static frame-00 overlay candidates for
`closed_unlit`, `opening_unlit`, `bolted_unlit`, and `deny_unlit` are present.
The opening overlay is the persistent moving/open visual; no native overlay
animation or panel transition scheduler is implied. Native output header overlay
models are the source-defined layers; ordinary full-base header art is already
in the atlas. Overlay static sprites are not implicitly emissive; power-gated
lighting remains a renderer responsibility.

Source-to-local mappings are exact suffix-preserving copies from
`export/resourcepack/assets/moonstation14/{models,textures}/block/airlock/vertical_all/standard_basic/`.
Components map to `models/.../components/<part>.json`; native overlay layers map
to `models/.../overlays/<state>_00_header.json` and, when supplied,
`<state>_00_reader.json`; associated PNGs retain their `textures/...` IDs.
The texture atlas is `textures/.../atlas.png`. Closed item geometry is
`models/.../poses/closed_opaque.json`.

## Source hashes (SHA-256)

These hashes are for the exact saved files copied from the accepted source:

| File suffix | SHA-256 |
|---|---|
| components/fixed.json | `27B65560AB0B54F39BDAFBA51DBA1F6616AC3F53300551D37353EE39F96BA184` |
| components/header_lamps.json | `92AD0B6AAE65538AB9E92605DA9498D2AC19A99A74C021E45025E7E4B9DE98EC` |
| components/upper_leaf.json | `0B87B00704D73A08D771358720F85E1C7C08F9DEC2879FA55DA8F8E7BCBF29E8` |
| components/lower_leaf.json | `FB7FF1DAA7D642EE8702F465E4FC5FA5538E45CB15352C2E0386368001FF14FB` |
| components/upper_infill.json | `2E434A537AB05778B8F0AA72EA7B7610CC27C970ADCDE60F237CDE2FDD3621D1` |
| components/reader_cover.json | `F923D5E657A8BB21649D29619D29D9BC4287D98678220C15C671211836CF3B3B` |
| components/reader_housing.json | `CF8F6AC51145315AE106B799448139E2E346B56160B0D095F4D36AAB17E2D011` |
| components/maintenance_wires.json | `D6C01FDD8A7F8ED1A0EA5A1B89394E2082AEE7BF0DF4655FD538B873E5245EA9` |
| poses/closed_opaque.json | `C292BB8DCD043ED0C937189FEDAC9CC5D2C51F431B0B38FC55A6EC4CBDF93964` |
| textures/atlas.png | `9C4F02CF05E2FCCA6282114D100551D9FAED4820237D47C3F9E84CCDD99FCCBE` |
| overlays/closed_unlit_00_header.json | `F796BC9C38F9135334D7FA9CB80F40B9B23B5EF6A18B89AE18C430A0CE968782` |
| overlays/opening_unlit_00_header.json | `385672BB310A7C8663A522D3ABF515971631EF0E85DB97AD20A6C009473D8480` |
| overlays/opening_unlit_00_reader.json | `0E8FE08B4CFC1C9D2EAB94449111A42D212AA1FA7D8F0EB5423CCA90AE6AA75D` |
| overlays/bolted_unlit_00_header.json | `63E719DF088EA504DD8B8F81B0B70E44B1E45DD9FCD98B83CD0FFCDDFA238F83` |
| overlays/deny_unlit_00_header.json | `97BA8B264DAB36AEDB44BE351AAF3E2166A933F7AF5F9C94D8C7F326C57BAE79` |
| overlays/deny_unlit_00_reader.json | `A42F4FE73D23BD908117808407D17215932CBEBFCCA739CF4935CC6B6BBC3509` |

The separate provenance package (not render resources) preserves the exact saved
records: `ATTRIBUTION.md`
`65974D6AAF1C5F2321EA93272C7037DEB1C788AEC37DC33BCDF3057A88081AAC`,
`provenance/source/resources.json`
`5B8F821BDD442E67CAC6EFF5C3B2749938E256ED3B169C22E6ACF71A8CD994DD`, and
`provenance/source/rsi/standard_basic/meta.json`
`5F7A1E047CCC898354EFFBB0730BA96D532EC0FF0BF9EBB5D532C686BD98560A`.

## Attribution

Packaged alongside these assets at `assets/moonstation14/provenance/airlock_vertical_standard_basic/` are
the source package's complete `ATTRIBUTION.md`, exact full `source/resources.json`,
and exact `source/rsi/standard_basic/meta.json` for the imported RSI. The source
record identifies `Resources/Textures/Structures/Doors/Airlocks/Standard/basic.rsi`,
pinned revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, and preserves the
copyright notice: “Taken from tgstation at commit
https://github.com/tgstation/tgstation/commit/c6e3401f2e7e1e55c57060cdf956a98ef1fefc24”.
Its license is CC-BY-SA-3.0: <https://creativecommons.org/licenses/by-sa/3.0/>;
legal code: <https://creativecommons.org/licenses/by-sa/3.0/legalcode>.

Changes in this local distribution include accepted reconstruction as cuboid
Minecraft models, plus the two-block clipped presentation and state binding. The
saved artwork itself was not altered. Adapted model/texture art remains
CC-BY-SA-3.0. No other 48 profile artwork is imported; the copied full resource
index is attribution metadata only, not additional installed game art.
