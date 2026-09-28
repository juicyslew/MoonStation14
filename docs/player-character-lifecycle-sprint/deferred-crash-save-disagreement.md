# Deferred: crash-time world/profile save disagreement

## Known limitation

An abrupt server crash can occur between persistence of the entity world and persistence of the lifecycle profile
file. After restart, the entity-world save and profile file can therefore disagree about a character's body or
ownership. This is a rare cross-save mismatch; it is not evidence that ordinary clean logout/restart/reconnect is
unsupported, and it is not fixed by the current lifecycle work.

## Current behavior

The lifecycle must fail closed when persisted ownership is missing, unavailable, duplicated, or ambiguous. In
particular, on a world/profile mismatch it must not guess which save is authoritative, spawn a duplicate body,
transfer the player to a ghost as a fallback, or silently repair either save. There is no operator recovery command
for this condition. The affected lifecycle claim remains unavailable pending manual intervention; ordinary runtime
validation must retain these fail-closed guards.

## Deferred follow-up

Operator-facing diagnostics, a deliberate operator recovery workflow, and automatic reconciliation are separately
planned future work, not current completion criteria. Any reconciliation must only be enabled if it can be shown
safe for the specific evidence and failure mode; ambiguous ownership must continue to fail closed. This note does
not claim that diagnostics, recovery, or automatic repair currently exist, nor does it waive normal restart and
same-body reconnect acceptance.

## Current sprint boundary

The ordinary healthy lifecycle remains in scope: logout, ordinary server restart, then reconnect to the same living
body and Mind, with the existing no-duplicate/unloaded/missing-body negative guards. Actual-death handling and the
offline badge remain in scope as well. Cross-save crash atomicity, operator recovery tooling, and automatic
reconciliation are deferred independently of those requirements.
