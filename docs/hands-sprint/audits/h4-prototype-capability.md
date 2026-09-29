# H4 prototype-declared hand capability

Character prototypes may declare an ordered `hands` string array. A missing or explicitly empty array means no hand capability; a nonempty array declares capability only as data. IDs must be nonblank, unique, no longer than `HandState.MAX_ID_LENGTH`, and the list may contain at most `HandState.MAX_HANDS` IDs. Strict schema auditing rejects wrong types, unknown root fields, duplicates, and out-of-bound declarations.

The human prototype declares `left`, then `right`, covering its player and villager host mappings. The pig prototype omits `hands` and does not implicitly gain them. This change does not initialize or attach hand state to entities, or alter player control, lifecycle, mixins, or vanilla inventory behavior.

`CharacterData.CODEC` validates declarations directly, and prototype catalog reload uses the same codec validation.

## Runtime status

Read-only runtime resolution is implemented through `HandCapability.resolve(body)`: it follows the body's existing bound character identity via `CharacterIdentitySystem.resolve`, returning an immutable ordered ID list only for a valid nonempty declaration. Unbound/dangling identities and bodies without server-side character resolution have no capability. Resolution does not materialize attachments or mutate identity.

`HandCapability.isCompatible(existing, prototypeHandIds)` provides a read-only persistence policy: existing hand IDs and order must match exactly. Mismatches are rejected, never rewritten or saved, so held tokens cannot be silently dropped. This is metadata and compatibility policy only; it does not provision hands, authorize player actions, gate vanilla behavior, or change lifecycle handling.
