# Implementation and validation

Owner accepted and closed this sprint on 2026-10-09. See `HANDOFF.md` for current
state. Entries below preserve validation history and limitations; older pending
acceptance statements are historical. Closure does not make the full suite green
or claim exact SS14 parity for unsupported systems.

## Owner test blockers

Latest owner follow-up: Defaults must clear pending port highlights; E does not
activate a dropped signaller; repeated use produces generic Server Denied; doors
render out before the advanced remote's 50-block range can be visually tested.
Defaults now clears highlights without changing its selected endpoint pair or
prototype-driven link operation. Ground E activation and silent cooldown handling
are implemented; full JUnit/compile/assembly passed **2,976 tests, zero failures**
(`build/device-ground-activation-build-final.log`). Native validation is below.
Render distance is recorded
as a separate visibility/performance limitation, not authorization to increase
all entity rendering in this fix.
Implementation reuses one held/loose `activatePrepared` operation for signal
invocation, cooldown and deferred cleanup. Empty-hand E uses a token-free,
UUID-targeted `ACTIVATE_TARGET` intent in the existing hand-action channel;
occupied-hand target use preserves configurator priority and reuses exact loose
target admission. Contained bodies cannot activate external targets. The new
client capability gate resolves the client prototype catalog, not the server's
catalog (which could mask the bug in an integrated client/server process).
Source inspection found no custom airlock distance override: its block-entity
renderer supplies the two-block bounds and leaves distance eligibility to Minecraft.
Client chunk tracking/visibility and server-loaded endpoint availability are
separate gates. The effective distant-client cutoff has not been measured; do not
attribute disappearance to a fixed custom 50-block limit or globally raise range.
Pinned SS14's generic `UseInHandInteraction` enforces the default one-second
UseDelay *before* raising the signaller event and silently handles delayed use.
Do not infer no cooldown from `SignallerSystem.OnUseInHand` alone. Ground E use
is a deliberate port adaptation; upstream signaller has no TriggerOnActivate.

Latest artifact: `build/libs/moonstation14-0.0.1.jar`, **7,559,525 bytes**, SHA-256
`AE1157774B9691AA4B19E49B22C26EAF81D2AF68AEAE944676300B9F4CEFF008`.
It supersedes every earlier artifact in this document. Replace it on both client
and server and restart both. New regression checks cover Defaults source/sink
highlight reset, token-free exact-UUID wire round-trip and invalid-token rejection,
client catalog/routing, contained-action rejection and shared cooldown arithmetic.
The new native fixture uses real prepared loose stacks and the production shared
activation operation; its boundary is trusted server-core use, not an authenticated
client E/Z request. Source review passed for actual actor/target proof and reuse.
Final isolated **filtered** native run: `device-network-owner,device-network`,
**9/725 selected, 9 passed, zero failed**; log
`build/device-ground-activation-native-focused.log`. The shared-core fixture
verifies real prepared standard/advanced sources, TX=0 with target-RX routing,
queued delivery, silent repeated use at ticks 0/1/19, activation at tick 20 without
deadline extension, and standard no-delivery versus advanced delivery at 20 blocks.
Successful disposable fixture removed after retaining its log. Connected-client
ground E/held Z confirmation remains pending; no automated GUI input occurred.

Latest isolated **full** native run: **725/725 selected, 685 passed, 40 failed**;
log `build/device-ground-activation-native-full.log`, retained failure world
`build/gametest-device-ground-activation-full-fixture`. **Non-green.** Of the nine
focused fixtures, seven passed in the full run, including the new shared loose
activation/cooldown/range fixture. Button→powered-airlock and alarm→vent-sync failed
their fresh delivered-power prerequisites, despite passing the focused run.
Thirty-nine failing method names also failed in the older 43-failure full run;
15 overlap the intervening 17-failure run. `oneWiredDebugLampStaysBelowTripThreshold`
is the only failing name absent from the older 43 list, also with unknown power
nodes/supply. Overlap does not establish causation or unrelated/preexisting status.
No power-runtime, render-distance or unrelated fixture fixes were included.
Retain all full-run failure artifacts; this result supersedes earlier full-run
counts for the current artifact. Owner acceptance remains open.

