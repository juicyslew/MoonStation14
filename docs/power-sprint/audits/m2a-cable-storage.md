# M2a — bounded face-cable storage and interactions

> **Historical baseline — superseded by M14.** The original M2a contract allowed only one tier on a host face. Preserve the details below as evidence of the original milestone; current storage is schema v2 and supports all three tiers on that same face. See [M14](m14-multitier-cables.md) for the current contract and v1 read migration.

## Implemented scope

- `POWER_CABLE_CHUNK` is an independent, server-owned, version-1 sparse `LevelChunk` attachment. Its deterministic records contain local X/Z, absolute signed Y, face, and HV/MV/APC tier. The codec rejects unknown versions, invalid local X/Z, duplicate host faces (including attempted tier conflicts), malformed face/tier IDs, and chunks over 4096 records. Empty attachment data is omitted. Reads do not materialize absent records; mutations are centralized, limited to loaded chunks, and mark the owning chunk unsaved.
- A face accepts at most one tier. Each of the six faces can independently contain a segment. A host face must be sturdy and have a full collision shape. This deliberately permits ordinary full blocks such as vanilla stone, including wall and ceiling faces; it is not a station-only eligibility tag and does not make hosts conductive.
- Server-side spool items place on precisely the clicked host face and consume one matching spool outside creative. The cutter removes precisely that face and returns exactly one spool of the stored tier. Both require a non-spectator player, block interaction/item-use permission, and a bounded eight-block center distance. No record means no item loss or refund.
- The station floor's existing finish-property transition does not touch records. Its `onRemove` path prunes all records only when the registered floor block is actually replaced, not when its tile enum changes.
- There is no cable BE/tick loop, client synchronization, renderer, topology solver, or atmosphere/player integration in this milestone. Models are project placeholders and no reference assets were copied.

## Lifecycle limitation (fail closed)

Each record stores the observed host block registry identity. Storage access lazily removes all faces at that position when the current block identity differs (including legacy records with no identity), marks the chunk dirty, and invalidates the power/visual caches. Thus a replaced host cannot be cut for a refund, and a storage read cannot expose the prior host's cable. The station-floor callback eagerly prunes on its actual block replacement while finish-property changes preserve records. This identity check cannot distinguish replacing a block with a fresh instance of the same registered block type, and arbitrary block writes do not necessarily invoke a supported universal callback. Current graph chunk refresh reads raw attachment records, so it does not itself perform this lazy identity validation; full arbitrary replacement invalidation before graph refresh remains follow-up required before claiming arbitrary host replacement can never transiently re-energize a stale segment. Do not treat this milestone as proving that stronger guarantee. No client-edit or per-tick cable scan is introduced.

## Validation coverage

Unit coverage exercises all faces, tier conflict rejection, version/duplicate decode failure, signed Y, round-trip determinism, bounded records, and host pruning. GameTests exercise all six-face storage, floor finish preservation, chunk dirty marking, owned-floor replacement cleanup, full-host/partial-slab eligibility, and spool/cutter conservation on an exact face. No manual Minecraft/client test is claimed.
