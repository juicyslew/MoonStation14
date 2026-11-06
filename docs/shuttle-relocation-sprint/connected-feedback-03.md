# Controlled native strike/contact response (acceptance pending)

## Cabin regression diagnostic — 2026-10-24

Latest `run_2965a7bfe0466479260320cb` **PASS**: 152 managed JVM cases and all
10 selected native cases passed at six substeps: cabin union 5/917, mixed
world-floor/frame-side replay 1/917, and exterior wall controls 4/917. Cleanup is
confirmed. The descending trace retains raw SDK `below=true` while resolved body
and returned motor state are airborne; bounded downward own motion is preserved.
Both native water cases and authenticated canonical server throw/pickup pass.

This is **diagnostic only**, not replacement OWNER_READY or M1 acceptance.
Supported heading has focused coordinate/lifecycle tests, not connected camera
validation. The hand fixture uses an embedded authenticated actor, not a client;
rendered camera versus logical/server action aim and live prediction continuity
remain unverified. Paired bounded `[HAND_AIM]` receipts are available for that
boundary. Owner testing and flight-fixture work remain paused pending that review.

### Hand-input correction in development

Latest `run_e414acb294350d98db5f9c42` **PASS**: fresh managed JVM checks
passed **340/340**; native union **7/918 selected, all 7 passed** at six
substeps. This includes canonical frozen-target throw with changed server facing,
away-facing normal/selected pickup, current native translation/rotation target
resolution, world-fixed coordinates, removal/unloaded rejection, and retained
pickup eligibility/conservation controls. Cleanup is confirmed. The two preceding
pickup failures were corrected in fixtures only; production behavior was not
relaxed. This remains diagnostic evidence, not connected verification, OWNER_READY
or M1 acceptance. Owner testing and flight-fixture work remain paused.

`run_b787bac7d027995268292b05` **FAIL**: fresh managed JVM checks passed
340 cases; the native union completed **7/918 selected, 5 passed and 2 failed**
with six substeps. Canonical captured-target throw/away-facing pickup, all three
captured-coordinate cases, and exact nominated-target/stale-state pickup passed.
The remaining fixtures still expected a delayed/reserved nearer item to block a
different nominated item, or an old gaze-ray miss to invalidate a still-reachable
nomination. Those assertions conflict with the intended nongaze contract; fixture
corrections retain nominated-target delay/reservation, current reach/LOS and
unchanged ownership/payload checks. Runtime revalidation is pending. Cleanup is
confirmed; this does not establish connected readiness or lift the testing pause.

`run_126fa064a0fb73be7a433885` ended **ERROR**, not a gameplay verdict:
the managed JVM command was entirely UP-TO-DATE and did not supply fresh typed
test evidence; native execution did not start. Cleanup is confirmed. The next
diagnostic must force fresh JVM execution while retaining both six-substep native
arguments. Existing local 340-case evidence is not a managed native PASS.

The pinned SS14 checkout (`c9df5ef5d675b0d1d226828bddf6b78c28502d91`)
uses captured pointer coordinates for throws (`HandsSystem.ThrowHeldItem`,
`PointerInputCmdMessage`), not cached actor facing. Ordinary nominated pickup
uses interaction range/obstruction validation, not gaze (`SharedInteractionSystem`,
`SharedItemSystem`, `SharedHandsSystem.Pickup`). Human inherits Hands through
`BaseSpeciesAppearance`; upstream defaults are speed 11, range 8 and cooldown
0.5 s. Existing local item-profile throw values remain an unchanged 3D adaptation.

The current correction extends existing hand requests and loaded-ray/transfer
owners: world targets remain fixed; frame targets store exact UUID, absolute
storage point and source cell, resolved against the current server pose without
world fallback. Throws retain current server origin and release physics. Both
pickup APIs validate the nominated entity's current nearest physical point and
loaded LOS without requiring facing. A separate response fix preserves committed
world-host transitions when the requested hand becomes empty.

These newer edits were not covered by `run_2965a7bfe0466479260320cb`;
their fresh managed proof is `run_e414acb294350d98db5f9c42` above. Its **7/918**
selection comprises canonical cabin 1, captured targets 3 and nominated pickup 3.
Fixture orientation comparisons copy the quaternion, never mutating live pose.
Connected aim and prediction remain unverified. Real null-plot/non-FULL replacement-frame fixture
coverage is unavailable; source guards fail closed through the shared residency
validator. No owner retest or M1-readiness claim is made.

`run_d5d45601ebb66608a80d7eaf` **FAIL** at six substeps: 151 managed JVM
cases passed; native union **4/5 passed (5/917 selected)**. Both water cases,
canonical throw/pickup, and jump-ceiling/side passed. Descending contact confirms
a false-floor state: request/bounded Y `-0.07544406705`, measured Y
`-0.16789992233`, geometric Y `-0.09245585528`, yet native-below, body grounded,
and returned grounded state were all true. The candidate now corrects grounding
in the shared owned-move boundary only after accepted ceiling evidence and an
exact, complete vanilla-world clip observation proving no floor clip. Raw SDK
flags and the native journal remain unchanged; frame membership is not rewritten.
Focused guard/proof/motor checks passed; native revalidation is pending. Preserved
controls did not run in this diagnostic; cleanup is confirmed.