Latest additional owner blocker: configured standard and advanced signallers did
not visibly activate with E or Z. Z is the default active-hand activation path;
E is world interaction. Held activation, cooldown mutation, endpoint ownership
and deferred delivery must be traced before treating portable use as validated.
The earlier portable native fixture proves loose preparation/probing, not an
authenticated held-owner activation or packet survival across metadata mutation.
Source tracing subsequently established two independent routing defects:
`USE_IN_HAND` looked up a required medical prototype before its signaller branch;
the missing-medical-item exception skipped both nonmedical remote variants.
Signaller dispatch now precedes that lookup and reuses `RemoteSignallerServer.activate`.
Separately, workspace Z consumed nonmedical activation without sending a request;
its fallback now uses the same generic `USE_IN_HAND` request gateway as gameplay.
Medical gesture precedence, active-hand proof, pending-action and server authority
checks remain unchanged. Source review passed; new validation is recorded below.
E remains world interaction, not the in-hand activation key. No zero-TX rejection
or post-activation token/revision mutation was demonstrated; neither was changed.
`LiveHands.replaceActiveUseStack` preserves the occupant token and increments its
revision; signaller address preparation refreshes that revision before capturing
the deferred source guard. It is not the verified routing failure above.
Existing resolved-profile tests establish standard range 15, advanced inherited
range 50, `Pressed`, one-second use delay and unset TX frequency. Signal-switch
Status is remembered High/Low; its On/Off events remain momentary. No prototype,
port semantics, transport frequency override or range values changed in this fix.

Remote activation routing follow-up: full JUnit/compile/assembly passed **2,971
tests, zero failures** (`build/device-remote-activation-build-final.log`). Two
method-scoped source-wiring regression checks guard independent signaller dispatch
before medical catalog admission and workspace generic-use fallback. They are
not authenticated player/client tests. Independent source review passed.
That follow-up jar was `build/libs/moonstation14-0.0.1.jar`, **7,556,692 bytes**, SHA-256
`B86EA341B20DC918D53558590F040175DC7DD4F534F9ADD9E19A7C74189474F7`;
it is superseded by the ground-activation artifact at the start of this document.
Owner retest: configured active-hand remote, Z with workspace closed
and open; remain within 15 blocks (advanced 50) of a powered/unbolted linked door.
Prior full native suite remains non-green (707/724; 17 failures); this narrow
routing correction does not diagnose or repair those failures.
Final isolated **filtered** native rerun (`device-network-owner,device-network`):
**8/724 selected, 8 passed, zero failures**; log
`build/device-remote-activation-native-final.log`. Successful disposable fixture
removed. These existing native tests are not authenticated held-use/UI coverage;
owner confirmation of Z activation remains pending.

Latest owner feedback confirms the Link UI now opens, but acceptance remains open:
player-facing UUIDs are unusable/overlap, localization keys are unresolved, pane
and text sizing are excessive, default-link meaning is unclear, Clear does not
clear pending highlights, selected rows cannot be deselected, bent bus lines are
ambiguous, and configurator use on a loose signaller does not reach its selector.
Follow-up covers short SS14-style network addresses (UUID identity stays internal),
viewport-bounded panes/chat-scale text, complete labels/tooltips, shared render/hit
geometry, selection toggling/Clear behavior, direct lines, and the authenticated
item-target use route. Prior test results are not evidence of these new corrections.

Owner testing found the following; these invalidate readiness for further manual
workflow testing despite the automated results below:

1. Dropped button/switch visuals sit inside the floor and are effectively invisible.
2. Configurator displays an entire RSI sheet rather than the intended animated icon.
3. Button/switch placement fails during ordinary gameplay; creative placement worked.
4. Placed button/switch visuals also display entire RSI sheets.
5. Configurator mode is not apparent.
6. Configurator use on the button, then the airlock, produced no visible result.

The owner stopped further testing. First priority is a separate shared-targeting
issue: looking at an item showed a pale-blue item outline plus a white outline on
the block behind it; the white current-target outline did not select the item.
The outline correction below was delivered first, before device follow-up work.

Source tracing identified independent block and loose-item outline renderers.
The outline-only correction reuses existing item nomination and ray geometry,
shares the original near-white block style, and gives the foreground target
priority. It does not change interaction admission, examination highlights, or
the device rendering/placement/configurator defects listed above. Owner visual
retesting remains necessary.

