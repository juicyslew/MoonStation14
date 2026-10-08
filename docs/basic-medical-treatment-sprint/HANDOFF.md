# Basic Medical Treatment + Health Analyzer

## STATUS

Closed by owner acceptance on 2026-10-07. Supplies, persistent analyzer, basic
syringes, and the requested inventory/UI/audio corrections are complete. Owner
explicitly confirmed compactness and controlled-body `/give`, then accepted the
sprint including the final fall-audio follow-up. Acceptance does not imply a
clean full GameTest suite or exhaustive connected-path automation.

No commit or push was requested; preserve the existing mixed staged/unstaged
worktree. Surgery, defibrillation, new medicines, and unrelated power/ventilation
repairs were not added.

Subsequent owner-initiated main integration is indexed in `MERGE-MAIN.md`;
the sprint-closure validation below predates that combined tree.

## ARCHITECTURE

- `medical_item` and `injector_mode` are typed, inherited prototype families.
  Live services consume resolved healing, scanner, item/stack, syringe and mode
  policies; they are not merely JSON descriptions of hardcoded behavior.
- Treatment uses existing timed interactions, typed damage, bleeding and body
  authority. `MedicalTreatmentEffects` is the production compound-effect consumer.
  Bloodloss damage and current bleeding are separate; `/ms14damagecheck` reports
  both. Hand changes cancel treatment, matching the pinned upstream rule.
- All gameplay hand mutations use `LiveHandsCommitGateway`. Batches publish the
  final authoritative owner snapshot. Candidate validation must use the same
  proposed hand/worn/grid state throughout, never candidate hands with current
  equipment. Direct writes remain restricted to the gateway and bootstrap.
- `BodyHandStateService` retains exact connection/body/epoch issuance across
  rate-limited/replayed queries and ordinary revisions. Its legacy
  `requireFreshQuery` now publishes fresh authoritative state instead of revoking
  valid admission. Successful full pushes qualify as issued state; actual lost or
  changed authority invalidates it. Revision checks and FakePlayer rejection remain.
- Syringes reuse bloodstream/reagent stores, injection reactions and the shared
  liquid transaction. `LiquidInteractionCapability.plan` retains a five-unit
  default, but its explicit-dose overload accepts the caller's admitted amount;
  the former global five-unit ceiling incorrectly rejected 10/15-unit requests.
  Injection is not oral/Touch exposure; Human/Pig reactive permissions stay intact.
- Analyzer windows reuse storage pointer lifecycle, sibling ordering and machine
  toolkit chrome. `MedicalPortraitSnapshot`/`NeutralPortrait` produce detached,
  upright standing/alive Human-harness, Pig and Villager previews with appearance
  and equipment preserved; they must not mutate the live patient.
- Fall audio belongs to the shared forced standing-to-down transition in
  `PostureSystem`, not a separate Critical-only listener. Resolved
  `StandingState.down_sound` selects the event. Repeats and restored-down bodies
  are quiet; pre-registration initialization is suppressed using live-level
  membership, **not native tick count**. Reuse `ControlledSoundDispatch` for
  camera-positioned listeners; do not add a second world/owner playback.

## IMPORTANT FILES

Java paths below are relative to `src/main/java/com/juicyslew/moonstation14/`.

- `ms14/medical/`: prototype data/catalogs, `MedicalTreatmentService`,
  `MedicalTreatmentEffects`, `HealthAnalyzerServer`, lifecycle/readings/payloads.
- `ms14/medical/syringe/`: mode changes, timing, body/container services and
  post-transfer full/empty mode transitions.
- `ms14/medical/client/`: analyzer layout/workspace/renderer and neutral portrait.
- `ms14/hands/live/`: commit gateway, `LiveHands`, `BodyLiquidInteraction`,
  `BodyGiveService`, shared drop creation/placement in `BodyHandItemTransfer`.
- `ms14/hands/network/BodyHandStateService.java`; `ms14/hands/client/`:
  owner push/reconciliation, pointer controls, hand-label rendering/layout.
- `ms14/standing/PostureSystem.java`,
  `ms14/character/components/StandingStateComponent.java`, and
  `component/codec/json/CharacterSchemaAudit.java`: down sound and audited field.
- `sounds/ModSounds.java`, native `sounds.json`, and sound assets: resource entries
  alone do **not** register native sound events.
- `src/main/resources/data/moonstation14/moonstation14/{medical_item,injector_mode}/`
  and `data/moonstation14/item_footprint/`: resolved policies and storage footprints.
