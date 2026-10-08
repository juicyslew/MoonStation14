# Examination sprint handoff

## STATUS

Owner accepted and closed the sprint on 2026-10-07. Delivered world examination,
transparent-target cycling, nonmodal readouts, solution information, and Sun/Earth
inspection, including the final status-column/menu-priority refinement.

Changes remain in the working tree; closure does not imply a commit, push, or merge.
Artifact: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`F17A2DF0F3B697EC877FF78678B801B2193EEE1F8C9DDDE23B7106141BCF854C`.
Client and server must use the same rebuilt jar.

## ARCHITECTURE

- Separate rendered-view selection, server eligibility, and contributor-provided
  information. `ExamineService` authenticates the committed body/observer and
  epoch, resolves loaded targets, checks LOS/attempt veto, and collects bounded
  styled entries. Queries must not initialize or mutate gameplay state.
- Shared `ExamineWorldSelector` uses explicit transparency. Airlocks canonicalize
  to the lower controller; renderer/selection reuse `AirlockGeometry`. Highlight
  bounds cover the assembly, while ray hits test individual frame/leaf pieces.
- Typed inherited examiner flags and metadata feed live consumers. Resolved Puddle
  and Jug profiles supply capacity/examine settings. `SolutionExamineFormatter`
  uses detached reagent snapshots and resolved definitions, not another store.
- `SCENE` targets identify resolved Sun/Earth profiles, not fake entities/blocks.
  Renderer and ray hits share profile-driven geometry. World candidates, including
  glass, always win. Scene admission retains authority, dimension/time/medical
  visibility, and loaded LOS; only physical examine distance is exempt. Metadata
  uses the common collector/response assembly. Scenes have no world-space outline.
- Client latch, response correlation, identity/stamp guards, detail policy, and
  refresh policy are separate helpers. Explicit clicks replace the latest target
  immediately; automatic refreshes are single-flight with one coalesced follow-up.
  Stale replies cannot release a newer request's slot.

## IMPORTANT FILES

Java paths are relative to `src/main/java/com/juicyslew/moonstation14/`.

- `ms14/examine/`: service/authority, resolver/codecs, contributors, selector,
  and solution formatter. `client/`: presentation, detail/refresh policies, latch,
  cycling, controller identity and response stamps. `scene/`: profiles/geometry/LOS.
- `ms14/puddle/`, `ms14/jug/`; `block/block_entity/PuddleBlockEntity.java`;
  `item/custom/JugItem.java`: composition and runtime capacity consumers.
- `ms14/chat/client/TopRightHudLayout.java`: shared status/examine layout.
  `ms14/hands/client/`: input/focus/workspace integration.
- `ms14/doors/AirlockGeometry.java`, `MoonStation14Client.java`: shared door
  geometry and profile-backed sky/status rendering.
- `src/main/resources/data/moonstation14/moonstation14/`: inherited profiles.
  `assets/moonstation14/lang/en_us.json`: solution templates, 406 reagent names,
  and all 98 authored physical-description/article pairs.
- `src/test/.../ms14/examine/`, puddle/jug/reagent tests;
  `src/gametest/.../ms14/examine/`: focused fixtures.
- This folder's `PARITY.md`, `CONTENT.md`, `SELECTION.md`, `VALIDATION.md` index
  detailed source mapping, behavior contracts, and historical evidence.

## SS14 CORRESPONDENCE

Reference pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Examine/solution systems, component defaults, and relevant prototype parent chains
were inspected; see `PARITY.md`/`CONTENT.md` for citations.

Ordinary range 16, details 3, critical/blind 1.3, dead 0.75; stun alone does not
block examination. Ordinary LOS exempts actual shapes containing either endpoint.
Exact-boundary negative traversal and null clipping regressions are covered.

Puddle uses approximate volume; Jug uses exact volume and a 200-unit `drink`
solution. Jug capacity intentionally changed from hardcoded 150 to 200. Only
recognizable names are disclosed; primary physical description uses mixture
color, individual recognizable names their prototype colors. All 59 local true
flags match upstream: SpaceLube true, Bicaridine defaults false. Empty solutions
emit empty volume before exact formatting. Nested translations/styles survive
the response codec.

## DECISIONS / WHY

- Shift-left-click examines; Shift-scroll cycles. Default selection looks through
  glass; manual choices remain stable within the same transparent anchor. A new
  Shift hold restores automatic preference.
- Selection follows camera/pointer; eligibility uses the controlled body's
  physical eye and existing local prediction, never carrier/third-person camera.
  Missing medical attachment uses the shared healthy default read-only.
- Wall/entity/scene responses legitimately have null controller stamps. Initial
  controller replies require a stamp; refreshes match exactly.
- Details align to the shared right border below seven reserved status rows;
  adaptive width 56–112 GUI pixels, font scale 0.75. Overflow scrolls with non-Shift
  wheel. Name-only preview remains above the cursor with a 24-pixel gap.
- Local detail exit masks immediately; entry needs fresh server approval.
  Transitions refresh early, disagreement retries at four ticks when idle, matched
  content at twenty ticks. Refreshes never restart the popup timer.
- Unpinned expiry is ten real seconds. Pin suspends expiry/outside-click dismissal
  only; Escape/× still close. Looking away alone does not dismiss. Body/level,
  identity, range and visibility invalidation remain mandatory while pinned.
- Pause/non-world-host screens own clicks/wheel. Storage hosts world input outside
  interactive areas. Entry groups are ordering metadata, not visible labels.
  Active/next-target outlines coexist; entity bounds interpolate at render time.
- Celestial draw distance is virtual. Camera/actor LOS use the shared 128-block
  loaded-world bound independently of sky geometry; synthetic far-end blockers
  are not exempt. Sun/Earth are Minecraft adaptations, not SS14 prototype parity.

## KNOWN GAPS

- Live blind/blur projection is absent despite policy support. Unsupported puddle
  slip/evaporation statements and full solution scan verbs are not supplied.
- Middle-volume wording deterministically chooses half-full rather than examiner
  name; equal-quantity sorting uses stable IDs, not server-localized names.
- Native fixtures do not exercise real player packet/GUI input end to end. Owner
  manual acceptance supplies that gate; no GUI automation was authorized.
- Aggregate GameTests remain red; sprint acceptance does not waive failures.

## VALIDATION

Build/GameTest compilation/assembly passed; **2,676 JUnit tests**, zero failures,
errors or skips. Latest filtered native run: **12/668 selected**, all 12 completed
and passed, zero failed/incomplete (`gametest-examination-status-focus`).

Latest full run: **668 completed, 655 passed, 13 failed, zero incomplete**
(`gametest-examination-jug-scene-full`); all examination fixtures passed. Not
rerun for the final client-only refinement. Failures/artifacts are in
`VALIDATION.md`. The changed timed-puddle repeat failure has an unproven
deadline-boundary cause, does not involve a Jug, and must not be waived as
pre-existing. Jug child capacity is tested through the shared live-getter
projection; native coverage uses the registered 200-unit root, not a child item.
Disposable passing fixture worlds were removed; logs/reports and failed runs remain.

## NEXT WORK

No remaining authorized sprint implementation. Preserve accepted input/layout
rules when adding contributors. Audit upstream composition before new disclosures.
Investigate aggregate timing failures separately; do not implicitly expand
chemistry disclosure or reopen the accepted sprint.
