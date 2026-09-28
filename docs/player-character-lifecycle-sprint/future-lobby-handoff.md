# Future lobby handoff — readiness-aware character selection

This describes a future architecture direction, not implemented behavior or an acceptance result. The current immediate
first-join route is a temporary opt-in lifecycle path. Do not add a pseudo-lobby or a parallel movement/control route as
part of the current work. The final Ready handshake remains necessary even with a lobby because the client must first
track the selected entity before it can acknowledge readiness.

## Intended lifecycle

1. A new account enters a genuine server-owned `WAITING_CHOICES` lifecycle state. This is an explicit exception to the
   normal invariant that a Mind/body pair is active in play; it is not an `ACTIVE` character session.
2. The connection is carried by a spectator entity while the world is hidden from that player. The carrier is a
   presentation/transport mechanism only, not a second player-control route.
3. The server collects and validates the selected profile and appearance. Client-submitted choices are untrusted input;
   they do not establish profile ownership, body identity, or authority.
4. Once choices are valid, the server spawns the exact selected body and establishes its authoritative binding.
5. The server activates the Mind on that body, then performs Begin/Ready/Commit. The client sends Ready only after it
   can resolve the authoritative custom character body and its player/level state. Delayed/deferred Begin handling must
   continue to account for entity tracking and transient client binding.
6. Only after the server validates Ready and commits the session does the player enter active play and receive the normal
   world view/control. Any failure before commit remains non-active and fails closed; it must not silently create or
   activate a replacement body.

## Boundaries and open design work

- `WAITING_CHOICES` must be represented explicitly in the lifecycle model and durable transition rules. Do not overload
  `ACTIVE`, `OFFLINE`, or `DEAD_CLAIM` to mean lobby waiting.
- Specify disconnect, timeout, duplicate-login, restart, and persistence behavior for the waiting state before
  implementation. In particular, no crash/save disagreement should be automatically repaired by guessing which side is
  authoritative.
- Keep lobby visibility, validated choice collection, authoritative body selection, and readiness commit as one server
  owned flow. Do not expose the world or enable active character movement before commit.
- Preserve the final ready handshake: lobby UI completion alone cannot prove that the client tracks the chosen entity.
- The failed initial connected attempt and required safe retest are recorded in the
  [incident audit](audits/2026-09-28-initial-connected-ready-timeout.md). Crash-time world/profile save disagreement is
  separate deferred work described [here](deferred-crash-save-disagreement.md).

No lobby, pseudo-lobby, profile-selection UI, or parallel movement route is authorized by this architecture note.
