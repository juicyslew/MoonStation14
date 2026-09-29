# Blood and bleeding — solution extension contract

## Status and relationship to the v1 documents

This document is the ordered extension contract and current implementation record for replacing the sprint's **provisional scalar v1** blood model with a living, composition-aware bloodstream solution. It supersedes scalar-volume assumptions in [`Instructions.md`](Instructions.md) and [`architecture.md`](architecture.md) only where this contract explicitly extends or contradicts them. Those files remain historical records of the v1 implementation and are not to be silently rewritten as if solution physiology had already shipped.

The implementation now has a solution-backed bloodstream; the older scalar-only status below is obsolete. The gate ledger at the end of this document and [`current-status.md`](current-status.md) distinguish implemented/tested scope from incomplete acceptance. Preserve the prototype-owned eligibility boundary: no host/species checks or gameplay tuning hardcoded in Java.

## Contract vocabulary and compartment ownership

- **Living bloodstream:** exactly one authoritative, living reagent mixture per eligible living entity. This solution is the sole authority for circulating blood volume, composition, usable fraction, regulation, and bleed removal. Do not attach a generic `REAGENT` solution to living entities as a second bloodstream representation.
- **Generic `REAGENT`:** retain for ordinary solution containers and solution-bearing items, jugs, and puddles. These are normal solution containers and may use ordinary transfer/spill semantics. A puddle is not the living bloodstream and is not required for the first solution-backed implementation.
- **`STOMACH`:** remains a separate compartment with its own solution and metabolism lifecycle. Do not merge stomach contents into bloodstream composition or use stomach ownership as a substitute for blood.
- **`LUNGS`:** gas/exposure is a future, separate, prototype-configured compartment/prototype. Do not model gas as blood reagents, pretend an absent lungs compartment exists, or add a Java-only gas fallback as part of this contract.

The intended ownership is thus one bloodstream solution for living circulation, separate ordinary-container solutions for `REAGENT`, and separate stomach/lung compartments. Transfers between these domains require explicit future behavior; they must not arise from sharing or aliasing one mutable solution object.

## Prototype policy contract

Every eligible character prototype must explicitly provide the data needed to construct and operate its bloodstream. Extend the existing optional character `blood` policy; absence continues to mean that the entity is not enrolled and gets no living bloodstream. A present policy must strictly validate, reject unknown fields, and contain at least:

The strict v2 contract requires `reference_solution` (a nonempty map from canonical, namespaced reagent prototype ID to finite positive volume in increments of 0.01) and `max_volume_modifier` (finite and at least 1). `reference_solution` is the sole prototype authority for initial composition and its total; the cap is exactly `sum(reference_solution) * max_volume_modifier`. There are no `initial_volume` or `max_volume` policy fields: the strict audit rejects either legacy scalar with its JSON path. Human uses registered `moonstation14:blood` (reference total 300, modifier 2.0, cap 600); pig uses distinct registered `moonstation14:sulfurblood` (reference total 3, exact modifier 2.0, cap 6). The pig cap is therefore 6 rather than the prior scalar prototype cap of 4; the exact representable modifier is preferred over an approximate 1.33333333. No parallel scalar cap/initial tuning is retained. Runtime blood behavior is unchanged by this schema gate.

1. A **required blood reference composition**: a canonical reagent-to-amount mixture describing that character policy's reference blood composition. It must be nonempty, finite, nonnegative, and valid against registered reagent prototypes; define and validate a positive reference amount so normalization is well-defined. It is configuration, not a runtime solution or hidden default.
2. A **required maximum-volume modifier** with an explicit finite, positive interpretation against the reference amount. This determines the configured maximum capacity; it is not an implementation-wide capacity constant.
3. Explicit reference composition (which defines the initial fill), modifier-derived capacity, refresh policy, bleed policy, and metabolism exclusions. Initial bleed on the one-time scalar migration is zero; there is no separate prototype initial-bleed field. Define units and exact arithmetic in schema documentation/tests. A migration from scalar saved state must never silently reinterpret scalar units as reagent quantities.
4. Explicit regulation parameters and any per-type damage-to-bleed parameters that remain supported. Values affecting gameplay come from prototypes, with strict range and cross-field validation; no code fallback, default species, hardcoded host, or universal tuning.
5. Explicit metabolism exclusions for blood constituents that must not be consumed as ordinary circulating metabolism. Validate reagent identifiers and exclusion semantics; an omitted required exclusion set is not silently filled by code.

