# Connected feedback: sustained exterior jump

**Exterior-wall jump fix accepted by the owner; M1 remains open.** After R10,
the owner confirmed: "Yes!!! That particular issue is now fixed." This accepts
the connected exterior-wall issue only, not cargo presentation, offline
body/cargo containment, M1 as a whole, or later milestones.

R10 (`shuttle-feedback07-side-y-20261014-r10`) passed the sustained straight and
diagonal reproduction (1/1 selected from 878), focused shuttle integration
(43/43 selected from 878), full JVM tests (3,279 passed, none failed/skipped),
and unfiltered native fixtures (878/878), with clean native save/shutdown.
Its review snapshot was
`67f51c6f72285630b6da879ef48700d32fe7f5ccbed9b5392d8acacfbae84f37`.
The connected owner confirmation is additional evidence, not an automated gate
claim. Preserve the correction/evidence invariants below during subsequent work.

Historical baseline: Feedback-06 r15 passed 3,260 JVM tests,
42/42 focused native fixtures (877 discovered), and 877/877 unfiltered native
fixtures with clean saving/shutdown. Those results do not reproduce or resolve
the owner's connected exterior-wall failure.

## Required reproduction

Build the normal shuttle fixture. Walk into an exterior wall and jump while
continuing to hold inward movement through ascent, apex and descent. Repeat
straight-on and slightly diagonally. The owner observes head-bonking or slow
descent at the apex, with sideways glide for nonperpendicular movement.

The existing exterior probe is insufficient: it stops inward input after tick
35, changes to tangent input, and requests jump at selected ticks. It checks
airborne contact/support flags, not the sustained vertical trajectory against
an equivalent unobstructed jump. See `SableOwnedBodyCarryGameTests.ExteriorWallProbe`.

Next proof must use the production cabin and movement owner, preserve one
physical attempt per tick, capture actual selected contact geometry, and compare
ascent/apex/descent against unobstructed movement. True overhead obstruction
must remain a head collision; a side-wall contact must not become floor support.
Connected input/prediction remains a separate acceptance requirement.

Stable connected log snapshots are under `build/shuttle-owner-feedback-07/`,
with source/snapshot hashes and capture metadata in `manifest.json`. Both server
and client launch headers reference agent-03 development classes/resources;
this does not identify exact class bytes loaded at startup.

The upward rejection at `latest.log:71` is not an established flat-wall
reproduction: its selected floor-row block corresponds to the hatch lip in the
canonical cabin layout, and the airlock's collision state at that moment is not
established. Do not label it an invalid internal top face or relax airborne
movement bounds based on that record alone. The new probe must target the solid
WEST wall away from the hatch/window and use an unobstructed trajectory baseline.

## Reproduction input correction

Reproduction R3 still never supplied a real jump: its test constructed
`GhostControlPayloads.Intent` with the jump bit in `verticalWish` and zero in
`buttons`. The server correctly observed `jump=false`. R4 fixes the test's
argument order, pins it with `ExteriorWallJumpIntentFactoryTest`, and holds the
jump input until consumption is observed. Repeated held bits are allowed;
another physical takeoff is not. These are fixture corrections, not evidence
that the owner's collision problem is fixed.

## Other reported failures

- Jug models turn with ships, but highlight/examine boxes remain world-aligned.
- Picking up/putting down or dropping cargo resets model orientation to world.
- At speed 40+, cargo flickers slightly; lower priority than collision/ownership.
- After an upward launch and logout, a cabin landed tilted onto its corner with
  jugs outside on the ground. In another trial the owner rejoined dead outside
  before the cabin landed on the body. Causes remain unverified.

## Current angular scope

The owner defers pitch/roll navigation work to get the basics working first.
Individual characters must not require separate pitch/roll controls. Ships may
eventually support those controls, with reasonable passenger navigation in
sideways/upside-down cabins. Odd-angle crash entrapment may be acceptable;
this is not permission to silently freeze ship rotation, add artificial gravity,
or excuse unexplained body/cargo loss.

No client launch, GUI automation, installation, packaging or owner-world/config
mutation is authorized. M2–M6 remain paused behind M1 acceptance.

## Fixture attempt log