Outline-only follow-up: six focused outline tests passed; compilation and assembly
succeeded. That jar was `build/libs/moonstation14-0.0.1.jar`, 7,525,575 bytes,
SHA-256 `41008CBE7640833ED26ED032730DBFA7E69232D42E5D2B2A1EF27131C6382F7B`.
Retest a loose item in front of a block, then the block alone: the foreground
current target should receive the single near-white outline. Device blockers
remain unresolved. The broader evidence below predates this visual follow-up.

The owner subsequently authorized fixing all device blockers. Follow-up acceptance
must cover the ordinary controlled-body placement and configurator interaction
paths; trusted fixture `setBlock`/endpoint operations are not evidence for those
player-facing routes. Animation/frame import and dropped-item transforms also
require resource/consumer checks, not merely successful PNG parsing.

## Device owner-feedback follow-up

Owner retest reported: button→airlock clicks still did not open the linking
window. Placed control artwork also occupies only a small portion of its front
geometry, leaving black margins. These invalidate acceptance of the prior follow-up.
The UV and publication corrections below still require connected-client retest.

UV fit follow-up: the directional 32×32 control frames use transparent padding,
not opaque black margins. ImageIO/System.Drawing alpha scans found inclusive pixel
bounds N `[13,4..18,10]`, E `[21,13..27,18]`, S `[13,21..18,27]`, W
`[4,13..10,18]` for button and both switch states; no opaque black pixels occur
in those frames. Directional block models now map the complete nontransparent
rectangle (converted from pixel edges to 16×16 model UV units) onto their existing
front face. State models inherit that geometry; blockstate rotations continue to
cover up/down. Generated drop-item textures/models are untouched. A focused resource
test checks UV rectangles against every directional frame's alpha bounds.
The editor failure has a verified publication-order cause: both first-source and
second-sink selections published request ID `0` in the same session, while the
retained Link window rejects equal/older IDs. The second (open) snapshot was thus
discarded even when server-side endpoint selection succeeded. Follow-up tests
must include this closed→open same-session progression and subsequent commands.

The correction uses one shared `ConfiguratorOperationSequence` contract for live
server publications and client commands. Initial selection publishes `0`, the
second selection publishes `1`, and commands advance beyond the latest accepted
publication. Stale snapshots cannot replace either retained window's command
callback. List session publication also advances on state changes. Regression
tests use the live sequence helper for closed→open and command progression.
Editor opening no longer requires transport availability or pair link range;
link mutations still use the existing range checks, matching pinned SS14.

Switch-on models inherit their matching cropped directional model, not the old
full-cell base. Resource tests verify that live parent relationship and compare
the inherited UV rectangle against the actual on/off/button alpha bounds.

### Final window/UV correction validation

- `test compileGametestJava assemble runGameTestServer`: **2,955 JUnit tests passed,
  zero failures**; production and fixture compilation and assembly succeeded.
- Isolated **filtered** tags `device-network-owner,device-network`: **7/723 selected,
  7 passed, zero failed**. Log: `build/device-owner-window-uv-final.log`.
  Successful disposable fixture directory removed; prior full-suite failure
  artifacts remain untouched. This is not a new full-native-suite result.
- Source review confirmed live operation-sequence wiring, stale-callback
  protection, all directional on/off/button UV models and all six facing variants.
- Window/UV build `build/libs/moonstation14-0.0.1.jar`: **7,540,353 bytes**, SHA-256
  `3258F4CE7BF3AF60CFF1844B8B5E81C9D88DB77D4C452423DAFDE3EAF39F7463`.
  This predates the pending-click proof correction; do not use it for the next retest.

Owner checks: Link mode → click button → click airlock → linking window; verify
button and both switch states fill their front geometry. Actual connected-client
clicks and appearance remain unobserved, and sprint acceptance remains open.

Owner retest after the window/UV build again reported **no UI** after button→door
clicks. The publication correction is therefore insufficient to establish the
full workflow. Trace upstream of accepted snapshots and the retained-window
opening/lifecycle path; add visible selection/denial feedback rather than another
silent failure. Automated unit/native results above do not prove this path.