- Supporting indexes: `SYRINGE-PARITY.md`, `ASSETS.md`, `CORRECTIONS-UI.md`,
  `VALIDATION.md`, `../controlled-body-give/IMPLEMENTATION.md`,
  `../critical-death-prone-sprint/FALL-AUDIO.md`, and the hands inventory handoff.
  Correction notes retain historical/superseded statements; verify current source.

## SS14 CORRESPONDENCE

Reference checkout: `C:/Users/William/Documents/SS14-dev/space-station-14`, pinned
to `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `BaseItem → BaseHealingItem → Ointment/Brutepack/Gauze`: bruise packs heal
  Blunt/Slash/Piercing by 5; gauze heals Slash 5/Piercing 10 and reduces bleed
  amount by 10, without directly healing Blunt or accumulated Bloodloss.
  Ointment heals Heat/Cold/Shock 5 and Caustic 1.5.
- Supplies cap at 10 and occupy Small/two-cell storage footprints. Analyzer is
  Small/two-cell singleton; syringe is Tiny/one-cell singleton. Creative display
  copies are count one; gameplay factory quantities/components remain intact.
- `BaseItem → BaseSyringe → Syringe`: 15-unit capacity, draw/inject modes,
  selected 5/10/15 units. Body timing is 5 seconds plus 0.1 per admitted unit above
  5; self or downed target applies 0.5, not stacked. Container injection is zero
  duration; drawing uses only `ContainerDrawTime` (basic zero). The port reuses an
  atomic zero-tick transfer instead of scheduling SS14's zero-duration DoAfter.
  Container admission/live/commit reach is consistently two blocks. No cooldown
  is invented; full drawing selects inject, empty injection selects draw.
- Human and Pig inherit upstream `StandingState.DownSound = BodyFall`.
  All four upstream files are retained in one equal-weight native sound event.
  Audio remains server-authoritative; native per-listener variant selection is a
  Minecraft adaptation. Voluntary C-prone stays quiet; an upstream manual toggle
  counterpart was not established. Forced knockdown and medical downing sound.

## DECISIONS / WHY

- Analyzer scan range is **3.5 blocks**; UI retention is independently **3.0**
  from the scanner root. These owner-requested 3D adaptations replace upstream
  2.5/2.0. Patient range loss pauses cached readings/link and returning resumes.
  Dropping/storing stops scanning and clears the link; a nearby cached UI can remain.
- Z toggles a linked analyzer window; unlinked opening is denied. Re-activating
  the same target closes the subscription without stopping the scan. Last-window
  close recaptures the mouse unless another managed window/screen owns focus.
- Analyzer width is font-measured; healthy height is about 52 GUI pixels, cap 200
  or screen height minus 16. Overview/portrait stay fixed; only damage scrolls.
  Category rows are 8 GUI pixels, types 6, group separators 3; type readouts match
  faint type text, category totals match category text. Category icons are restored.
- Hand labels are 96 GUI pixels wide with original styling/anchors. Left text is
  right-aligned, right text left-aligned, with symmetric three-pixel insets using
  the actual 0.76 text scale.
- Controlled-body `/give` tries active-hand merge/insertion, then the other hand,
  then drops exact leftovers at the body's resolved location. No-hand or
  unrepresentable grants go wholly to the world; ordinary players retain vanilla
  behavior. Do not grant to the spectator carrier or blacklist hypothetical lockers.

## KNOWN GAPS

Owner deferred remaining analyzer/storage front-to-back issues. Full-suite
power/ventilation, pulling, explosion and puddle assertions remain unclassified;
do not assume they are inherited. Automated authenticated-player request/transport,
render/input and actual sound-hearing coverage is absent; trusted core fixtures
and audio-effect sink capture do not prove those paths. Four treatment and four
body-fall files retain unverified per-file provenance in
`docs/audio-unknown-provenance.md`.

## VALIDATION

Final build and GameTest compilation passed: **2,596 unit tests, zero failures,
errors or skips**. Focused fall/audio: **3/661 selected, all passed**. Full native:
**661 selected; 649 passed, 12 failed**; exact failures are in `FALL-AUDIO.md`.
Logs: `build/validation-logs/body-fall-audio-complete-{unit,focused,full}.log`;
failed full world: `build/gametest-body-fall-audio-complete-fixture`.
Final JAR: `build/libs/moonstation14-0.0.1.jar`, 6,243,954 bytes. Earlier focused
medical, syringe and give evidence remains indexed in `VALIDATION.md`.
Disposable and resolved-failure fall-audio worlds were removed; their validation
logs and the final failed full-suite world remain.

## NEXT WORK

No active sprint work remains. Start future inventory changes from the gateway
and issuance invariants; scanner changes from its resolved policy/shared window
geometry; syringe changes from the existing liquid transaction and mode catalog.
Treat deferred UI ordering, full-suite failures and provenance research separately.
