# Critical State, Death & Prone

**Closed by owner acceptance, 2026-10-07.** Start future work from `HANDOFF.md`.
Entries below preserve chronological evidence; older pending/blocker language is
historical. Full-suite automated failures remain recorded, not silently waived.

## Latest owner acceptance and sprite adjustment

Owner confirmed ghost entry and movement work after the Observer policy-source
repair. Pulling, other medical behavior, and absence of the dev head remain
confirmed; no control changes are part of this follow-up.

At owner request, `GhostMobHarnessRenderer` now draws a 1.15-block square instead
of 0.85, centered at the ghost's actual eye height. Eye translation occurs before
billboarding/scaling, with centered quad vertices, so pitch cannot displace the
sprite center. Existing texture, directional UVs, translucency, and first-person
suppression remain unchanged. Entity bounds, eye height, cameras, authority,
and movement are unchanged.

Focused sprite validation: 2 tests / 2 suites, zero failures/errors/skips;
`compileJava` passed. Log: `build/ghost-sprite-geometry-unit.log`. No runtime
launches. Visual size/placement awaits owner confirmation; no sprint closure.

## Latest follow-up: pig, ghost startup, and local carrier head

**Superseded ghost diagnosis:** Owner retest still could not leave the corpse.
The fresh 00:37–00:38 server trace (`build/ghost-entry-denied-owner-2026-10-07.log`)
shows repeated `server-corpse-movement-unresolved` denials before staging.
Changing to the server resolver was insufficient: Human intentionally has no
`ObserverMovement`; that component belongs to `observer_base → observer`.
The prior compile-only fixture asserting corpse movement was consequently
incorrect. Ghost entry must pass the already resolved Observer movement policy
from `ObserverSystem.requestGhost` into the bound lifecycle handoff; corpse
`GhostOnMove` remains the admission policy. Pulling, other medical behavior,
and removal of the dev head are owner-confirmed; leave those paths alone.

Correction implemented: `ObserverSystem.requestGhost` passes its resolved
Observer movement through `BoundObserverAuthority.startGhost` to the lifecycle
bridge and existing Begin transport. The corpse movement lookup is removed.
The bridge rejects null movement before staging; no Human component or new
controller was introduced. The native fixture was corrected, and runnable
`CharacterPrototypeTest` now proves actual Human/Observer component ownership,
Observer inheritance (8/12), and custom resolved rates (3/7).

Latest validation: **2,173 tests / 359 suites**, zero failures/errors/skips;
GameTest compilation and both client/server preparation tasks passed.
Log: `build/ghost-policy-source-unit.log`. Independent source review passed.
No server/client or GameTest runtime was launched. The recorded pre-staging
rejection is addressed; connected ghost entry and flight remain unconfirmed.

Owner confirmed villager/pig Critical and Dead behavior; pig prone appearance is
explicitly accepted for now and must not be adjusted in this sprint. Pinned SS14
`MobPig` inherits Critical=100 / Dead=200; both states use the same dead sprite.
Production pig values remain unchanged. Added resolved real-pig boundary tests
and a test-only inherited Alive/Dead pig with death at 60, exercising the runtime
state reducer rather than hardcoded human boundaries.

Found a definite ghost-start blocker: the server bridge called client-only
`CharacterIdentitySystem.projectForActor`, which always returned empty on the
server. Missing movement then rolled back the staged ghost before Begin. The
bridge now uses authoritative `resolveForActor` before staging, so a failed
lookup cannot advance the corpse claim generation. A tagged server fixture
exercises this exact production resolver on a detached Dead harness; it is
compiled, **not executed**. Staging already advances generations; no client
epoch reset or separate protocol counter was introduced.

Begin now carries server-resolved ghost movement rates. Client Ready/prediction
no longer waits for observer snapshots or client corpse/prototype availability.
The local native carrier model is suppressed only during distinct-body medical
observation; existing remote tracking suppression and actual body/ghost renderers
remain intact. This addresses a separate owner-local head-rendering gap, not
proof that every reported remote head has been eliminated.

Validation: **2,172 tests / 359 suites**, zero failures/errors/skips;
`compileGametestJava`, `prepareServerRun`, and `prepareClientRun` succeeded.
Log: `build/pig-ghost-readiness-unit.log`. Independent source reviews passed.
No server/client or GameTest runtime was launched. Connected ghost movement and
head visibility still require owner confirmation; earlier full GameTest failures
remain unresolved. This is not sprint acceptance or closure.

## Approved design