`run_f1d030d682a3379e51b3af67` **FAIL**, with normal six-substep staging restored:
151 managed JVM cases passed; the native union completed 5/917, **3 passed and
2 failed**. Canonical cabin throw/pickup, unrotated water slipping, and grounded
jump-plus-ceiling/side passed. The descending case obtained ceiling position
evidence but failed a later compound assertion; scalar diagnostics are being
added before that assertion. The yawed water actor remained grounded with a
known available frame query, but ground friction stopped the one-shot rotation
at `0.04909 rad`, below the test's arbitrary `0.1 rad` target. The query fixture
now requires a witnessed `0.02 rad` rotation; it is not a sustained-flight test.
Preserved controls did not run. Cleanup is confirmed; connected behavior remains
unverified and owner testing remains paused.

Earlier `run_e4fcf710766c14c5816a9603` **FAIL**: managed JVM checks passed
145 cases; the native union completed 5/917, with one pass and four failures.
Unrotated deck water slipping passed. Yawed deck setup timed out; the throw was
accepted but its response lacked the committed world-host transition. Initial
triage incorrectly treated this as a fixture expectation error; later inspection
found the empty-hand response branch discarded the transition. That branch is
now corrected and the independent UUID/token assertion restored.
Both ceiling cases reached real contacts without creating position evidence.
Preserved-control checks did not run; cleanup is confirmed.

This run used **ten** native substeps. The diagnostic assembler had omitted the
existing isolated-GameTest six-substep staging/expectation arguments used by the
earlier admitted diagnostics. It does not certify the required six-step setup.
Fresh requests must restore those arguments and the new cases must check the live
SDK value. No owner configuration change is needed.

Verified core corrections following that run: ceiling admission must not require
positive own-Y, and the ceiling position-evidence domain must retain the existing
`1e-6` residual allowance for tiny positive **side** geometry contributions. A
coupled native-SAT unit test produces positive side Y about `1e-7`, negative roof
Y, and accepted position evidence. No global tolerance or movement coefficient
was increased. Runtime revalidation remains pending.

`run_51160c41dcceef06d4486985` **FAIL**: managed focused JVM checks passed
130 cases; the first native selection ran 2/917 ceiling cases and both failed.
Later puddle, hand, and preserved-control checks did not run. Cleanup is confirmed.
Do not represent this as a partial native pass or a readiness verdict.

Fixture review found that its initial body pose was clear of the upper roof and
side wall, and the jump case lacked established ground contact. Its compound
assertion demanded contact evidence without establishing either contact. The
fixture is being corrected to use the lower cabin's actual ceiling/wall and a
genuine grounded jump; failure diagnostics now expose each prerequisite and the
native proof refusal. This failure does not establish a production-proof defect.
Owner testing remains paused; the five current blockers are indexed at the top
of `OWNER-FEEDBACK.md`.

## Client replay side-proof correction — 2026-10-23

Root cause verified in `SableOwnedBodyMotion`: pre-move bounds were captured on
the server only, so client-side `existingWorldClipObservation.matchesCall` could
not admit the existing ordinary-world-floor plus wall-side proof. The fix captures
the AABB on both sides and reuses the existing bounded, exact-entity/before-bounds/
input-output/downward and unchanged-XZ side-geometry contract. Default-required
side geometry was already captured even when optional geometry observation was
disabled. No global tolerance, strict-bounds, friction, air-slowdown, or camera
change was made.

Focused evidence `run9c6aefe3375dd96a5997ad62`: JUnit 33 passed (scope 11,
side-proof 18, prediction-failure 4); native mixed-contact 1/912 and server-wall
1/912 passed in separate fresh worlds; six-substep cleanup confirmed. The native
headless server adapter is not a live-client certification. Owner's latest
recording was mostly fine, but intermittent outside-armed-window teleport to
previous locations remains. In `logs/latest.log` at 19:27:02.746, replay stopped
at seq 3097 / ack 3096 on combined steel shuttle wall (normal `(-1,0,0)`) and
ordinary world floor: request `(.23849,-.44483,-.14908)`, measured
`(-2.4e-6,-.121297,-.149077)`, horizontal certificate null, end ground Y `-60`.
Prediction failure clears unacknowledged history and waits for a snapshot, which
is consistent with snapback, but the exact visible authority correction was not
logged; no matching server `WORLD_STEP_REJECTED` was found. Do not treat the
visible cause as proven. Connected retest remains required; no OWNER_READY/M1
acceptance is claimed.

Manual next check: restart with the updated client, stand still on a spawned
stationary shuttle, then jump against an outside wall for 20–30 seconds without
nudge or yaw. Note the clock time if a snap occurs; the existing 100-tick
diagnostic arm is optional and may miss a sporadic event (error logs still record
replay stops).

