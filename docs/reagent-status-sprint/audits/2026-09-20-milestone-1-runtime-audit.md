# Milestone 1 Runtime Audit and Completion Record

**Historical audit date:** 2026-09-20  
**Completion-record date:** 2026-09-21  
**Scope:** Milestone 1 only. This record does not claim Milestones 2-8 or completion of reagent effect behavior.

## Historical baseline (2026-09-20)

This file began as a pre-implementation gap audit and remains a record of that baseline. It is a durable resume point, not a replacement for [`Instructions.md`](../../Instructions.md), which remains the normative implementation handoff.

**Repository baseline:** `main` at `06cf5fd34e4afed5a9956717ae6d1d76322856c0`, with the project baseline of Java 21, Minecraft 1.21.1, NeoForge 21.1.224, ModDevGradle 2.0.141, and Gradle 9.2.1. The existing handoff and pinned upstream reference remain the source of the broader behavior contract.

The baseline already contained `PrototypeResolver`, `PrototypeCatalog`, `PrototypeMergeStrategy`, `PrototypeResolutionException`, `ReagentPrototypeMergeStrategy`, an inventory of **411 raw**, **406 concrete**, **197 parent links**, and **5 abstract** prototypes, plus **11 tests previously passing**. That foundation resolved and cataloged data but did not yet make the result the live runtime authority.

The baseline gaps were:

- no typed decode/publication manager;
- no `ResourceManager` reload listener or atomic rollback;
- no authoritative runtime reagent integration;
- no client catalog synchronization or cleanup; and
- direct registry reads in `TickHooks` and `ReagentComponent`.

The schema blockers were casing mismatches, nullable `conditions`, ignored or absent common/effect-specific fields, the incorrect `Oxygenate` decoded type mapping, and missing strict unknown-field, ID/reference, and round-trip/canonical re-encoding validation. The approved constraint was to make resolved catalogs authoritative while preserving `ResourceKey<ReagentData>` and avoiding a broad registry rewrite.

## Milestone 1 completion record (2026-09-21)

### Generic prototype runtime foundation

- Added the generic `PrototypeType`, `PrototypeManager`, `PrototypeCatalog`, `PrototypeResolver`, merge strategies, JSON/catalog validators, and typed load exceptions.
- `PrototypeType` carries the type ID, resource directory, codec, optional merge strategy, raw/resolved JSON validator, and typed catalog validator.
- The strict reload listener reads and parses all registered prototype resources, rejects malformed or duplicate IDs, and stages a complete candidate. It does not publish a candidate itself.
- Raw and resolved JSON are validated before decode. Typed catalog validation runs after decode, and the resolver applies inheritance while excluding abstract prototypes from the concrete catalog.
- Server and client managers are separate, instance-owned managers with no shared catalog state. Cleanup clears staged and published catalogs while retaining type registrations.

The production inventory is **411 raw**, **406 concrete**, **5 abstract**, **197 parents**, and **334 validated references**.

### Reload and publication transaction

The lifecycle is intentionally transactional:

1. The reload listener stages only; staging leaves the previously published snapshot unchanged.
2. The exact encoded full snapshot is passed through real bounded transport validation before it becomes commit-eligible. A failed reload or failed validation therefore cannot replace the last published snapshot.
3. The successful aggregate `OnDatapackSync` event is identified by its null player. That event commits the staged candidate and then syncs the committed snapshot to relevant players. A joining-player sync sends committed data only and does not commit a candidate.
4. `ServerStarted` performs the initial commit for startup ordering cases where no aggregate sync event was emitted.
5. `ServerStopped` clears staged and published values but retains registrations.

### Full-snapshot network protocol

The generic protocol sends one full snapshot covering every registered prototype type, including empty catalogs. Type IDs and prototype IDs are sorted deterministically, and the chunker produces deterministic output. The receive and send paths enforce the exact **512 KiB (524,288-byte)** encoded-payload bound.

The protocol limits are: **4,096** chunks, **8,192** entries per chunk, **256** types, **100,000** total entries, **32 MiB** total JSON, **512 KiB** per JSON value, and **512 bytes** per encoded ID. Decoding also rejects malformed/non-canonical varints, invalid UTF-8 or JSON, truncation, trailing bytes, invalid IDs, duplicate entries, and inconsistent totals.

The client assembler stages chunks and publishes only after every chunk and total has validated, so out-of-order delivery is atomic. It replaces older incomplete staging with a newer revision, rejects conflicting duplicates/metadata, and clears failed staging. Login/logout cleanup clears both the assembler and the client manager. Empty type catalogs remain present as empty catalogs after assembly.

The latest full production test output recorded a one-chunk sync with **261,763 JSON bytes** and **281,486 encoded bytes**.

### Canonical reagent validation and runtime authority

- The canonical lowercase reagent schema is audited structurally, including unknown fields, required fields, field types, nested effect/condition/plant data, and canonical IDs. Raw and resolved reference validation rejects malformed, missing, or abstract reagent targets.
- An unqualified reagent ID defaults to the `moonstation14` namespace, matching the runtime key construction and codec behavior.
- The legacy reagent datapack registry authority was removed. `ResourceKey<ReagentData>` identity remains in codecs, recipes, and components.
- `TickHooks` now reads the server catalog snapshot, while `ReagentComponent` reads the side-appropriate catalog snapshot. This is a runtime authority migration for catalog lookup, not a claim that reagent effect behavior is complete.

## Prior final-review blockers and fixes

The prior final-review blockers are addressed in this Milestone 1 implementation:

- **Publication during reload:** replaced by manager-owned stage/commit tokens and aggregate lifecycle commit.
- **Commit before transport safety:** candidate eligibility now uses the actual bounded chunker/encoded representation, rather than only a modeled size check.
- **Wire-size and count enforcement:** send and receive enforce the same exact 512 KiB bound and all count/byte limits.
- **Partial or nondeterministic client state:** deterministic ordering, empty-catalog preservation, out-of-order atomic assembly, duplicate/conflict handling, and cleanup are covered.
- **Server/client contamination:** independent managers and side-specific snapshots prevent cross-side catalog sharing.
- **Schema/reference ambiguity:** canonical schema checks and strict references use the runtime-matching `moonstation14` default namespace and exclude abstract targets.
- **Revision-width boundary:** transport validation reserves the wire width of `Long.MAX_VALUE`; the boundary is tested rather than assuming the width of ordinary live revisions.
- **Legacy registry authority:** consumer reads moved to catalog snapshots while preserving the existing `ResourceKey` identity surface.

## Validation record

Passed validation consists of focused network tests, all prototype tests, focused reagent tests, `compileJava`, the full test suite, and a read-only diff check. Transient Gradle parallel/stale-worker failures were environmental; a clean serial full test passed. No manual game launch was performed, per policy.

The final review was accepted as a read-only Milestone 1 review. Residual risks are limited to:

- no full NeoForge lifecycle integration/GameTest;
- an ignored `false` return from commit could send the previous snapshot; and
- dedicated-server classloading and event order were verified by source/tests, not by a manual launch.

## Working-tree preservation warning

This warning remains in force: `Instructions.md` has both staged and unstaged changes (`MM`), `build.gradle` is modified, and the current prototype files, `ReagentPrototypeMergeStrategy.java`, and test files under `src/test/` are untracked. Preserve these changes; do not stage, commit, reset, or rewrite them while continuing the implementation.
