# Owner feedback — 2026-11-05

The owner reports jug flicker is less frequent but still very regular. The
viewport-corner disappearance is fixed; the owner confirmed it has not recurred
after the accepted cull fix, so leave culling alone. Newly placed cargo jitters
at first ground placement, and dropped cargo partly entering shuttle walls is a
new confirmed failure. The current fixes postdate those observations; they do
not establish post-fix pixels or owner acceptance.

Candidate changes address the reported paths: initial frame cargo position is
seeded from the authoritative storage point on initial tracking, with matching
orientation ownership and explicit WORLD/FRAME/UNKNOWN spawn states. Wall-root
drop fallback checks candidate clearance and final hydrated entity bounds before
launch/transfer commit (unsafe placement keeps the item in hand). The contained-
owner path is unchanged. A shared committed render pose now projects the light
probe consistently with rendered cargo. Regular flicker remains unexplained: no
unique cause is established and there is no post-fix diagnostic capture or pixel
evidence. See [cargo orientation](CARGO-ORIENTATION.md) and
[capture notes](CARGO-CLIENT-CAPTURE.md).

Automated readiness passed for earlier run
(`run_81c05d1867d050790604a0d4`; 675 selected, 91 classes); it predates these
changes and is not current proof or visual acceptance. The subsequent candidate
passed 683 JVM tests across 91 classes, but readiness run
`run_4df62c70eda84a3a0b4f51b5` failed: 102/996 native cases were selected,
100 passed, and the two new wall fixtures failed before their drop transactions.
The world fixture omitted the cabin's half-block world-origin offset; the native
fixture incorrectly required the preliminary planner to reject every candidate.
The revised walls cover the bounded aim path and check actual planned/final bounds.
Corrected fixtures retain strict collision and ownership assertions. The next run,
`run_26fe8db78072a0fad0abbeb4`, again passed the other 100 native cases but found
that both fixtures demanded a view acknowledgement synchronously after queueing
input (`applied=0`). They now reuse the existing bounded acknowledgement-and-look
wait before reading placement geometry or hand state; runtime verification is pending. M1 remains
unfinished; no merge or M2 is authorized. No new recording, UUID, or upload was
requested. Medical death drift and two-client status remain unknown/unresolved.

Latest owner observations (Nov 5): repeated cargo commands reported “Could not
place cargo safely; no jug was published,” so the owner instead spawned jugs in
hand and placed them. Initial placement jitter is confirmed resolved; the
viewport-corner fix remains accepted and unchanged. Lighting still varies
regularly with speed, correlated with yaw or horizontal translation but not
vertical translation, and affects the player character too; ship tilt is
imperceptible. No lighting cause is established. Jumping aboard a descending
ship exposed inconsistent landing, sustained bouncing, and intermittent
`onGround`; this is newly exposed, not proven to be a cargo-code regression.
Preserve frozen XYZ launch-velocity references and once-only point-velocity
inheritance, apply world-down gravity, and do not add smoothing, body freezing, velocity wipes, continuous
airship force, native overrides, or relaxed query tolerances. The latest
readiness run passed 683 JVM tests and 102/996 selected native cases; package
provenance passed. This is not visual/M1 acceptance. Logs and two complete but
discarded automatic cargo captures are preserved under ignored
`build/owner-shuttle-feedback-20261105/session02/` (copy hashes and observed
stability in `manifest.json`; row extraction in `capture-lines.log`). Log scans
showed medical trace class loading only, no death/drift trace proving an
outcome; death drift remains unknown. The capture windows were incomplete
(`windowComplete=false`) though rows are valid/complete without overflow; do not
claim yaw/translation/time-segment findings from them without analysis.
The copied log records one current connection (Dev, player entity 1; character
body 2), with snapshots showing that body first on the world and later on
`FRAME:0b152505-9c1c-47a8-bcc3-aee361103d8b`; two captured cargo entities do not
establish a second connected client.

The owner additionally reports whole-ship visual jitter only when observing from
world ground. Ship and surrounding world are smooth while aboard; after jumping
off, ship rendering stays smooth until the exact moment of world-ground contact.
This is an observer-state clue, not proof of unstable server physics.

Source investigation found distinct observer interpolation, double frame-accounting
for entity light, and descending-landing contracts; correction details and actual
source index are in [shared frame corrections](SHARED-FRAME-CORRECTIONS.md).
Lighting replay remains an inference: model/frame orientation correspondence is not
raw-spawn telemetry, and plot sky values/GPU pixels are unmeasured. Source fixes and
compiled regressions do not prove visual success or owner acceptance. Cargo's
authorized command grant path is also covered in that index; failure-stage and
connected/visual outcomes remain to verify. Current source-bound and managed
readiness gates remain pending. M1/owner acceptance remains outstanding; no M2 or
merge authorization.

Normal logs were copied once, without modifying or controlling owner processes, to
ignored `build/owner-shuttle-feedback-20261105/session01/`. Their hashes, observed
source/copy metadata and per-file before/after stability results are in
`manifest.json`; medical-trace and cargo diagnostic/error scans are alongside the
copies. No new medical trace or relevant cargo diagnostic/error line was found.

See [Nov 4 evidence](OWNER-FEEDBACK-20261104.md) for the completed offline replay
of all 407 captured frames. CPU query replay does not establish GPU pixels or
connected visual acceptance. Preserve accepted character fixes and the confirmed
culling fix. Owner manual check when a candidate is ready: observe a resting jug
while its ship spins (including initial ground placement and viewport edges),
then ordinary cargo drops near a wall and a clear-area/airlock control. Do not
deliberately trigger a death or request capture IDs/uploads. M1/two-client
acceptance remains outstanding; no merge/M2 until owner acceptance and
authorization.

Latest owner feedback: jug lighting flicker is gone, but all movement is now
choppy, including ordinary world walking (“like interpolation turned off”); this
is a serious regression in the latest candidate. Newly observed: a placed jug
appears behind the cursor while rotating. Preserve the earlier confirmed cargo
initial-placement jitter and viewport-cull fixes. Gate
`run_07740820117d8c701c12c876` passed (695 JVM tests, 92 classes; 106/998 selected
native cases; package/provenance passed), but that machine PASS is not acceptance
and does not override the newly reported movement regression. No M1 merge/M2;
medical and two-client outcomes remain unverified. Urgently restore actor-camera
data isolation while retaining the lighting improvement, and analyze cursor
surface targeting; do not claim cause or fix before review and a new run.

Passive normal-log copies and hashes are in ignored
`build/owner-shuttle-feedback-20261105/session04/`. All four files were stable
across the bounded copy/hash check. Two auto-source jug captures were found
(845 rows total; frame windows 17745–17895 and 17979–18021); both ended before
their windows completed, so they do not establish ordinary world-walking
coverage. The rows include camera position and partial-tick context for follow-up
analysis, not proof of a walking episode. The focused log scan found two
`WORLD_STEP_REJECTED` warnings before these capture windows, no Renderer ERROR, and no
class-loading or FPS warnings. Medical trace lines exist, but are not evidence
for a new medical outcome.
