# Pollen identity design

Status: reference/carrier foundation implemented, with focused JVM and compilation evidence in `M6-IDENTITY-VALIDATION.md`; native execution remains unverified. The directional crossing engine is implemented separately (`POLLEN-CROSS-COMPOSITION.md`). Swab interaction, capture/cross/swap gameplay, and an owner-testable scenario remain unfinished. Swab art is requested separately in `botany-genetics-target-items-swab-20261015-r1`. This is not a gameplay-completion claim.

## Source behavior

Reference: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, `BotanySwabSystem`, `SeedData.Clone`, `PlantHolderSystem.EnsureUniqueSeed`, and `MutationSystem.Cross`.

A swab captures the actual seed reference without marking it shared. A unique seed can subsequently change in place and those changes are visible through the swab. Clone-on-write, species change, and crossing replace the holder's seed reference; the swab continues referencing the former seed. Planting adopts the packet's reference, rather than automatically cloning it. Removing the plant does not erase captured pollen. Crossing retains recipient identity and swaps the swab to the recipient's pre-cross reference.

Both initial capture and subsequent crossing use the same two-second, hand-required, movement-cancelled do-after (`BotanySwabSystem.cs:42–79`). Resolve sample and recipient at completion. Empty capture from an empty holder leaves the sample null but still presents the source capture popup; an already-used swab on an empty holder returns without crossing. Do not make initial capture immediate or cancel solely because the holder's seed reference changed while the timer was pending.

An immutable capture-time snapshot or a reference to the current tray would both be incorrect. The latter would follow replacement plants and species changes that the captured reference must not follow.

## Runtime identity, immutable values

Keep `GeneticValue` and its resolved profile immutable. Introduce a narrowly scoped server seed-reference cell containing the current immutable value. Only botany's plant-state owner can update that cell. No global reference registry is required.

Each resolved `SeedProfile` also carries the pinned source seed `Name` as `sourceName` (`source_name` in profile data). This is source identity, not translated display text, a local prototype ID, or a visual key. Ordinary profile copies retain it; species replacement adopts the target profile's name. Pollen hybridization compares these source names, so profiles sharing a local species ID can still differ by upstream seed identity.

The holder distinguishes operations:

- Planting adopts an existing packet seed reference, including its uniqueness state. A prototype packet without a runtime reference starts a non-unique identity from its resolved value.
- `EnsureUniqueSeed` detaches a non-unique identity before the source's admitted reagent and mutation paths. Detachment may reuse immutable profile data, but SS14 `SeedPrototype.Clone` omits `ScreamSound`: local detachment therefore uses the shared seed-copy profile helper to reset audio to its default while retaining `CanScream`. The old captured cell remains untouched. A value can be reused only where its copy defaults already match.
- In-place genetic changes replace the value inside the current unique identity.
- Species change and crossing replace the identity, not the value inside the former identity.
- Actual source sharing paths, including repeat/multiple-product harvest, mark the current identity non-unique at their authoritative commit boundary.
- Clearing a holder drops its reference; swabs may retain the cell and its last value.

The same botany-specific reference component must travel on harvested produce and extracted packets, not only swabs. Preserve it whether currently unique or shared: uniqueness can change while other references remain alive. Harvest marks shared only when actual generation has multiple outputs or repeat mode; extraction marks shared only when producing multiple packets. Neither operation clones the reference. In particular, a unique seed can survive single-output non-repeat harvest, one-packet extraction, and replanting without detachment. A previously captured swab must still observe subsequent in-place mutations. Source extractor defaults are 1–3 packets (`SeedExtractorComponent:12–19`); this is a reachable case, not speculative compatibility.

Reuse one effective-genetics reader for carrier planting, extraction, and other server genetic reads: a runtime reference is authoritative when present, otherwise use the existing immutable `GENETIC_VALUE` snapshot or resolved prototype fallback. Do not duplicate lineage implementations for swabs, produce, and packets. Keep `GENETIC_VALUE` unchanged as a value component; its capture-time fallback must not override a newer referenced value. Existing produce solution contents remain independent: reading current seed genetics must not regenerate chemicals or refill depleted produce.

Audit all genetic writers and sharing paths before implementing this distinction. Do not infer identity replacement merely from a changed species ID: same-species crossing still creates a new identity.

## Explicit reference-component contract

The component shared by swabs and genetic carriers represents an **opaque seed reference**, not an exclusively owned genetics value. Give it a stable reference ID and a read-only link to the server cell. Equality and hashing compare that reference ID, never mutable genetic contents. Do not change `GENETIC_VALUE` or its existing value equality.

This is an intentional boundary: `ItemStack.matches` continues to validate the swab's reference identity across ownership transfers. It must not invalidate a transfer merely because the referenced donor changed in place. A pollen action must resolve the reference's current value at completion; it must not prepare effects from an earlier cached genome and then treat stack equality as a genome-version check. Changing the swab's captured reference must replace its component and use the existing held-item mutation/revision owner.

This decision accepts ID-only equality specifically for this opaque-reference component. It does not authorize mutable maps in value components or weaker ownership/token checks. Test the existing hand/world transfer paths, copied stacks, component codec round trips, and timed completion against this contract before acceptance.

## Persistence and networking adaptation

Encode the reference ID, uniqueness state, and its current immutable genetics snapshot. Decode a detached reference containing that snapshot. An actual save therefore retains current pollen/carrier values, not capture-time values, without a permanent world-wide alias registry or orphan-reference collector.

Aliases do not reconnect across save/load or server restart. Record and test this Minecraft adaptation explicitly; SS14's round-local references do not establish a persistent-world reconstruction contract. Client decoding is also detached: clients receive presentation data, never authority to supply the genome used by the server's crossing operation.

Do not expose changing gene details in client presentation without an explicit synchronization owner. Upstream's used/unused swab examination does not require live gene-detail synchronization.

## Acceptance prerequisites

- Verify source clone-on-write boundaries, including nonempty sub-unit soil solutions and harvest sharing.
- Preserve capture-before/after-uniqueness behavior, in-place mutation visibility, replacement detachment, and survival after removal.
- Preserve alias identity through produce → single extracted packet → replant, and prove multi-output/repeat sharing triggers later clone-on-write. Audit all carrier factories and server genetic readers; do not limit coverage to tray-held seeds.
- Preserve the directional crossing algorithm and its verified RNG order for supported traits; document unsupported traits rather than adding inert fields. Pinned-source correction (`MutationSystem.cs:59–101, 107–195`): there is no WeedHighLevelThreshold crossing/draw; IdealLight and LightTolerance draws remain despite lacking local fields. Empty-chemical/gas different-species crosses consume 21 scalar/unsupported draws (nutrient through Scream, indexes 0–20) plus hybrid seedless draw index 21, total 22; same-species crosses skip that final draw, total 21. Ligneous is index 18, Seedless 17, Kudzu 19. The hybrid probability is strictly `NextDouble() < 0.7`, unlike ordinary 0.5 scalar draws. Chemical crossing starts from the recipient clone, adds donor-only entries first, then draws each recipient-only removal using the current result count; each draw is consumed even when the sole remaining entry cannot be removed.
- Reuse authenticated held-item admission and the shared two-second, movement-cancellable timed-interaction owner.
- Prove stale hand/tool/target denial, sample swapping, and reference updates during a pending action using native tests.
- Register no substitute item or player-facing scenario before the swab asset and resolved composition are ready.