Further verified blocker: `BodyHandClient.networkConfiguratorProof()` used
`inventoryProof()`, which requires `BodyHandClientPolicy.inventoryView()`.
That input view is deliberately unavailable while a block click is pending.
The server sends the configurator snapshot inside world-use dispatch, before
`BodyBlockUseResult`; the client therefore rejects the opening snapshot for a
missing proof. Hand-state pushes do not clear that pending block-click barrier.
List activation has the analogous pending-action ordering. The regression gate
must exercise policy/proof/window delivery **before** click acknowledgement,
not merely two synthetic window snapshots with increasing sequence numbers.

The fix must preserve the distinction already used by hands/storage: confirmed
observations may be presented while action input is temporarily barred. It must
not remove suspension/authority guards or make inventory actions available during
pending requests. Configurator commands still require fresh server validation.

Implemented pending-click correction: `BodyHandClientPolicy.confirmedSnapshot()`
exposes the already validated read-only snapshot while requests are pending;
`networkConfiguratorProof()` now uses it rather than the action-input inventory
view. Suspension/authority and epoch guards remain required. Normal inventory
action barriers are unchanged. The regression test delivers the retained window
before `BodyBlockUseResult`, verifies visibility before/after acknowledgement,
and verifies that suspension revokes the proof. Configurator window visibility
now participates in automatic pointer cleanup, and closing the final pane invokes
the existing cleanup hook. LINK clicks report authoritative selection/opening
or rejection feedback, rather than only reporting mode.

Pending-click build validation: **2,957 JUnit tests passed, zero failures**;
`test compileGametestJava assemble` succeeded. Log:
`build/device-owner-preack-build-final.log`. Source review passed for the pending
proof boundary and shared pointer lifetime. The combined initial validation
command reached **7/723 selected, all 7 native fixtures passed**, but its shell
timed out after server shutdown; log `build/device-owner-preack-final.log`.
Separate isolated **filtered** native rerun completed successfully: tags
`device-network-owner,device-network`, **7/723 selected, 7 passed, zero failed**;
Gradle reported `BUILD SUCCESSFUL`. Log: `build/device-owner-preack-native-final.log`.
Successful disposable fixture directory removed; earlier full-suite failure
artifacts remain untouched. No new full-native-suite run is claimed.

Current jar: `build/libs/moonstation14-0.0.1.jar`, **7,541,794 bytes**, SHA-256
`DBC945ED27E934CC2E6240A97FC63C49262806B4C760BF17E545001AD50AF02A`.
Replace the jar on **both client and server and restart both** for this retest.
The latest regression test is headless policy/window coverage, not an observed
connected-client click. Owner acceptance and the prior full-suite failures remain
open; no GUI input or owner launch/configuration changes were performed.

## Usability/address/portable-target follow-up

Pinned SS14 composition audit: `RemoteSignaller` inherits `BaseItem`; the
advanced remote inherits the standard remote. Both resolve `Pressed` and the
one-second `UseDelay` default; advanced overrides wireless range 15→50. The
SharedInteractionSystem held-use wrapper gates repeated use for that full delay
and silently handles an early repeat, so the Minecraft source cooldown retains
the same one-second interval and treats an authorized early request as OK/no-op.
Ordinary E on a loose-world remote is an intentional Minecraft world-use
adaptation; upstream held `UseInHand` does not establish ground-E parity, and
the upstream prototypes do not claim `TriggerOnActivate`.

The tagged `LooseRemoteSharedActivationGameTests` fixture exercises the production
shared activation operation with actual prepared loose standard/advanced stacks,
resolved source compositions, near/far native wireless receivers, and deferred
runtime drain. It checks standard/advanced 15/50-block range behavior and the core
cooldown at ticks 0/19/20. The fixture uses the
package-internal trusted operation directly and deliberately does not claim
authenticated packet/actor admission coverage; that boundary remains guarded by
the production request adapter and source-wiring tests.

Implemented owner-feedback scope (connected-client acceptance still pending):

- Internal UUID identities remain unchanged. Shared transport allocates random
  uppercase `XXXX-XXXX` addresses against its existing network membership map.
  Block defaults persist in existing dimension network SavedData; portable defaults
  persist on the actual signaller stack. Automatic collisions regenerate; custom
  duplicate addresses refuse connection. Unload retains defaults; retirement
  removes block ownership. Live address edits still invalidate queued packets.
- Resolved prefixes now match pinned SS14: `AIR-`, `VNT-`, `SCR-`; controls,
  airlocks and signallers have no prefix. See `PARITY.md` for audited parent/default
  mapping and the English-literal prefix persistence adaptation.
