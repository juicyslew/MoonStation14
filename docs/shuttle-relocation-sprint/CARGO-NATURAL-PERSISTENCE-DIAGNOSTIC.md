# M1 — observe natural cargo persistence after R39

## Question and constraints

R39 proved nonzero flight at actual frame/body/cargo unload, but all three jug
UUIDs remained absent during reload. Do not change cargo physics, restore/rebind
entities, repair ledgers, weaken identity/content/local-position checks or infer
a cause from post-failure region files. The next diagnostic reuses the same
default-two, one upward20m/s launch and actual-unload/reload fixture.

Observe the **natural calls to existing local loose-item save/load overrides**,
not a new Sable/vanilla/physics mixin or engine hook. Never manually invoke
`save`, `saveWithoutId`, `load`, deserialization or entity reconstruction to
manufacture a witness. Reading a naturally supplied tag is not evidence that its
storage write completed; observing a natural load is not proof of admission.

## Reuse and observation boundary

- Extend the existing bounded diagnostic owner
  `entities/LooseMotionDiagnostics.java` if the persistence watch can remain
  small and isolated from its existing motion watches. A separate narrow helper
  is justified only if it avoids mixing independent lifecycle/ownership state;
  do not add a general tracing/event framework or another cargo owner.
- Add inert-by-default observation calls at the **end** of existing
  `LooseItemEntity.addAdditionalSaveData` and `readAdditionalSaveData`, after the
  existing superclass and local operations. These are owned local serialization
  methods, not new engine/native/physics hooks. Never mutate the supplied tag,
  item stack, position, velocity, frame tracking or removal state.
- Register only the existing fixture's exact three jug UUIDs and exact level,
  before the ordinary launch/save window; use a bounded event budget and explicit
  terminal close on both success and failure. No global always-on item logging,
  operator/owner configuration or command is needed. Existing motion watches
  must behave unchanged. Closing a stale registration must not clear another.
- Reuse SDK/current local diagnostic helpers for snapshot data where appropriate,
  but do not call world queries or native methods from a possibly asynchronous
  save/load callback. Capture bounded immutable values only. Do not retain
  entity/world/tag references in events or expose mutable payloads to consumers.
  Snapshot capture must be thread-safe and must not let diagnostic failures
  alter ordinary serialization. Avoid per-unwatched-entity allocations.

## Required witnesses

**Source correction:** the investigated public `mahtomedi/minecraft` mirror
identifies itself as1.20.3-pre1 (world version3693), not1.21.1. A data-version
number in entity data cannot establish the source revision. Do not use that
mirror's entity-manager predicates or serializer order as verified1.21.1 facts.
Exact installed `Entity.shouldBeSaved()` semantics and base-tag write ordering
remain source-unverified. Typed runtime eligibility observations and actual
naturally supplied tag fields must be reported as such, not inherited-version
parity claims. This does not justify accessing either denied artifact/cache.

At existing cargo readiness/release, log each UUID, ordinary `shouldBeSaved()`
result, passenger/removal status, nonempty authoritative stack, world position,
world chunk, stable storage point and known frame UUID. Distinguish observed
runtime eligibility from any source inference. Existing original cargo token
and exact water amounts500/600/700 cents remain asserted.

For each natural save/load call record UUID, phase, monotonic/event sequence,
current position/velocity and relevant supplied fields: `Pos`, `Motion`, `Item`
including reagent contents, `LooseSettled`, `LooseFrameOrientation`, and
`LooseFrameStoragePoint`. Preserve absence/type information rather than quietly
turning missing fields into zero. A small bounded representation of the three
known fixture stacks is sufficient; never copy/log an entire frame/block payload.
Record natural join/admission observation through the existing loose-item join
diagnostic listener if it helps distinguish read from admission; do not change
event cancellation or lookup behavior.

Print bounded summaries at actual absence, before reload and timeout/success:
per-UUID save/read/join counts, overflow and complete coverage. Save callbacks
must be correlated with release/absence and subsequent unload; a prior live
save alone is not proof of the final unload write. Explicitly report any missing
coverage or budget exhaustion. Full evidence stays in logs; the concise failure
message must remain small enough for Minecraft's GameTest failure book.

## Interpretation and validation

No save callback narrows the question to eligibility/section ownership/save
invocation, but does not prove one particular predicate caused the omission.
A save callback with `Pos`/`Motion` proves serializer invocation and its fields,
not disk commit. A natural read with an empty/discarded stack or a join event
followed by absence narrows load/admission/early-tick behavior. Do not guess a
coordinate fix or load entities from the observer.

Cheap tests must cover inert/unmatched scope, UUID/level filtering, bounded
counts/overflow, closure/stale close, immutable snapshots, malformed/missing tag
fields and save/load phase capture. Compile ordinary typed APIs; no denied cache
or artifact inspection. Independent review must check that observation cannot
mutate gameplay or supply an alternate persistence source of truth.

No new native attempt is authorized merely by writing this design. After
implementation and review, submit one fresh diagnostic plan using a never-used
directory (R40 is the intended next revision), retaining R38 and R39 intact.
Native execution remains lead-owned; author must not prepare the target.
Production/default remains2; M1 is unaccepted and M2–M6 remain paused.

## Author candidate

Implementation extends `LooseMotionDiagnostics` with one exclusive persistence
watch, exact scope and1–3 UUIDs, a96-event budget, immutable events and typed
per-UUID counts including zeros. Existing local save/read overrides have inert
observation calls at their ends; the existing diagnostic join listener observes
the same scope. Only the flight fixture registers the watch. Terminal fixture
cleanup closes it; stale close cannot clear a replacement watch. Existing motion
watch behavior and all cargo/body assertions are unchanged.

Author `compileGameTestJava` and focused JVM checks passed: **4 watch +5 payload
=9 tests**, zero failures/errors/skips. The existing compiled-tag preflight
selects1/895. Strict preparer Python14/14 passed. R40's exact default-two target
was confirmed absent; R38's abandoned config and R39's failed world remain
intact. No author preparation or native execution occurred. The fresh draft
`GATE-PLAN-08-CARGO-NATURAL-SAVE-R40.json` binds compilation, lead-only preparation
and one instrumented native diagnosis after independent review, not readiness.
Independent source/reuse/plan review passed, including observation-only behavior,
exact filtering, bounded immutable snapshots, explicit incomplete coverage,
terminal scope cleanup and preservation of existing assertions. Live natural
callbacks, cargo reconstruction and shutdown behavior remain unverified.
