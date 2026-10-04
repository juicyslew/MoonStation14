# Visible Timed Interactions + Ingestion

## STATUS

**Owner-closed October 4, 2026**, after connected feedback and follow-up fixes.
Reusable timed-operation foundation, visible progress and held/world/puddle
drinking are delivered. Owner closure is acceptance, not evidence that every
manual check was individually rerun. Changes remain uncommitted on `5d55c01`;
no assistant staging, commit or push occurred. Read this handoff before source
exploration; `PARITY.md` has upstream composition and source references.

## ARCHITECTURE

- `TIMED_OPERATIONS` is transient actor-owned immutable state: identities, revision,
  timings, phases and interruption flags. `TimedInteractionEngine` supplies pure
  transitions; `TimedInteractionSystem` holds server-only validation/completion
  delegates separately and ticks indexed actors, not every entity. Independent
  kind/source keys can coexist; duplicate keys cancel the existing action and
  block the replacement. Presentation is structurally bounded to 32 entries.
- Due completion runs once per cycle. A committed repeat restarts the full duration
  on the same identity. Terminal bars linger ten ticks; snapshots reach trackers
  and the owner, including the final empty state **before** owner metadata is pruned.
  Tracking, entity removal and server-stop cleanup are wired.
- Required typed `Edible` composition resolves through the `ingestion_profile`
  catalog. Abstract defaults, drink-base mapping and concrete jug/bottle/puddle
  entries supply live dose/delay/empty policy. Unknown/unready profiles fail closed.
- `BodyTimedIngestionService` captures actor authority; `TimedIngestionAccess`
  admits/revalidates exact sources and calls existing safe liquid/puddle transactions.
  Starts consume nothing. Completion uses current source/stomach state, conditional
  compensation and commit-dependent cleanup. Repeat requires confirmed commitment,
  remaining source and admission capacity. Ambiguous mutation never repeats.
- Held source tokens may move between hands; switching active hands does not cancel.
  Dropping/storing/replacing the source does. World roots retain exact entity/block
  entity identity and loaded reach/LOS; continued aiming is unnecessary. Initial
  world/puddle admission requires an empty, unreserved active hand. Minimal request
  authority/revision checks precede duplicate cancellation; full stomach/source
  admission follows, so an authorized stop remains possible when capacity fills.
- `AppliedDamageEvent` is emitted after actual positive damage ledger commits;
  healing/rejected damage does not interrupt. Ingestion cancels at one typed damage
  unit, including damage followed by healing in the same tick; ordinary movement
  does not cancel self-drinking.
- Generic presentation snapshots contain no source tokens/stacks. Client prediction
  affects progress only. Reconciliation is body-UUID **and** sequence qualified;
  concurrent starts wait for authoritative presentation. Generic progress has no
  hands side effects: successful ingestion sends owner-only `IngestionCommitted`
  body/epoch hints. Hands refresh through normal queries while retaining HUD and
  pending action/block correlations.

## IMPORTANT FILES

- `ms14/timed_interaction/`: engine, state, server runtime, snapshot/networking.
- `ms14/timed_interaction/client/`: presentation cache, billboard renderer/math.
- `ms14/ingestion/`: typed catalog, controller, source access and commit notification.
- `data/moonstation14/moonstation14/ingestion_profile/`: inherited definitions.
- `hands/live/{BodyLiquidInteraction,BodyPuddleDrinkService,BodyItemTargetProbe}.java`;
  `hands/network/BodyHandRequestService.java`: completion seams and live dispatch.
- `hands/client/{BodyHandClient,BodyInputGesturePolicy,BodyHandClientPolicy}.java`:
  gestures, key-press modifier capture and hand-proof refresh safeguards.
- `entities/{LooseItemEntity,LooseClientPositionInterpolation}.java`: client smoothing.
- `gametest/.../hands/live/*Timed*GameTests.java`, `BicaridineIngestionGameTests.java`,
  `BloodIngestionGameTests.java`; unit tests under ingestion/timed_interaction/entities.
