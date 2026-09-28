# M5 dormant-character safety prerequisite

**Status: independent safety prerequisite only; M5 remains gated and unaccepted.** Newly constructed
`moonstation14:player_character_harness` entities have `NoAI` set in their constructor, before the entity can be
inserted into a level or ticked. Entity NBT loading reasserts `NoAI` immediately after vanilla Mob data is read, so a
saved `NoAI=false` cannot enable voluntary Mob AI. This applies regardless of whether the saved character identity is
absent, valid, or malformed; explicit owner binding and malformed-binding evidence behavior are unchanged.

This policy is deliberately limited to voluntary AI. The character remains an ordinary-physics Mob: gravity and
collision are not disabled, and this change does not freeze movement/physics, alter status, health, or damage handling,
or grant invulnerability. It does not change global vanilla Mob AI or leasing behavior and does not affect the debug
Villager/Pig route. No `noGravity`, `noPhysics`, or damage-immunity policy is introduced. Appearance synchronization and
the existing explicit binder are untouched.

The dedicated GameTest fixture checks no-AI state before `addFreshEntity` and after insertion, then reads saved data
claiming `NoAI=false` for unbound, valid-bound, and invalid-owner cases. It also checks ordinary gravity/collision and
that owner-state handling remains intact. JUnit tests cover the existing lifecycle binding/policy suite; no damage
response assertion was added because this prerequisite does not alter damage code.

This foundation alone was not M5 completion. The integrated logout, OFFLINE marker, same-body reconnect, and bounded
retry behavior are described below; M5 remains unaccepted pending the connected owner smoke and ordinary restart proof.

### Pure durable OFFLINE transition prerequisite

A clean-stop/logout path handles only an exact lifecycle session with its matching live, owner-bound custom body.
On `PlayerLoggedOutEvent`, connected-session loss observed on tick, and `ServerStoppingEvent` before startup context
teardown, it writes ACTIVE to OFFLINE under the primary-store lock before removing the controller session or
movement-owned marker. The same Mind and body remain claimed; the body remains in-world, NoAI and subject to normal
physics/status/damage. The OFFLINE record captures the live dimension, position, appearance and body shape. Duplicate
logout/stop delivery is harmless because the first successful transition removes that exact session. A missing body,
stale durable claim or failed write suspends authorization and records in-memory recovery required rather than
claiming clean OFFLINE. No shared debug logout behavior is changed.

If OFFLINE persistence succeeds but the in-memory disconnect commit fails, the lifecycle Mind is suspended
without releasing its body claim or invoking eligibility callbacks, and the in-memory record is marked
`RECOVERY_REQUIRED` and inactive. The durable OFFLINE state is retained and normal reconnect is not allowed
from this ambiguous state; operator/recovery handling is required. A persistence/CAS failure before that barrier
does not change active memory. The runtime clean-stop path explicitly suspends old authorization after such a failure,
leaving durable ACTIVE evidence for fail-closed recovery. Death ghost handling is not implemented, and crash
mismatch recovery remains fail-closed. The experimental master gate remains default-off; when disabled the logout
and reconnect paths have no lifecycle effects.

### Existing-account reconnect wiring

The normal authenticated existing-account join branch is now wired behind the same startup-sampled gate. Only a
unique durable OFFLINE record is eligible. The server reobserves the exact saved UUID in the saved dimension and
requires the custom CHARACTER harness, saved account/profile/Mind binding, living HUMAN identity and NoAI body.
An unloaded recorded location gets at most one exact recorded-chunk load attempt followed by fresh loaded-only
observations. Because vanilla may still be draining the entity-loading inbox after `getChunk(FULL)` returns, an exact
body not yet visible is held pending for at most 20 server ticks, revalidating the connected player, startup gates and
durable OFFLINE claim on each tick. The carrier remains SPECTATOR while waiting; no second chunk load or neighboring
chunk search occurs. A delayed exact body can complete the normal reconnect, while timeout gives actionable retry
guidance; dead, mismatched, ambiguous, stale-owner and duplicate evidence still fail closed immediately. There is no
body search, replacement spawn, or ghost fallback. The carrier is made SPECTATOR and placed in
the saved dimension/location before the same disconnected stable Mind is restored from the current-primary lease;
the durable OFFLINE-to-ACTIVE generation CAS precedes Begin/Ready/Commit activation. A failed CAS does not authorize
the carrier, and a post-CAS session failure suspends the in-memory claim for recovery.