Provide at least **two distinct reference compositions** in prototype fixtures and acceptance data. Tests must prove the resulting living mixtures remain distinct and that both are selected through existing character identity/host mapping. These are test fixtures, not fixed production host assignments. Never branch on entity class, species name, or a hardcoded host list to choose a composition.

### Composition-derived usable fraction

For each configured reference component `r` with a positive reference amount, derive its component fraction as `current(r) / reference(r)`, clamped to `[0, 1]`. The living bloodstream usable fraction is the minimum of those component fractions:

```text
usableFraction = min over configured reference components r of
                  clamp(current(r) / reference(r), 0, 1)
```

Components absent from current mixture count as zero. Extra current reagents not listed in the reference composition do not increase usable fraction. Reject invalid/empty reference composition rather than inventing a denominator. Bloodloss threshold/regulation uses this composition-derived fraction, not scalar current-volume divided by a scalar cap. Tests cover a limiting component, a missing component, excess of a non-limiting component, clamping, and distinct compositions.

## Runtime invariants and migration rule

### Milestone C implementation note

The scalar v1 authority has been removed from runtime state: `BLOOD` now stores only
`bleed_rate` and an `initialized` migration marker, while `BLOODSTREAM` owns all
reagent quantities. A legacy BLOOD codec object is still decodable; its old
`current_volume` is ignored, and its bleed value is discarded when the marker is
false. Enrollment resolves and validates every configured reagent. If a bloodstream
attachment already exists, it is adopted as-is, even when empty; reference contents
are created only when no stream attachment exists. The initialized marker is then
persisted. A conflict between populated `REAGENT` and `BLOODSTREAM`
remains fail-closed through `BloodstreamStorage`. A populated bloodstream is not
reinitialized after depletion because the marker remains persisted. No policy or
invalid reagent set means no migration. Initial bleed on migration is explicitly
zero; this is the requested migration rule rather than a Java fallback for an
otherwise unspecified tuning value.

The migration ordering is intentionally stricter than the initial proposal: policy
and every referenced reagent are validated before generic `REAGENT` migration can
mutate either attachment. If a `BLOODSTREAM` attachment already exists (including
an empty one), it is authoritative and is adopted without adding reference contents.
If none exists, a complete reference solution is staged and attached before the
initialized marker is committed. Therefore a retry after solution commit but before
marker commit adopts that stream instead of adding the reference a second time.
An adopted empty stream remains empty; initialization is not a refill operation.

Runtime reduction now computes usability as the minimum reference-constituent
fraction, restores deficient reference constituents up to reference quantities
within the modifier-derived capacity, and removes the actual mixture proportionally
for bleed. Bleed removal is bounded to the available solution and the configured
bleed rate for that update; the returned per-reagent cent mixture is intentionally
discarded at the end of that update. Puddle/output routing is deferred, so there is
no retained spill pool and no generic `REAGENT` attachment on living entities.
Conservation is accounted as starting solution = final bloodstream + discarded
mixture; the discarded portion is not accumulated or recreated. Extra constituents
remain in the mixture and do not raise usability.
Bloodloss damage/recovery uses that derived fraction. Blood-level effects regulate
the mixture and bleed effects update only persisted bleed metadata.

Metabolism exclusions are now required as explicit character blood policy and every
reference constituent must appear in that list. The authoritative bloodstream tick
passes those exclusions to metabolism; stomach processing deliberately passes no
exclusions, so the same reagent remains metabolizable in the separate stomach. The
registered reagent check remains part of server blood enrollment/reconciliation.
The migration and living-solution paths have server GameTest coverage, including legacy generic-solution migration, identical-store retry deduplication, conflicting-store fail-closed behavior, empty-stream persistence by codec, effects, and actual bleed removal. These GameTests and NBT/codec checks do not exercise a world save/restart or live datapack reload. Normal candidate reload validation is covered separately; direct `PrototypeManager` publication is a test/programmatic seam and does not itself run the normal reload listener's cross-catalog validation.

### Current gate ledger (2026-09-28)

