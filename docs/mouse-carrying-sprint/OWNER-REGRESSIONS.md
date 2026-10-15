# Owner-reported carrying regressions

## Current correction: r34 (supersedes interruption/gait claims below)

r33 full native selected 910/910 and failed two further expected-powered close
starts: unsafe retained-dead crush and repeated dead-tangent closing (full log
lines 24224/24227; SHA-256
`167f17103b672de6c18b72943607f5691d38a5c8f077505455a21aba2ce170ae`).
`AirlockConnectedGameTests` now routes powered-phase boundaries through the
existing full-power barrier, rechecking phase and root identity immediately
before action. The complete activation audit also covered both stationary
critical-crush closes, including the post-safety-wire and repeated-cycle paths.
Intentional unpowered/obstruction denials and post-close power cuts remain
separate. All expected-powered starts were reviewed; no production rules changed.
Compilation and independent review passed. Fresh r34 full validation and
packaging remain required; no current artifact is ready for owner testing.

r32 full JVM passed 3,321, focused native passed 344/344 of 910, and affected
readiness passed. Full native selected 910/910 and failed two airlock close-start
assertions (`powerLossDuringUnsafeCloseAndAdjacentActorDoNotShock` and
`safeLateObstructionAbortsWithoutCrushOrContactShock`, full log lines 23537–23538).
Those fixtures now reuse `AirlockCoreGameTests.waitForFullPower` immediately
before close, asserting the door is still OPEN. The unsafe power cut remains
after close starts, and safe obstruction/shock/crush assertions remain intact.
The shared `PowerRuntime.airlockPower` predicate already verifies topology,
generation, sample age and APC identity/revision; no second freshness owner was
introduced. Only fixture readiness changed. Compilation and independent review
passed; fresh full-native confirmation and packaging remain required.

r31 full JVM passed 3,321; focused selected 344/910 with two required failures.
Actual log SHA-256 `70258961bd3400ddf7a1b778da8013f428e958cd28dd87b36c0f1b2b8b71b0e1`
records dead throwing post-launch recovery and an already-arrived idle fixture.
The remaining portable-target postcondition also used native `live(target)`;
all preflight/placement/post-launch target checks now reuse exact retained-body
validity, while thrower authority and lease/position/velocity checks remain.
The idle fixture now observes an actual destination after ticking readiness,
allows short routes to finish naturally, and uses bounded retries for a route
long enough to observe replanning before arrival. Same-destination, actual-arrival
and wait assertions remain; NPC speed/arrival thresholds are unchanged.
Both corrections passed independent source review; native confirmation remains
required. Earlier candidate results below are historical, not current passes.

r30 fresh full JVM passed 3,321 tests; focused native selected 344/910 and failed
five required checks. Current corrections (source-reviewed; native rerun pending):

- Dead throwing's actual denial was `BodyLooseThrow` placement revalidation using
  native `live(target)`. It now reuses exact loaded/retained validity from
  `BodyContainmentService`; actor action authority remains unchanged.
- Pull/stop matrix re-used request sequences across medical states, causing
  `REPLAY`; one strictly increasing sequence now spans the entire matrix.
- Physical-drop fixture used a fabricated `escape-drop-key` hand token, not its
  portable reference UUID. Fixtures now use canonical tokens and assert the
  production reference/hand-token relationship; the guard was not relaxed.
- Throw countdown recursion now retains one immutable absolute lease end tick,
  instead of reusing a decreasing prior remainder with the original time base.
- Jump probe now uses shared `CommittedMouseRoleSetup` and accepted production
  input through `RegisteredPlayerReadyDriver`, not manually granted ownership.
  It asserts a second post-tick invocation without new input leaves position and
  velocity unchanged. Native/remote presentation remains unverified.

r29 dispatch stopped before validation because its bounded prompt exceeded the
limit. r30 condenses duplicate plan prose without dropping required checks or
behavior criteria; gameplay source is unchanged from r29 and uses fresh worlds.

Latest owner: dragging and dead-mouse throwing fail; repeated jumping appears
airborne to remote viewers; carrier camera override embeds the view; hand label
still shows the item description; disposal mouse eye is inside the bin. Escape
and flushing now work and must remain intact. The owner requests a concise test
checklist with each build; earlier no-testing notes are historical.

- Inherited `Pullable` is restored on `supported_simple_mob` (current concrete
  descendants: pig and mouse), matching pinned SS14 `BaseMob`. Shared
  `BodyPullService`/client projection already consume it. Mouse remains handless
  and is not a puller. New normal human pull/stop tests cover all three mouse
  medical states.
- Corrected upstream finding: `HandsSystem.ThrowHeldItem`/`CanThrow(user,item)`
  apply mob-state action blocking to the **thrower**, not the dead held target.
  Shared `OwnedCorpsePhysics` eligibility now allows resolved retained bodies to
  keep their external flight; arbitrary dead mobs remain excluded. New normal
  dead-mouse pickup/throw coverage preserves the original body.
- `BodyHandClient.drawHandInfo` now uses the presented stack's hover name for
  all named items, rather than ignoring `CUSTOM_NAME` for registry descriptions.
- The rejected carrier camera override and unused render-parent metadata/codec
  are removed. Native held-camera placement is restored; held-motion smoothness
  remains unresolved. Disposal-unit `positionFor` aligns all occupant eye heights
  using `Player.DEFAULT_EYE_HEIGHT`; transit keeps its existing bore alignment.
  A live unit test covers human/mouse viewpoint and authoritative pinning.
- A held-jump trace reuses the owned motor and vanilla packet producer, recording
  grounded jump starts/positions over multiple cycles. This is diagnostic coverage,
  **not a fix or proof of remote-client interpolation**.
- Motion fixture uses incremental carrier movement rather than a discontinuous
  teleport before body pinning; same-operation checks remain. Drop preflight and
  original SUCCESS/cancellation checks remain; native denial is still unresolved.

Independent reviews passed for shared pull/corpse flight, HUD name selection,
camera rollback/unit view, and jump probe. Serialized author compilation and
82 focused JVM tests passed (zero failures/errors/skips). Fresh r29 native checks
force task execution to avoid stale outputs from concurrent author compiles.
No new validated artifact or GPU/connected-client acceptance is claimed.

### r28 evidence correction

The central prose report's four-failure count, sound failure and line references
did not match its bound native artifact. Actual focused log SHA-256
`3d6882346418fb7e17e9654839d1d380d7ce09a3b3c00ed95125a218ecf9efe8`
has 1,198 lines, selects 335/907 and records **13 required failures**: multiple
registered-player `ClosedChannelException` failures, operation replacement
(line 810), and DROP denial (line 943). There is no ambient squeak failure in
that bound log; the unrelated fixture edit was reverted. Central evidence
correction was requested without rerunning the obsolete candidate. All current
checks require fresh r29 worlds and counted evidence; none of those failures is
waived. Earlier r28 prose references below are superseded by this correction.

The latest owner report confirms improved throw motion and successful hand/backpack
escape, but identifies slow NPC travel, invisible escape progress, hand-selection
and carrier-motion resets, held-camera jolts, unreadable names and a playable-mouse
throw recovery kick. No further owner testing is requested.

- Pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91` uses sprint steering for
  ordinary NPC travel; the mouse resolves walk/sprint 2.5/5. The local half-walk
  idle policy was incorrect. Autonomous travel now consumes resolved sprint speed
  through the existing absolute-speed motor conversion; player gait is unchanged.
- **Correction to earlier parity notes:** SS14 `DoAfter.cs:44–57`,
  `SharedDoAfterSystem.cs:229–238`, `SharedDoAfterSystem.Update.cs:218–241`, and
  Robust `SharedTransformSystem.Coordinates.cs:194–208` store/compare parent-relative
  coordinates. Carrier translation does not inherently interrupt escape.
  `TimedInteractionSystem` now accepts a movement-coordinate supplier; portable
  escape supplies body-to-root coordinates. Other callers retain world sampling.
  Five seconds, 0.3 relative-movement/separation thresholds, range 1.5 and single
  damage threshold 1 remain. Active-hand selection preserves progress; physical
  drop/transfer cancels. Existing custody/release ownership remains authoritative.
- SS14 `DoAfterOverlay.cs:108–120` hides contained progress from non-local viewers;
  only the escaping actor sees its dimmed bar. Captor-visible progress is not the
  standard parity target. Timed client ownership now uses the existing stable
  presentation identity, not medical-tracker/camera ownership. Connected HUD
  visibility remains unverified; automatic event registration was already present.
- The throw kick's source mismatch was a six-tick snapshot/prediction limit versus
  the mouse's resolved fifteen-tick flight lease. Codec and prediction now preserve
  nonnegative leases; terminal snapshot failures retain their cause in the log.
  Registered-player coverage exercises normal pickup/throw, production snapshots,
  codec round-trip and committed identity/control through expiry.
- Portable key creation/refresh retain `CharacterPublicName` Components instead
  of flattening translatable names into server text and then literal item names.
- Render-parent metadata is separate from custody identity: the latter is the
  reference token, **not** the carrier UUID. Carrier `viewFor(body)` supplies the
  actual entity UUID/relative offset; the camera follows its interpolated render
  position without moving the authoritative body. Attachment ends with containment;
  unavailable carriers fall back to the existing camera path. Lookup caches one
  validated loaded carrier. GPU smoothness remains unverified.

Author validation: main/GameTest compilation and 84 focused JVM tests across 11
classes passed (zero failures/errors/skips). Independent source reviews passed
for names, flight leases, relative escape and camera composition. A name worker
also ran one native name test successfully but improperly ran a broader author
selector with seven startup/channel failures; these are **not waived**. The fresh
central gate must establish current native/readiness/full-suite results.
The r26 gate stopped at tag preflight before native fixtures: the submitted
`portable-player-throw` selector was unregistered. r27 removes it; the new
`committedMouseThrowSnapshotKeepsLongFlightLease` fixture is registered under
`portable-throw`, already selected. Fresh r27 fixture worlds are required.
No connected-client automation route was established, and no deployment, commit,
owner acceptance or sprint closure is implied.

### r27 gate findings and r28 fixture corrections

Fresh full JVM: 3,320 passed, zero failures/errors/skips. Focused native selected
335/907 with three failures; readiness/full native/packaging did not run.
The throw fixture incorrectly checked the former six-tick boundary while the
valid lease was still above it. It now compares snapshot and authoritative ticks
against the exact captured launch-time countdown through zero; independent review
passed. The moving-carrier fixture checked at 120 ticks, after a 100-tick action's
terminal record had expired (10-tick retention). Its completion check now runs
inside retention and separates same-operation identity from completion.
The physical-drop fixture failed at `dropOwned` admission, before cancellation;
the cause remains unresolved. Non-mutating per-guard preflight now reuses the
production owner, placement, clearance and release-snapshot checks; the existing
clearance predicate is package-visible for this fixture, with no behavior change.
The original drop-success/cancellation assertions remain; independent review
passed and main/GameTest compilation passed. These are
source findings, not native passes; a fresh central gate remains required.

The owner rejected the current behavior of candidate
`mouse-carrying-20261013-r3`. Earlier technical recommendations are not owner
acceptance, and helper tests do not establish working physical input paths.

The owner has since reported that dead pickup, smooth throw acceleration and AI
escape remain broken, and explicitly declined further testing. Do not request
more owner checks. Reproduce and verify those cases through the central gate;
compilation and source review do not clear them. The newly published shared
workflow uses authenticated `opencode api` dispatch, replacing the old raw-HTTP
mechanism that failed with 401.

## Ordered work units

1. **Hand interactions:** repair normal drop, make portable-target pickup
   independent of target medical state, and align hover affordances with actual
   pickup eligibility. Reuse normal request authority, shared target probes and
   the existing portable release transaction. Do not create another remover.
2. **Shared flight:** apply existing gravity/vertical drag throughout external
   flight while retaining steering suppression, momentum, collision and ownership.
   This affects throwing and disposal momentum, not just mice.
3. **Names and diagnostics:** stop position-based chunk-log spam; use existing
   resolved public names/components consistently through cached views and examine.
4. **Escape:** verify actual adopted-player directional input and AI custody with
   matched stationary-holder conditions. Fix handless progress presentation;
   diagnose physical release rather than assuming presentation explains it.
5. Review all changes, submit one structured candidate, automatically invoke the
   central integration-lead gate in worktree 05, and correct/resubmit on failure.
   Only that gate runs expensive shared validation or builds. No owner-testing
   request or technically-ready claim before it passes.

## Report mapping and current evidence

| Owner item | Disposition |
| --- | --- |
| 1: Cannot drop | Bug under investigation. Ordinary drop already dispatches to `dropPortableBody`; the missing behavior must be traced in the real request/placement path, not fixed by adding a duplicate release implementation. |
| 2: Delayed gravity | Confirmed: the shared movement motor suppresses gravity during the entire external-flight lease. Correction and first-three-tick unit checks exist; native consumer checks await the central gate. |
| 3: Cached-chunk spam | Confirmed: diagnostic deduplication includes sub-chunk render position. Meaningful chunk/cache-state deduplication is implemented and its focused test passed. The query is non-loading. |
| 4, 12: Unreadable names | Shared public-name policy now prioritizes native custom names, resolved character metadata and native component fallback; cache/examine/preview consumers reuse it. Four focused naming/diagnostic/gesture/examine classes passed. Live rendering remains unverified. |
| 5, 6: AI escape depends on holder control | Unresolved. Automatic AI escape is an explicitly requested Minecraft adaptation, not an upstream mouse HTN task. Creative is not an upstream escape gate. Match physical conditions and distinguish attempt cancellation from never starting. |
| 7: Live mouse in backpack | Expected SS14 behavior for an ordinary backpack. Tiny Item composition fits storage; inspected storage admission has no living-mob rejection. Preserve it. |
| 8: Critical/dead pickup fails | The initial retained-body eligibility change missed a second `target.isAlive()` rejection after the successful server ray. That final rejection is now removed, preserving identity/position/range/authority/capability revalidation. The normal-request DEAD fixture explicitly asserts native `isAlive() == false` before same-UUID pickup. Compilation/source review pass; native outcomes await the gate. |
| 9: No interactable highlight | Hover selection and outline consumers now share portable pickup eligibility/ordering. Source compiles; native cursor/GPU appearance is not claimed. |
| 10: Holder movement interrupts escape | SS14 parity, not a bug. Keep the default movement and separation checks. |
| 11: Player mouse cannot escape | Open physical outcome. The hands-only presenter bug is corrected. The compiled/source-reviewed canonical adopted-mouse fixture covers ordinary pickup, accepted directional input, controller progress/origin sequence, repeat timing and release/control identity. Two AI holder-state fixtures also compile; native execution awaits the gate. |

## Verified upstream invariants

Checkout pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`MobMouseDead` inherits the mouse Item/escape composition. Shared hands pickup
does not require the target to be alive. Ordinary backpacks accept Tiny items
without an alive blacklist. Escape is movement-input-triggered, takes five
seconds, requires no hand, and interrupts on user displacement or user-target
separation change above 0.3, inaccessible/out-of-range targets at 1.5, and a
single committed damage event of at least 1. No Creative-holder exception exists.

Sources: `animals.yml`, `simplemob.yml`, animal body parents, `backpacks.yml`,
`SharedHandsSystem.Pickup.cs`, `SharedStorageSystem.cs`,
`EscapeInventorySystem.cs`, `DoAfterArgs.cs`, and
`SharedDoAfterSystem.Update.cs` in the pinned checkout. SS14 does not establish
Minecraft 3D ballistic height physics; shared flight gravity is a port adaptation.

## Pre-gate evidence

The first owner-regression candidate introduced another client pickup regression:
client nomination called `PortableBodyCarrying.resolvedPortableBodyCapability`,
which reached the server-only `CharacterIdentitySystem.resolveForActor` and
always returned empty on clients. This is corrected by the existing
`projectForActor` client read owner plus `PortableLivingClientTargetPolicy`.
Server authority was not broadened. Click and hover now use the client policy;
**51 focused JVM tests passed** and GameTest sources compile. Independent source
review passed, but no real client level or physical click was exercised.

Shared motor/flight-relocation unit selection: **46 passed**. Naming, diagnostics,
gesture and examine policy: **four focused classes passed**. Main and GameTest
sources compile, including the normal DROP/medical pickup fixtures and three
escape control cases. Independent read-only reviews found no source blocker;
they do not establish native outcomes. No author ran expensive suites or built a
replacement JAR under the updated workflow. The structured central plan is
`owner-regressions-gate.json`; DROP and physical escape remain mandatory runtime
gates, not completed fixes inferred from source or compilation.

Historical transport blocker: the first automatic central invocation failed before creating a session:
OpenCode `/api/info` returned HTTP 401. A direct authenticated CLI invocation in
worktree 05 was denied by shell permissions; it was not retried through another
route. No expensive gate checks or replacement build have run. The corrected
client-boundary revision must be resubmitted and the authorized gate handoff
enabled before technical readiness. The installed local shim now loads the shared
authenticated-CLI dispatcher; a fresh revision will use that path. There will be
no further owner-testing request.

Latest correction adds a bounded, normally enrolled mouse-flight trace across
lease expiry using the vanilla `ServerEntity` packet producer with the mouse's
actual interval/delta settings. Eighteen per-tick position/velocity/advance/packet
samples are emitted once, including on success, under `npc-flight` and
`flight-packet-trace`. Sources compile; cadence and client-frame behavior remain
unmeasured until execution. The nominal source interval is three ticks. This
diagnostic is not a claim that the reported acceleration jolts are fixed.

## Central gate r3 — needs changes

Authenticated dispatch succeeded for `mouse-carrying-owner-regressions-r3`,
central session `ses_ec917ac31ffe7obVO9QkiNVk2X`. Focused native selection was
**310/899**, completed with **302 passing / 8 required failures**. The runner
stopped before readiness, full JVM/native suites and packaging. No replacement
artifact or technical readiness is claimed.

Evidence: `build/gametest-owner-regressions-gate-focused-r3/logs/latest.log`.

- Critical and dead pickup requests reported `OK`, a canonical carrier owner and
  native `isAlive=false`, but their combined conservation assertions failed.
  Investigate current-versus-original containment flags and canonical hand views;
  do not waive the assertions or claim physical client pickup.
- The DROP fixture stopped at a stale request returning `STALE_HAND`; valid DROP
  was not reached. Establish fresh revision/token proof before testing stale-token
  rejection and normal release.
- The normal-holder AI operation reached `COMPLETED`, but its release
  postcondition failed. A terminal timer alone is not successful release.
- Player-mouse directional input was accepted, but no active escape was observed.
  Verify the synthetic fixture drives the real player-tick handler and distinguish
  that boundary from an actual client-input defect.
- Three NPC tests failed: step/drop, idle arrival and bounded flee expiry. The
  step/drop trace showed native `noGravity=true`, `onGround=false` and zero
  velocity despite a valid route. Review engine gravity suppression versus the
  shared grounded motor's gravity responsibility.
- The bounded mouse flight/packet trace ran and was emitted. Samples include
  changing velocities and three-tick movement packets; they do not establish
  client-frame smoothness. Inspect native/motor phase coupling before tuning
  packet cadence or adding interpolation.

The author is correcting these groups for resubmission. All failure artifacts
are retained, and the owner is not being asked to test again.

### r4 corrections awaiting the gate

- Grounded profile gravity is again independent of the native no-gravity flag,
  which is used to suppress vanilla physics. The extra vanilla `aiStep` 0.98
  scaling is bypassed only for shared-motor-owned bodies, leaving ordinary mobs
  unchanged. The actual mouse-flight trace now checks single-step gravity/drag
  and horizontal momentum. Focused motor/relocation checks: **46 passed**;
  GameTest sources compile; independent source review passed.
- Medical pickup fixtures distinguish pinned runtime flags from original flags
  retained in the custody export. Stale-token DROP uses a fresh canonical hand
  revision before the valid DROP/restored-original-flags assertions. Source
  compiles; runtime results remain pending.
- The synthetic registered-player escape fixture explicitly dispatches accepted
  input through the existing public player post-tick handler. This is production
  handler coverage, not a real socket/client event. Bounded custody diagnostics
  were added to the AI cases so a completed timer with failed release reports its
  actual failing gate. Sources compile; no physical escape pass is inferred.

## Central gate r4 — needs changes

Central session `ses_ec909b1ebffe7PfAwKGstxBu2g`: focused JVM **114/114 passed**;
native **310/899 selected, 305 passed / 5 required failures**. Readiness, full
suites and packaging were not reached. Evidence is retained in
`build/gametest-owner-regressions-gate-focused-r4/logs/latest.log`.

The three NPC failures from r3 no longer failed, and the exact single-step mouse
flight trace passed. This supports the shared gravity/damping correction, not
client-frame smoothness. Remaining failures are critical/dead pickup
postconditions, stale-token DROP assertion, and player/normal-holder AI escape
release. The raw native log shows DROP still stopped at the wrong-token request
(`STALE_HAND`), before valid DROP; the central summary's subsequent-release
wording must not be mistaken for execution evidence. Player escape now starts
and reaches a completed timer, but retains custody. These are active corrections,
not accepted behavior. Further owner testing remains prohibited.

### r5 correction/diagnostic candidate

Medical conservation assertions now capture original flags immediately before
pickup, after enrollment, and diagnose each invariant individually. Wrong-token
DROP expects the shared admission's existing `STALE_HAND` result, preserves exact
ledger/custody checks, and then attempts valid DROP with a fresh queried revision.
Main/GameTest compilation passed; native outcomes await the central run.

Escape diagnostics previously filled their bounded buffer with repeated
`started` events before the terminal outcome. Fixtures now filter those repeats
and include the shared release transaction reason. This is a diagnostic
correction, **not an escape fix**; another central execution is needed to expose
the release denial. GameTest compilation and diff checks passed. Source is frozen
for central verification; no additional owner testing or replacement artifact is
authorized by these checks.

Independent review required an additional valid-DROP assertion: the current
canonical hand ledger must contain neither token nor stack after release. That
assertion is now included and GameTest compilation passed.

The complete r4 flight trace contains 18 samples, ticks 1874–1891, with constant
horizontal velocity 0.5. Lease expiry at 1888 preserves the per-tick gravity/drag
sequence; no server velocity discontinuity is shown. The final cumulative packet
list contains six position packets, within the fixture's 5–8 bound. Tool preview
truncation is not artifact truncation, and cumulative packet lists must not be
added across sample rows. Client-frame smoothness remains unverified.

## Central gate r5 — needs changes

Central session `ses_ec90078eaffe2ZtOkjvWxGxWs1`: focused JVM **114 passed**;
native **310/899 selected, 307 passed / 3 required failures**, per the raw log's
completion summary. The central response mentions the AI failure but omits the
player-release and ambient-squeak failures; all three require resolution.
Evidence: `build/gametest-owner-regressions-gate-focused-r5/logs/latest.log`.

Critical/dead pickup conservation and normal DROP no longer fail their runtime
fixtures. Player and normal-holder AI escape still retain custody. AI completion
diagnostics show current/removable custody but `placementClear=false` at the
holder's feet, therefore release is denied before the transaction. The reported
`release=success` is stale shared diagnostic state, not this escape's outcome.
The shared collision query already excludes the exact holder; determine the
actual blocker rather than adding a duplicate holder exception.

The squeak fixture missed a resolved randomized emission within its fixed wait;
no production sound defect is established. Its policy-derived budget is under
review. Both its failure text and the AI diagnostic exceeded the native 1024
character marker limit and caused encoding errors; shorten fixture diagnostics
without discarding the decisive denial. Readiness, full suites and packaging
remain unrun. The owner will not be asked for more testing.

### r6 candidate: physical overlap eligibility and bounded diagnostics

`BodyItemTargetProbe.portableBodyReleaseClear` now excludes nonphysical
(`noPhysics`) entities in addition to its existing exact-holder/item exclusions.
Loaded solid blocks and other physical entities still deny placement. The native
`releasePlacementIgnoresOnlyNonphysicalOverlaps` fixture covers all three cases;
source compilation and independent source review passed. The actual r5 blocker
was not identified, so this correction is not yet proof that escape succeeds.
Escape failures now have bounded text and report overlapping entity/physics
details without overflowing the native marker limit.

The ambient fixture's existing 410-tick wait already exceeds the resolved
160–400 tick interval. A shorter wait would not fix r5 and was rejected. The wait
is retained; a read-only scheduled-tick accessor and short position/timer
diagnostics now distinguish producer timing from a missed sound event. This is
diagnostic-only, not a sound fix. Sources compile.

Pinned upstream clarification: at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
`Content.Server/Resist/EscapeInventorySystem.cs:OnEscape` calls
`AttachParentToContainerOrGrid`; it does not explicitly place at feet/hands or
probe collision clearance. Local loaded placement/solid-world checks are a
Minecraft adaptation, not verified upstream rules. Do not invent an upstream
offset or claim the local clearance algorithm is SS14 behavior.

## Central gate r6 — needs changes

Central session `ses_ec8f892baffe32UbQ1OZL0mPei`: focused JVM **114 passed**;
native **311/900 selected, 309 passed / 2 required failures**. The new collision
eligibility fixture passed, as did medical pickup, DROP and the ambient fixture
on this invocation. A single passing ambient invocation does not establish the
cause of the earlier missed sound. Readiness/full suites/packaging remain unrun.

Both player and normal-holder AI escape still complete their timers but retain
custody. The shortened failure assertions exposed only the final denial, not the
preceding placement/blocker diagnostic. That evidence gap must be corrected:
emit the bounded full terminal diagnostic once from the fixture logger while
keeping the native assertion text short. Do not make further speculative
production collision changes or declare the actual blocker identified.

Evidence: `build/gametest-owner-regressions-gate-focused-r6/logs/latest.log`.

### r7 diagnostic candidate

Both escape fixtures now emit their full bounded terminal diagnostic lists once
before asserting release, separately from the short native marker text. Placement
diagnostics include overlapping entity type/UUID, position, native physics and
spectator status. GameTest compilation and diff checks passed. No further
production release filter changes were made, and escape is not claimed fixed.
The next fresh central run is required to identify the actual blocker.

## Central gate r7 — blocker identified

Central session `ses_ec8f3ae65ffe1A0OV1XecHIQsr`: native **311/900 selected,
309 passed / 2 required failures**; broad/readiness/packaging checks remain
blocked. Full fixture diagnostics identify an overlapping player shell with
`spectator=true` and `noPhysics=false` at the escape position. The collision query
wrongly treated this spectator as a physical blocker. This explains why excluding
only the holder and native `noPhysics` entities was insufficient.

Evidence: `build/gametest-owner-regressions-gate-focused-r7/logs/latest.log`.
The correction will reuse Minecraft's spectator-exclusion predicate in shared
placement checks, with a native regression distinguishing spectator and ordinary
physical player overlaps. No blanket player exclusion or lifecycle bypass is
needed. Actual player/AI release remains pending the corrected central run.

### r8 correction candidate

Shared drop/release collision filtering now reuses vanilla spectator exclusion
and native `noPhysics` eligibility. Both placement-candidate and release checks
consume this predicate. Solid blocks and ordinary physical players remain
blocking. `releasePlacementIgnoresSpectatorShellButBlocksSurvivalPlayer` tests
the exact native `spectator=true/noPhysics=false` combination against a Survival
player, alongside the earlier physical/nonphysical/block fixture. GameTest
compilation, diff checks and independent source review passed. The original
player/AI runtime matrix is unchanged; central execution remains mandatory.

## Central gate r8 — escape passes; fixture setup fails

Central session `ses_ec8ef39f7ffeYV5JtWUah5G5yb`: native **312/901 selected,
311 passed / 1 required failure**. Both the accepted-input player escape and the
normal-holder AI escape now pass release/custody/identity assertions. Medical
pickup and normal DROP also pass. This is registered server-path evidence, not
authenticated client input or owner acceptance.

The only failure is the new spectator fixture's setup assertion, before its
clearance calls. It incorrectly interpreted `setGameMode`'s return value as
admission rather than whether the mode changed. r9 calls the setter, explicitly
sets the required native physics flag afterward, and checks actual mode/flags.
Both spectator allowance and Survival blocking assertions remain; cleanup now
runs in `finally`. GameTest compilation and diff checks passed. Production
collision code was not changed. Remaining broad/readiness/packaging checks still
require a fresh complete gate.

Evidence: `build/gametest-owner-regressions-gate-focused-r8/logs/latest.log`.

## Central gate r9 — physical-player fixture setup

Central session `ses_ec8eae1c0ffetnGd6PBi4YmtFe`: focused **312/901 selected**,
one required failure in the new fixture's Survival-mode setup. Spectator
clearance now passes; the real player/AI escape cases continue to pass. The
registered lifecycle controller must remain a spectator and should not be
repurposed as a physical Survival actor. No production change is justified.

r10 leaves that registered controller in Spectator mode and reuses the existing
NeoForge `FakePlayer` fixture pattern for a separate unbound physical Survival
player. Both are placed in the release box with `noPhysics=false`; the test
requires spectator clearance and physical-player denial. Cleanup runs in
`finally`. GameTest compilation, diff checks and independent source review
passed; runtime checks and the remaining serialized gates are pending.

## Central gate r10 — mouse checks pass; full native gate blocked

Central session `ses_ec8e5e3d7ffegGKV1D48JC4F77` was resumed after an incomplete
transport wait; the same candidate/session produced the final `needs_changes`.
Fresh serialized evidence:

- Focused JVM: **114 passed**, filtered; selected/total not reported.
- Focused native: **312/901 selected, all 312 passed**.
- Readiness `ui,interaction,prediction`: **163 passed**, filtered; selected/total
  not reported. This is JVM contract evidence, not connected input/GPU proof.
- Full JVM: **3,314 passed**, zero failures/errors/skips.
- Full native: log says 901 tests started and one required failure,
  `stationaryCriticalCrushContactShocksOncePerSolidification`, shock delta 0
  instead of the expected face-tangent contact shock. The evidence parser lacked
  native selection-preflight/all-passed markers, so full selection/pass counts
  remain unknown. Do not claim a full native pass or waive this failure.

Packaging was not reached. The blocking contact-shock fixture is being
investigated narrowly; mouse release/medical/drop source is frozen. Evidence:
shared `gate-reports/mouse-carrying-owner-regressions-r10.json` and local
`build/gate-owner-regressions-full.log` (SHA-256
`b3226690e8f357d683480dcaabf861630175f38fdc2938248228949df7843da2`).
Owner testing remains prohibited; no replacement artifact or acceptance claimed.

### r11 contact-boundary fixture candidate

An initial overlap-based adjustment was rejected because it weakened the
face-tolerance test. The revised fixture uses actual body width to establish a
positive, non-overlapping gap within the existing tolerance, asserting that
geometry both initially and at the shock check. The once-per-solidification and
critical-state shock assertions remain. Geometry JVM coverage checks a positive
accepted gap and a farther-than-tolerance negative: **4 passed**; GameTest
sources compile. Airlock production behavior is unchanged.

The original measured gap (~9.988e-6) was itself within tolerance, so coordinate
rounding alone did not establish the zero-shock cause. r11 must supply actual
native proof; the focused selector now also includes `airlock_stationary_contact`
before the full suite. Do not infer resolution from the geometry unit tests.

## Central gate r11 — focused contact passes; full OFFLINE fixture fails

Central session `ses_ec8dbbd10ffepCXYnt7n1YJYSB` was resumed after an incomplete
transport wait. The focused native selection **319/901, all 319 passed** includes
the contact-boundary fixture. Full native raw logs show 901 tests complete and one
required failure: `canonicalOfflineRoleOwnedMouseCannotEscapeOrFleeAutonomously`,
at the prerequisite assertion `corpse observer must transition through bound
ghost lifecycle`. Investigate that genuine registered lifecycle path; do not
manufacture an OFFLINE claim merely to pass autonomy checks.

The central report only noted missing parseable full-run markers. Raw failure:
`build/gametest-owner-regressions-gate-full-r11/logs/latest.log:21713`, also
`build/gate-owner-regressions-full.log:22320`. The full Gradle run does not invoke
the existing tag preflight, which is configured only when a tag property exists;
the shared evidence reader requires that preflight marker. A minimal project-side
correction will reuse the existing compiled-fixture catalogue for an absent
filter, preserving explicit invalid-filter rejection. Shared workflow runtime and
selection rules are not being redesigned. Packaging remains blocked, and full
counts are not claimed as centrally validated.

### r12 sustained OFFLINE proof and full preflight

The duplicate NPC fixture's role-adoption prerequisite failed before any autonomy
assertion. Its proof is now consolidated into
`OfflinePortableMouseRelocationGameTests`' existing genuine role-adoption and
durable OFFLINE flow, rather than copying or forging that setup. Under real
portable-key custody it drives the existing NPC consumer across **120 delayed
server ticks**, checking original body/Mind binding, valid custody, absence of
any escape operation, no flee phase and stable position each tick. Existing
relocation assertions follow. Independent source review passed; native execution
is pending. `ESCAPE.md` points to the consolidated proof.

The existing bytecode preflight now runs for both unfiltered and filtered native
commands. An absent filter reuses `validate(..., null)`; explicit blank/malformed
filters still reject rather than falling back to full. Cheap default and `mouse`
preflight commands passed before consolidation (901/901 and 1/901 respectively),
and independent source review passed. Current counts must be recomputed after
fixture consolidation. No shared workflow runtime/parser was changed. Main
fixture compilation and diff checks passed; a fresh serialized gate is required.

## Central gate r12 — initial pass superseded by failed repeated run

The first fresh r12 execution recorded all six checks passing: focused JVM 118,
focused native 318/900, readiness 163, full JVM 3,314, full native 900/900 and JAR
SHA-256 `c9c68d61b1b68044011c99ec48dbe7e309c4b1f6e8d884576a4af0286d8c3b55`.
Its report finalization was not completed. During wait/report recovery, central
validation was repeated in the same focused fixture directory and replaced the
active validation record with a failure. The earlier pass/artifact is historical,
not a technically ready publication.

The same central session `ses_ec8ce9edbffeMRHUE4jEdFDiT5` now has a properly
finalized `needs_changes`: **318/900 selected, nine required failures**, all
`java.nio.channels.ClosedChannelException` in registered-player disposal,
pickup/medical, escape and OFFLINE-relocation fixtures. No gameplay denial was
identified; fixture connection or reused-world isolation is only a hypothesis.
Investigate the actual channel/operation before changing gameplay or waiving the
run. No further expensive jobs were requested during finalization repair.

Latest raw log: `build/gate-owner-regressions-focused.log`, SHA-256
`0215d73cfd5378c3de757c0be71608245944ccbc4a7139be7ff3f8bd3614a7f1`.
Source/evidence investigation is in progress. No replacement build is handed off,
and owner testing remains prohibited. GPU/client-rendered throw smoothness is
still unverified.

### r13 gate-plan correction; no gameplay change

Actual rerun startup evidence identifies the closed-channel cause: lifecycle
startup reads the reused world's `moonstation14-lifecycle/profiles.json`; persisted
`ACTIVE` profiles classify startup as `RECOVERY_REQUIRED`. First-join admission
then rejects new fixture accounts and intentionally disconnects them before
outbound fixture polling. References: `LifecycleStartupRuntime.java:74–84,97–119`,
`LifecycleFirstJoinHandler.java:139–145`, and failed native log lines 97–122.
Do not relax recovery authority to accommodate a reused fixture world. r13 uses
new disposable focused/full worlds and preserves r12 artifacts.

The initial passing r12 gate could not finalize because the later full `test`
invocation overwrote the earlier focused invocation's bound JUnit XML. r13 runs
the full JVM suite once, covering both affected and broad tests; it does not
create a parallel report implementation or weaken freshness checks. Readiness
has its existing separate report owner. Native logs are revision-specific. The
five-check sequence remains full JVM, focused native, affected readiness, full
native, then JAR. Shared workflow runtime and gameplay source are unchanged.

## Central gate r13 — cheese snapshot timing blocks full native

Central session `ses_ec8beb958ffer6MH3EyvRXbKri`: full JVM **3,314 passed**,
focused native **318/900 selected, all 318 passed**, readiness **163 passed**.
Full native selected 900/900 but failed
`mouseEatsPrototypeInitializedCheeseSlice` at its exact 3.75-unit stomach
assertion. Packaging did not run. Evidence:
`build/gate-owner-regressions-full-r13.log`, SHA-256
`92877519a3ca4d8b432ed8137e2fc1fcbbbaa37e4daa58f1614c09dcc9619ca1`.

r14 observes transfer on tick 20 rather than 21. The fixture's existing phase
helper excludes metabolism on completion tick 20, and `TickHooks` drains the
stomach under that same activity; the extra tick could coincide with digestion.
Exact 375-unit and source-removal assertions remain. Source review and compilation
passed; callback ordering and actual transfer require native proof. `ingestion`
is added to focused coverage so this behavior runs before the full suite.

### r14 mouse motion cadence

`ModEntities.MOUSE` now uses native `updateInterval(1)` for its whole lifetime,
reusing the registration knob already used by loose items and disposal holders.
No new prototype field, client interpolation owner, lease-specific networking,
physics or authority rule is introduced. The existing mouse/vanilla `ServerEntity`
flight trace checks per-tick packet deltas over 18 samples through lease expiry,
retaining exact gravity/drag/momentum/advancement assertions. Compilation, **46
focused motor/relocation JVM checks**, and independent source review passed.
Native packet assertions are pending.

Pinned upstream `Robust.Shared/CVars.cs:NetTickrate` defaults to 30 Hz processing
and updates; no slower mouse-specific cadence is declared. Minecraft's practical
20 Hz one-tick tracking is a cadence-parity adaptation, not exact frequency
parity or an unconditional upstream per-entity snapshot guarantee. Client
`TransformSystem.ActivateLerp/FrameUpdate` is a separate interpolation stage.
The earlier measured three-tick/150 ms Minecraft packets were consistent with
the reported acceleration steps, but the cause remains inferred. **No GPU frame
or perceived-smoothness pass is claimed**, and the owner will not be asked to test.

Flight fixture diagnostics emit the bounded full trace before terminal checks;
native assertion messages contain only concise values/counts, preventing another
1024-character debug-marker overflow if cadence assertions fail. Compilation
passed after this test-only diagnostic correction.

## Central gate r14 — cadence passes; timing and power admission block

Central session `ses_ec8b095daffeFsiUCox5Ex1EKf`: focused native **327/900
selected, four required failures**. The one-tick tracking/18-step packet fixture
passes; this verifies server cadence, not GPU smoothness. Cheese checked at tick
20 failed source-removal, proving that callback precedes timed completion.
Three connected-airlock fixtures failed fresh power admission, with zero delivery:
`criticalTangentContactReopensWithoutAnotherShock`,
`retainedDeadTangentBodyQuietlyReopensEachNormalClose`, and
`unsafeCrushRetainsLoadedMedicalDeadBodyUntilPaidReopen`. Readiness, full native
and packaging were not reached. Evidence:
`build/gate-owner-regressions-focused-r14.log`.

r15 restores the post-completion observation at tick 21 and waits for a start
phase where metabolism is due at neither completion tick 20 nor observation tick
21. `EntityActivity.REAGENT_METABOLISM` has a 20-tick period, so 18 of 20 starting
residues permit this two-tick window; focused JVM coverage proves that for every
entity-ID residue. Exact 375-unit and source-removal assertions remain. Fixture
compilation and independent source review passed; native proof is pending.

Airlock changes are diagnostic-only: admission timeout now includes the existing
meter reason/sample details. Topology/current-root alone did not establish fresh
power delivery. Production power behavior and paid admission remain unchanged;
no power fix or waived failure is claimed. The next run must reveal that gate's
reason if the failures recur. Native marker diagnostics remain bounded.

## Central gate r15 — mouse/ingestion passes; full reopen phase blocks

Central session `ses_ec8a912fffferq0TBZOMLSkTyZ`: focused native **327/900
selected, all 327 passed**. The cheese transfer window and one-tick mouse packet
cadence now pass native fixtures, as do carrying, medical pickup, DROP and the
player/AI escape matrix. The previously failing power-admission fixtures pass on
this invocation; that does not establish their earlier cause.

Full native selected **900/900**, but
`stationaryCriticalCrushContactShocksOncePerSolidification` failed awaiting fully
`OPEN`, observing `OPENING_PASSABLE` instead. Shock/contact assertions passed
before the reopen wait. Packaging remains blocked. Evidence:
`build/gate-owner-regressions-full-r15.log:24219,24279`.

r16 adds phase-wait diagnostics only: game/server time, transition timestamps,
crush retry deadline, power state and the existing meter. Complete diagnostics
are logged once on failure; native assertion text stays short. Expected `OPEN`,
wait budget and paid-admission rules are unchanged. GameTest compilation passed;
the stall/delay cause still requires native evidence. No production power/motion
fix or fully completed reopen is claimed from `OPENING_PASSABLE`.

## Central gate r16 — absent power sample requires investigation

Central session `ses_ec89edd3dffeJHkKw9OLCNhN7V`: full JVM **3,315 passed**,
zero failures/errors/skips. Focused native selected **327/900** and failed two
connected-airlock fixtures, so readiness, full native and packaging were not
executed. Raw log distinguishes the failures:

- `retainedDeadTangentBodyQuietlyReopensEachNormalClose`: initial full-power
  admission failed with topology ready/current root, demand 20, no known delivery,
  and **`meter=sample=absent`**, not a recorded insufficient-power reason.
- `unsafeCrushRetainsLoadedMedicalDeadBodyUntilPaidReopen`: `No sequences
  finished` timeout; do not equate this with the other fixture's exact assertion.

Evidence: `build/gametest-owner-regressions-gate-focused-r16/logs/latest.log:1380,1383`
and revision-specific captured gate log. Trace meter production/registration,
fixture footprint and shared-level cleanup rather than increasing waits or
waiving paid admission. A dedicated read-only power investigation is in progress;
mouse gameplay source is frozen and no production power fix is claimed.

### r17 producer-boundary diagnostics

The existing read-only `PowerRuntime.airlockPowerDiagnostic` now reports current
airlock-index/meter membership, non-loading full-chunk/root identity checks,
the existing graph snapshot and last-solve summary. A solve can omit a meter when
the indexed door's receiver graph is unknown, so `sample=absent` alone was not a
diagnosis. Current membership is not a claim about an earlier solve's input.
No topology rebuild/invalidation, chunk loading, registration or power allocation
is performed by the diagnostic. Complete fixture failure context is logged once
before a short assertion. Compilation and independent scoped source review passed;
the next native run must identify the missing producer boundary. No fix is claimed.

## Central gate r17 — root registered; receiver knowledge unresolved

Central session `ses_ec894257dffeMf2JmQ4kV7wmwV`: full JVM **3,315 passed**;
focused native **327/900 selected**, one required airlock failure. The root was
FULL/current/indexed, but had no meter entry (7 indexed airlocks, 6 meters). The
current global graph had no pending work; the last-solve snapshot still had one
pending chunk. This rules out current root-index loss, not a receiver-footprint
or solve-time knowledge problem. `nodesNear` can return unknown when its local
footprint is incomplete despite global readiness. No runtime allocation fix is
justified by those aggregate metrics alone.

### r18 fixture ownership correction

Core and connected powered fixtures now reuse `FixtureChunkTickets`' uniquely
owned per-test native region leases instead of shared `setChunkForced` flags.
With the old flag bookkeeping, a fixture relying on an already-forced chunk had
no independent ownership when another fixture cleaned up. The existing bounded
network/receiver footprints are retained, and readiness also checks entity-ticking
positions. Automatic pass/failure/rerun cleanup removes only each lease's tickets.
The intentional unload fixture uses its own lifecycle and was not changed.

Receiver-specific diagnostics now report current footprint chunk/graph/pending
state and nearby APC nodes using existing cached graph data without loading or
reconciliation. Core/connected/device-button wait failures log full context once
and keep native markers short. Production power allocation, phase deadlines and
paid admission are unchanged. GameTest compilation, **19 focused PowerGraph JVM
checks** and independent scoped source review passed. This fixes a concrete
fixture ownership weakness; native confirmation is still needed, and it is not
proof that this caused the earlier missing meter.

## Central gate r18 — powered fixtures pass; NPC arrival fails

Central session `ses_ec8814433ffersvRLYuLI742zl`: full JVM **3,315 passed**;
focused native **327/900 selected**, one required failure in
`idleDestinationPersistsAcrossReplansAndWaitsAfterArrival`. Powered airlock
fixtures passed this invocation after leased ownership, but earlier meter causes
are not independently proven. The idle mouse exhausted its 211-tick route budget
near the goal with nonzero velocity/opposed input. Its oversized failure text
also exceeded the native 1024-character book/marker limit. Readiness/full
native/packaging were not reached.

### r19 NPC absolute-speed correction

The task-specific NPC route call previously selected sprint gait for ordinary
tasks, inconsistent with the intended local task rates. It now selects walk for
food/drink, half resolved walk for idle and resolved sprint only while fleeing.
Absolute task rates are converted against the shared motor's selected resolved
profile speed through `CharacterMovementPolicy.factorForAbsoluteSpeed`; player
baseline adaptation, status modifiers, flight physics and authority remain.
No goal-radius/deadline change, teleport or parallel movement pipeline was added.

Mouse task-rate JVM coverage verifies **1.25 / 2.5 / 5 blocks/s**, including wish
scaling, against resolved prototype policy. Main/GameTest compilation and **47
focused motor/relocation tests** passed. Source review found only a parity-comment
error, now corrected: half-walk idle is a **local task policy**, not an established
upstream default. Pinned SS14 mouse walk/sprint values are 2.5/5; upstream steering
defaults to range 0.2/radius 0.35 and distance-weighted directional interest, not
the new scalar braking rule previously considered and rejected. Existing local
arrival thresholds remain unchanged. Idle failure context logs the last eight
samples once, with a short native assertion. Native arrival proof is pending.

## Central gate r19 — NPC arrival passes; ChemMaster full-suite blocker

Central session `ses_ec8722779ffeg5q6o0wm5Pggu6`: full JVM **3,316 passed**,
focused native **327/900 selected, all 327 passed**, affected readiness **163
passed**. The NPC absolute-speed/idle-arrival correction passes native coverage;
powered airlock fixtures also pass this invocation.

Full native selected **900/900**, with one required failure:
`ordinaryAdmittedUseOpensAndRefreshesChemMasterWorkspace`. Session diagnostics
show `interaction invalid`, distance squared 3.3125 and range 2.0, while power
was not topology-ready/fresh at tick 7761. The distance is below the squared
range limit; do not misdiagnose this as out-of-range. Investigate the actual
session/power prerequisite through existing fixture and admission owners.

Evidence: `build/gate-owner-regressions-full-r19.log:23184,23504`. Packaging did
not execute. No native failure is waived, no replacement artifact is handed off,
and no owner testing or client-rendered smoothness claim is made.

### r20 strict original-session diagnostic fixture

ChemMaster session admission/query and every server post-tick require fresh full
power. Initial readiness does not guarantee retention across later topology
changes. The fixture now captures bounded read-only power/topology context at
pre-admission, activation, open and query boundaries, logging it once on failure.
Native assertion text stays short.

Review found existing recovery branches could reopen a replacement session and
rerun retention assertions. Those branches and unused helpers are removed from
this contract: failed or replaced responses fail against the original session ID.
Initial readiness, ordinary pickup/USE, full-power checks and original-session
retention requirements remain. No production behavior or power rule changed.
The dedicated `chemmaster-admission-regression` tag adds this case to focused
coverage before the full suite. Compilation and scoped diff checks passed;
source review verified strict identity/retention, with its logger concern corrected
to one bounded `MoonStation14.LOGGER.warn` call. Native evidence is pending, and
the topology event causing invalidation remains unresolved.

## Central gate r20 — strict ChemMaster passes; idle budget fails

Central session `ses_ec8681282ffe1cY5xH6bogV5PX`: full JVM **3,316 passed**;
focused native **328/900 selected**, one required failure. Raw native log
identifies `idleDestinationPersistsAcrossReplansAndWaitsAfterArrival`, not a
ChemMaster failure. The strict original-session ChemMaster case passed this
invocation; its earlier invalidation cause remains unproven.

Idle reached its route-derived 215-tick budget while moving toward its goal,
distance about 0.556, just outside the existing arrival threshold. The corrected
absolute task rate must be compared with the fixture budget's actual speed
assumptions before changing a deadline. Preserve goal persistence, arrival/wait
assertions and thresholds; no arbitrary timeout waiver. Readiness/full native/JAR
were not executed. Evidence:
`build/gametest-owner-regressions-gate-focused-r20/logs/latest.log:1291,1443–1444`.

### r21 route budget uses the live task-speed owner

`NpcBehaviorSystem.taskSpeedPerSecond` is now the single rule consumed by both
live movement and the fixture: idle half resolved walk, food/drink walk, flee
sprint. Arrival budget uses route polyline length divided by that actual idle
rate, plus a bounded waypoint/final-sample allowance for the existing steering
taper. Goal thresholds, persistent destination and grounded arrival/wait checks
are unchanged. Existing fixture idle-wait/plan-window/40-tick slack is retained,
not increased or claimed as physics/SS14 behavior; the original 40-tick margin's
rationale is undocumented.

Main/GameTest compilation and **54 focused motor/flight/task-planning JVM checks**
passed. Review verified the correct absolute-path method and shared speed rule;
its concern about the undocumented preserved slack is now explicitly recorded.
The initial review inspected a different idle-direction method and does not
establish absence of this implementation. Native timing/arrival still requires
the fresh r21 gate; no full-suite pass or replacement artifact is claimed.

## Central gate r21 — current receiver ready, last solve incomplete

Central session `ses_ec85edc6dffeQyKjgz65ix3DMb`: focused native selected
**328/900**, one required airlock admission failure. Later readiness/full native/JAR
did not execute. The failing root was current, indexed, entity-ticking and FULL;
all four receiver footprint chunks were graph-loaded with three KNOWN APC nodes.
Current global pending work was zero, but the last solve recorded one pending
chunk. Current readiness does not prove what the last solve observed.

Source tracing establishes a mid-solve invalidation mechanism: receiver queries
can reconcile a loaded-but-unindexed footprint into the global pending set; later
queries then return UNKNOWN and omit meters. The triggering receiver in this
particular run remains unidentified. A scoped fix is preparing current receiver
footprints before allocation and deferring publication if indexing remains pending,
using existing loaded graph owners without loading chunks or granting stale power.
Independent architecture review and changed-behavior regression coverage are in
progress; compilation alone does not establish correctness.

The exact connected admission timeout still embedded 1,460 characters of context
in its native exception. It now logs complete context once and throws a concise
message without changing admission or timeout; GameTest compilation passed.
Evidence: `build/gametest-owner-regressions-gate-focused-r21/logs/latest.log`.

### r22 receiver preparation and regression fixtures

`PowerGraphService.prepareLoadedFootprint` reuses loaded-only reconciliation,
including while other indexing work is pending. `PowerRuntime` prepares current
receiver footprints before allocation and defers a solve if indexing remains
pending. Native-unloaded local footprints retain existing fail-closed behavior;
they do not indefinitely defer an otherwise-ready global solve. Independent
architecture review found no loading, allocation-order or freshness issue.

`PowerReceiverPreflightGameTests` adds tagged coverage for pending topology and
for discovery from READY: two empty native FULL chunks are removed from graph
bookkeeping through the public chunk-event boundary, then both footprint
preparations must queue work even after the first makes topology pending.
The fixture checks eventual recovery through the normal powered-airlock owner.
Namespace/template and synchronous pending assertions were corrected during review.
GameTest compilation and independent fixture review passed; native execution is
pending. The fixtures do not directly measure allocation/storage conservation
during a deferred solve; side-effect ordering was verified by source review.
Focused coverage now includes `power_receiver_preflight` before the required full
suite. No exact R21 triggering receiver or GPU/client rendering pass is claimed.

## Central gate r22 — fixture template startup crash

Central session `ses_ec8535665ffe5xEwDAtz7PhqeX`: full JVM **3,317 passed**;
native preflight selected **330/902**, but the server crashed before completing
tests. Raw log identifies a missing class-prefixed structure:
`moonstation14:powerreceiverpreflightgametests.power_live_chain_clear`.
No native pass/fail totals or later readiness/full/JAR evidence exists.

r23 disables automatic template prefixing with the existing NeoForge annotation.
Direct source inspection also found the pending-meter assertion still waited a
tick despite earlier worker reports; it is now synchronous, before indexing can
finish. Synthetic empty chunks are leased far from neighboring fixtures and
checked for power/cable contents before bookkeeping simulation. GameTest sources
compile. These correct fixture binding/race/isolation, not production power rules;
actual preflight behavior and full-suite stability remain for the fresh gate.
Evidence: `build/gate-owner-regressions-focused-r22.log:653,696`.

## Central gate r23 — preflight passes; full session retention fails

Central session `ses_ec84e9946ffeBxeh2PeG6fqwU1`: full JVM **3,317 passed**,
focused native **330/902 selected, all 330 passed**, affected readiness **163
passed**. Both new receiver-preparation fixtures pass native coverage.

Full native selected **902/902**, completed 902 tests and reported one required
failure: `ordinaryAdmittedUseOpensAndRefreshesChemMasterWorkspace`, original
session lost at stun-query request 2. No JAR packaging ran. Boundary diagnostics
show full fresh power at initial admission; the original-session retention checks
must not recover by reopening or weakening power validity.

The test currently shares the default batch with other world-mutating fixtures.
Investigate whether a dedicated existing GameTest batch supplies the required
stable fixture interval without altering runtime session rules. Batch serialization
must be established from the actual runner before prescribing that correction.
Evidence: `build/gametest-owner-regressions-gate-full-r23/logs/latest.log:22817–22818,23132–23133`.

### r24 retention fixture setup/isolation

The ChemMaster test now uses a dedicated named batch and relocates the controlled
body before the existing fresh-power barrier, rather than moving it immediately
after readiness. Fixture build/location changes precede admission; strict original
session, stun, workspace and refresh assertions remain. No recovery/re-admission
or production power rule changed. Compilation and scoped independent review passed.

Public Minecraft runner source supports serial advancement of named batches;
the referenced mirror is not a verified exact local 1.21.1 source revision.
Actual isolation in this project's runner and residual cross-batch effects remain
for native validation. No particular external topology mutator is claimed as the
proven R23 cause. The full fresh r24 suite is required before packaging.

## Central gate r24 — raw log identifies idle fixture failures

Central session `ses_ec8453712ffe7MGXCB1KQ8z7IB`: full JVM **3,317 passed**;
focused native preflight selected **330/902**, required gate failed. The central
summary described an incomplete startup log, but the native `latest.log` actually
records 330 tests complete and two required failures: idle destination arrival
and independent idle directions (nine destinations observed). Readiness/full
native/JAR did not execute. Use the raw log rather than treating this as a startup
failure.

The arrival trace freezes across eight advancing world ticks. Its last motor step
crossed from x=-834991.98 to x=-834992.04, over the x=-834992 chunk boundary.
This points to fixture entity-ticking ownership rather than a new speed/deadline
issue. Investigate each failed movement fixture's owned chunk footprint and reuse
`FixtureChunkTickets`; do not widen arrival or independence assertions.
Evidence: `build/gametest-owner-regressions-gate-focused-r24/logs/latest.log:1307–1308,1461–1464`.

### r25 idle fixture entity-ticking ownership

Both failed idle fixtures now reuse per-test `FixtureChunkTickets` across their
bounded spawn/destination envelopes (single mouse ±7 blocks; 16-mouse grid plus
that range) and await entity-ticking readiness before sampling. Failure traces
include `tickCount` and entity-ticking state. No production movement, timeout,
arrival or direction-count requirement changed.

Review caught absolute tick checkpoints reached only after asynchronous readiness
and projection checks. They now use relative 12-tick intervals, preserving the
intended observation spacing. Compilation and independent scoped review passed.
Native/full-suite checks remain pending; leased power and session fixtures are
unchanged from r24.
