# Living mouse carrying — active

## Target

Preserve one live mouse entity and its UUID, Mind, physiology and contents through
hands/storage, escape, throwing and disposal transit. Carrying is now explicitly
authorized; prior deferral in mouse interaction notes is superseded. AI mice that
escape should flee immediately afterward (requested Minecraft adaptation).

## Architecture and sequence

1. Extend existing hand/grid ownership with a typed **non-owning body-reference
   key**, not serialized mouse state. `LiveHands`/`GridContents` remain the slot
   ledger; `BodyContainmentService` remains the authoritative body-owner index.
   References must never be consumed or spawned as substitute loose creatures.
2. Implement hand pickup/drop and conserved containment transitions through
   existing targeting, authority and `LiveHandsCommitGateway` publication.
3. Extend storage transitions, presentation and ordinary save/load rebinding.
   Audit all stack consumers; unsupported operations must deny unchanged.
4. Reuse shared timed interactions for five-second inventory escape. Player
   movement requests escape; NPC attempts and post-escape fleeing are explicitly
   adapted. Fleeing must reuse the existing planner/motor and work budgets.
5. Extend existing throw/disposal entry paths to release/transfer the same body.
   Reuse `ExternalBodyFlight` and disposal's existing living-body containment,
   routing, air and exit handling—not item clones or a mouse-only transit engine.
6. Verify physiology and control while contained, and route live stomach contents
   through the shared reagent recipe engine. Reagent reactions currently support
   products and sounds; recipe-driven gas, heat and explosion effects are a
   separate missing chemistry capability, not implemented by this integration.

## Parity and invariants

Pinned upstream: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`MobMouse` inherits `SimpleMobBase` and `BaseMobAnimal`, composes Tiny `Item` and
`CanEscapeInventory` with a default five-second resist do-after. Native escape
is movement-triggered, hands-free, owner/removal-gated and interrupted by movement
or damage. Upstream's held "contested" branch adds no chance/stat contest.
Actor displacement and change in actor-to-owner separation each cancel above
0.3; the separate current-range/accessibility threshold is 1.5. A single committed
damage event of at least 1 cancels. Repeated input must not restart the operation.
Disposal admits supported item/mob entities.
Throwing is generic physics; do not invent mouse-specific impact damage.

- Consume resolved typed capabilities; no Mouse-class gameplay special cases.
- Never copy body NBT/health into a key or delete a body as an item transfer.
- Failures conserve the original body, reference and ownership.
- Loaded-only targeting/routing, existing interaction LOS, authority and
  prediction boundaries remain in force.
- Preserve no-hands mouse composition, player takeover, voice/emotes, NPC work
  budgets, appearance and ordinary lifecycle return/reconnect semantics.
- Ordinary `MouseMob` gains `MindControlledMob` through `MobMindHarnessOwnerMixin`
  at runtime. Its role lease uses the existing controlled world-step path; do not
  introduce a second ordinary-mouse input pipeline. Accepted intent sequences must
  reach the portable escape owner and shared timed snapshots.

## Parallel side tasks

Food-pile acquisition randomness and unpowered airlock lamp rendering are
separate fixes. Food has focused native evidence; the airlock planner has unit
evidence but connected-client visual acceptance remains pending.
Food range remains strictly below the configured 10-block cutoff; meals require
the existing one-block approach/admission path. Candidate acquisition does not
currently impose food-specific sight-ray admission at long range.

## Acceptance

Current focused evidence (not final feature acceptance): hand/body-grid/world-bag
custody passed the `living-carry` selector, latest **5/5**. Stomach recipe coverage
passed **1/843 selected**, with 15 focused stomach unit checks. Food-pile and
existing mouse-AI checks passed **25/842 selected**; airlock lamp planner tests
passed. Registry totals change as new fixtures are added.

Forced-flight focused validation passed **6/851 selected**; the adjacent movement
run was blocked by carry API compilation changes. World-storage review uncovered
an unsafe owner cast and an overrestrictive multi-body destination guard; fixes
and regression coverage are pending, so the earlier 5/5 is not final acceptance.
Faithful carried-body presentation,
normal cold rebinding, escape/fleeing, held throw and disposal entry wiring remain
pending. The offline-locator API has written but unrun tests and is not yet wired
to physical custody. Do not treat source-only modules as completed interactions.

Escape dispatch, resolved capability, bounded flee and input-sequence forwarding
exist in source; runtime fixtures are pending. Portable release review found
duplicated root mutation and non-conserving compensation paths. Those are blocking
until shared staged commit owners and failure-path tests establish conservation.
The existing generic timed HUD supports escape progress; no new presenter is
required, but committed owner and accepted sequence provenance need verification.

Latest targeted evidence: the two repaired escape fixtures passed **2/878**;
component/offline relocation/startup/flight-relocation JVM checks passed **21/21**.
The broader portable-escape selector selected **22/880** but aborted on the
carrier-before-body cold recovery assertion; it is not a passing selector.
Preserved logs: `build/portable-escape-22-final2.log` and
`build/portable-escape-jvm-final2.log`. New disposal fixture compilation and
carrier-first recovery are the current validation blockers. Throw/disposal
bridges and cold recovery have source changes but no accepted native result yet.

Subsequent focused evidence: cold recovery **7/881 selected passed** and original
body throwing **5/886 selected passed**. Disposal selected **1/886** but failed
fixture player admission with lifecycle startup `DEFERRED`; full-cycle acceptance
remains blocked. Appearance/model/locomotion focused CPU checks passed. An
independent reviewer opened the generated contact sheet and confirmed the 3×3
coat/state mapping and icon bounds; its header clips at the right edge. This is
source-frame CPU evidence, not GPU mesh/runtime proof. PNG:
`build/minecraft-junit/build/reports/living-carry-preview/mouse-appearance-contact-sheet.png`
(SHA-256 `25DF763E7DDB643E578E72714F1E66E790F4AB2AA2D2529686FA71D2F4DE5728`).

Before asking for owner manual review, publish an evidence-backed coding candidate
through `tooling/agent_workflow/workflow.py` under the current scoped development
workflow. Candidate recommendation, owner acceptance and release remain separate.

Require actual pickup/storage/drop conservation, escape then flee, throw landing,
disposal transit/exit, contained chemistry/physiology and controlled-body tests.
Run affected readiness selectors, focused fixtures, then consolidated full JVM
and native suites. Retain useful failed artifacts; no owner-world/configuration
changes, commits, pushes or sprint closure are authorized. No replacement JAR
until the selected features have passed acceptance.
