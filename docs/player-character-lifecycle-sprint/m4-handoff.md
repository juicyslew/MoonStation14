# M4 lifecycle session handoff — integrated, acceptance pending

**Status: M4 remains unaccepted pending connected-owner evidence.** Gated first join, exact-body session ownership,
Begin/Ready/Commit, and custom-character movement are integrated. Automated test/build results are recorded in the
[current handoff](current-handoff.md); they do not prove a live client handshake or owner acceptance.

The owner-connected first-join regression found in the initial Steve/custom-body smoke is fixed in code: the client now
recognizes the exact registered `PlayerCharacterHarnessEntity` without depending on `host_entity_types`, and uses the
client HUMAN prototype directly for prediction when that catalog is available. Ready still requires the visible body;
server-side binding and ACTIVE-session checks remain authoritative. The server now sends Begin on the first Post tick
after join, after revalidating the exact player/body, and starts its bounded Ready timeout from that send. A brief queued
Begin handles a client whose exact payload-context player or local player/level is not bound at arrival. Deferred Begin
survives transient local-owner cleanup, is accepted only for a newer epoch once the current owner and level are available,
and is discarded on explicit logout so it cannot cross server sessions. Other payloads are not deferred. One Begin/commit INFO and bounded failure
WARN diagnostics are available for the next smoke. Connected proof is still required.

Do not repair or edit the failed test world/profile. Preserve and back it up; perform the next proof only on a fresh,
disposable backed-up world after the fix. A future lobby should establish client readiness before lifecycle handoff,
but lobby implementation is explicitly deferred and is not part of this fix.

The production login path stages a persistent custom HUMAN character and stable Mind for a fresh account, then uses
the lifecycle Begin/Ready/Commit protocol. Failures disconnect/fail closed; no player packet selects a body and no
transient ghost is the first-join possession target. The existing COMMON `experimentalMindGhostControl` startup-sampled
gate remains default-off and preserves vanilla behavior when disabled. Movement requires
`experimentalVerticalSliceMovement=false` and an explicit exact custom-type WorldStep eligibility path; the type is
not added to `host_entity_types`.

After authentication, Ready is bounded and revalidates the current ACTIVE profile, body, binding, gate and spectator
preconditions. Commit owns the camera and intents are epoch/sequence/tick bounded. The server applies authoritative
movement. Invalid ownership or movement suspends and disconnects without handing control to a carrier/debug route.
Clean logout or server stopping persists ACTIVE-to-OFFLINE before retiring matching movement/session ownership;
body and Mind remain, with normal physics, status and damage.

Existing-account reconnect accepts only a unique exact OFFLINE claim and exact same saved, loaded body/Mind. It makes
one recorded-chunk load attempt and allows a bounded 20-server-tick wait for that same body to become visible; there is
no neighboring search or replacement spawn. The durable generation advances before the new session begins. Healthy
ordinary restart/reconnect is implemented but still requires the owner smoke. Duplicate, ambiguous, mismatched, and
failed-handshake cases must fail closed.

The owner must follow the [connected acceptance checklist](connected-acceptance-checklist.md), including handshake,
movement, no transient ghost, same-body reconnect and restart, and negative cases. No manual pass is claimed. Crash/save
disagreement and operator recovery are future work, not a M4 acceptance criterion for this smoke.
