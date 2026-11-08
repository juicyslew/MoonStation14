# PA rendering evidence and remaining blocker

**Current status:** Owner reports the PA otherwise looks correct after the UV
fix; the remaining reported orientation issues were the backwards control
monitor and outward-facing side-emitter connectors. The native presentation
correction below is in place and awaits owner recheck. It is not yet GPU/owner
accepted. No screenshot is required, and successful headless tests do not
substitute for that acceptance. Full connected-singularity warp acceptance is
also still pending separately.

## Established source/integration evidence

- PA parts use ordinary native block models, not a custom PA renderer. Accepted
  R6 originals remain unchanged; `reactor_bindings/pa_*` adapters reference them.
- `PowerR6MinecraftModelBakeTest` checks native FaceBakery vertex winding against
  the transformed source-element center, using rotation about the block center.
  It covers current PA roles/variants and native rotations. This is independent
  of the packed normal generated from that same winding, but it does not test
  the actual GPU draw. Its additional control-box harness reads the blockstate's
  Y rotation/UV-lock flag and compares production/opposite native baked UVs with
  per-face source-frame alpha samples for each facing.
- **Historical nearest-face report:** see-through faces, visible interiors and
  missing ground were reported before the UV-lock/no-occlusion corrections; they
  are not the current confirmed visual bug. The owner now reports the PA
  otherwise looks correct after the UV fix.
- Native UV/alpha evidence identified an integration defect. With the original
  blockstate `uvlock:true`, UV-lock changed 130/168 control-box face/facing
  instances. For east, south, west it moved respectively 904, 1,134, and 790
  of 3,402 per-face grid samples onto alpha-0 texels (0 partial-alpha samples);
  north remained fully opaque. With `uvlock:false`, all 42 faces at each facing
  (3,402 samples/facing, 13,608 total) sample alpha 255. The model's authored
  source UVs therefore depend on preserving their atlas coordinates rather than
  vanilla UV-lock rotation. All seven PA adapter blockstates now set `uvlock:false`
  across their variants; accepted R6 source models/textures are unchanged. The
  test asserts all production variant flags and fully opaque post-bake samples
  for each control-box facing. This is strong evidence for a baked-UV alpha
  integration defect, not proof of the complete live visual cause. The owner
  accepts the nearest-face/opacity defect as corrected on the tested build; the
  new orientation adjustment still awaits recheck.
- PA registration now uses `noOcclusion()`: partial machinery must not hide
  adjacent ground faces as if it filled the whole cell. Native ground-top
  coverage lives in `FieldContainmentRuntimeGameTests`. This separate ground
  visibility correction is not evidence about nearest-face rendering.
- `PowerR6PaFrameAlphaTest` follows production blockstate references and model
  parents, resolves texture aliases, and samples each used animation frame's
  face UV rectangle. Animation UVs use frame dimensions, not the entire sheet.
  Its scoped test passed; this checks for wholly transparent authored faces,
  not every live nearest-face pixel or resource-pack override.
- A read-only pixel analysis of the aimed `pa_control_box` `unlitp` 128x128
  texture matched the owner log/source SHA-256
  `e71d97bec9604cb40286fd9017d0070db07eaa305bf333ded6c0c12eeb049592`.
  All 42 source face-UV rectangles for that model were fully opaque: 5,670
  sampled pixels, alpha 255 throughout, with no partial-alpha or transparent
  samples. Across the whole 16,384-pixel texture, 11,107 pixels (67.79%) have
  alpha 0 and 5,277 (32.21%) alpha 255; there are no alpha 1–254 pixels. This
  rules out bad/transparent source pixels within the authored UV rectangles,
  but does not imply those pixels remain sampled after baking. Native UV-lock
  baking moved faces outside those opaque authored rectangles and onto alpha-0
  texels, as quantified above. The blockstate UV-lock integration is corrected;
  this source-pixel analysis alone does not establish live GPU presentation or
  owner acceptance; this original-alpha evidence remains valid historical
  evidence, not the current reported issue.

## Actual-client evidence collection

Aim at a PA part and run `/ms14_singularity_visual_status`. The existing command
reports the actual native baked model, general/directional quad counts,
representative centers/winding, resolved sprite identities, PA occlusion, the
block below, and whether that block's top face should render. Empty occlusion
shapes are supported; general quads are included because source PA faces have
no `cullface`. For up to eight unique baked PA sprites it also reports actual
frame dimensions plus the active resource-pack ID and SHA-256 of the resolved
PNG bytes (and `.mcmeta` bytes when present); missing resources and read errors
are reported without changing game state.

It arms read-only captures at `VertexBuffer.draw()` (the native chunk submission
path called by `LevelRenderer.renderSectionLayer`) for the layer shaders in the
aimed model's actual `ChunkRenderTypeSet`. Supported explicit mappings are
solid, cutout, cutout-mipped, and translucent; `RenderType` source confirms their
shader states map to `rendertype_solid`, `rendertype_cutout`,
`rendertype_cutout_mipped`, and `rendertype_translucent`. Each selected shader
pass is captured at most once; unsupported layer types are named unsupported in
the diagnostic rather than guessed. At draw time the probe reads the current
shader's applied `ModelViewMat` uniform buffer; the determinant is not taken
from the global RenderSystem matrix or the earlier helper's un-applied
arguments. The log records depth testing/writes/function, culling/front-face
mode, framebuffer IDs and applied model-view determinant. This is **generic
matching-pass submission context**, not proof that a captured submission
belongs to the aimed PA. Other shaders and draws without the uniform leave their
one-shot armed. If the uniform buffer has fewer than 16 readable floats, the
capture reports the determinant as unavailable rather than using a fabricated
matrix.
Evidence is written to the client log; no screenshot is needed.

The earlier client log line 92 reports the expected aimed model/source,
sprite, directional quad counts, and winding, but contains no successful
one-shot submission capture before shutdown. The corrected probe now selects
the aimed model's actual chunk-layer shader pass(es), including mandatory
`rendertype_cutout` for the control-box asset; any optional SOLID capture is
only ground context. Captures remain generic pass submissions, not aimed-PA
  attribution. This log predates latest owner feedback and is historical, not
  current orientation acceptance evidence.

No global culling disable, double-sided-model workaround, reversed source
faces or accepted-art repaint was introduced. The former nearest-face/opacity
report is no longer the current confirmed defect; GPU confirmation of the new
monitor/connector orientation remains pending the owner's actual client rerun.

## Operator-facing orientation correction

Accepted R6 source geometry is fronted toward model -Z and is already centered;
native `reactor_bindings/pa_*` parents do not recenter it. Keep accepted originals
immutable. The control-box face art is reversed relative to local facing, so its
blockstate art rotation is advanced 180 degrees relative to gameplay facing.
Side emitter adapter parents are crossed (local PORT uses accepted STARBOARD
art and local STARBOARD uses accepted PORT art); actual placed blocks and role
identities are not swapped. Emitter state yaw remains the native
north=0/east=90/south=180/west=270 mapping. The role's actual facing is the gameplay firing direction
(for a south-facing control, WEST); native FaceBakery verifies that the accepted
`forward_aperture` NORTH face becomes that firing direction. The accepted
`offset_connector` centers are transformed through the same production state
rotation and checked toward Fore. These are presentation-only mappings: local
part identities, typed offsets and -90-degree gameplay firing orientation are
unchanged. `uvlock:false` remains set for every variant. These orientation
changes await owner recheck; they are not claimed as accepted GPU presentation.
`PowerR6MinecraftModelBakeTest` bakes the accepted named muzzle face and
transforms connector centers for all four root facings. GPU/owner acceptance
remains pending.
