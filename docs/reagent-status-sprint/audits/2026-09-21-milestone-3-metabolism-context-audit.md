# Milestone 3 Metabolism Context Audit and Completion Record

**Audit/completion date:** 2026-09-21  
**Scope:** Ordered Milestone 3 only. This record does not claim Milestones 4–8, including status work or the deferred effect-behavior milestones.

## Normative references

- [`Instructions.md`](../../Instructions.md), especially the pinned upstream execution contract and the Milestone 3 acceptance gate.
- Pinned SS14 checkout commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Verdict

Milestone 3 is accepted. Metabolism consumption, stage ordering, capacity accounting, metabolite production, effect context, and TickHooks integration satisfy the Milestone 3 boundary. Shared compartments are not claimed as faithful anatomy.

## Accepted implementation summary

- Metabolism uses a closed four-stage model with explicit `SHARED_LIVING` order: `RESPIRATION`, `DIGESTION`, `BLOODSTREAM`, `METABOLITES`. The process cap is 2 per stage.
- `ReagentData` uses a typed metabolism map. Absent metabolisms decode as empty; no synthetic default metabolism is created.
- Production contains 386 total stage definitions: 223 digestion, 148 bloodstream, 9 respiration, and 6 metabolites. Thirty-nine prototypes have empty metabolisms.
- Rates are strictly finite and positive. Metabolite ratios are finite and nonnegative.
- `removeUpTo` is the actual removal authority. Effect scale uses `actualRemoved / rate`, and production uses `actualRemoved * ratio`.
- Capacity accounting is transactional and safe: requested, retained, and excess quantities are tracked explicitly, while pre-existing quantities are preserved.
- Candidates use canonical sorting followed by injected Fisher–Yates shuffling. Each stage takes a positive stage-start candidate snapshot, rereads live quantities, resets its per-stage cap, isolates current-stage work, and makes later-stage products available.
- The callback runs after ordinary removal and before product addition. Product addition is attempted even when an arbitrary callback fails, with primary and suppressed exceptions preserved. Ordinary effect `RuntimeException`s become `EffectResult.FAILED` in `EffectSystem`, so later effects and products continue.
- `ReagentEffectContext` is typed and includes the source `TraitHandler`, live source solution, reagent key, stage, profile, immutable pre-removal snapshot, and explicit optional organ. The current shared model leaves organ and metabolizer-type capability unavailable.
- Fresh per-effect reagent condition views restore pre-removal visibility and incorporate mutations made since the post-removal baseline.
- `TickHooks` uses the existing living-entity `IReagentTrait` capacity, the server `RandomSource`, a 20-tick cadence, and provider update. The old stale copied-map/fixed-rate loop and unused cap constant were removed.

## Validation record

The following validation passed:

- Focused metabolism tests.
- Focused effect tests.
- `.\gradlew.bat test --rerun-tasks --no-daemon`
- `.\gradlew.bat build --no-daemon`
- `.\gradlew.bat compileJava --no-daemon`
- `git diff --check` — line-ending warnings only; no whitespace errors.
- Final read-only review: **PASS**, with no high- or medium-severity findings.

No manual client/server launch and no GameTests were performed; neither is required for this Milestone 3 acceptance.

### Post-acceptance follow-up

- The zero-total `naiveRemove`/Vomit risk is resolved: `IClampedMapHolder.naiveRemove` now validates transactionally and safely returns an empty removal while trimming zero entries for zero-total sources. Focused `IClampedMapHolderNaiveRemoveTest` regression coverage includes the Vomit precondition. Full Vomit physiology remains partial and is deferred to later effect work.

## Residual risks and deferred boundaries

- The shared compartment is not faithful anatomy; organ and metabolizer-type capabilities remain explicitly unavailable.
- There is no end-to-end Minecraft TickHooks GameTest. Behavior proof is kernel/unit coverage plus structural integration review.
- An arbitrary callback `RuntimeException`/`Error` is a programming boundary and propagates after products are attempted; this is distinct from an ordinary handler returning `FAILED`.
- **Milestone 5 prerequisite:** Before `AdjustReagent` or any new source-solution-mutating handler is accepted, direct `ReagentAttachment` map mutation must be encapsulated behind validated mutation APIs/read-only queries, with explicit synchronization ownership defined. This is intentionally not an M3 architecture rewrite; if it changes established provider/bridge architecture, the owner approval gate in `Instructions.md` applies. Milestone 8 must verify there are no bypassing mutation callers and synchronization remains correct.
- Status, exposure, and effect expansion in Milestones 4–8 remains deferred.

**Changed file:** `docs/audits/2026-09-21-milestone-3-metabolism-context-audit.md`