R1's first filtered native attempt failed in setup (`neutral support input not
acknowledged`) before either jump arc or any wall-contact assertion ran; it is not a
collision reproduction. The support wait previously sent a new neutral sequence on
each poll. It now retains one pending sequence until the existing monotonic applied
ack catches up, and its bounded failure log records that sequence/ack, retry tick,
last physical-step tick, paired pre/post movement snapshot, and native move evidence.
No acknowledgement, movement, or grounding behavior is altered.

## R4 native reproduction and narrow correction

R4 completed the filtered native run cleanly (1 selected / 878 discovered; 0
passed, 1 failed). It reached the sustained jump on the flat WEST steel wall.
The first upward request was `0.42000000000000004`; native measured/bounded Y
was `0.4199999201441642` (an undershoot of about `8e-8`). Every captured row
was horizontal side contact against the cabin wall, with no vertical collision,
below-support, or world-horizontal flag; geometry and native movement budgets
were complete. The motor interpreted the tiny Y displacement mismatch as a
blocked vertical axis, zeroed wall vertical velocity, and diverged from the free
baseline (`0.3332` vertical velocity) on the next step. This establishes a
collision-response reproduction, not a head/hatch or angular-classification
failure.

The scoped follow-up reuses `NativeSideContactProof`'s complete ordered geometry
replay and `ContactPositionCorrectionEvidence`'s existing motor-consumed position
correction. Only a fully verified pure-side trace may retain requested own-Y;
the signed measured-Y remainder is position-only and must stay within the
existing `1e-6` residual budget. Floor/mixed rows, any vertical row, incomplete
geometry/native budget, and larger discrepancies remain on existing strict
clipping behavior. The final R10 validation and owner confirmation are recorded
above; intermediate attempt status below is historical.

## R5 result and R6 tangent oracle

R5 applied the narrow pure-side Y correction: straight-arc vertical motion then
matched the free baseline, while the diagonal fixture failed its tangent oracle.
That tangent comparison was invalid: after the wall's normal component is
removed, the wall actor's dot-product speed in the held diagonal wish direction
falls below target, so ordinary airborne acceleration can legitimately increase
its tangent component while the free actor is already at target speed. This is
the shared-mover acceleration law (`SharedMoverController.Accelerate` in the
pinned SS14 checkout); the local Minecraft motor uses the same wish-speed minus
velocity-dot-wish calculation as an adaptation, not full SS14 air-control parity.
R6 keeps the free actor as the vertical trajectory baseline and a diagnostic for
diagonal tangent, but checks wall tangent per-step and cumulatively against the
wall's captured `requestedOwnDisplacement` projected onto the fixed cabin
tangent. Existing `.02` per-step and `.10` cumulative excess bounds remain
unchanged. The straight arc still compares against zero requested tangent. This
changes the fixture oracle, not movement behavior or collision evidence.

R7 passed the corrected straight/diagonal sustained-jump fixture (1/878 selected)
but failed two of 43 focused exterior probes. The NORTH wall had own request
`(.110484959, -.001, .110485910)` and measured displacement
`(-1.229493e-6, 0, .110485471)`; its selected normal included Z `-7.22531e-6`.
The complete Sable trace contained side rows only, while the SDK's requested
motion still had a small negative Y (`about -.00100014`) and vanilla world
collision clipped measured Y to zero. The tracked-sublevel floor-row proof
correctly could not claim that world-floor clip, and strict native-to-measured Y
matching rejected the case. R8 adds an exact-entity, single-call observation of
the stock `Entity.collide(Vec3)` input/output inside the existing tracked
collision scope. World-floor side proof still requires complete native side
geometry/response replay, same-call input/output and pre-move bounds, a downward
request/input, an observed downward Y clip, grounded/below flags, no vertical
Sable row, and no material world X/Z clipping. It does not assert a Sable floor
row, enlarge tolerances, or alter movement/velocity behavior.

R8's 1/878 selected reproduction passed, but one of 43 exterior probes failed
on a WEST wall during a real landing. At tick 36 the own request was
`(0,-.426909648,.207892231)` and measured movement was approximately
`(-1.15669e-6,-.0443874444,.207892231)`; the actor's pre-move feet were at
`300.0443874` and it landed grounded on world terrain. The complete SDK trace
had only side rows and accounted for the normal correction. R8 incorrectly
required the vanilla clip's output Y to be exactly zero, so it rejected this
legitimate partial downward clip.

The follow-up accepts an observed floor-clipping stage only when its captured
input Y is negative and its output Y is strictly greater than the input while
remaining nonpositive. The proof retains that measured partial descent as
bounded own-Y and applies no Y position correction; the motor then grounds
normally and stops subsequent downward velocity. Zero-output landings remain
covered. No upward/step output, non-clipping movement, missing or mismatched
observation, material X/Z world clip, vertical Sable row, or incomplete native
replay gains authority. R9's subsequent result is recorded below.

R9 passed the focused sustained-jump reproduction (1/878 selected) but still
needed one exterior-probe correction. After the partial landing was accepted,
the WEST wall on tick 37 had request
`(-7.97017e-7,-.001,.15625)`, measured displacement
`(-1.848467e-6,0,.15625)`, and a complete side MTV of approximately
`(-1.05145e-6,-1.23202e-13,0)`. The tiny outward X request and side MTV point in
the same direction, so native replay preserves the own X request and treats the
MTV as position-only correction. The world-floor context was rejected because
Sable's aggregate `subLevelHorizontalCollision` flag was false even though its
first collision was a known horizontal steel-wall contact and the complete SAT
trace certified the side geometry. In the pinned SDK, `firstCollisions` records
the horizontal contact before `tryStepUp`; that aggregate flag is set only when
`tryStepUp` returns false. Its false value can therefore mean the current motion
already points outward along the MTV; it is not proof that the captured side row
is absent.

The R10 candidate removes only that aggregate flag as a prerequisite for the
observed-world-floor route. The route still requires the exact downward vanilla
clip token, grounded/below collision state, no world-horizontal collision, the
captured first horizontal native contact, and complete same-frame SAT/response
replay with no vertical Sable row. Existing Sable-floor and pure-side paths keep
their prior flag rules. R10 subsequently passed the full gate and received the
owner's issue-specific connected confirmation, as recorded above.
