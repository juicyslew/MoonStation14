# H8 item-location contract

## Scope

This is a pure, in-memory canonical ledger for opaque `ItemToken` identities. It coordinates domain locations only: it neither observes nor conserves, creates, consumes, or transfers Minecraft `ItemStack`s. It adds no networking, lifecycle hooks, UI, stun behavior, or mixins.

The immutable snapshot uses a deterministic insertion-ordered map, has a 4,096-item maximum, and carries a monotonically increasing revision. Registration is once per token. Compare-and-move requires the exact snapshot revision and source location, and requires a different destination. Stale, missing, duplicate, invalid, and overflow operations reject without replacing the current snapshot. IDs are bounded to 64 characters (the existing opaque item token has its own 128-character bound).

## Locations

Locations are typed body hand (body UUID + hand ID), body equipment slot (body UUID + slot ID), item-owned generic storage cell (container token + anchor + orientation + cell ID), or world entity (world UUID + entity UUID). There is deliberately no body-owned grid location. Vanilla creative inventory is account-owned in separate `QuarantineState`; it is not representable or assignable as a token location in this playable SS14 ledger.

Each exact body hand, equipment slot, and storage-cell identity may be claimed by at most one token. A registration or move to an occupied exact location rejects as `DESTINATION_OCCUPIED` and preserves the snapshot and revision. World locations are entity-identified, so distinct entity UUIDs in the same world remain distinct. Storage occupancy compares the exact generic cell tuple only: arbitrary multi-cell geometric overlap is delegated to the `GridGeometry` adapter and is not solved or asserted here.

Storage nesting policy is intentionally conservative: storage occupants cannot themselves be storage containers, and a token that already owns a storage cell cannot be placed inside storage. Self-containment is rejected. No recursive graph walk or cycle traversal is performed. This keeps validation bounded and declines unapproved nested-container behavior.

## Complexity and limits

The ledger caps entries at 4,096. Registration and accepted/rejected moves copy the bounded map, taking O(n) time and memory; storage validation is at most one additional O(n) scan. Read-only snapshot lookups are O(1), and listing is O(n) in deterministic order. At a 20-player scale of 10 represented items each, a listing/move handles 200 entries (rather than traversing Minecraft entities or recursively walking containers). This is a domain bound, not a real-time performance guarantee.

## Non-goals / risks

No physical source consumption, destination capacity beyond exact location identity, collision, world existence, generic multi-cell overlap, or account authorization is asserted. Grid geometry and real game-state reconciliation require adapters/policies beyond this ledger. The ledger's synchronization protects its own snapshot replacement only; callers remain responsible for reconciling real game state.
