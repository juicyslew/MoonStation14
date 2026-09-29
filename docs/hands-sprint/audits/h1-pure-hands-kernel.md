# H1 Pure Hands Kernel Audit

## Scope delivered

This slice provides only a pure Java 21 domain kernel: an immutable bounded ordered set of named hands, one active hand, optional opaque item tokens, and pure select/place/remove/move/swap transitions. Hand IDs are stable caller-provided names; item tokens are value identities and are not Minecraft `ItemStack`s. Invalid definitions are rejected at construction. Failed transitions return an explicit rejection and the unchanged input snapshot. This is not a runtime inventory or item-location implementation.

The kernel enforces its own abstract-state invariants: the configured hand count is at most 16, hand IDs are non-null/non-blank and unique, each hand has at most one token, and one token cannot occupy two hands in a state. It does not claim item conservation in the Minecraft world, vanilla inventory, or across carriers.

## Explicit exclusions and unresolved runtime work

- No live body/lease authority, runtime body lease validation, lifecycle handling, or authenticated request path is implemented or established here.
- Vanilla inventory isolation is unresolved. This code neither disables nor parks/restores vanilla inventory, and does not prevent a vanilla path from owning or moving the same real item.
- Creative transition behavior is unresolved. This code does not implement the approved Creative/non-Creative boundary or safe no-loss inventory parking/restoration.
- No `ItemStack` conversion, NeoForge attachment, persistence, networking, mixin, UI/hotbar, or Creative handling is included.

Accordingly, passing these pure tests establishes only the abstract state contract. Runtime ownership, transaction/conservation guarantees, lease safety, vanilla isolation, and Creative transitions require separate reviewed work and validation.
