# H7 — Quarantine state model (pure protocol)

## Scope

`QuarantineState` is a deterministic, immutable Java value model that records access
authorization intentions for the protocol boundary between a player's account-owned
Creative vanilla inventory and a non-Creative body. It is server-independent. It stores
only an account UUID, session generation, transition identity, phase, and protocol
confirmation flags. It contains no `ItemStack`, inventory reference, player/UI/lifecycle
hook, I/O, or live mutation. This pure model is not the quarantine implementation and does
not establish that the full quarantine is complete. This audit does not claim a disk format,
transaction, or crash-atomic storage implementation.

The account remains the owner throughout. While the associated body is eligible and
non-Creative, `PARKED` records an intention that the body may use hands, equipment, and
item-owned storage only. It never authorizes access to the carrier's vanilla inventory.
This value is not enforcement of that restriction. On actual Creative eligibility, a caller
may request restoration; death and reconnect alone are not restoration intents. Thus a
death cannot copy account items to hands or discard account ownership, and reconnect
preserves a parked account across a monotonically newer session generation.

## State and grant table

| Phase | Modeled access intention | Meaning |
| --- | --- | --- |
| `CREATIVE_AVAILABLE` | Creative only | Account-owned inventory may be granted to Creative. |
| `PREPARING` | Neither | Park intent underway; snapshot proof is required before clear authorization. |
| `PARKED` | Body-only intention (hands/equipment/item-owned storage) | Never carrier vanilla inventory; account ownership remains parked separately from body death. |
| `RESTORING` | Neither | Restore intent underway; access waits for verified restoration. |
| `RECOVERY_REQUIRED` | Neither | Reopened journal is unresolved; both owners fail closed. |

The model exposes one derived `Access` intention, not independent booleans. `BODY_ONLY`
does not mean body access to the carrier's vanilla inventory, and the model alone does not
enforce any access restriction.

## Protocol

1. `PARK` is accepted only from `CREATIVE_AVAILABLE` and moves to `PREPARING` with no grant.
2. `SNAPSHOT_PREPARED` records the caller's confirmation that a durable snapshot is prepared.
   This boolean is a protocol fact, not evidence independently verified by this model.
3. `CLEAR_AUTHORIZED` is rejected without that proof. A caller may authorize its external
   clear only after receiving the accepted state containing this confirmation.
4. `CLEAR_CONFIRMED` is accepted only after authorization and moves to `PARKED`.
5. `RESTORE` is accepted only from `PARKED` (the caller is responsible for invoking it only
   when the player is actually Creative). `RESTORE_VERIFIED` moves to
   `CREATIVE_AVAILABLE`; until then neither owner receives vanilla access.

Every request includes the account's session generation and a unique transition UUID.
Repeated current or most recently completed intents, and repeated current confirmed steps,
are idempotent. Stale generations, mismatched transition IDs, missing proof, and forbidden
steps return a rejection with the identical prior state. The model keeps one active
transition and one completed transition ID, so it is bounded and intentionally not an
unbounded event ledger.

`reconnect(newGeneration)` only advances the generation and is accepted for stable states.
It retains `PARKED`/`CREATIVE_AVAILABLE` ownership and access; in-flight or recovery state
must be resolved instead. There is intentionally no death transition.

## Crash boundary and limitations

`journal()` returns only bounded protocol metadata. `recover(journal)` treats any incoming
journal as unresolved and enters `RECOVERY_REQUIRED`, even if its last phase appears stable.
It grants neither owner and retains the account UUID and ownership marker. Normal restart
recovery is deliberately left manually unresolved; there is no automatic resolution or
rollback because the pure model cannot inspect durable storage or the live inventory. A
separate, audited recovery decision is required before any grant. The model cannot guarantee
that a purported snapshot is actually durable, that clear/restore happened atomically, or
that a caller obeys an authorization intention. The successful protocol path in these tests
is not evidence that an integrated quarantine has completed.

## Validation coverage

`QuarantineStateTest` exercises the modeled park/restore path; missing snapshot proof and
unauthorized clear; idempotent duplicate intent/proof; stale generation and transition;
death/reconnect retention; crash reopen at successive protocol stages; mutual exclusion;
forbidden transition immutability; account ownership retention; and the rule that only
`CREATIVE_AVAILABLE` intends Creative vanilla access, while the body never gets carrier
vanilla access and in-flight/recovery phases intend no access. These are pure unit tests, not
live inventory or lifecycle tests.