- Link/List panes fit within one-sixth of viewport area. Render, click, drag,
  tooltip and scrolling use the same display mapping. Body text targets effective
  chat scale 0.5; titles target 0.65, with smaller fallback only for tiny viewports.
  Field text is width-fitted and full addresses remain available on hover.
- Configurator and current port labels/tooltips have English translations. Defaults
  explains matched prototype defaults. Clear links clears pending highlights too;
  clicking a highlighted port deselects it. Connection lines are direct segments,
  with scrolled row mapping and clipping. Button bounds are shared by paint/input.
- Loose-item `USE_TARGET` now admits the exact nominated signaller via the existing
  body-eye/hand proof path. Block and loose configurator use share initialization
  and fresh-proof handling; endpoint preparation and server selectors are reused.
  No pickup, background-block dependency or alternate input channel was added.

Full JUnit/compilation/assembly follow-up passed **2,969 tests, zero failures**;
log `build/device-owner-usability-build-final.log`. Source reviews passed for
address ownership/collision policy, compact render/input geometry and exact loose
target use. Final isolated **filtered** native run: tags
`device-network-owner,device-network`, **8/724 selected, 8 passed, zero failed**;
log `build/device-owner-usability-native-focused-final.log`. It includes both
standard/advanced resolved portable endpoints, exact nominated-item ray admission,
`Pressed` route identity, movement-stable addresses and occlusion. Fixture setup
warms the probe's bounded chunk envelope and aims at the real loose-item bounds;
production remains loaded-only. Earlier fixture failures were incorrect aiming
and a display-name-versus-port-ID assertion, not relaxed production admission.
These tests do not establish an authenticated connected-client mouse flow.

Current jar: `build/libs/moonstation14-0.0.1.jar`, **7,556,697 bytes**, SHA-256
`4907DD90A529C95CDC5EFF4A7EEABBFE29BCEEF0D417F0C6D408118D408CA17D`.
This was superseded by the remote-activation routing follow-up at the start of
this document. For that latest artifact, replace it on both client/server and
restart both.

New isolated **full** native run: **724/724 selected, 707 passed, 17 failed**.
The full suite remains **non-green**; reduced failure count versus the earlier
43 is not proof that these failures are unrelated or preexisting. The retained
log is `build/device-owner-usability-native-full.log`, and the failure world/logs
are under `build/gametest-device-owner-usability-full-fixture`. Failures span
power readiness, movement/pulling, airlock timing/contact and finite-gas fixtures.
All eight required device-network/owner fixtures completed without failure in the
full run (the completed 724-test result and exact failure-name list were checked).
Sixteen of the 17 failing method names also failed in the prior 43-failure run;
`leasesOnlyExplicitlyConfiguredHumanVillagers` is the one newly failing name.
Name overlap is not a causal diagnosis. No unrelated runtime fixes were included.
A separate isolated **filtered** `character` check selected **77/724** and passed
**77, zero failures**, including that lease fixture; log
`build/device-owner-usability-character-check.log`. This establishes that its
full-run failure does not reproduce in that filtered run, not that it is unrelated.
Do not discard either full-run failure world. Successful focused fixture removed
after retaining its log. Connected-client appearance, interaction and restart
behavior still require owner retesting; sprint acceptance is not closed.

Implemented corrections awaiting connected-client retest:

- Configurator uses a native animated 32×32 strip: 32 pinned RSI frames, two
  Minecraft ticks each. Separate Link/List mode-light layers follow synchronized
  stack mode through the existing item-property registration service.
- Controls use extracted directional 32×32 frames and state-specific block models.
  Their flat loose-object profiles now consume generated item models rather than
  rotated wall-block geometry. Shared loose-item rendering/physics is unchanged.
- Placement no longer requires the controlled body to be a Player. The existing
  authenticated placement path uses a nullable-player placement context with body
  pose, vanilla face ordering, and existing permission/survival/collision,
  consumption and rollback checks. The existing supported-block list is retained.
- Fresh configurator initialization uses its newly committed hand token/revision
  for selection and publishes updated hand state. It previously continued with
  the stale proof. Existing primary-use dispatch and editor sessions are reused.
  Use and mode switching also explicitly report the current mode.