- **Gate 1 — schema/references/composition: implemented and unit-tested.** Strict v2 policy, registered reagent cross-catalog validation on the normal reload path, distinct human/pig compositions, exact modifier-derived caps, and composition-derived usable fraction are present. Direct manager publication can bypass the normal listener's candidate validation, so do not treat that seam as equivalent to datapack reload validation.
- **Gate 2 — living routing/migration/persistence: implementation and GameTest/codec coverage present; world lifecycle acceptance incomplete.** `BLOODSTREAM` is authoritative and generic living `REAGENT` storage migrates only for an eligible policy. Matching populated stores deduplicate; differing populated stores are preserved and fail closed. Empty streams remain persisted and are not refilled. World save/restart is untested.
- **Gate 3 — solution physiology/effects: implemented with bounded scope and tested, but not full parity.** Refresh, proportional actual-mixture bleed removal, composition-based regulation, explicit metabolism exclusions, and blood reagent effects operate on bloodstream semantics. Removed mixture is bounded to available material and is discarded without puddle routing. Runtime injection is absent; full solution spillage/puddles and lungs are absent.
- **Gate 4 — integrated closeout: incomplete.** Automated build and GameTest coverage exist, but live datapack reload, world save/restart, multiplayer/performance, injection, full spill/puddle behavior, and manual gameplay acceptance remain unverified or unimplemented. See [`current-status.md`](current-status.md) for the latest run and its unrelated flaky GameTest failure.

1. **One authority:** for a migrated/configured living entity, the bloodstream solution is authoritative. The old `current_volume`/`bleed_rate` attachment must not continue to drive ticks, effects, or be recomputed alongside the solution. Do not retain scalar volume as a second mutable blood authority.
2. **Initialization:** on first valid enrollment, construct exactly one bloodstream from the resolved prototype reference composition and explicitly configured starting/capacity semantics. No initialization for missing/unresolved policy, clients, or unsupported targets. Reconciliation must not refill an existing solution merely because the entity joined or the prototype reloaded.
3. **Persist and sync:** persist and synchronize the authoritative solution and any necessary bleed/cadence state using established attachment/component conventions. Define copy/death-clone behavior explicitly. Solution contents and composition must survive encode/decode without loss, duplication, or scalar re-derivation.
4. **Legacy scalar migration (required rule):** under a valid new policy, validate policy and references before touching legacy generic solution storage. If no bloodstream attachment exists, initialize it once from the policy reference; if one exists, adopt it exactly, even when empty. Discard legacy `current_volume` as authority and discard legacy `bleed_rate`, initializing bleed to zero. Commit the initialized marker only after the authoritative solution is committed. This reset/adoption rule is deliberate: v1 scalar values have no composition and cannot be safely converted into reagent quantities, while an already persisted solution must not be overwritten or duplicated. The operation is retry-idempotent across a failure between solution and marker writes. A failed/missing/invalid policy must leave all stores untouched and behavior disabled until migration can succeed. Never retain or consult scalar values after successful migration, and never operate both authorities during migration.
5. **Policy reload/removal:** valid changed policy reconciles the existing solution to explicit new capacity/reference rules without silently recreating it. A changed policy whose capacity is below the already-saved mixture fails closed: retain the complete mixture, do not clamp/delete reagents, and suspend ticks, effects, and admissions until a corrected policy can contain it. Transfers into living targets require a successfully initialized, policy-eligible bloodstream and use that policy capacity (never the generic trait capacity). Removed/invalid policy stops simulation, effects, and routing while preserving saved data; invalid configuration is surfaced through the normal validation boundary. State restoration on re-add follows the explicit migration/reconciliation rule, not an implicit refill.

## Ordered stages and acceptance gates

Each stage is a separate gate. Use the ledger above for current status; a listed implementation or unit/GameTest check does not pass untested integration requirements. Stage tests should use data-driven arbitrary hosts, and must not encode production hosts or tuning into test-only code paths.

### Gate 1 — schema, references, and independent composition tests

- Define strict character JSON schema/codec/audit for required reference composition, maximum-volume modifier/capacity, initial-fill, refresh, bleed, regulation, and metabolism exclusions.
- Validate reagent keys against registered reagent prototypes, amounts/modifiers/ranges, nonempty reference composition, positive denominators, and cross-field constraints. Preserve character-policy identity/host mapping and collision rejection.
- Add at least two distinct composition prototypes/fixtures selected only by explicit JSON mapping. Unit/schema tests independently prove strict decoding, unknown/missing/wrong-type/non-finite/negative values, unknown reagents, invalid reference amounts, bad capacity/modifier, exclusions, and two-composition distinction. Add pure tests for the minimum-component usable-fraction formula and its boundaries.
- **Pass condition:** schema and composition math tests pass without creating a living solution or changing runtime blood routing. This gate is independent of legacy migration and scalar replacement.