`SharedNativeSelectedContactJournal` records the exact selected SAT
`CollisionInfo` on both sides; it keys pose intervals with side-neutral
`levelGameTime` while retaining `serverTick` for fixture compatibility.
`NativeNormalContactPolicy.Target` is an immutable absolute m/s normal
prescription (idempotent, not an additive impulse). The hull selector floors,
skips compatible planes, and refuses unknown/overflow/corner cases; it checks
world endpoints before and after. `SableOwnedBodyMotion.MoveResult` keeps the
caller-requested preclip and `.05` incoming-velocity intent distinct from
measured accepted movement.

`NativeNormalContactFinalizer` runs after motor and frame correction for all
three owners: `GroundedHarnessWorldStep`, `OwnedCorpsePhysics`, and
`GhostControlClient`. Admission requires exactly one native move and verified
world endpoints. Client history retains pending sequence/target through replay,
with ack/skip/barrier/capacity/lost-history clearing; live capture is client-only.
Replay disables new native targets and reuses the stored normal prescription,
never an additive delta. The final server body velocity remains the existing
authoritative snapshot source; no packet was added. Replay aligns current pose
and geometry, not historical hull rollback. Connected latency/fidelity remains
unverified. Human rest-0 upstream mapping is already recorded in `PARITY.md`;
this adds no prototype fields or components.

Latest evidence `ab7d45b0f3e0650e328e5926`: PASS all 13 across four managed
checks (owned translation 1/908, headless union 10/908, hand 1, wall 1);
cleanup confirmed. Focused cheap checks: 36 XML checks (normal 11, history 18,
selector 3, finalizer 3, journal 1) and compilation. This does not cover the
new owned-rotation fixture (not yet validated) or corpse-specific runtime.
Camera, connected wall, extreme spin and persistence remain unresolved; this is
not OWNER_READY or M1 acceptance. The loose-only `moveInFlight` diagnostic is
not an eligibility signal for mob/owned movement.

## Generic native-mob frame-contact response

The bounded native adapter handles generic non-player mobs in world space after
Sable's native frame motion. Selected-contact geometry is captured on the exact
native `CollisionInfo` by `NativeSelectedContactJournal` / `Access`; this journal
is the authoritative SAT source. Both this adapter and loose-item response reuse
`ms14/shuttle/sable/NativeNormalContactPolicy`. The adapter consumes immutable
journal snapshots, runs at priority 900 after Sable's priority-1000 travel, and
uses actual owner exclusions. It refuses nonparallel contacts and fails closed
on stale, overflowed, or insufficient evidence. Vanilla restitution 0 is an
adapter case, not an SS14 pig-prototype implementation. No tangent carrier
velocity is copied; controlled-body prediction and connected-wall fidelity are
outside this result.

## Bounded automated support verdicts

- `rune9a483`: **PASS, 1/907**. After native frame motion, exact-frame ground
  contact remained bounded-local: local delta <1 while the frame displaced >.02
  over 35 physical ticks.
- `run9a513`: **OVERALL FAIL**, although its strike case **passed, 2/907**; the
  separate supported fixture failed setup. Do not represent this run as overall
  passing.
- Item union: **PASS, 1/7** (`1d7d`).

These are bounded automated checks, not sprint acceptance or readiness.

## Airborne bound-human separation — 2026-10-23

Latest native evidence `run4ff914aa3bac153cea87d2e4` passed **2/911**: the
translation and rotating cases `translatingHullSideImpactSeparatesAirborneBoundHuman`
and `rotatingHullSideImpactSeparatesAirborneBoundHuman`. The cases begin with a
real jump, no horizontal input, and target the normal after native motor motion.
They require a complete fresh side contact, a free own movement step, outgoing
normal velocity above `.05` with positive own outward displacement, total
displacement above `.02`, and exact OBB SAT positive clearance. Translation
committed `1.70987797 m/s`; rotation committed `.71851073 m/s`.

The separate grounded oracle `runb99d4eb3a574824d396b415d` passed **2 actual
cases**, not the initially prepared 4: its tags were misassigned and corrected
after bytecode preflight began reporting actual names. Do not call it a 4-case
native pass. It explicitly checks contact stop, world ownership, first-normal
response, outward displacement and no material penetration. The touching SAT
residuals (`6.23e-8` translation, `9.79e-8` rotation) are within native
`1e-6`; this is exact touching, not a free gap or burial. The shared tag selects
4/911; dedicated airborne selection is 2/911. All four native cases passed
across two runs, not in a single four-case run.

No production friction/air-slowdown tuning or external-flight lifetime was added.
The source invariant remains that `moveInFlight` is loose-item-only, not a
generic/owned eligibility signal; rotated exits use the actual oriented shape,
not a global AABB. This bounded fixture proof does not resolve connected wall,
camera, latency, extreme-spin or persistence behavior, and is not OWNER_READY,
M1 acceptance, sprint completion or a handoff.