Prior device follow-up JUnit: **2,953 passed, zero failures**; production and fixture
compilation and assembly succeeded. Resource tests cover animation geometry and
timing, mode layers, directional/state models and flat-profile consumers.
Source reviews passed for the client dispatch, authority/mutation reuse,
nullable-player context and native asset consumers.

Final isolated **filtered** native follow-up: tags `device-network-owner,device-network`,
**7/723 selected, 7 passed, zero failed**. Includes the six networking workflows
and a controlled-mob context fixture with both supported wall-control placement
states. Log: `build/device-owner-native-final.log`. The initial run failed an
incorrect fixture assumption that an entity-collision query detects occupied
blocks; the fixture now tests block replaceability separately. Production was
not changed to accommodate that assertion. Initial log retained at
`build/device-owner-native.log`; successful disposable fixture world removed.

Prior device follow-up jar: `build/libs/moonstation14-0.0.1.jar`, 7,539,330 bytes,
SHA-256 `DD26B86C32C547447A97E8923CED96B64B2506FA164C402C90F1AA301D887399`.
That artifact predates the UV/publication corrections and is superseded by the
next validated build. Always use matching client/server jars.

Validation boundary: the context fixture is not an authenticated end-to-end
placement/consumption or configurator two-click fixture. Current fixture actors
do not supply the player-list-registered, non-fake spectator plus active body
authority required by that route; no mock authority bypass was added. Actual
connected-client placement, clicks and visual appearance remain owner checks in
`MANUAL.md`. The broader full-native-suite failures below remain unresolved.

## Delivered

- Shared resolved device components, numeric frequency/network/port catalogs,
  loaded endpoint adapters, directed persisted links and remembered signals,
  queued typed packets, receiver filters and configurable overload accounting.
- Network Configurator Link/List/Configure retained windows, contextual mode
  switching, immediate link editing, staging and canonical list operations,
  membership visualization, and dropped-signaller configuration.
- Standard/advanced portable signallers and wall button/switch; airlock signal
  inputs and logical DoorStatus output use existing authoritative transitions.
- Alarm membership remains one canonical owner store. Manual vent/scrubber
  controls use queued commands and observed replies, not direct menu writes.
  Existing finite gas transactions and paid-work authorization remain unchanged.
- Pinned sprites/sounds, retained attribution, explicit storage footprints.

Reuse and extension APIs: `EXTENDING.md`, `CORE-CONTRACT.md`, `CORE-COMPONENTS.md`.
Verified source mappings and intentional adaptations: `PARITY.md`.

## Automated evidence

- Final `test compileGameTestJava assemble`: **2,947 JUnit tests passed**, zero
  failures; production jar and native fixture compilation succeeded.
- Final isolated **filtered** native run, tag `device-network`: **6/722 selected**,
  **6 passed, 0 failed**. Includes native queue cadence, offline graph retention,
  routing filters, live control registration, button→paid airlock and queued
  alarm→vent sync/disable/re-enable. Log:
  `build/device-network-focused-native-final.log`.
- **Full** isolated native run: **722/722 selected**, **679 passed, 43 failed**.
  The suite is **not green**. Failures include shared power-topology/readiness,
  movement/disposal fixtures and a medical prototype fixture. The new paid door
  and alarm workflows also fail their power-readiness prerequisite in this full
  run, despite passing the filtered run. This does not prove those failures are
  unrelated or preexisting. Retained evidence:
  `build/gametest-device-network-full-fixture/logs/latest.log` and its world.
  The earlier `build/device-network-full-native.log` records a failed incremental
  compile attempt, **not** the completed full test result; forcing fixture
  recompilation cleared that compile problem.

## Device build before the outline follow-up

`build/libs/moonstation14-0.0.1.jar` — 7,522,741 bytes.
SHA-256: `3A029C44861D900231FD1D5EB81874407D384B5A57210A389AB61217AB72B06C`.
Use the same jar on client and server.

Follow `MANUAL.md` for GUI/drag/scale, portable ownership/restart and two-client
testing. No automated GUI input or normal owner launch/configuration changes were
used. Those connected-client observations remain unverified.

Remaining boundaries: one static transport grid per dimension, manual supported
atmos controls only, and
unresolved file-specific defibrillator-zap attribution documented in
`docs/audio-unknown-provenance.md`. The full-suite failure cluster remains open.