### Gate 2 — living-solution routing, persistence, and legacy migration

- Add the dedicated living bloodstream ownership/routing path and codec/synchronization. Ordinary `REAGENT` containers remain ordinary containers; `STOMACH` remains independent; do not invent or fake `LUNGS`.
- Implement the explicit, versioned scalar-save migration rule from the invariants above. Include pre-migration, post-migration, repeated migration, restart/codec, missing policy, changed policy, and invalid policy cases.
- Prove one and only one authoritative live bloodstream solution after enrollment/migration; verify no generic `REAGENT` attachment is also being used as living blood and no scalar tick/effect path remains authoritative.
- **Pass condition:** automated lifecycle/codec and migration tests demonstrate deterministic, persisted, idempotent routing and no double authority. Live save/restart integration should be distinguished from unit/codec coverage.

### Gate 3 — replace scalar physiology and audit reagent effects

- Replace—not layer over—the scalar blood arithmetic with solution-backed refresh, composition-based usable fraction/regulation, and bleed-out that removes the configured/actual constituent solution from the living bloodstream. The removed material must be an actual solution derived from current composition; do not synthesize a generic scalar loss or claim volume removed if mixture removal failed.
- Bound removal by available solution and configured bleed policy. Bound any spill/output by explicit policy and solution-container limits. The first implementation may defer creation/routing of puddles; if a spill is not materialized, document/test its bounded deferred/discard behavior rather than creating an unbounded or hidden pool. Generic item/jug/puddle solution transfer remains ordinary-container behavior.
- Preserve prototype-owned typed damage/bleed policy and server-authoritative event semantics. Exclude bloodloss-generated damage from feeding back into bleed accumulation. Use the component-derived usable fraction in regulation.
- Audit every injection/solution-add path. There is currently no runtime injection behavior to assume: do not claim injection support or add a fake path. If injection is introduced later, require a separately specified, tested audit of reagent compatibility, capacity/overflow, provenance/policy, and authority before accepting it.
- Audit blood reagent metabolism separately from generic metabolism and enforce configured exclusions. Do not allow reference blood constituents to be consumed accidentally by generic metabolism.
- Make both blood reagent effects (`ModifyBleed`, `ModifyBloodLevel`) operate on the authoritative living bloodstream semantics, including explicit unsupported results for ineligible targets and single application of effect scale. Define how each effect maps to solution/bleed policy; scalar-only mutation is not an acceptable post-migration behavior.
- **Pass condition:** reducer/system and integration tests prove actual solution composition changes for refresh and bleed removal, composition-based regulation, caps, metabolism exclusions, no feedback, effect behavior, and no scalar fallback/double mutation.

### Gate 4 — integrated automated acceptance and closeout

- Add integration coverage for both distinct composition policies, arbitrary prototype host mapping, initialization, solution persistence, legacy migration idempotence, reload/clamp/preserve rules, metabolism exclusions, regulation, bleed removal, bounded spill behavior, reagent effects, and server/client boundaries.
- Validate registered prototype references and all fixtures. Include negative tests for absent policy, malformed policy, unresolved identity, empty/invalid solution, full depletion, missing reference components, capacity boundaries, failed transfer/removal, and invalid migration input.
- Report exactly which checks are unit/schema, codec, GameTest/server integration, or manual. Automated `GameTestServer` results do not prove a world save/restart or live datapack reload unless those exact integration paths were exercised.
- **Pass condition:** required automated checks pass and the completion report explicitly identifies remaining manual, restart/reload, connected gameplay, and performance gaps. No manual game launch is part of this contract; the owner handles manual/connected acceptance separately.

## Explicitly deferred scope and risk boundary

This extension does not authorize full SS14 forensics/DNA, blood typing/crossmatch/transfusion reactions, complete transfusion/injection gameplay, wound visuals/alerts/sounds, medical UI/examination, broad disease/chemistry redesign, puddle forensics or full puddle interaction, blood pressure/oxygenation/respiration, organs/bone marrow, or connected gameplay acceptance. `LUNGS` gas/exposure remains a future prototype-configured compartment, not an approximation implemented by blood. Any later scope must define its own ownership, prototype schema, migration, and tests.

No manual game run is required or implied by passing automated gates. Report open risks plainly: scalar-save conversion semantics, live world restart/reload coverage, spill/puddle behavior, injection absence, container limits, and any deferred SS14 parity. Never describe solution-backed local behavior as full upstream parity without tests and implementation for the claimed feature.