After a successful clean ACTIVE-to-OFFLINE durable transition, the exact owner-bound body saves that same
server wall-clock timestamp in its entity NBT. Its normal server tick synchronizes an `Offline` badge after
60 seconds have elapsed, including elapsed time across restart; ordinary gravity, collision, status and NoAI
behavior remain unchanged. Reconnect first reconciles the exact resolved body's timestamp from the durable
OFFLINE profile, then clears it only after a successful OFFLINE-to-ACTIVE durable transition. Failed or
ambiguous transitions do not clear the indication. Invalid entity timestamps fail closed. The client renderer
draws a separate small label and does not alter the body's custom name or force ordinary name visibility.
This remains gated, has focused boundary/NBT coverage, and is not connected-client render acceptance.

### Temporary operator Creative escape

An exact connected lifecycle CHARACTER owner with permission level 2 may request vanilla Creative as a bounded
development escape. Before vanilla changes mode, the server verifies the startup lifecycle gate, no legacy movement
conflict, exact listed connected player/server thread, and current ACTIVE Mind/body binding, then durably performs the
same ACTIVE-to-OFFLINE transition as clean logout using the body's current position and appearance. Only after that CAS
does it revoke the old generation, clear the session/input gate and movement-owned marker, send Stop, and restore the
camera to the carrier; the same stable Mind and NoAI body remain in-world. A failed persistence step cancels Creative and
leaves the session/camera/movement intact without invoking generic failure recovery. Non-operators cannot use the escape,
and other non-SPECTATOR mode requests are rejected while the session is owned. Creative is not supported as a simultaneous
controller for an ACTIVE MS14 body. This is a temporary development exception, not production gameplay or a future
lobby/MS14-native developer mode. Logout in Creative leaves the already-OFFLINE claim alone; the ordinary gated next
 login can reclaim the same body. An operator can also use `/ms14dev return` on that same exact Creative carrier to
 reclaim the existing living body without logout. This shortcut accepts only an already-loaded exact body and the
 current primary OFFLINE profile; it does not force-load a chunk. If unavailable or ambiguous, it leaves the carrier
 Creative and tracked so the operator may retry or use ordinary logout/rejoin. Reclaim uses the durable reconnect
 generation and the existing Begin/Ready/Commit handshake; after the durable ACTIVE claim, startup failure disconnects
 rather than leaving Creative alongside an active Mind. Until return or logout, Survival and Adventure are blocked for
 the tracked carrier; Creative and the return's spectator transition are allowed. Logout/server stop clears only the
 ephemeral carrier tracking and does not rewrite the already-OFFLINE profile.

This is implementation wiring, not ordinary restart acceptance. ACTIVE/PREPARING/RECOVERY_REQUIRED records remain
fail-closed, and a second concurrent login cannot reclaim an active session. Full build and full JUnit recently passed
after the movement fix. The isolated `runGameTestServer -Pms14GameTestDir=build/gametest-lifecycle-movement-final
--no-daemon` run reported 158 GameTests, all required tests passed, with log at
`build/gametest-lifecycle-movement-final/logs/latest.log`. These precede owner testing and are not connected-client or
restart proof. Follow the [connected acceptance checklist](connected-acceptance-checklist.md); no manual pass is
claimed. Crash-time world/profile disagreement remains fail-closed future work.
