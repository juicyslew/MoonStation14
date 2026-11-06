# Character movement: durable invariants

This note records the movement/authority contracts to preserve while returning
to shuttle work. The owner reports movement is mostly good and natural air
slowdown is present; **stop tuning movement feel**. A later owner report says
falling off-station looks like ground contact followed by harm about a moment
later. The cause is unverified; track that as a separate follow-up, not a reason
to retune movement here. These notes document current intended behavior, not
completion or acceptance of the shuttle sprint.

## One physical simulation and its ownership

There is one simulation pipeline: server authority, live client prediction, and
every replayed prediction step must run the same movement phases and ordering.
Do not introduce replay-only physics or a second motor. Native owned `aiStep`
normalization writes (the `.98` velocity handling and tiny `.003` writes before
travel) remain guarded for custom-owned bodies. They must not be re-enabled as
hidden updates. Cancelling `travel` alone does not prove those earlier writes
are harmless; preserve the ownership guard and its tests.

The ordinary intended Minecraft PLAYER adaptation lives in the shared
`GroundedHarnessMotor`/movement pipeline: quantized input and the existing
resolved policy; `.003` blocks per-tick cleanup (`.06` blocks/second) before
forces/jump; horizontal `.91F` retention after movement (the float field is
promoted for arithmetic), chosen from the
pre-step grounded state (grounded on takeoff, airborne on landing); and existing
vertical `.98` handling. NoAI's `.98` all-axis retention is a distinct native
behavior, not normal player air drag. Ground controls are SS14-tuned; zero-G and
external flight remain separate modes. This is a Minecraft adaptation, not a
claim of full vanilla or SS14 movement parity. Continue consuming existing
resolved typed policy speeds; do not add prototype fields for feel tuning.

Gravity plus projected wish speed alone lets lateral air steering accumulate
unbounded horizontal energy. Bound only the voluntary candidate maximum using
the pre-resultant speed (including existing external impulse) and actual wish
speed. No-input momentum is not globally speed-clamped, but still receives the
intended normal-air retention; forces, constraints, external flight, and SS14
weightlessness are not globally clamped. The pinned SS14 ordinary-gravity
mover disables non-grounded input unless `CanMoveInAir` permits it. This
Minecraft adaptation deliberately provides gravity/jump air steering and its
voluntary-speed bound; it does not claim SS14 ordinary-gravity air-control
parity. Upstream SS14 weightless projected acceleration remains preserved
separately.

## Tracker and server authority

For the exact owned CHARACTER, vanilla relative, velocity, and absolute tracker
updates must not mutate the client-predicted pose, velocity, on-ground state, or
prediction history. Relative packet position decoding must still maintain the
position-codec base. An absolute teleport packet is not proof of an actual
teleport: `ServerEntity.sendChanges` in pinned NeoForge 21.1.228 emits that packet
for a normal on-ground toggle. An actual custom authoritative snapshot may
replace pose/velocity even without a new server revision; a server-side rebase
still requires its explicit revision/mode signal. Preserve the legacy GHOST fallback.

History ACK is the server's settled-through watermark: it may cover physically
applied commands or sequences explicitly discarded at a server-authority
barrier (for example, `LifecycleCharacterSessionControl.clearQueuedMovement`).
Client-local abandonment must never counterfeit an ACK, and coalescing several
predicted commands into one physical step must not advance the watermark as if
all were applied. When history is lost, retain a bounded valid suffix if
possible; otherwise continue sending input, but do not pretend delayed ACKs can
recover discarded history. Never replay across gaps or acknowledge a command
unless it was physically applied or explicitly settled by the server barrier.
History remains bounded and epoch/session-local.

## Reference and regression entry points

SS14 reference is pinned at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
SS14 ordinary-gravity non-grounded input is disabled unless `CanMoveInAir`
allows it; the local gravity/jump air-steering behavior is a deliberate
Minecraft adaptation, not parity. SS14 weightless projected acceleration is
preserved separately. This is not full movement parity.
Relevant focused starts: `GhostPredictionHistoryTest`,
`GhostOwnedEntityTrackerMixinTest`, `LivingEntityMindHarnessAiStepMixinTest`,
`GroundedHarnessMotorTest`, `CharacterMovementMotorTest`, and
`AirborneVelocityReplayRegressionTest`. Native owned-velocity coverage is tagged
`owned-velocity-authority`: compare real `aiStep` ownership against unowned
control and the shared adapter. Its model/open-collision boundary is not a full
client fixture.

Ownership fixtures require fresh disposable saves and managed per-run isolation
under the fixed coordinator. Never delete old worlds or weaken tests to make a
run pass. The latest accepted readiness snapshot is
`run_770945b0d3798b46c23f1e4a` (fresh native + JUnit + package; local 100
tests).
That readiness PASS and the owner's mostly-good / natural-coast report do not
mean full-suite validation, full shuttle-sprint completion, or M1 acceptance.
Keep movement work stopped absent new owner direction; parent retains final
acceptance and shuttle follow-up ownership.
