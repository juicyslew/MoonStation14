# Airlocks sprint handoff

## STATUS

Closed by the owner on 2026-10-07. Powered airlocks, wiring, electrification,
crushing/reopening, and connected presentation are accepted for this sprint.
Closure does **not** establish a green full GameTest suite or complete SS14 parity.
No assistant staging, commit, or push accompanied closure; sprint changes remain
in the working tree.

Final artifact: `build/libs/moonstation14-0.0.1.jar`.
SHA-256: `C9A2D693C5F865F5D287B1480DDC76342CBE0561EF0A467BF7E2C4AE26C34A74`.

## ARCHITECTURE

- One lower controller owns a one-wide, two-tall doorway. Upper/lower interaction
  resolves to the same identity; both cells participate in atmosphere sealing.
  Preserve accepted pressure/fire behavior, float-backed gases, and the 15 Hz
  simulation. This sprint did not authorize simplifying those systems.
- Live behavior consumes inherited, resolved typed `AirlockData`: door phases,
  receiver demand, safety, bolts, electrification, and wire settings. Opening
  requires known, fresh, fully paid APC delivery and raised bolts. Standard
  demand is 20 W; the local receiver-extension reach is 3 blocks.
- Bump opening uses the resolved door and character `DoorBumpOpener` components
  and a bounded swept-AABB movement query, not proximity. Every root at the
  nearest blocking plane is considered, so one seam contact can open two doors.
  Contact memos are per root; power graph reconciliation is loaded-only/bounded.
- Eight resolved wire actions feed server-owned opaque slots. Wire sessions bind
  actor/body authority, inventory-menu identity, root identity, reach, panel,
  revision, and held-tool quality. Render replication excludes functional maps.
- Wire views are retained gameplay windows hosted by `StoragePopupScreen`.
  Global L-Alt pointer intent does not close the session. HUD, hands, storage,
  chat, and ordinary gameplay routing remain available. Embedded screens must
  undergo normal `Screen.init` before rendering, including first use and resize.

## IMPORTANT FILES

- `src/main/java/com/juicyslew/moonstation14/ms14/doors/`: controller/state,
  prototypes, bump/contact dispatch, renderer, and wire protocol/UI.
- `src/main/resources/data/moonstation14/moonstation14/airlock/`: definitions.
- `.../ms14/hands/network/BodyHandRequestService.java`: active-action admission.
- `.../ms14/hands/client/{BodyHandClient,StoragePopupScreen}.java`: shared
  workspace/HUD/pointer routing.
- `.../ms14/character/CharacterControlSystem.java`: authoritative status gates.
- `.../mixin/client/LivingEntityWishFacingMixin.java`: knockdown projection;
  renderer injection requires the fourth `scale` float.
- `src/gametest/java/com/juicyslew/moonstation14/ms14/doors/`: live fixtures.
- Supporting indexes: `PARITY.md`, `ELECTRIFICATION.md`, `WIRE-UI.md`,
  `RENDERING.md`, `ASSETS.md`, `BUMP.md`, `VALIDATION.md`, and `MANUAL.md` here;
  also `docs/STUN-ACTIONS.md` and `docs/airlock-crush-cycle.md`.

## SS14 CORRESPONDENCE

Verified reference: `C:\Users\William\Documents\SS14-dev\space-station-14`
at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Relevant composition is
`Airlock -> BaseStructure`, with wire layout/actions resolved separately.
Consumers include `SharedDoorSystem`, `ElectrocutionSystem`, `PowerWireAction`,
door wire actions, `LogWireAction`, and `AiInteractWireAction`.

- Normal phases use 0.4 s then 0.2 s; auto-close defaults to 5 s. Timing cut sets
  its delay modifier to 0.01; pulse sets 0.5 for 30 s with slow blinking. Neither
  changes opening/closing animation duration.
- Unsafe crush applies 15 Blunt once and 2.4 s paralysis/knockdown. After a
  separate 2 s wait, reopening starts **normal OPENING**, resets animation time,
  becomes passable after phase one, then fully OPEN. Auto-close arms only then.
  Unknown/unavailable power uses bounded 20-tick retries, never invented power.
- Shocks do **not** veto an eligible bump or already-admitted activation.
  Configured 7.5 shock damage truncates after coefficient arithmetic to 7 at
  coefficient 1; paralysis lasts 5 s. Distinct contact triggers, not tick damage.
- One power-wire cut can shock while leaving power/electrification available;
  both cuts remove power. A single power pulse disables the entire receiver
  for 30/15 s. Preserve the pinned pulse/mend timer-cancellation latch quirk.
- Log pulse disables circuit indication for resolved 30 s; repeat pulses do not
  extend the timer. Cut cancels recovery; Mend enables without canceling a still
  active timer; power loss cancels recovery but leaves disablement latched.
  Circuit disable/deadline and physical cuts are persisted. Actual access-log
  events remain unsupported. AIA is ON intact/OFF cut; its pulse is intentionally
  empty, matching upstream.
- Already-stunned **new active requests** reject before shocks or mutations.
  A newly admitted power-wire action may shock/stun and still finish its own
  mutation. Speech remains permitted. Existing machine UIs generally stay open
  during stun; opening/action admission and UI lifetime are separate. Range,
  lifecycle, and machine-specific closure rules still apply. Wire feedback says
  “Cannot interact while stunned”; passive queries retain it, accepted actions
  clear it.

## DECISIONS / WHY

All-access doors, E activation, walking contact, and strict powered operation
are retained. Minecraft geometry adapts SS14 sprites to horizontally sliding
3D leaves occupying most block depth. Front/back reconstruct complete artwork;
other faces use source-derived `#545454`. Both sides show the full maintenance
hatch. Do not replace accepted art with simpler vanilla geometry.

Wire clicking resolves held-tool cut/mend/pulse server-side. The compact UI uses
sharp art, larger bright-red ON cores, smaller dim OFF cores, and no white bezel
or wire-specific L-Alt hint. Airlocks are non-suffocating; only registered trapped
victims receive a root's transient collision exemption until passability/exit.

## KNOWN GAPS

Equipment insulation/glove protection and controlled-body melee shock routing
are unsupported. Departmental access/electronics, welding/construction, remote
networks, AI services, actual access logging, and vertical leaf motion are deferred.
Asset attribution is retained in `ASSETS.md`; unresolved audio provenance is in
`docs/audio-unknown-provenance.md`. An earlier client shutdown reported vanilla
`TextureManager.close` concurrent modification; its cause remains unestablished.

## VALIDATION

Final `test compileGameTestJava assemble`: **2,393 JUnit tests passed**, no
failures/errors/skips. One focused server selected **5/555**, completed/passed
**5/5**: wire persistence, Log pulse/NBT/repeat/expiry, electrified bump opening,
fast unsafe crush reopening, and stun selection denial/recovery. Evidence:
`build/airlocks-validation/log-feedback-final/`; passing disposable world removed.

Historical full run remains **544 passed, 5 failed, 3 incomplete**. Later focused
passes do not replace that gate. Failure names and retained-world paths are in
`VALIDATION.md`; do not assume remaining failures unrelated. Headless coverage
does not provide authenticated-player end-to-end E/wire UI evidence; connected
acceptance is the owner's declaration, not an automated test result.

## NEXT WORK

Start from this handoff and current validation before broader exploration.
Diagnose outstanding full-suite failures separately. Preserve failed artifacts;
avoid repeated server/client launches. Extend missing features only after pinned
prototype-chain/default/consumer audits, with resolved local runtime tests and
explicit adaptation boundaries. No further feature work is required for closure.