Owner approved implementation after the design discussion. Start from pinned SS14
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`; the preceding timed-interaction sprint
is merged at `d89eba8`.

- Keep medical state separate from standing/knockdown and stun. Resolve thresholds
  and allowed states from prototypes; Human is 100 Critical / 200 Dead, not a
  universal rule for every mob. Ordinary damage reduction recovers Critical but
  does not revive Dead by default. Preserve an explicit future revival path.
- Physical prone lowers collision/eye height and permits crawling through one-block
  gaps. Human crawl speed is `.4 × free hands / total hands`: 40%, 20%, 0% for
  zero, one, two occupied hands. Voluntary C-toggle does not drop items. Standing
  normally takes two seconds and must not grow the body into a blocked ceiling.
- Critical/Dead block normal actions, movement and speech, and attempt to drop all
  held items. Worn equipment and storage contents stay with the persistent body.
  Conscious prone alone does not block ordinary item actions.
- Switch to rear third-person on entry to Critical and corpse-first Dead; F5 remains
  unrestricted. Restore the prior perspective on recovery/ghosting only if the
  player has not manually changed it.
- Critical visibility uses progressively tighter, body-centered fog affecting
  terrain and entities while retaining a visible nearby body. Avoid a heavy
  disorienting screen overlay. **Clear critical fog on death**, restoring it if
  the observed body is explicitly revived into Critical; do not clear environmental fog.
- Corpse-first viewing retains body identity/contents. Ghosting needs a fresh
  movement press after death, not a direction already held. Revival while ghosted
  offers return to the body rather than forcibly moving the player back.
- Implement body dragging, reusing existing pulling/hand ownership boundaries.
  Body inventory access and strip UI are out of scope.

## Scope boundaries

### Latest owner corrections (supersede earlier presentation defaults)

- Increase resolved drag slack so an ordinary full jump is not held down; retain
  three-dimensional corrective forces beyond slack and collision/gravity.
- Repair choppy player dragging and slow pulling-hand indicators at their actual
  control/synchronization boundaries, not by disabling the tether.
- Critical and Dead owned bodies must continue passive gravity and pulling motion
  without owner input, AI restoration, or client prediction freezing them.
- Ghost camera yaw, head/body presentation, and movement heading must agree;
  unrestricted mouse yaw and pitch must work on the active observer path.
- Revival must clear lingering native death tint on the exact linked body.
- Entry into Critical/Dead preserves an already-prone body's stomach/back
  orientation, including external turning before death. Standing medical entry
  retains its default; knockdown now defaults face down.
- Center the prone model and corresponding interaction/render geometry on the
  entity position so turning does not sweep it around an offset pivot.

This correction batch uses unit/build checks only: no server or client launches.

No Last Words/Succumb or numeric active-capability UI; critical speech remains
blocked. No medical supplies/revival equipment, surgery, new role/lobby/automatic
respawn, save migrations, wind or full rigid-body simulation. Preserve existing
runtime gates and audit actual body-control/movement ownership; do not silently
enable an unaccepted experiment.

## Ordered units

1. Typed resolved MobState/MobThresholds/Crawler composition and pure transitions.
2. Damage/native-health/death integration and incapacitation effects; preserve one
   mutation boundary, exact-body authority and exactly-once corpse transitions.
3. Standing/knockdown, timed get-up, physical posture and movement/prediction gates.
4. Corpse observation/ghost return, presentation and body dragging, with coordinated
   networking and hand-drop/storage continuity.
5. Focused prototype/unit/live tests, sequential tagged server checks and a full
   regression run; connected camera, fog, collision and death/revival checks.

The live consumers must use resolved prototypes. Before extending timed actions,
map the get-up consumer's actual DoAfter arguments; do not assume ingestion's
fixed interruption thresholds or argument subset are sufficient.

## Research anchors

- SS14 `MobDamageable` supplies inherited thresholds; Human ancestry resolves
  through organic species bases; Pig resolves through SimpleMobBase and has
  StandingState but no Crawler. Mouse has 10/20 and some mobs omit Critical.
- `MobThresholdSystem`, `MobStateSystem` and `MobStateSystem.Subscribers` supply
  transition, action, hand-drop and recovery behavior.
- `StandingStateSystem`, `SharedStunSystem.Knockdown`, `CrawlerComponent` and
  shared hands knockdown refresh supply posture and occupied-hand speed behavior.
- `RespiratorSystem`: Critical suppresses gas exchange but keeps saturation drain
  and suffocation updates; Dead pauses the respirator.
- `GhostSystem` uses held directional input upstream; requiring a fresh press here
  is a deliberate owner-approved adaptation.

Detailed correspondence and source references will be recorded with implementation.
Body-pulling composition/consumers are being audited before their fields are designed.

## State

### Idle prone/supine rendering follow-up

Idle downed humanoids now neutralize head/hat, arms, legs, jacket and pants
rotations after vanilla animation. Only owner-authorized synchronized crawling
retains animation; voluntary fake-death, incapacitation, and externally dragged
bodies share idle rendering. Camera look is not disabled, but the rendered head
does not track it. Prone grounding now uses the 1.21.1 headwear outer geometry
(4.5/16 model units), independent of physical collision width; both renderer
paths and culling bounds consume the shared helper. Geometry and focused helper
tests added; visual/connected acceptance remains pending. See PARITY.md for the
geometry rationale/source index. No Gradle or runtime validation run (parent
serializes validation).

Implementation is **partial**, not ready for owner acceptance. Current server core:

- Resolved MobState/MobThresholds/Crawler codecs, policy and reducer; persisted,
  synchronized medical state and native maximum-health projection.
- Native death state recording, retained policy-backed corpses, explicit same-UUID
  revival including delayed revival; removed bodies cannot be revived.
- Medical action/local-speech gates, pull release and authoritative all-hands drops.
- Persisted/synchronized posture, slip/medical downing and timed get-up with
  occupied-hand timing, standing-space checks and per-operation damage threshold.
  Downed bodies are tracked rather than scanning all entities every tick.

### Integration checkpoint — 2026-10-05

Implemented, pending connected-client acceptance and regression assessment:

- Physical swimming-pose bridge with 0.6-high collision and 0.4 eye height,
  C requests, horizontal rendering, and unchanged STANDING dimension queries.
- Resolved crawl/hand availability and down friction in vanilla and existing
  opt-in motors. Coarse hand counts/pulling state synchronize through POSTURE;
  private LiveHands contents do not synchronize. Native main/offhand occupancy
  unions with typed hand occupancy/reservations by resolved left/right ID.
- Critical respirators drain saturation without gas exchange; Dead skips updates.
- Lifecycle medical-observer bridge and ghost-mob harness control are integrated
  with normal required flags ON. Native-player corpse observation remains an
  optional path, not the acceptance path. The lifecycle path uses committed-body
  death authority and observer session; camera/view tracking does not move the
  original corpse. Source visibility staging is owner/ghost-observer scoped, but
  connected transport/second-viewer behavior is not proven.
- Typed Pullable/Puller capabilities and collision-respecting body leases through
  the existing pulling service. Native P has a bounded owner-only admission path;
  it does not broaden the old body-hand authority or enable experimental gates.
- Native held-slot medical drops, retained worn/native inventory and suppressed
  native death loot. Native-slot publication restores only unadmitted transfers.

Lifecycle integration is implemented but is not yet proven by focused fixtures or
connected-client evidence. Preserve normal required flags; do not globally enable
experimental gates or replace existing protocols. The owner's prior native-only
testing direction was withdrawn. Keep test readiness PENDING until focused new
fixtures pass and the connected-client run documents the current server-flags
workflow. Native-bound trust-backend fixtures do not prove authenticated
lifecycle/Ready transport.

Lifecycle details: UUID-based native corpse death retains the durable DEAD_CLAIM
row in existing stager persisted state; ghosting the same-UUID body uses
`returnRevivedCorpse` via CAS, then standard `beginReadyCommit`. CORPSE-only revival
auto-restores control without creating a ghost. Operator-bound ghost harness
control transfers the same mind, holds the body lease, and on CORPSE-only revival
renews the binding on the unchanged body epoch before `beginReadyCommit`. Explicit
revival clears harness-confirmed-death before the mob-state event. Legacy offline
login still takes the old direct-ghost-session path; new corpse-first login is not
established, and legacy manual R does not use the medical observer path.

Still required: connected-client gameplay/latency and second-viewer evidence,
including proxy-camera mouse/F5 behavior, fog composition, remote chunk streaming,
native movement correction while dragged and real connected player death/revival.
FakePlayer's hurt method is a no-op: the native-held fixture tests the public
Critical notification consumer, not native damage admission. Observer fixtures
test trusted server APIs, not authenticated network transport. Administrative
slash-command routes are not incapacitation-gated; gameplay command fallback
coverage needs a deliberate review, not a blanket ban on operator commands.

### Earlier core checkpoint (superseded integration list)

Physical prone dimensions/eye height/rendering; C input/network request; crawl
movement, friction, AI and supported owner prediction gates; Critical respirator
behavior; corpse-first camera/control and fresh-input ghosting; revival return;
critical fog/perspective transitions; prototype-backed body dragging. Current
posture data and action gating are **not** physical crawling or full movement denial.
Check all speech fallback routes during body-control integration. No acceptance
claim for presentation, prediction, or completed pulling composition.

Next start points: `ms14/mobs/MobStateSystem`, `ms14/standing/PostureSystem`,
`MedicalIncapacitationEffects`, `TimedInteractionSystem.start`'s new damage-threshold
overload, and `BodyPullService`. Standing fit currently uses normal STANDING
dimensions; the forthcoming prone dimension adapter must preserve that query.

### Validation checkpoint

- Full unit suite: **2,139 passed / 2,139**, zero failures.
- Filtered `medical-state,posture`: **8/524 selected; 8 passed**, zero failures,
  zero untagged. Includes native Critical/death/healing, late revival and removal
  rejection, both-hand drop/action recovery, get-up grace/collision/damage threshold.
- Latest full server run: **524/524 selected**, **523 executed**, **517 passed,
  6 failed**; one selected fixture did not execute (identity not established).
  Failures: `builtfixtureinitializesandreportsresolvedruntime`,
  `localextensionreceiverrespectsradiusandcablecuts`,
  `poweredventmetertracksbreakerandreplacement`,
  `farplayercannotdespawnboundunboundorcorpse`, `disableddevicedoesnotmovegas`,
  `onlycompletedpaidsolvemovesconservedgas`. Causes are not established; do not
  equate this list with the prior sprint's different full-run failures.
- Broader filtered connected run: **144/524 selected**, **143 executed**,
  **142 passed / 1 failed** (`heldbicaridinedosemetabolizesandhealsbrutedamage`).
  This fixture passed in the preceding full run; intermittent cause is unresolved.
- Logs: `build/critical-core-unit-checkpoint.log`,
  `build/critical-core-medical-checkpoint.log`,
  `build/critical-core-full-gametest-next.log`,
  `build/critical-core-connected-checkpoint.log`. Failure logs retained; successful
  disposable fixture world removed. Owner configuration was not modified.

Native-health fixture adaptations now reconcile manually seeded typed damage
before capturing health. Pressure-cap recovery after crossing Dead requires
explicit revival, rather than falsely expecting ordinary healing to unlock Dead.
This checkpoint is not a sprint handoff or closure; changes remain uncommitted.

### Latest validation (supersedes counts above)

- Current unit suite: **2,155 passed**, zero failures/errors/skips
  (`build/critical-bound-final-unit.log`).
- Focused medical/observer GameTests: **14/535 selected**, **14 executed and
  passed**, zero untagged (`build/critical-bound-focused-final.log`). These use
  trusted backend fixtures, not authenticated lifecycle/Ready transport.
- Latest full GameTests: **535/535 selected and executed**, **527 passed / 8 failed**,
  zero untagged (`build/critical-bound-full-retry.log`):
  `skyexposedcellusesambientandrejectsinjection`,
  `builtfixtureinitializesandreportsresolvedruntime`,
  `oldhandsaveandfilledcontainerrejectoverwrite`,
  `gridroundtriprequiresfreshrepresentablestateandconservesitem`,
  `localextensionreceiverrespectsradiusandcablecuts`,
  `alarmusesinheritedhundredwattsandradiusthree`,
  `puddleconsumerwaitsforresolvedprofiledeadlineandrepeats`,
  `disableddevicedoesnotmovegas`. Causes remain unestablished. Failed fixture
  artifacts are retained in `build/gametest-tags-fixture`; owner config is unchanged.
- Historical pre-bridge full run: **533/533**, **530 passed / 3 failed**:
  `builtfixtureinitializesandreportsresolvedruntime`,
  `farplayercannotdespawnboundunboundorcorpse`,
  `poweredscrubbercapturesonlyselectedgas`. Causes remain unestablished; do not
  classify these as unrelated solely by their names. Earlier full runs had
  different atmosphere/power failures.
- Four new fixtures initially omitted explicit templateNamespace and therefore
  were selected but not executed. All four are corrected; catalogue validation
  now requires explicit `templateNamespace = "moonstation14"`.
- Previous logs: `build/critical-final-unit.log`, `build/critical-final-focused.log`,
  `build/critical-final-full.log`; current logs include
  `build/critical-bound-final-unit.log`, `build/critical-bound-focused-final.log`,
  and `build/critical-bound-full-retry.log`.
  Sprint remains unaccepted/uncommitted; test readiness is PENDING.
- Client startup reached resource loading and applied the prone-render and
  mouse-look mixins without a startup failure; the owned validation process was
  stopped. This is not connected gameplay or title-screen acceptance.
  Log: `build/critical-client-startup.log`.

### Owner-reported connected-client corrections

The approved face-up/face-down representation is recorded in `PARITY.md`.
Posture now carries synchronized orientation independently of medical state:
voluntary prone starts face down, involuntary downing starts face up, conscious
self-crawl turns face down, and dragging does not turn the body. C retains get-up;
rebindable K rolls the actor, and Shift+K turns an aimed-at downed body with normal
actor authority, free-hand, range, and visibility checks. Pull input is now
Ctrl+left-click rather than P. Client-only transformed render bounds cover the
horizontal model without enlarging physical collision bounds.

The possessed-body HUD uses synchronized typed damage, resolved thresholds, and
observed medical state. Its healthy display is normalized to 100; Human Critical
and Dead remain 100 and 200 damage. The internal native maximum of 40 is retained
as a compatibility bridge, not displayed as the character's health capacity.
The reported early ghosting has no established causal defect yet; a HUD change
alone is not evidence that Critical-state gameplay is fixed.

`/ms14 revive` is an operator debug command for the caller's exact linked body in
bound CORPSE or GHOST sessions. It clears typed damage and invokes explicit
same-body revival. Ghosted callers still return with R; corpse-first recovery
uses the existing control handshake. The return notification now has English
text. Command-dispatch fixtures cover permission denial and both observer modes;
the corpse-first fixture's explicit recovery fallback does not establish
authenticated automatic Ready transport.

Automatic knockback inside Minecraft 1.21.1 `LivingEntity.hurt` is suppressed;
direct explicit impulses remain. The exact target and single knockback invocation
were verified in the pinned NeoForge 21.1.224 Minecraft source artifact.
Source review passed after correcting the initial version-mismatched selector.

Automated validation of this correction set is pending. The previous results
above are historical, not proof for these edits. Connected rendering, Critical
progression, held-input death behavior, and automatic revival return still require
the concise checks in `MANUAL.md`. This is not sprint closure or a handoff.

### Connected-regression repair checkpoint

The initial correction set was not visually accepted. Its lying transform turned
the wrong axes, and the HUD called server-only actor resolution on a client body.
The replacement pose helper preserves head/feet direction while flipping the
front normal; living/harness rendering and culling share that geometry. Culling
also conservatively covers differing/interpolated body yaw. Synchronized own-crawl
state controls idle arms, independently of external dragging or medical state.

HUD, Critical fog, and the medical camera now resolve the owned presentation body
independently of the current camera; strict movement/control ownership guards are
unchanged. Health policy uses client prototype projection and observer-body epochs,
not server authority APIs. Critical/death connected transport remains unaccepted.

Damage feedback now retains its resolved source policy through the entire native
hurt call, including broadcasts after damage-post processing. Chemical damage is
quiet; explicit physical causes drive interruption and presentation separately.
Retained corpses do not keep vanilla's permanent death tint. Quiet damage does not
erase an already-active physical response. Chat-screen PageUp/PageDown routes to
wrapped-row history, including parts of a single long message. Respiratory gasps
use the attributed SS14 assets and resolved cooldown/saturation policy.

Latest completed server checks, before the final client-only culling enlargement
and transition-only diagnostic logging:
- Unit/build: **2,126 passed**, zero failures/errors/skips; GameTest compilation and
  `prepareServerRun` passed (`build/critical-regressions-final-unit.log`).
- Focused core: **75/543 selected**, **75 passed**, zero untagged
  (`build/critical-regressions-final-focused.log`).
- Full: **543/543 selected**, **537 passed / 6 failed**, zero untagged
  (`build/critical-regressions-final-full.log`):
  `serviceexchangeisatomicandconservesfinitecellgasandenergy`,
  `leasedpigandvillagerkeepvanillawalkanimation`,
  `poweredventmetertracksbreakerandreplacement`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
  `onlycompletedpaidsolvemovesconservedgas`. Causes remain unestablished; these are
  not classified as unrelated merely by their names. Full-run failure artifacts
  remain in `build/gametest-critical-regressions-final-full-fixture`.

Native-player medical fixtures override FakePlayer's unconditional damage immunity
and clear its never-expiring spawn immunity; they assert actual ledger/state
changes before observer admission. They are not authenticated connection evidence.
Hand-drop fixtures identify their own exact two marked stacks rather than counting
unrelated items from neighboring tests. Chemical/physical action tests explicitly
select their intended feedback cause. Owner configuration/worlds were not changed.

Final checks after the culling enlargement and diagnostics supersede the unit/core
counts above: **2,127 unit tests passed**, zero failures/errors/skips; production,
GameTest compilation, `prepareServerRun`, and `prepareClientRun` passed
(`build/critical-regressions-client-final-unit.log`). Core GameTests again passed
**75/543 selected, 75 passed**, zero untagged
(`build/critical-regressions-client-final-focused.log`). The retained full result
is **537/543 passed**, with the six unresolved failures listed above.

An isolated client startup applied the new chat, prone, sleeve/arm, and feedback
mixins and initialized SoundEngine/GUI atlases without a mixin application error.
The 90-second smoke did not establish title-screen completion or connected
gameplay. Logs: `build/critical-regressions-client-startup.stdout.log` and
`build/critical-regressions-client-latest.log`. Only the owned smoke process tree
was stopped; its temporary init script and successful focused fixture were removed.
Full-run failure artifacts and smoke files remain. Transition-only medical logs
now identify body/state/epoch and admission failures for further connected diagnosis.
Neither this checkpoint nor source review establishes that the owner's permanent
corpse lock is resolved; authenticated Critical/death/return testing remains pending.

### Follow-up connected findings

Owner verification established working Critical entry, tightening fog, quiet hurt
feedback, respiratory gasps, and resting arms. Remaining reports: failed body
drag/turn targeting, no ghost movement/look, floating bodies and non-neutral idle
head/legs, insufficiently opaque distant fog, and lag-based jug detachment.

The current repair batch separates gameplay interaction bounds from the crawling
collision box and renderer culling margins, admits retained Dead targets for
turning, and routes Ctrl+left-click through the controlled body. Lifecycle GHOST
validity now compares active memory's body ID to the ghost, not the corpse; the
durable DEAD_CLAIM still names the corpse. The actual owner log showed GHOST at
22:03:18.934 and END at 22:03:18.984, consistent with that next-tick guard failure.
The added lifecycle identity fixture does not stage an authenticated ghost or
establish multi-tick transport/input survival.

Presentation changes include neutral idle head/legs and matching outerwear,
model-geometry grounding, a temporary species-threshold-based death-progress
row, black Critical fog, and suppressed unfogged sky/cloud passes. Pulling uses
ephemeral synchronized rope state and a shared Entity.move limiter, with
collision-resolved target catch-up rather than capped spring lag. Both committed
authority and its client predictor already move through Entity.move; snapshot
setPos reconciliation is intentionally not constrained.

Validation for this batch is pending and supersedes no previous result until
its commands finish. Connected dragging, body turning, ghost movement/look,
ground contact, and complete distant blackout remain acceptance checks in
`MANUAL.md`. No closure handoff or commit is authorized.

Follow-up validation completed: **2,135 unit tests passed**, zero failures/errors/
skips; main/GameTest compilation and server/client preparation passed
(`build/critical-body-followup-unit-final.log`). Core filtered GameTests:
**79/547 selected, 79 passed**; pulling-only **2/547 selected, 2 passed**;
posture-plus-pulling **11/547 selected, 11 passed**. Core log:
`build/critical-body-followup-core.log`.

Full GameTests: **547/547 selected, 540 passed / 7 failed**
(`build/critical-body-followup-full-final.log`):
`serviceexchangeisatomicandconservesfinitecellgasandenergy`,
`poweredventmetertracksbreakerandreplacement`,
`puddleconsumerwaitsforresolvedprofiledeadlineandrepeats`,
`farplayercannotdespawnboundunboundorcorpse`, `disableddevicedoesnotmovegas`,
`poweredscrubbercapturesonlyselectedgas`, `onlycompletedpaidsolvemovesconservedgas`.
Failure artifacts remain in `build/gametest-critical-body-followup-full-final-fixture`.
The despawn fixture failed its nearest-player setup assertion because another
fixture's `soap-slip` FakePlayer was closer; it did not reach checkDespawn or
corpse-survival assertions. This identifies a fixture interference condition,
not proof of a corpse-removal defect or a passing retention check. Other causes
remain unresolved.

A separate 90-second isolated client smoke applied the prone, feedback, cloud,
and chat hooks, initialized SoundEngine/GUI atlases, and reported no mixin or
classloading errors. It did not establish title-screen completion or in-world
behavior. Logs: `build/critical-body-followup-client-startup.stdout.log` and
`build/critical-body-followup-client-latest.log`. Owned processes were stopped
and the temporary init script removed; owner processes/configuration were not
changed. Connected input/render/ghost acceptance remains pending.

Final torso-grounding correction: head-only contact still left the torso about
0.13 blocks above the floor. The pivot now uses torso outer depth; a pose-local
head offset supports the larger head in either orientation. Head support applies
during crawling, idle rotations remain neutral, and standing resets the offset.
Independent source/full-transform review passed. Validation after this correction:
**2,135 unit tests passed**, zero failures/errors/skips, with main/GameTest builds
and server/client preparation successful (`build/critical-body-grounded-final-unit.log`).
Core GameTests again passed **79/547 selected, 79 passed, zero untagged**
(`build/critical-body-grounded-final-core.log`). The retained broad full-suite
result remains **540/547 passed** with the seven failures above; it was not rerun
for this local pose correction.

The final isolated client smoke applied the prone, sleeves, clouds, and related
hooks and initialized audio/GUI resources without mixin/classloading errors.
It did not establish title-screen completion or in-world rendering. Logs:
`build/critical-body-grounded-client-startup.stdout.log` and
`build/critical-body-grounded-client-latest.log`. Owned smoke processes and its
temporary init script were cleaned up; owner services/configuration/world were
untouched. Restart both real clients and server before the concise `MANUAL.md`
checks. This remains unaccepted pending connected dragging/turning, ghost input,
opaque fog, and indistinguishable resting-body visuals.

### Connected repair checkpoint — 2026-10-06

Owner confirmed body turning and opaque fog work. Body dragging and ghost control
still failed; additional reports were look-dependent prone appearance, mid-air
corpse suspension, a small ground gap, unwanted damage noise, and fog radius.

- Client pull nomination called the server-only character resolver, rejecting all
  living-body candidates. It now consumes read-only resolved client projection;
  server pull authority is unchanged. A source-boundary regression guard covers
  that call site, not real mouse input.
- The current owner log showed GHOST persisting without the former END. Staged
  ghosts were classified as medical before world-add but visibility did not
  authorize their staged owner, who was still in CORPSE mode. Visibility now
  consults that exact-owner staging map. The fixture checks staged owner access,
  non-owner denial, and owner access after commit; connected delivery/input remains
  pending.
- Synchronized prone facing follows accepted owned crawl movement independently
  of look yaw, and is held while stationary or externally moved. Prone head
  rotations remain neutral even during crawling. Standing facing is unchanged.
  Native-player movement-route integration is not established.
- Retained owned corpses now receive zero-input gravity/collision at the existing
  suppressed-travel hook. A real-harness GameTest confirms damage-triggered
  mid-air death, falling/settling, UUID retention, and worn gear retention.
- Grounding uses solid torso/head depth (2/4 pixels), not transparent outerwear;
  thin outerwear may slightly penetrate the floor rather than levitate the body.
- Resolved feedback profiles add optional `hurt_sound`, default false for quiet
  and physical damage. Native server hurt playback consumes it; explicit gasps
  and death audio remain separate. Explicit opt-in listener playback is not
  independently fixture-validated.
- Critical body-radius presentation now closes from 16 to 1.5 blocks using
  resolved thresholds. Existing third-person camera-offset compensation remains
  an approximation, documented in `PARITY.md`.

Latest unit/build chain: **2,137 passed**, zero failures/errors/skips; main/GameTest
compilation and server/client preparation passed (`build/critical-control-final-unit.log`).
Filtered core GameTests: **81/549 selected, 81 passed, zero untagged**
(`build/critical-control-final-core.log`). Full suite: **549/549 selected,
542 passed / 7 failed** (`build/critical-control-final-full.log`):
`serviceexchangeisatomicandconservesfinitecellgasandenergy`,
`onewireddebuglampstaysbelowtripthreshold`, `poweredventmetertracksbreakerandreplacement`,
`farplayercannotdespawnboundunboundorcorpse`, `disableddevicedoesnotmovegas`,
`poweredscrubbercapturesonlyselectedgas`, `onlycompletedpaidsolvemovesconservedgas`.
The seven current causes remain unresolved; do not equate the changed failure
list with the previous run. Failure artifacts remain in
`build/gametest-critical-control-final-full-fixture`.

The initial new client-smoke launch exited before startup with control-C status
`-1073741510`, so it validated no client hooks/resources. Its temporary script and
owned processes were cleaned up, without touching owner services. A replacement
isolated smoke is pending. Source review passed the pull/visibility/facing paths;
it does not establish connected acceptance. No closure handoff or commit requested.

Final boundary review found that prone entry copied raw entity yaw into a bounded
facing field; accumulated native yaw could throw outside ±180 degrees. A finite
angle-normalization setter correction and regression coverage are pending. The
counts above precede that localized correction. The setter correction and tests
are now implemented; final unit/build and posture validation are running.

Replacement isolated client smoke applied prone-render, damage-feedback, cloud,
and chat mixins and initialized SoundEngine/GUI atlas without observed mixin or
classloading errors. It reached resource initialization, not proven title-screen
completion or connected gameplay. The launch later ended with control-C status
`-1073741510`; no startup-failure trace appeared. Stdout/stderr are preserved at
`build/critical-control-final-client-startup.*.log`. Temporary script was removed;
final owned-process verification/log-copy/disposable-directory cleanup is pending.

Independent final source review passed retained-corpse physics and default-off
hurt audio. The bounded-yaw review finding is corrected in `PostureData.facing()`:
finite yaw is normalized before construction; non-finite input is still rejected.
Dedicated explicit audio opt-in playback coverage remains absent, as noted above.

Final localized yaw-correction validation: **2,138 unit tests passed**, zero
failures/errors/skips, with main/GameTest builds and server/client preparation
successful (`build/critical-control-yaw-final-unit.log`). Posture GameTests passed
**11/549 selected, 11 passed, zero untagged**
(`build/critical-control-yaw-final-posture.log`). The full result remains
**542/549 passed** with the seven failures listed above; it was not rerun for the
angle-normalization setter correction.

Smoke logs, including `build/critical-control-final-client-latest.log`, were copied
before removing its disposable directory. No smoke-owned client Java process
remained; temporary script and successful final posture fixture were removed.
Owner services/configuration/world were untouched. Restart the real server and
both clients, then run `MANUAL.md`, prioritizing body dragging and sustained ghost
movement/look. Connected acceptance remains pending; the sprint is unaccepted
and uncommitted.

### Owner findings and observer crash repair — 2026-10-06

Owner confirms body dragging now works. Connected dragging remains unacceptable:
choppy target motion, glitchy jumps, no vertical hauling, and mid-air hovering at
taut distance. Source findings: the limiter measures only X/Z but scales the whole
movement vector, including Y; catch-up also corrects only X/Z. This can suppress
gravity while taut and cannot haul upward. Hard catch-up plausibly contributes to
jitter; client/camera interpolation is not yet established. No pulling change is
made here: owner requested upstream findings and a joint decision first. Pinned
SS14 uses a slack distance joint with a hard limit (initial separation +0.15),
not a progressive spring. Three-dimensional hauling/jumps are Minecraft adaptations.

Actual `run/logs/latest.log` records GHOST epoch 9 at 19:28:15.240 followed by an
`invalid observer intent` exception at 19:28:15.268. Stack: payload constructor →
`ObserverClient.perClientTick`. Camera yaw accumulated freely while the constructor
accepted only ±180 degrees; normal camera orientation could throw during packet
construction. The live sender now uses a camera-intent factory that normalizes
finite yaw. Visual look remains unrestricted; decoded-wire bounds and non-finite
rejection stay intact. Independent source review passed.

Validation: **2,140 unit tests passed**, zero failures/errors/skips; GameTest
compilation and server/client preparation passed
(`build/observer-intent-yaw-final-unit.log`). The two new yaw tests exercise the
exact production factory, not a running client tick. A relative-source-path guard
was removed because the test worker could not resolve it; production caller use
was checked directly. No GameTest/server/client smoke rerun for this localized
packet-boundary fix. Prior full-suite failures remain unresolved. Connected ghost
movement/look requires retesting after restarting clients/server.

Lexorin investigation: a swallowed dose enters the stomach in 5-unit sips.
Non-Digestion reagents lose 0.25 units/second from stomach, delivering only 0.12
units/second to bloodstream after efficacy/cent rounding. Lexorin's default
0.5-unit metabolism rate scales its 5 Asphyxiation +5 Bloodloss effect to about
1.2 of each per second while supplied orally. Healthy respiration/blood recovery
oppose those types. A fully swallowed 50-unit dose supplies 24 bloodstream units
over roughly 200 seconds, not an immediate bolus. The current owner log records
Critical at 19:26:38.754 and Dead at 19:28:08.755, but contains no dose attribution.
No chemistry behavior was changed. See pinned `toxins.yml`, `ReagentPrototype`,
`MetabolizerSystem`, and local `StomachDigestionTransfer`/`MetabolismMath` for the
verified route/defaults; do not rank oral potency by multiplying damage by rate.

### Approved elastic pulling follow-up

Owner chose the spring-damper adaptation after the SS14 comparison. Work units:
1. Accept all finite observer yaw and canonicalize before use; retain NaN/infinity
   rejection and independent movement/session validation.
2. Replace horizontal clipping/hard catch-up with a resolved, three-dimensional,
   progressive spring-damper consumed by normal motion on both ends, including
   owned server/prediction, retained corpses, and loose objects. Preserve gravity,
   collisions, reservation/release rules, and ordinary-lag attachment.
3. Validate resolved defaults/inheritance, live consumers, jumping/falling, stuck
   targets, vertical hauling, prediction, and chained pulls; connected smoothness
   remains a real-client check.

Dedicated body tossing and artificial-gravity controls are not included. Ordinary
hauling should gain vertical force, not guaranteed effortless stair climbing.
The spring settings are local adaptation values, not SS14 stiffness values.
Verified parent composition: MobHuman → BaseSpeciesMobOrganic / AppearanceHuman;
Organic composes physiology plus BaseSpeciesMob; the latter supplies Puller and
inherits BaseMob → BaseControllable, supplying Pullable. Jug inherits DrinkBase
→ BaseItem, supplying dynamic Pullable physics. Keep supported local components;
do not invent unsupported upstream mass/joint families.

Both Lexorin and amatoxin can be processed by the two-reagent bloodstream profile.
Transfer is per reagent: 25u of each in the stomach supplies 12u of each over
roughly 100 seconds. Nominal combined damage is 240 Lexorin +180 amatoxin, or
4.2/second before recovery/resistance. The chemical damage types bypass native
cooldown, supporting concurrent application; no dedicated same-tick mixed-toxin
GameTest establishes this complete path. Other eligible reagents can compete for
the two processed slots. No chemistry behavior is changed.

Elastic implementation checkpoint (not acceptance): resolved `Puller.tether`
settings and partial nested inheritance are wired to both-endpoint snapshots,
`TetherSpring`/`TetherMotion`, controlled server/client movement, retained corpses,
native living travel, and loose-item ticks. Hard movement clipping and target
snapping are removed. Owned movement uses a new post-friction/post-input/post-jump
constraint impulse before collision; the original external-impulse semantics stay
unchanged. Normalized equal endpoint response is a deliberate adaptation, not a
mass solver. Damping was reduced from 20 to 10 for 20Hz stability; defaults/Human
are stiffness 60, progressive stiffness 40, damping 10, max acceleration 160.

Initial source integration exposed strict-schema rejection of the new nested
settings and an erased standing-body reaction. Schema/merge support and the force
phase are corrected. An intermediate unit/build chain passed **2,141 tests**,
zero failures/errors/skips. Its filtered server run selected **37/554**, with
**35 passed / 2 failed**, zero untagged (`build/elastic-pulling-final-core.log`).
The two failures were initial admission in the high-puller and jug fixtures,
before runtime motion assertions. Their aiming setups are corrected, pending rerun.
Standing restraint, an actual jump followed by continued zero-input physics and
landing, and link cleanup were reached by the intermediate fixtures. Final broader
movement/loose-object checks and the full suite are still pending.

The corpse-fall fixture must preserve production `NoAI=true`; changing it to false
is not accepted as a workaround. `NoAI` is distinct from `noGravity`/`noPhysics`.
Pinned Minecraft source calls `Mob.aiStep → LivingEntity.aiStep → travel` regardless
of NoAI, so any remaining discrepancy requires actual hook/fixture diagnosis.
The final finite-yaw, damping simulations, admission corrections, and production
NoAI fixture semantics supersede intermediate validation once rerun finishes.

### Completion repair and validation budget

Owner requires minimal heavyweight testing: unit/build checks during iteration,
then one consolidated server validation after a substantial completed batch. No
repeated focused/diagnostic servers or client startup loops. One validation owner
coordinates the run; a failed run does not authorize automatic retries.

Saved incomplete runs selected 139/554 and 18/554. Both crashed in GameTest's
fastutil scheduled-task iterator, not a tether production frame. The new jug
fixture installed `onEachTick` from a delayed callback; the chain case also nested
delayed scheduling. These mutate the map during callback iteration. Both cases
now register one callback at fixture entry and use tick-state transitions. No
production/framework mixin workaround is added. The exact crashing callback is
not identified by the stack, but the unsafe scheduling chains are removed.

A separate presentation defect reset the medical camera's previous position to
its current position every tick, bypassing native interpolation. The anchor now
copies previous/current body-eye samples and maintains its own rotation history.
Pure policy tests cover horizontal, vertical, and stationary interpolation;
independent source review passed both camera following and fixture scheduling.

One resource-limited consolidated full GameTest run is authorized for the finished
batch after unit/build checks, with process-local `ActiveProcessorCount=2` and
Gradle `--max-workers=1`. No diagnostic retries or client smoke are scheduled.
Results are pending; no incomplete run is a passing acceptance result.

The single consolidated full run completed in 78 seconds, without the scheduled
map crash: **554/554 selected, 543 passed / 11 failed**
(`build/elastic-pulling-completion-full.log`). Elastic restraint, actual jump/fall/
landing, and chain cleanup passed; corpse ledge hauling and native loose-jug travel
failed their motion assertions. Owned corpse falling/settling passed with
`NoAI=true` and movement ownership retained. These two elastic motion failures
remain active completion blockers; they are being diagnosed from saved evidence,
not by launching another server.

Other full failures: `skyexposedcellusesambientandrejectsinjection`,
`serviceexchangeisatomicandconservesfinitecellgasandenergy`,
`slidingsuperslipperyslipdoesnotspendreactivetouchdose`,
`poweredventmetertracksbreakerandreplacement`,
`puddleconsumerwaitsforresolvedprofiledeadlineandrepeats`,
`farplayercannotdespawnboundunboundorcorpse`, `disableddevicedoesnotmovegas`,
`poweredscrubbercapturesonlyselectedgas`, `onlycompletedpaidsolvemovesconservedgas`.
The despawn failure still stopped at nearest-player setup (`soap-slip` interference).
No claim is made that the other failures have the same cause as prior runs.

Unit/build/preparation passed **2,143 tests**, zero failures/errors/skips; the later
client camera-rotation bookkeeping compile also passed. No owned GameTest server
remained. CPU was limited process-locally to two processors, with one Gradle worker.
No server retry or client startup was launched. Failure artifacts are retained.

Post-run source diagnosis: the original haul fixture placed the puller only one
block above the corpse, at the desired ledge height. A shallow tether angle can
balance horizontal input without overcoming gravity; this is not proof of a
missing vertical consumer. The revised fixture starts higher and closer, preserving
initial reach and the actual height-plus-forward motion assertion. A spring unit
check verifies upward acceleration above gravity at the sampled geometry, not
successful traversal. The one-block gap under the raised platform accommodates
the down-posture collision height of 0.6 (`LivingEntityProneDimensionsMixin`).

Jug force is applied before the settled early return, so that ordering is not the
diagnosed defect. Its fixture omitted the bounded swept-chunk preload used by
existing loose-item tests and did not report native tick eligibility, removal,
velocity, or unloaded-motion blocking. Fixture prerequisites and diagnostics
are now corrected; the saved run cannot establish which condition caused the failure.
Neither revised fixture has a passing runtime result. Connected owner checks in
`MANUAL.md`, including ghost control and dragged camera smoothness, remain pending.

Final lightweight validation after all source changes: **2,144 unit tests passed**,
zero failures/errors/skips; GameTest compilation and server/client preparation
passed (`build/elastic-pulling-completion-final-unit.log`). The upward-force unit
uses actual foot anchors and admission slack (initial distance +0.15); independent
review verified that contract. Jug fixtures now preload a bounded motion region,
require native ticking, and report removal, settling, velocity, unloaded-sweep
blocking, and tick eligibility when travel fails. Actual movement assertions and
production physics rules are unchanged. Target prone collision clearance is also
asserted in the haul fixture.

Code repairs and lightweight checks are finished for this batch. Runtime acceptance
is still blocked by the two saved elastic failures and pending connected checks;
the revised fixtures are compiled but have not been rerun. No additional server
or client launch occurred. The sprint remains unaccepted and uncommitted; no closure
handoff is written. Continue with owner checks in `MANUAL.md`, preserving the saved
full-run failures rather than treating fixture corrections as passing evidence.

### Connected-owner regression repair batch

The owner reported jump restraint, choppy player pulling, slow hand icons,
airborne Critical/Dead freezing, unmovable incapacitated targets, ghost yaw/control
regressions, lingering revival tint, unwanted medical rolls, and an offset prone
pivot. Latest posture requirements are recorded near the scope section above.

Source roots and repair boundaries:

- `Puller.tether.slackAllowance` is now a resolved, validated local setting (1.5
  blocks). Admission captures initial foot separation plus that allowance; stream
  snapshots preserve the same lease tuning. Prototype inheritance/schema and a
  real grounded-motor vertical-jump comparison have focused unit coverage.
- Controlled ALIVE characters previously advanced physics only when a fresh
  movement packet arrived, while vanilla travel was always suppressed. The server
  controller now advances one zero-wish physics step when a packet is absent.
  Conscious move input still releases a pull only when movement is permitted.
- Critical was excluded from corpse physics, and Dead relied on a travel hook
  that was not a reliable common consumer. Owned incapacitated physics now runs
  once at the native entity-tick tail. Incapacitated command input is acknowledged
  without a second simulation step. Client recording, tracker ownership, and
  replay are suspended across medical incapacity with a prediction-history barrier.
- Observer projection previously updated entity yaw but not the native head yaw
  read by `LivingEntity.getViewYRot`. Shared `ObserverGhostLook` now aligns all
  current/previous camera, head, and body rotations with the observer's own look.
  The ghost fixture exercises that live helper and native interpolated view yaw.
- Owned Ctrl-clicks previously sent the native pull request, bypassing correlated
  hand results and leaving the icon stale until recovery polling. They now use
  authenticated hand actions. Correlated pull results and synchronized pull-state
  mismatches trigger a fresh hand snapshot, including automatic release.

These are source repairs, not claims of connected runtime acceptance. No server
or client launch is authorized for this batch; final unit/build evidence and
posture-render/tint review will be recorded after the source batch is complete.

Posture/presentation repairs are now implemented: typed downing reasons preserve
already-prone medical orientation and choose face-down knockdown defaults. The
render root, interaction bounds, and culling bounds share centered prone geometry;
physical dimensions are unchanged. Both red-overlay and hurt-camera consumers
classify medically managed bodies independently of current death state, ignoring
stale native death timers after revival while preserving profile-controlled hurt
feedback. Exact-body revival clears server native death/hurt bookkeeping. Independent
source review passed posture, centering, and feedback presentation. No connected
visual result is claimed.

Final batch validation: **2,155 unit tests passed** across 355 suites, zero
failures/errors/skips. `compileGametestJava`, `prepareServerRun`, and
`prepareClientRun` passed, with process-local CPU limit and one Gradle worker;
`git diff --check` passed. Logs: `build/critical-owner-regressions-unit.log`,
`build/critical-owner-regressions-focused.log`, and
`build/critical-owner-regressions-compile.log`. No GameTest execution or
server/client launch occurred.

Review caught misplaced tracker gating and a missing recovery authority barrier;
both are corrected. Stable presentation identity remains available while
incapacitated, but prediction recording, tracker ownership, and replay do not.
Recovery clears history before resuming. Validation also corrected bounds corner
sampling and signed-zero vector test comparison without weakening the jump or
centering contracts. Owned pulling retains any-free-hand selection (active empty
hand preferred), rather than denying the action when only the active hand is held.

Implementation and lightweight validation are complete for this correction batch.
Restart the real server and clients, then prioritize ghost yaw/heading, airborne
Critical/Dead fall and dragging, and pulling/jump/icon checks in `MANUAL.md`.
Connected smoothness and visuals remain pending. The prior full-run failures and
unverified elastic fixtures are not relabeled as passes; the sprint remains
unaccepted and uncommitted.

### Owner follow-up: remaining runtime regressions

Owner confirms Critical dragging and ghost orientation now work, but reports
stuttery player pulling, an offset prone model, immovable Dead bodies, laggy ghost
movement, and a visible native spectator-carrier head above the corpse.

Newly established roots supersede the earlier source-only acceptance:

- Medical ghosts bypassed the existing predicted `GhostControlClient` transport
  and used an observer-only server movement loop. The old predictor was not
  deleted; this integration bypass was a regression. The repair reconnects the
  staged lifecycle ghost to existing Begin/Ready/Commit transport and disables
  duplicate observer movement, keeping medical authority and exact-body return.
- `PlayerCharacterHarnessRenderer` overrides the fallback prone render hook and
  did not receive its centering correction. Both render paths now share the native
  humanoid mesh-root transform; standing collision height is not its mesh midpoint.
- Death/session teardown clears movement ownership. The old passive body step
  required ownership, so an otherwise valid corpse lost gravity and tether motion
  after detachment. Physics eligibility is now separate from mind/input ownership,
  and native travel suppression uses the same predicate to avoid double stepping.
- Replayed pulled-player input used current, not historical, tether endpoint
  snapshots and hard-reset the body position on correction. Incoming pull roles
  now mark externally authoritative target motion: native tracking interpolates
  it, while input still reaches the server for conscious release. Outgoing-only
  pullers retain prediction; transition barriers protect history/sequence progress.
- Carrier heads belong to the distinct native spectator player, not the ghost.
  Observer-session tracking now excludes that carrier from other viewers and
  refreshes tracking immediately on transitions. Ordinary spectators and native
  player bodies that are themselves the corpse are not globally hidden.

This follow-up again permits unit/build checks only, no server/client launches.
New native-matrix and detached-corpse tests must replace the earlier inadequate
geometry/owned-only seams; authenticated connected ghost transport remains an
owner check rather than a claim inferred from trusted fixtures.

The follow-up also established a physical stutter source: two standing controllers
apply ground friction before equal-endpoint tether impulses. Former damping 10
produced a persistent alternating response; the fixed-anchor test did not exercise
this case. Damping is now 6 in resolved defaults and Human data, retaining
stiffness 60, progressive stiffness 40, force cap 160, and slack allowance 1.5.
The real two-grounded-motor regression measured former target velocity spread
4.5 versus approximately 9.24e-14 with the new default (steady mean -2.25 in the
fixture's movement direction). Authoritative target interpolation additionally
avoids replaying historical input against current endpoint snapshots.

Native-source verification caught a further matrix-test gap: entity scale is
already applied before `setupRotations`. The setup-local centering/pivot must use
model scale only. Production PoseStack tests now reproduce the exact native order,
not merely an equivalent intended final projection, and cover both model/entity
scales, quarter turns, face orientations, mesh centering, bounds, and solid floor
contact. Both actual render paths call the shared correction.

Final lightweight validation: **2,166 tests passed** in 358 suites, zero
failures/errors/skips. `compileGametestJava`, `prepareServerRun`, and
`prepareClientRun` passed with process-local CPU limit 2 and one Gradle worker;
`git diff --check` passed. Log: `build/critical-runtime-followup-unit.log`.
Detached-corpse native-tick fall/drag GameTests are compiled, not executed. Lifecycle
route decisions and custom shared-motor rates have unit coverage, but authenticated
Begin/Ready/Commit handoff has not been runtime-tested. Source review passed the transport,
prediction boundaries, visibility, and detached-body consumers. No server or
client was launched.

Owner retest remains required for ghost movement responsiveness, actual prone
centering, player-drag smoothness, Dead dragging after control leaves the body,
and second-client carrier invisibility. Critical dragging and ghost orientation
were owner-confirmed before this follow-up and must stay working. Do not describe
this source/unit result as connected acceptance or a passing full GameTest suite.
