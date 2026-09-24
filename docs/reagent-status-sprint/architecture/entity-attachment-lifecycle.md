# Entity attachment lifecycle

## Read and mutation rules

Reads of an absent attachment must not create runtime state. The first real
mutation attaches the authoritative attachment through the provider. All
mutations owned by a centralized system may operate on an existing attachment
in place, but must finalize through the provider whenever they change the
attachment's emptiness/activity predicate or require external synchronization.
Internal countdown mutations may skip provider finalization only while they
cannot change that predicate. Activation, expiry, and removal always finalize
through the provider; `StatusEffectSystem` follows these rules.

Authoritative attachments (currently reagents and status effects) own the
actual gameplay state. `ACTIVE_SYSTEMS` is derived scheduling state only; it
must never be used as evidence that an authoritative attachment exists.

## Empty state and persistence

Empty authoritative attachments are retained at runtime after their last entry
is removed. Their serializers omit empty values, so saving and loading omits
those empty attachments. Synchronization remains change-only and uses the
existing recipient policy; the derived activity attachment is neither
serialized nor synchronized.

## Scheduling and loading

The central activity dispatcher performs due status-effect work before reagent
metabolism. Activity intervals are staggered by entity id. On server-side
entity join, activity flags are reconstructed from any existing authoritative
attachments. This covers persisted loads, spawns, and dimension re-entry and
is intentionally idempotent.

## Adding a future linked system

For a new system, use this checklist:

1. Add its authoritative attachment and empty-state serializer policy.
2. Add an `EntityActivity` entry and interval if it needs ticking.
3. Add an activity binding to its `SystemLink`, with a predicate over the
   authoritative attachment.
4. Finalize predicate-changing or externally synchronized entity mutations
   through `MS14Provider`, using `updateIfChanged` where appropriate.
5. Include the authoritative link in activity reconciliation and add focused
   transition, persistence, and tick tests.
