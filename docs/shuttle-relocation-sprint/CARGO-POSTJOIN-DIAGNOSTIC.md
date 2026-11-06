# M1 — follow reconstructed cargo through its first ticks

## Established boundary

R40's three jugs naturally saved, read nonempty and reached JOIN while not
removed, with correct same-frame metadata and complete observer coverage. All
three were absent by the reload timeout. No positive coordinate mismatch,
empty-item rejection or never-saved/never-loaded cause was found. Read
`CARGO-NATURAL-SAVE-R40-RESULT.md` first; retain its failed world/evidence.

The next diagnostic observes the interval **after JOIN**, not another speculative
cargo fix. Reuse the current default-two one20m/s upward launch, real logout,
nonzero saved velocity at actual unload, quiet phase and ordinary reload harness.
Preserve all original UUID/Mind/hands/held-token/full-stack/water, settled/deck
support and `.12` local-position assertions. Production/default remains2; M1 is
unaccepted, M2–M6 paused.

## Architecture and observations

Extend the existing opt-in `LooseMotionDiagnostics.PersistenceWatch`, not another
trace registry. The existing join listener creates a bounded motion `Watch` with
the entity reference. On its existing server Post tick, capture the watched
post-JOIN state **before** the removed/unloaded guard drops that reference. This
can observe removal even when UUID lookup is already null; no removal engine
hook is needed. Do not trace original pre-unload entities before their JOIN was
observed by the active persistence watch.

Record current position/own velocity, game/entity ticks, settled/onGround,
removed/reason, UUID lookup relation, nonempty stack/reagents, exact native
tracking owner and loaded-only actor point/frame/storage point. Query world and
frame state only on that existing server-thread observation, never from possibly
asynchronous natural save/read callbacks. Unknown/removed frame state is a
witness, not permission to assign tracking or reposition an entity.

At the existing local jug impact gate, observe its actual requested/accepted own
travel, total approach speed, resolved threshold and eligibility, including
genuine clipped candidates rejected below threshold. Absence of an admitted
break event must not be mistaken for absence of any clipped impact. At existing `breakJug`
outcomes observe no-reagent/no-loaded-surface or actual spill remainder/landing
before the original discard/partial-retention branch. Do not change the gate,
call spill twice, add collision queries, alter data or hook engine/native physics.
These are inert read-only calls within the owned local implementation; no new
Sable/vanilla/physics mixin, access transformer, callback or binary change.

All events remain immutable bounded values without retained entity/world/tag
references. Registry scope stays one exact level/three UUIDs and closes on
fixture termination; existing motion behavior is unchanged. Budget grows from
R40's96 to768 events: three UUIDs over the existing200-tick reload window need
up to603 per-tick observations, plus serialization/join and impact/spill events.
Per-tick records contain bounded values, not repeated full NBT payloads.
Overflow, truncated/capture-error fields or loss of a watched join reference
must mark coverage incomplete. Typed counts derive from captured events; no
string-parsed counters or alternate gameplay source of truth.

A captured removed-object event is a legitimate terminal witness. Stopping
because the object changed level or its chunk became unavailable without such
a terminal observation is coverage loss, not successful completion.

## Interpretation and validation

Frame support loss followed by a genuine impact/spill is a hypothesis. A
removed/discarded object alone is not proof that branch ran. Exact local gate
and spill observations discriminate it from another unload/admission/removal
path. Do not widen collision tolerance, jug threshold, support acceptance or
restore/rebind cargo to make this pass. Observe first, then select a causal fix.

Focused tests must cover post-JOIN gating, typed phase/count extension, immutable
events, bounded budget/overflow, lost-reference coverage and unchanged
scope/closure behavior. Compile and independent observation-only review precede
any submission. Authors must not prepare the intended fresh
`build/gametest-shuttle-feedback-08-cargo-postjoin-r41` target. R38–R40 remain
retained; none is reused. R41, if submitted, is one lead-owned diagnostic, not
readiness, owner acceptance, a process-restart/client proof or automatic retry.

## Author evidence

The extension compiles. Focused JVM validation passed **12/12** (7 persistence
watch,5 saved-payload tests), zero failures/errors/skips; current tag selection
remains1/895. Strict preparer Python14/14 passed. R41's target remains absent;
R38's abandoned config and R39/R40 failed directories remain intact. No author
preparation or native execution occurred. Full new event details are logged
once, with phase/count/coverage summaries at the existing fixture stages.
Independent source/reuse/plan review passed after correcting nonterminal
observation-exit coverage and adding rejected clipped-impact witnesses. Captured
removal is a valid terminal event; scope/chunk loss is explicitly incomplete.
The actual break predicate remains unchanged. None of these author checks
identifies the live disappearance cause.
