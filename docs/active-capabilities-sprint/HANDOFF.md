# Active capabilities

## STATUS

Closed by the owner on 25 October 2026 after reporting that everything appeared
to work and idle lag did not recur. The compact actions menu was also explicitly
approved. No performance capture established a lag cause; do not attribute its
disappearance to a particular change.

The final revision passed compilation, focused JVM checks and independent source
review. A fresh workflow gate has **not** run after the final sizing, cooldown and
F3-key changes. Earlier technical PASS does not certify those later edits. No
commit, installation, merge or post-acceptance full-suite result is implied.

## ARCHITECTURE

- Resolved typed `character_action` prototypes define actions; resolved character
  components and current worn equipment supply grants. `BodyActionDiscovery`
  discovers composition/provider grants, not a universal possessed-body action set.
- `BodyActionService` reuses `BodyActionAuthority` for body/session identity and
  performs action-specific admission. Requests bind body UUID/epoch, snapshot
  revision, provider identity and sequence. Client snapshots never authorize effects.
- Combat mode is body-owned state, without melee. Scream reuses the shared emote
  and vocal delivery pipeline. The debug cap uses real body-give/hands/equipment
  ownership; removing/replacing its occupancy token revokes its action.
- `BodyActionClientState` owns replicated ordering, pending state and cooldown
  presentation. Each accepted snapshot anchors its server timestamp to a guarded,
  monotonic local level-clock value. Never carry pre-anchor elapsed time into a
  new snapshot: that caused later scream timers to disappear. HUD and activation
  gating use this same clock; server cooldown/admission remain authoritative.
- The HUD and persistent actions-menu overlay use the shared pointer workspace.
  `BodyHandClient` owns chat workspace painting and ordered click/capture dispatch;
  do not add a second feature-local chat painter or click handler.
- Shared emotes use `CharacterPublicName.component`: custom name, saved personal
  identity from the body's account/profile binding and existing chat ledger,
  resolved prototype metadata, then native fallback. The ledger lookup is
  non-creating and does not depend on current speech permission or camera control.

## IMPORTANT FILES

- `src/main/java/com/juicyslew/moonstation14/ms14/actions/`: discovery, service,
  admission, runtime state, effects, DTOs and networking; `client/` contains HUD,
  menu, fixed slots, layout, key policy and replicated state.
- `src/main/resources/data/moonstation14/moonstation14/character_action/`:
  inherited action definitions. Character components include `CombatModeComponent`
  and `VocalComponent`; equipment data has typed supported action grants.
- `ms14/character/CharacterPublicName.java`, `effect/EmoteEffect.java` and
  `effect/VocalScreamDelivery.java`: shared naming and vocal output. Existing
  `chat/identity/ChatIdentitySavedData` is the personal-name owner used by speech.
- `mixin/client/MinecraftBodyActionKeysMixin.java`: pre-vanilla action routing.
  `BodyActionKeys.InputGuard` also discards F3 chord clicks at event time.
- `src/test/java/.../ms14/actions/`: clock, layout, menu, slot, chat-workspace,
  ordering, codec and admission tests. `build.gradle` has readiness selectors.
- `src/gametest/java/.../hands/live/BodyActionGameTests.java` and
  `.../chat/server/EmoteCommandGameTests.java`: registered-body authority,
  equipment, cooldown, personal-name and negative-admission coverage.
- `PROTOTYPES.md`, `MANUAL.md`, `ACCEPTANCE.md`: mapping, concise manual steps and
  evidence boundaries. Asset provenance is indexed by `docs/actions-provenance.md`
  and `docs/audio/audio-interaction-provenance-notes.md`.

## SS14 CORRESPONDENCE

Pinned reference: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- Combat toggle is mental: no interaction/consciousness requirement. Scream
  requires consciousness, skips interaction checks and has a 200-tick action delay.
- Vocal settings come from composition. Human audio has an explicit unsexed
  fallback; species overrides and documented pitch/Wilhelm probabilities remain.
- SS14 emotes use the current identity representation, normally a personal name,
  not the species prototype name. Full identity-obscuring behavior is not ported.
- Upstream actions occupy a horizontal row in a top-left host. The delivered
  **vertical** layout is the owner's explicit preference. Circular cooldown wipe,
  seconds and brief completion feedback remain distinct from toggle/disabled state.

## DECISIONS / WHY

- Fixed configurable slots: **1 combat, 2 scream, 3 debug pulse**. Ungranted buttons
  are omitted without renumbering other bindings. K is the supplementary menu;
  direct disposal climbing defaults to Ctrl+K. Saved controls are not overwritten.
- Presentation uses local half-scale coordinates: 14-pixel buttons, 15-pixel
  stride and a normally 95-pixel-wide menu. Icons occupy 12 of 14 displayed button
  pixels. Shared inverse coordinates cover paint, close, drag and row hits;
  tiny viewports clamp bounds and may display zero rows.
- F3+number chords discard only mod action queues, preserving native input.
  NeoForge's mapped `KeyboardHandler.keyPress` records `KeyMapping.click` before
  posting `InputEvent.Key`, so event-time draining also handles quick releases.
  This guard covers numbered action mappings, not every possible menu binding.
- Vanilla F3+L profiling requires cursor-locked play with no Screen host; the
  free-cursor workspace does not satisfy vanilla's debug-key admission.

## KNOWN GAPS

No melee, action drag/reorder/search, user action-to-slot reassignment, or migration
of existing animal commands. No client-wide personal-name entity projection.
Reptilian recording licensing remains unverified in `docs/audio-unknown-provenance.md`.

An unrelated committed `PrototypeCatalogSyncTest` expects Flight cruise height
1.5 while committed `FlightComponent` defaults to 4.0. It was reproduced and left
unchanged; do not claim a full-suite PASS or reopen the closed animals sprint here.

## VALIDATION

- Final main/GameTest compilation and **118 JVM tests across 24 fresh suites**:
  zero failures/errors/skips. Sizing, clock/art and F3 source reviews passed.
- Earlier workflow **PASS**: `run_cd9e0a6eb722cce655c3452c`, before the final compact
  UI/clock/F3 revision. Earlier native evidence includes 17/17 selected tests;
  it is not a fresh native result for the final revision.
- Owner manually retested and reported working behavior and no recurring idle lag.
- Native emote listeners must filter their fixture body/carrier and observation
  window. Shared-level sound capture produced false failures when cases overlapped;
  GameTest tags do not isolate fixtures.

## NEXT WORK

Complete final serialized workflow/post-acceptance regression when scheduled;
keep its result separate from owner closure. If lag recurs, profile the actual
laggy session rather than guessing from cooldown render code. Future visible-name
work must reuse the personal identity owner and test names distinct from species,
account and native harness names.
