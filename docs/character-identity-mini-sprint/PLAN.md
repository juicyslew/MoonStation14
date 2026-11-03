# Current-character identity ownership

**Closed by owner acceptance, November 2, 2026.** See `HANDOFF.md` for final
architecture, validation and boundaries. The plan below is retained as scope history.

## Scope

Separate live character identity from account/mind ownership. A new character must
not inherit or expose the previous character's profile through its live binding.
Initial and subsequently controlled bodies obey the same rules, regardless of
entity class. No round-history subsystem, migrations or lifecycle redesign.

## Ordered work

1. Correct adoption's persisted current-character key and binding together, reusing
   the existing lifecycle transaction and chat identity ledger. Preserve the mind
   and account, not the previous character key. Reconnect/return must reuse the
   exact current body's identity. Failed adoption must preserve the ghost context.
2. Resolve public names, emotes, ordinary speech and owner identity updates from
   that current identity. Remove the profile-harness class filter. Clear obsolete
   client identity on ownership transitions using existing context-reset owners.
3. Test identity separation at the data/binding boundary, not only rendered text:
   old character retained on its corpse, new body/current session cannot resolve
   the old profile, repeated transitions and reconnect preserve the new identity,
   custom names work, and ghost corpse return remains explicitly scoped.

## Reuse and constraints

Use `PlayerLifecycleRegistry`, `LifecycleServerContext`, the existing adoption
transaction, `PlayerCharacterHarnessBinder`, `ChatIdentitySavedData`/
`ChatIdentityRegistry`, and shared public-name/speech owners. Do not add a parallel
identity registry or clear/replace the mind to hide stale character state. Native
body bindings are control evidence, not a license to use a former human's profile.
Preserve accepted combat, throwing, movement, role UI and speech acoustics.

Pinned SS14 reference verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`:
`MindSystem.TransferTo` separates mind ownership from the body's identity;
`MindComponent` also retains character-name/original-owner information for other
purposes. This mini-sprint follows the owner's stricter rule: historical identity
must not be a fallback for the current body. No historical reporting is added.

## Validation

Focused JVM/compile checks first, independent source review, then one complete
OWNER_READY submission with affected native tags and applicable readiness checks.
No routine diagnostics or interactive client launches. Manual check after PASS:
change from a named human to another body, speak/scream, and verify the new name
and preserved corpse/custom names.
