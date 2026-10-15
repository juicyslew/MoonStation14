# Mouse carrying and shared character interactions

## STATUS

Closed by owner direction on 2026-10-14. Owner confirmed r47 working, then closed
the sprint and waived further manual testing of the final false “Unrevivable”
warning correction. That waiver is not a claim of connected visual verification.
The previously dispatched r48 technical gate also completed successfully.
Changes remain uncommitted and undeployed. No cleanup or owner-decision queue
handoff was performed; preserve existing unrelated dirty-tree changes.

## ARCHITECTURE

- Mice remain original living characters, not replacement inventory items.
  Portable inventory references are non-owning; custody/release preserves UUID,
  Mind/control, stomach chemistry and retained medical bodies. Shared services
  handle carrying, hand transfers, storage, drop, throwing and disposal transit.
- Escape uses the existing timed-interaction owner: five seconds, directional and
  hands-free, repeated input does not restart it. Relative displacement threshold
  is 0.3, range 1.5, single-event interrupt damage 1. Carrier movement/jumping,
  active-hand selection and same-holder physical hand transfer preserve progress.
  A real manual drop explicitly cancels the old attempt before same-tick pickup.
  AI escape releases the original mouse and immediately resumes fleeing.
- Relative motion samples pair the body with the root frame from its latest
  canonical containment pin. Transfer preflight validates old custody; after
  verified commit, refresh the operation’s custody from a **fresh** snapshot.
  Carrier/reference/token may remain unchanged while its release locator changes.
- Nominated pickup/pull use `BodyItemTargetProbe.probeSelectedGeometry`: exact live
  identity, nearest-AABB reach and loaded block LOS, not a fresh server aim-ray
  intersection. Pull retains resolved `Pullable`, authority, containment and hand
  reservation policy. Automatic loose-item candidates remain item-only/ray-based.
  Explicit eligible living nominations through legacy pickup use selected policy.
- Held-camera translation follows the holder’s rendered `xOld/yOld/zOld` with the
  same partial tick. Preserve native placement/orientation and detached zoom /
  collision behavior. Ordinary clicks, gameplay-window clicks and outlines share
  `BodyHandClient.resolvePrimaryWorldTarget`.
- Cursor drag presentation reconciles exact settled source token/item/count; it
  must not keep drawing a stale portable proxy after escape.

## IMPORTANT FILES

Java paths are below `src/main/java/com/juicyslew/moonstation14/`.

- `ms14/hands/live/`: `PortableBodyCarrying`, escape, release/drop and
  `BodyHandItemTransfer`; `ms14/loose_objects/pulling/BodyPullService.java`.
- `ms14/hands/client/`: shared target selection, cursor/workspace reconciliation;
  `HELD-CAMERA-MOTION.md`, `R43-CUSTODY-BREAK.md` document key invariants.
- `ms14/medical/HealthAnalyzerReadings.java`, `MedicalPortraitSnapshot.java`;
  `ms14/character/CharacterVisualState.java`, `CharacterVisualService.java`.
- `ms14/damage/DamageSystem.java`, `ms14/mobs/MobStateSystem.java`,
  `mixin/LivingEntityMedicalCorpseMixin.java`; barotrauma and explosion consumers.
- `ANALYZER-AND-TRANSFER.md`, `ANALYZER-GENERALIZATION.md`,
  `ANALYZER-SPECIES-ADAPTATION.md`, `CORPSE-DAMAGE.md`,
  `R44-NATIVE-CORRECTIONS.md` are focused supporting indexes.

## SS14 CORRESPONDENCE

Reference checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Mouse composition includes `CanEscapeInventory`; same-holder transfer does not
count as a world drop. Do not add random escape contests or a longer held timer.

Analyzer state uses resolved medical policy and `MobStateSystem.currentState`;
healthy default Alive need not have an attachment. Species uses inherited typed
`Species`, with humanoid fallback: identifying animals deliberately improves on
upstream’s non-humanoid Unknown label. Native renderer snapshots use one immutable
appearance capture for application and cache comparison. Custom Mouse/Human
harness own their visual state; shared native Pig/Villager/Sheep adapters handle
known variants. Sheep is intentionally host-bound by a posture-audio test profile.
No whole save-NBT or indiscriminate synced authority data is copied.

SS14 damage application does not reject dead `Damageable` entities. Physical
fire/temperature/pressure sources retain their component/exposure conditions;
respiration and bloodloss shutdown remain source-specific. Minecraft falling is
an adaptation, not a universal SS14 gravity mechanic. The native hurt mixin only
relaxes two dead checks for an already-retained medically Dead body, preserving
normal mitigation/cancellation/Post events and skipping repeated death/totem work.
First lethal hits still perform ordinary death. Read-only damage previews must
return an existing corpse ledger before considering living-only baseline import;
otherwise pressure caps incorrectly see zero damage after death.

“Unrevivable” is an upstream explicit `UnrevivableComponent.Analyzable` warning,
not Dead state or `MobThresholds.allow_revives`. The latter controls ordinary
healing transitions, not explicit revival. No equivalent permanent marker exists
locally, so r48 omits that warning. Defibrillators, rot and gibbing were not added;
absence of the warning does not promise a successful revival.

## DECISIONS / WHY

Dynamic contracts discover production character JSON and resolve inheritance,
rejecting missing species on concrete medical hosts. Native contracts enumerate
resolved host bindings of every namespace, reject missing factories / generic
appearance fallback, and map logical player presentation to the Human harness.
No fixed Human/Pig/Mouse allowlist or analyzer-specific mob branches.

Native validation needs fresh lifecycle isolation: reused test worlds can contain
valid OFFLINE reservations and correctly start DEFERRED. Do not weaken production
admission to bypass that. Power fixture tickets need the full one-block candidate
halo; a clean graph can still be UNKNOWN at an unloaded frontier. Preserve its
initial 12 kW phase before adding the second lamp. Bounded readiness evaluates the
inclusive deadline condition before timing out; callbacks run one tick later.

## VALIDATION

- r47: 3,332/3,332 full JVM; 932/932 full native; 165/165 UI/interaction/prediction
  readiness; controlled isolated client startup and packaged JAR. Owner reported
  everything working, except the subsequently corrected analyzer warning.
- r48: 39/39 focused JVM; 12/12 analyzer GameTests selected of 932; 136/136 UI
  readiness; packaged JAR. No fresh full-suite or connected final-warning claim.
- Bound reports: `build/agent-workflow/mouse-carrying-sprint/reports/`;
  artifact: `build/libs/moonstation14-0.0.1.jar`. r43/r47 JAR archives are under
  `build/agent-workflow/mouse-carrying-sprint/artifacts/`.

## KNOWN GAPS / NEXT WORK

Residual catch misses and continuous-jump observer landing / remote-release glide
presentation are optional unresolved issues; do not promise perfect prediction.
New visual owners must implement their snapshot once. Contracts catch omission,
not arbitrary future renderer bugs or third-party appearance variants. No further
work is queued by this closure; start future medical revival work from canonical
damage/state systems and pinned defibrillator rules, not this warning boolean.
