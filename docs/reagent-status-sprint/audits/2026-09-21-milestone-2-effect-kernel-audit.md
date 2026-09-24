# Milestone 2 Effect Kernel Audit and Completion Record

**Audit/completion date:** 2026-09-21
**Scope:** Ordered Milestone 2 only. This record does not claim Milestones 3–8, including metabolism, status, or faithful effect-behavior completion.

## Verdict

Milestone 2 is complete: the common effect kernel, condition layer, typed dispatch boundary, and deterministic gate behavior are implemented and tested. The pinned upstream behavior reference is commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Data and schema boundary

- `EffectData` retains all 34 variants as immutable, data-only records. Deferred `PlantMetabolismData` is also data-only; its plant execution API was removed. The flat JSON common-field shape remains (`conditions`, `probability`, `minscale`, and `scaling`), rather than introducing a nested common object.
- All eight `ConditionData` variants—`ReagentCondition`, `MobStateCondition`, `MetabolizerTypeCondition`, `TemperatureCondition`, `BreathingCondition`, `InternalsCondition`, `TagCondition`, and `HungerCondition`—use flat `inverted`, defaulting to `false`; ranged conditions default `min` to `0`. Strict per-variant schema validation rejects misplaced or unknown fields.

## Normative effect gate

`EffectSystem` follows the pinned order for each effect:

1. Reject when the **original** scale is below `minScale`.
2. Consume exactly one independent probability roll, including when `p >= 1`; probability is not multiplied by scale.
3. Evaluate conditions as an AND with short-circuiting.
4. When `scaling=false`, cap with `min(scale, 1)` without raising a partial scale.
5. Dispatch through the typed handler selected for the effect data type.

The gate uses the injected level `RandomSource`, never `Math.random`, and preserves skipped-result distinctions.

## Conditions and capabilities

`ConditionContext` is immutable, pure/world-independent, and exposes only explicit capabilities. `ConditionSystem` implements inclusive min/max ranges, exact mob state, ANY overlap for metabolizer types, reagent membership, and boolean capability values. A missing capability evaluates raw-false and is then inverted normally; it is never approximated as true. Current production supplies only fresh source-reagent quantities per metabolism effect. Mob state, metabolizer, temperature, hunger, breathing, internals, and tags remain explicitly unavailable rather than being approximated.

## Runtime boundary and support status

- `EffectContext` is immutable and server-only, with injected level `RandomSource` and `EffectCause`; `EffectResult` reports the outcome.
- `EffectDispatcher` is generic and typed, rejects duplicate registrations, warns once for unsupported effects, and isolates failures: each per-effect exception is logged and returned as `FAILED`, allowing later attempts to continue.
- Only the four retained baseline handlers are registered: `EvenHealthChange`, `HealthChange`, `Vomit`, and `Jitter`. They remain explicitly partial and are not Milestone 5 completion.
- `ModifyBleed`, `ModifyBloodLevel`, and the other no-op/unimplemented variants return `SKIPPED_UNSUPPORTED`. The harmful blood-damage substitutions are no longer reachable.
- `TickHooks` routes metabolism effects exclusively through `EffectSystem`; ordinary effect execution contains no `Math.random`.

## Validation record

Focused coverage passed: **14 effect-kernel tests** and **6 condition-layer tests**, including all 34 codec variants, schema/resource suites, source snapshots, inversion, inclusive ranges, gate order, independent randomness, scaling cap, unsupported handling, and observable short-circuit behavior. The added random tests cover `p=1`, `p>1`, and scale-independent probability.

The focused effect checks, focused reagent checks, `compileJava`, and the full test suite passed; the full test suite passed on rerun after the initial run. The read-only diff check passed. No manual launch was required or performed for implementation acceptance.

The final read-only review accepted Milestone 2 after removal of the plant execution API and addition of observable short-circuit plus `p=1`/`p>1`/scale-independent tests.

## Deferred work and residual risks

- Milestone 3 remains deferred: actual metabolism quantity, metabolite math, process caps, and complete context/source routing.
- Milestones 4–5 remain deferred: status core and faithful effect behavior. Only the four handlers above are partial.
- Real server/GameTest coverage for typed dispatch and exception continuation remains later work.
- The existing working tree must be preserved; this audit does not authorize staging, committing, resetting, or rewriting other changes.

**Changed file:** `docs/audits/2026-09-21-milestone-2-effect-kernel-audit.md`
**Diff check:** passed (read-only).
