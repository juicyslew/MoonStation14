# Loose cargo orientation under moving frames

Loose cargo presentation now anchors its local quaternion basis to the same authoritative owner used for position: `LoadedFrameRayQuery.currentActorPoint`. `LooseItemEntity.FRAME_ORIENTATION` is one `COMPOUND_TAG` synced value containing the frame UUID and normalized local quaternion; its compound encoding is reused in entity NBT. This gives late trackers the server's existing local basis rather than reconstructing it from their first observed frame pose. The existing scalar `ROTATION` remains the independently synchronized local spin and is composed after the basis by `LooseItemRenderer`.

For a frame parent, rendering composes `world = renderFrameOrientation * localBasis * localSpin`. The server preserves its world basis across native owner transitions by resolving the departing snapshot's frame at its last authoritative storage point (using its latest validated pose), then computing `newLocal = inverse(newFrame) * oldWorldBasis`; only if that old frame pose is unknown does it use the last safe world basis. World ownership uses identity as the parent basis. Initial anchoring similarly preserves the entity's world basis. Unknown frame points/poses do not select a replacement parent or reset orientation. The client only projects a snapshot when its UUID equals the committed `LooseClientPositionInterpolation` frame UUID, and holds its last safe world basis on paused unknown/mismatched metadata; paused unknown is not interpreted as world.

Reuse points: `LooseClientPositionInterpolation` packet staging/unknown-storage pause, `LoadedFrameRayQuery.currentActorPoint` native ownership, and `LoadedFrameRayQuery.projectActorPoint`'s existing loaded-frame admission. `LoadedFrameRayQuery.frameOrientation` extends that service with the same frame identity/residency checks and selects Sable's logical pose for authority or interpolated render pose for client presentation. No additional parent tracker, network channel, physical rotation, attachment, or global registry was added.

Position-phase correction: `LooseItemEntity.renderPosition(partialTick)` projects
tick-boundary local anchors through the frame render pose, including for native-tracked
cargo. This accessor is an actual presentation point, not a renderer-offset convention.
`LooseItemRenderer.getRenderOffset` explicitly returns zero for native-tracked cargo:
Sable already projects its mesh once. The separate outline therefore uses the projected
point rather than a chord between logical world endpoints. First-entry and large-snap
anchor handling are unchanged; unknown metadata retains the existing safe fallback.

Outline nomination, blocker priority and drawing share one render-frame camera-ray
and captured physical query geometry. Candidate discovery unions bounded world queries
with the existing loaded frame-ray contexts mapped back to logical-world entity-index
coordinates; UUID deduplication and committed-frame checks precede common narrowphase.
Server targeting, support, momentum and scalar spin are unchanged.

The jug's baked-model entity draw uses `Sheets.cutoutBlockSheet()`, matching Minecraft's
entity mapping for cutout block-atlas models. The former terrain `RenderType.cutout()`
selected a chunk shader with a `ChunkOffset` contract, inappropriate for this entity
draw. Model, texture, color, pose, light and overlay inputs remain unchanged; no camera,
matrix-offset or culling workaround was added. This source correction is not proof of
the owner's visual outcome. Optional default-off packet/tick/cull/draw observation is
documented in `CARGO-CLIENT-CAPTURE.md`; raw spawn packets are outside its coverage.

SS14 parity: the pinned SS14 revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91` resolves jug through `DrinkBaseMaterialStrongPlastic -> DrinkBaseMaterialPlastic`, `DrinkBase -> BaseItem`, and `DrinkVisualsAll -> DrinkVisualsOpenable + DrinkVisualsFill`; these do not override transform orientation. Generic transform parent changes preserve world orientation by compensating local rotation, and transform state includes local rotation and parent. Minecraft's full 3D quaternion basis is a deliberate adaptation; SS14's 2D transform is not direct pitch/roll parity. Local runtime cargo classification/rendering remains resolved through `LooseObjectProfile.resolveForStack`, including inherited typed profile catalog lookup; orientation does not add a jug-specific prototype field.

## Current placement and light-probe corrections

First frame tracking previously initialized local position from the exact vanilla
world spawn point, without the initial storage anchor. Sable's raw-frame callback
covers movement/teleport, not ADD. Initial tracking now carries the current exact
authoritative frame anchor in NeoForge's standard complex-spawn pairing data
(including late-join pairing), alongside entity addition and before first render.
Both local render endpoints seed from that storage point; world spawns remain
world-space. Established world-to-frame continuity is retained; unknown state
fails closed. This reuses the resolved frame-position service and bounded history;
no tick heuristic, snap-all behavior, or new channel was added.

Fresh surface drops also prepare their existing orientation snapshot from the
same native owner after hydration and before tracking begins. This prevents
initial frame-position metadata from being paired with a world-owned orientation
snapshot. AIR releases retain their world basis; contained-owner release is unchanged.

For light, an untracked probe at `worldUp * eyeHeight` disagreed with Sable's
tracked path-frame `renderPose.transform(storageEye)` under pitch/roll. The shared
committed render-point projection remains. Newer source investigation supersedes
the former cached sky-scale/AABB-cell hypothesis below: the packed-light route
frame-accounted the whole world cell and then accounted the precise probe again.
See [current shared-frame correction contract and source index](SHARED-FRAME-CORRECTIONS.md).
The 103-draw replay supports the wrong-first-cell inference, but orientation mapping
is inferred, raw plot sky values and GPU pixels were not measured, and visual
correction is not established. Native-tracked fallback and SDK light computation
remain; no fullbright or smoothing.

Wall-root drop fallback now checks existing candidate clearance and the final
hydrated entity bounds with the loaded-item box-clear endpoint query
(`NativeFrameShapeQuery`/SAT) before launch or transfer commit. If unsafe, the
hand stack remains. Contained-owner release is unchanged. AIR versus SURFACE
selection, one-time inheritance, frozen XYZ, gravity and damage controls
are unchanged. Regression cases cover these paths and spawn/pose continuity, but
the new wall fixture and spawn codec/first-frame tests await the combined run;
do not read this as a passing readiness result.

Validation boundary: math tests cover full quaternion pitch/roll composition and A-to-B/world transitions. The live GameTest deliberately uses a naturally stable, identity-oriented deck; `requireStoneSupportUnderProjectedFootprint` validates its initial placement immediately after authoring (before allowed natural fall/contact residual) over the whole pad, then a real native yaw angular velocity verifies authoritative local-basis stability, measured nonidentity parent*local world basis, persistence reconstruction, and scalar-spin composition. This fixture passed the central feedback-06 r4 and r5 focused native runs, although both aggregate gates failed other fixtures. It does not claim connected live pitch/roll parity; that remains a Minecraft 3D adaptation with live tilted-frame behavior unverified. Connected-client late-join, packet-order/correction behavior and visual pose inspection remain unverified.
