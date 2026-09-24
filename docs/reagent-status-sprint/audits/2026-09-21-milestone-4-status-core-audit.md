# Milestone 4 Status Core Audit and Completion Record

**Audit/completion date:** 2026-09-21  
**Scope:** `Instructions.md` Ordered Milestone 4 status core only. This record does not claim Milestone 5 effect behavior.

## Verdict

Milestone 4 is accepted. The status storage, reduction, lifecycle, persistence/network, definition, reference-validation, and dedicated-server test boundaries are implemented and verified. Acceptance is limited to Milestone 4.

## Accepted implementation summary

- Status state is an immutable typed integer-tick `StatusEffectInstance`: pending or active, with finite or permanent duration. The pure `StatusEffectReducer` implements `Update`, `Add`, `Remove`, and `Set`, including maximum/accumulating/subtractive/exact semantics, earliest pending start, and explicit permanent removal.
- The existing attachment/component/provider architecture is preserved through `StatusEffectAttachment`, `StatusEffectComponent`, `MS14Bridges.STATUS_EFFECT`, and `MS14Provider`. Maps and queries are defensively copied/read-only; JSON/NBT/network persistence maps are bounded.
- Lifecycle hooks are centralized as `canApply`, `onApplied`, `onChanged`, `ensureApplied`, and `onRemoved`. Exact one-tick pending activation and finite expiry are covered. The living-entity tick phases run status advancement before reagent metabolism.
- Synchronization is transition-only: unchanged reductions and countdown-only ticks do not request provider updates; creation, change, activation, expiry, removal, and invalidation do.
- JSON/NBT/network persistence is strict and bounded, with no legacy float migration. Old float-map data, malformed fields, invalid states, and invalid integer values are rejected. Status definitions and references validate transactionally, so a failed candidate does not replace the published catalogs.
- The resource/reference gate contains 16 status definitions; all 54 `ModifyStatusEffect` references and their 15 status IDs resolve.

## Runtime and test evidence

The dedicated GameTest server passed **5/5** `StatusEffectGameTests`. Each test uses the correct relative spawn position and the real entity `tickCount` as proof that server ticks occurred:

1. An unaffected tick leaves no attachment; removing an absent status is unchanged and remains absent.
2. A delayed finite status follows the exact pending, activation, countdown, and expiry sequence.
3. A permanent status is removed explicitly and does not reappear on the following tick.
4. A missing definition invalidates and removes the authoritative attachment state.
5. An entity NBT save/load round trip preserves the pending status attachment.

Dedicated GameTest server classloading succeeded while running these tests. Focused status tests passed, `compileJava` passed, `runGameTestServer` passed 5/5, and `build --no-daemon` passed including all JUnit tests. `git diff --check` passed with only the repository's existing CRLF warnings.

## Deferred and manual risks

- Milestone 5 owns faithful `Jitter`, `Drunk`, `ModifyStatusEffect`, and `GenericStatusEffect` behavior; none of those behaviors is claimed complete here. Their transient attribute projection/cleanup/rehydration, player-death behavior, and server-only supported-effect execution GameTests remain M5 work and are deferred.
- Actual network packet counting is not instrumented. No-spam is proven by the transition-report/provider-update structure and unit tests, not by a packet counter.
- Missing-definition invalidation removes authoritative attachment state. Future external projections require definition-independent cleanup.
- The GameTest log contains an existing unrelated `reagent_container` loot-table/data-component warning.
- No manual client/server launch was performed: `Instructions.md` prohibits manual launches, and the dedicated GameTest server was the required automated run and passed.

**Changed file:** `docs/audits/2026-09-21-milestone-4-status-core-audit.md`
