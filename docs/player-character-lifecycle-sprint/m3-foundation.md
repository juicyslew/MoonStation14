# M3 limited custom Mob and identity-binding foundation

**Status: foundation only; this is not M3 acceptance.** This owner-approved narrow prerequisite creates the distinct
`moonstation14:player_character_harness` Mob type and a server-only explicit binder. It is persistent (the entity
type is not `.noSave()`), has ordinary gravity/collision, and is not a `ServerPlayer`, Villager, or Pig. Registration
does not spawn an entity, bind a player, establish a Mind, or provide automatic login.

The binder accepts account UUID, profile key, Mind UUID, and character prototype key as distinct values. The prototype
key must exactly equal `ModCharacters.HUMAN_ID`; other resolvable prototypes (including Pig) are refused before any
identity attachment or per-instance owner binding can be written. It still resolves that Human key through the current
server character catalog on every bind attempt, and refuses dangling keys or conflicting existing identity. It first
requires the existing startup-sampled `MindGhostStartupGate` to be enabled and independently rejects when
`MovementStartupGate` is enabled, before inspecting the target or its server/lifecycle state. If permitted, it then
requires execution on the target server thread before further target validation/mutation. It validates the registered custom entity, server ownership, registered prototype,
and existing identity conflicts; repeated identical binding is idempotent,
and conflicting instance/prototype identity is refused. Per-instance account/profile/Mind values serialize in the
entity's own additional NBT, separate from the existing prototype identity attachment. The profile identity is the
durable profile key's exact bounded string (for example `main`), not a normalized resource location. A saved binding
key that is malformed, partial, or the wrong NBT type permanently marks that entity instance invalid-unbindable;
its raw NBT evidence is retained across save and loading into a freshly spawned entity, and the binder never treats it
as an unbound body. The intrinsic invalid-state check is independent of the startup gate, so malformed data remains
provably invalid even when the gate prevents binder inspection. Only a truly absent
binding key is eligible for first binding, and only while both startup gates permit it. The read-only accessor is
intended for later exact owner/Mind/body proof; it is not itself proof of Mind attachment or authenticated session.

The first limited presentation option is implemented in source: a client-only renderer uses the vanilla wide player model
and the bundled Steve/Alex wide skins. A second independent bounded option selects wide or slim geometry and matching
vanilla Steve/Alex textures using `ModelLayers.PLAYER` / `PLAYER_SLIM`; both choices are per-entity synchronized, so
the renderer selects geometry and texture from each tracked entity without retaining its prior selection. The body-shape
index is stored separately from skin appearance and account/profile/Mind
binding NBT and synchronized with entity data, so tracking clients render the authoritative selection. Unsupported
indexes or malformed appearance NBT fall back to Steve while preserving malformed NBT evidence; they do not alter
binding validity. An explicit server API first requires the startup gates, before inspecting the entity, player, or
lifecycle state; if permitted, it requires a live server-thread entity and the exact bound account UUID, derived only
from an exact connected `ServerPlayer` registered in that server's player list and in the
body's same server/world. Fake players and stale/unregistered handles are rejected. Both setters additionally require
an explicitly supplied server-owned `PlayerLifecycleRegistry` and server-known expected connection generation. It
requires an `ACTIVE` profile snapshot with matching account, Mind, profile key, and exact body identity, then checks
the registry's generation-sensitive authorization with character-only eligibility. A stale same-account body,
inactive profile, or stale generation is rejected. The self-service command obtains authority only from the exact
committed lifecycle session; it accepts no target UUID, client packet, or unrestricted edit request. No spawn path or
automatic Mind attachment is included.
In particular,
`GroundedHarnessLease` still restricts eligibility through `host_entity_types`; this new
type is intentionally not added to that mapping. These remain only two bounded visual options; no hair choice or
connected two-client observer visual proof has been performed. A minimal owner command is now available as
`/ms14char skin default|alex` and `/ms14char shape wide|slim`. It accepts no target argument and requires the exact
connected real player, committed active lifecycle session, loaded custom bound HUMAN body, and both existing startup
gates. The selected field is CAS-persisted in the current primary ACTIVE profile before the matching synchronized
entity setter runs; unrelated appearance fields are retained. A failed CAS leaves the entity unchanged, while a failure
after persistence suspends the exact session and disconnects fail-closed. This is a self-service command, not polished
UI or connected visual proof. The debug operator route is untouched; startup flags remain default-off.

## Evidence and limitations

Focused unit tests cover gate default-off/conflict policy, immutable owner/profile/Mind serialization, exact profile
key representation, rejection of incomplete/invalid binding data, bounded skin and body-shape indexes and their four
combinations, pure appearance
owner/server-thread/authenticated-player checks, and lifecycle snapshot identity/state/generation policy (including
wrong Mind/profile/body, stale generation, and inactive snapshots). GameTests check type identity, saveable registration, normal gravity/collision, lack of
automatic per-instance binding, exact valid binding read/save/load on a fresh entity, wrong-type and partial
malformed-binding invalidity/rejection/evidence preservation across fresh-entity load, skin/body-shape persistence
independently of binding and safe preservation of unsupported appearance and shape NBT, and absence from the host mapping.
These test cases/fixtures were included in validation. Full build and full JUnit recently passed after the movement
fix; the isolated `runGameTestServer -Pms14GameTestDir=build/gametest-lifecycle-movement-final --no-daemon` run
reported 158 GameTests, all required tests passed (`build/gametest-lifecycle-movement-final/logs/latest.log`). These
automated results do not prove a connected handshake, remote-observer appearance, or restart/reconciliation. The
custom character has an explicit WorldStep eligibility path; it is not added to `host_entity_types`. M3 and M2–M7
remain unaccepted until connected owner evidence; see [connected acceptance checklist](connected-acceptance-checklist.md).
The master flag remains default-off.
