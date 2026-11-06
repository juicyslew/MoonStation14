# Native-frame examination integration status

This integration reuses `ExamineTarget` identity/codec and the shared
`LoadedFrameRayQuery` frame-ray context (SDK candidate discovery, side-specific
pose, inverse endpoints, and known-cell reads); it adds no frame component,
prototype field, or copied frame-query kernel. A block target identifies a global
storage `BlockPos` together with the exact Sable frame UUID, and request/response
correlation consequently distinguishes same-coordinate blocks in different
frames from ordinary world blocks. The authoritative resolver checks the live
frame identity, own-plot membership, FULL loaded storage, and projects the target
through the current server pose. Examination LOS uses the existing shared
examination cell walker and shape/transparency policy over both world cells and
loaded frame contexts. It does not require the target to be the nearest solid hit:
configured transparent windows are non-occluding, while nearer opaque world or
frame geometry blocks it. The stock collision clip remains for physical queries,
but cannot implement examination visibility alone because windows can collide
without being examination occluders.

The selector reuses the established supercover cell traversal for inverse-projected
rays through SDK-broadphase-nominated frames; it only reads already-known plot
chunks, emits frame-qualified targets and preserves transparent candidates,
shape hits, and canonical airlock roots. Authoritative LOS reuses that same cell
walk for world and loaded frame cells, skips only the existing explicit examine
transparent classification, and fails closed on unknown owned frame residency.
Frame outlines are drawn in storage geometry under Sable 2.0.6
`ClientSubLevel.renderPose(partialTick)`; selector and outline use the same
renderer-supplied partial tick. Airlocks use existing `AirlockGeometry` assembly
bounds for eligibility/highlight and actual piece intersections for selection.

Parity mapping: pinned SS14 `Content.Shared/Examine/ExamineSystemShared.cs:80-155,
165-253` uses mapped positions and examine-specific obstruction policy;
`Robust.Shared/GameObjects/Systems/OccluderSystem.cs:83-152` distinguishes explicit
occluders (BaseWall) from physically colliding windows. The Minecraft adaptation
keeps its resolved explicit examine-transparent profile/tag policy rather than
inferring transparency from collision/alpha. The local standard examine range
and endpoint-shape handling remain unchanged. No prototype/component changes.

**Not verified:** connected real-client chunk-residency/input/render behavior.
Automated validation passed 26/831 focused and 831/831 full GameTests, including
rotated native selector-to-resolver, transparent windows, nearer world/frame walls,
airlock piece/root/aperture selection and non-materializing controller reads. See
`OWNER-FEEDBACK.md` for logs. The outside-envelope allocated-but-unknown chunk crop
has no dedicated regression fixture yet.
The resolver uses current logical pose and the client uses the render pose; this
is intentional current-pose selection, not historical rollback. Interactive
client selection/render acceptance remains a manual gate.

Upstream composition does not change: see `docs/examination-sprint/PARITY.md` for
examiner defaults/consumers and `docs/shuttle-relocation-sprint/PARITY.md` for
native frame query adaptation. This work adds no SS14 parity claim.