- `CONNECTED-01.md`, `CONNECTED-02.md`: connected corrections and drug evidence;
  `SPRINT.md`: historical runs and unresolved full-suite failures; `MANUAL.md`: checks.

## SS14 CORRESPONDENCE

Verified pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Edible defaults are five units/one second; puddle explicitly overrides to one
unit/three seconds, not five/three. Jug/bottle inherit or explicitly select
destroy-on-empty false. Bottle derives from `BaseItem`, not `DrinkBase`; puddle
is standalone. Full ancestry is in `PARITY.md`.

Shared actor state, duplicate stop, completion-driven repetition, damage interruption
and terminal retention follow DoAfter concepts, **not its complete argument API**.
Prototype components supply feature data; consuming systems construct action args.
There is no new universal timed-policy prototype family.

Progress uses the verbatim 24×7 SS14 sprite with adjacent attribution. Fill runs
left-to-right; canceled fill freezes and flashes red/off while the frame stays
visible. Local bars are emissive, remote bars world-lit. First-person projection
places self progress in the lower view across FOV settings; third-person/world
bars sit clear of actor heads. This positioning is a Minecraft adaptation.
Existing completion-only drinking audio retains its unresolved provenance record.

## DECISIONS / WHY

- Z starts/stops held drinking. Alt+E and Alt+left-click share alternate world use;
  puddles are probed first. Keyboard modifier capture also works when NeoForge's
  Alt-specific mapping does not queue a click. Ordinary E/Shift+E remain unchanged.
- Jug's local 150-unit capacity and glass-like physics remain unchanged, versus
  upstream 200-unit strong plastic. Local stomach physiology and five-block loaded
  world access remain adaptations.
- Loose-item client smoothing buffers the latest server target until the next client
  tick, preserving render/outline old/current positions. Rotation-only updates use
  pending target getters; initial spawn uses Minecraft `moveTo`. Corrections over
  four blocks snap both histories, a deliberate heuristic. Server physics, ownership,
  tracking interval and vanilla pickup/merge/expiry suppression are unchanged.
- Oral bicaridine absorption is slow (.125 units reaching body per normal second);
  it heals brute injuries but does not ordinarily accumulate to bloodstream overdose
  thresholds. Blood digestion produces uncooked animal proteins that can poison
  humans. Both paths have actual ingestion/metabolism fixtures with untreated controls.

## KNOWN GAPS

Before **each new timed consumer**, audit its upstream components and the system
constructing `DoAfterArgs`. The current subset is not automatically sufficient:
per-operation thresholds, target/tool distance checks, generic hand options,
weightless movement, duplicate policies, attempt frequencies and typed completion
event dispatch are incomplete. Implement capabilities that consumer actually uses;
do not substitute drinking rules or invent speculative policy infrastructure.

Full intoxication visuals/speech, force-feeding, food/trash behavior, utensils and
clothing ingestion blockers are outside this slice. Color-blind progress palette
and contained-source visibility rules are not implemented. Construction integration
and heavy-object performance remain separate work.

## VALIDATION

Latest: **2,125 unit tests passed**; filtered `timed-interaction` **14/516 selected,
all 14 passed**, zero untagged. Prior connected fixes: `timed-interaction,loose-objects`
**31/515 selected, all 31 passed**. Independent architecture/input/render/smoothing
reviews passed. Logs: `build/timed-connected-0{1,2}-{unit,fixture}.log`.

Latest full run was earlier: **514/514 selected, 508 passed, six failed** (pouch
pickup, alarm restoration, nearest-player despawn assertion and three ventilation
solve timeouts). A fresh cross-domain filtered run passed **200/514**. These do not
establish baseline causes or make the full suite green. Full suite was not rerun
after narrow connected fixes. Failure artifacts retained; successful disposable
worlds removed. Tags select with OR, not isolation.

## NEXT WORK

Investigate full-suite failures separately. For a new feature, start with the
upstream consumer audit above, then reuse/extend the shared runtime only as needed.
Restart client and server after protocol/catalog changes. Owner acceptance does
not establish performance/player-capacity guarantees.
