# Player Character Lifecycle Sprint — current handoff

**Closed as handoff, not accepted. M0/M1 remain accepted only as pure-model work; M2–M7 remain unaccepted.** The
integrated lifecycle remains default-off. Connected evidence is partial: first join/Ready/Commit and body control worked;
`/gamemode creative` worked. After actual body death, `/ms14dev return` refused to reclaim the dead body, as expected.
Disconnect/rejoin produced a ghost, but moving it ended the ghost session with “Ghost session ended; your dead claim has
been retained” twice. The profile was `DEAD_CLAIM`; the exact ending reason is unknown because the prior generic log did
not preserve it. There was no connected retest after the later ghost motion-tolerance and player-readable messaging
changes. See [sprint closure](closure.md) and the [connected acceptance checklist](connected-acceptance-checklist.md);
unchecked checklist items are not acceptance.

## Connected first-join regression note

Incident timeline, original source diagnosis and validation boundary: [2026-09-28 initial connected
Ready-timeout audit](audits/2026-09-28-initial-connected-ready-timeout.md). The future lobby direction is documented in
the [future lobby handoff](future-lobby-handoff.md); it is not implemented.

The first owner-connected custom-character attempt exposed two handshake timing/policy issues: custom registered bodies
were incorrectly gated on a legacy host-entity mapping, and Begin could arrive before the client player/level was bound.
Both are fixed narrowly: the exact custom entity class is accepted as a visible CHARACTER body (the server still verifies
the authoritative binding), HUMAN prediction uses the client prototype catalog when available, Begin is deferred until
the first server Post tick after login, and the Ready timeout begins at actual Begin send. Do not use recovery workarounds
or automatic respawn. Retry only in a fresh disposable world; stop the server before deleting/recreating a test world and
never edit profile files. The earlier audit's preserve-world recommendation is superseded for this disposable-test
workflow by the newer [Creative carrier-escape audit](audits/2026-09-28-creative-carrier-escape.md). A readiness-aware
lobby remains future work; no lobby was added here. A later connected owner report confirmed
first join completed Ready/Commit and the owner controlled the character. This report is limited to that first-join/control
path; it does not accept M2–M7. Later partial Creative and post-death observations are recorded above.

## Integrated behavior (implementation, not acceptance)

- The existing COMMON `experimentalMindGhostControl` startup-sampled gate controls lifecycle effects. With it off,
  behavior remains vanilla. Lifecycle movement also requires
  `experimentalVerticalSliceMovement=false`; the custom character type has an explicit WorldStep eligibility path
  and is not added to `host_entity_types`.
- First join stages one persistent custom HUMAN character, stable Mind and authenticated Begin/Ready/Commit session.
  The carrier does not retain control and no transient ghost is used. Movement is server-authoritative.
- Clean logout persists OFFLINE before retiring control. Reconnect requires the exact same loaded bound body and stable
  Mind. One recorded-chunk load attempt is bounded by a 20-server-tick same-body visibility retry; it does not search
  or spawn replacements. A healthy ordinary restart should reconnect the same body.
- Temporary development escape (implemented): an operator (permission level 2+) may request
  Creative while owning a lifecycle CHARACTER session. The server first durably parks that exact body OFFLINE at its
  current position/appearance, advances the generation, and retires the session; on success, Stop is sent and the
  carrier camera is restored before vanilla Creative proceeds. Persistence/CAS failure cancels the mode change without
  losing the session. While parked, Survival/Adventure requests are refused. `/ms14dev return` on the same operator
  connection reclaims the same already-loaded body and Mind; if it cannot, the operator stays in Creative and receives
  retry/logout-rejoin guidance. This is a development escape, not production gameplay or a lobby/MS14-native developer
  mode. The observed `/gamemode creative` request worked. `/ms14dev return` after the owned body had actually died
  refused to reclaim it, which is correct for a dead claim and is not a test of return from a parked living body. Logout
  while parked leaves the profile OFFLINE for ordinary same-body reconnect.
- The owner-only `/ms14char skin default|alex` and `/ms14char shape wide|slim` commands persist selected appearance;
  synchronized rendering must still be verified by a remote observer. After 60 seconds offline, the synchronized
  `Offline` nameplate appears.
- Confirmed connected death durably claims and retains the corpse, then starts a fresh ghost on the same Mind.
  Offline death persists `DEAD_CLAIM`; that account starts a fresh ghost on reconnect. If the corpse chunk is unloaded,
  the login ghost may spawn at the already-loaded carrier location. Death evidence is actual damage/death, not entity
  removal.
- Same-account concurrent login and ambiguous/missing/duplicate ownership fail closed. Crash-time world/profile save
  disagreement remains fail-closed and is future work; there is no operator recovery tool.

## Validation boundary

Focused lifecycle tests and the latest `gradlew.bat build --no-daemon` passed after ghost motion-tolerance and
player-readable messaging implementation. The earlier isolated
`runGameTestServer -Pms14GameTestDir=build/gametest-lifecycle-creative-stopped --no-build-cache --no-daemon` ran 159
tests with one unrelated atmos failure, `skyexposedcellusesambientandrejectsinjection`; it was not a full GameTest pass.
Earlier build-file contention was resolved by stopping client/server before compilation. Connected results remain
partial as described above; no connected retest followed the latest ghost changes. A first-world same-server
UNINITIALIZED reconnect gating fix was implemented and tests passed, but final connected success evidence was not
established. The code still has an additional `validMotion` check: the owner rejected out-of-scope ghost movement
validation and explicitly stopped the requested removal, so do not claim it was removed. Only M0/M1 pure-model work is
accepted; M2–M7 require connected evidence. See the checklist for the original smoke scope; do not resume this sprint.

No source, repository config, power, or atmosphere change is authorized by this documentation handoff. For OWNER connected
testing only, follow the [connected acceptance checklist](connected-acceptance-checklist.md). Worlds are disposable and
may be deleted/recreated between tests; stop the server before deleting a world. Do not edit profile files. Edit only
`config/moonstation14-common.toml` in the installation that will actually run it (which may be the gitignored `run/config`
in a development setup). Keep production default-off; do not enable the feature on production. A real server-owned lobby
and readiness/character-selection flow remain future architecture, as described in the [future lobby handoff](future-lobby-handoff.md).
