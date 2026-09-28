# Authenticated player–character–Mob Harness lifecycle — proposal

**Status — 2026-09-28:** active, owner-authorized sprint planning and implementation, proceeding only through the conditional stage gates below. M0 and M1 are accepted; M2, M3, M4, and all later milestones are not accepted. This authorizes work within the documented scope; it does not mean any unaccepted stage is implemented or accepted. M0 was documentation-only and read-only against source/config. See the [M0 contract and verified local source map](m0-contract.md), the [current roadmap priority](../next-systems-roadmap.md), the [bounded Player Body Control closure](../player-body-control-sprint/closure.md), and the [deferred device UI platform proposal](../device-ui-platform-sprint/proposal.md).

### Runtime opt-in boundary (applies to every new effect)

Every new runtime effect in this sprint—including automatic authenticated join handling, first-join profile/body creation, persistence/recovery, appearance updates, offline handling/indicators, death/recovery transitions, packets, and supporting integrations—must be behind the existing COMMON config `Config.EXPERIMENTAL_MIND_GHOST_CONTROL` (`experimentalMindGhostControl`, default `false`) and the existing server-startup-sampled `MindGhostStartupGate`. The gate is sampled at server startup and closes at server stop; configuration edits do not hot-toggle it. When the gate is off, there is no automatic join/claim, lifecycle persistence or reconciliation, character spawn, or game-mode change. No separate lifecycle flag may be added.

The existing op-level `/ms14 mindghost` debug start/stop/configure/possess path remains operator-only and unchanged; automatic first eligible join is a separate permissionless path, permitted only while the shared master gate is enabled and after lifecycle acceptance. Do not route automatic join through the debug command. Preserve the independent `experimentalVerticalSliceMovement` conflict gate: existing debug start continues to reject while that legacy gate is on. It remains a separate transitional flag, not a substitute for or expansion of the lifecycle master gate.

## Goal and bounded MVP

Establish one authenticated account/profile owning one selected, persistent character identity and one custom-created character Mob Harness body, with reliable spawn, automatic Mind possession, logout, reconnect, death, and persistence behavior. A first-time eligible world join creates or resolves a valid default profile, spawns the custom character body, and automatically possesses that same body; it must not leave active play on a vanilla Villager/Pig carrier or a `ServerPlayer`. The identity of the custom entity prototype and the selected per-instance character profile/appearance are separate concerns.

Keep this to one profile, one supported species/custom human body, and the existing supported modes. Start with a valid default profile and limited, server-validated appearance choices stored persistently and visibly applied to the body. An MVP self-appearance command or small editor can update the current body and persisted profile; a polished/full character-creation menu is not necessary. This trades presentation polish and broad choice for proving profile validation, persistence, and actual body rendering before building a UI. The recorded planning default is live edits to the current body.

The central invariant is exclusive control: **at most one Mind is bound to a body, and at most one authenticated session generation controls a player Mind.** A logged-out living player-owned body keeps that player's same stable Mind bound, but disconnected and dormant: there is no authenticated session, voluntary player input, or AI control. This remains true even when the mob would normally have AI. Reconnect reattaches a new connection generation to that same Mind and body; it does not create a new Mind or body. Never attach two Minds to one body.

## Identity, session, and persistence model

Do not conflate an authenticated network session with a Mind. Recommend a stable profile-associated `MindId` for the character lifecycle, retained across reconnects of that same living character and when moving to a death ghost. The connection is ephemeral: each successful exclusive login/claim gets a monotonically increasing connection generation; input and lifecycle requests tagged with stale generations are rejected. Preserve the existing Mind identity on reconnect rather than manufacturing a replacement Mind; current bounded code uses a random in-memory Mind and `logout()` deletes it, so the lifecycle work must add persistence/rehydration and separate disconnect from destructive logout.

Persist a versioned record keyed by authenticated account UUID and profile ID, including stable Mind ID, selected character profile, body UUID and dimension/location, offline/dead/claim status, and connection generation. Persist appearance/profile data and enough transition/recovery information to reconcile records with world/entity saves. Claims and body/Mind transfers run on the server thread as exclusive transitions. On restart, reconcile profile records, entities, and saves; back up or fail closed on ambiguous ownership rather than guessing or spawning a duplicate. A profile claim remains reserved while its body is offline and dormant.

If a known body's chunk is unloaded during login, do not silently spawn a duplicate and do not send a living player to a ghost against the owner restriction. Boundedly resolve/load only the known saved body using a server-owned policy; if unavailable or ambiguous, fail closed/defer active entry with an explicit actionable error. Do not invent a lobby or a no-harness active-play fallback.

Prototype identity remains distinct from per-instance profile identity: `CharacterIdentityAttachment` currently stores a prototype key only, and `host_entity_types` remains the vanilla testing bridge. Add an explicit per-instance spawn binder for the custom Mob and keep old HUMAN player binding plus the default-off legacy route until later replacement proof; do not remove either now. Preserve the Player Body Control closure's legacy flag and do not imply that its bounded experiment passed lifecycle acceptance.

## Lifecycle policy to prove

### Join and reconnect

- Initial eligible world spawn: resolve/create account/profile/Mind record; create the custom character Mob Harness from the selected supported prototype; bind its per-instance profile/appearance; apply and sync appearance; then automatically Mind-possess it. Use a Begin/Ready/Commit-style acknowledged handoff with rollback so the session never commits to an unvalidated/missing body. Do not spawn `ServerPlayer`, vanilla Villager, or Pig as the character.
- Reconnect while the recorded body is alive: claim and possess that **same body UUID** with the stable player Mind; never duplicate-spawn or create a replacement Mind. The new connection generation reattaches to the Mind that remained bound to the body while offline.
- Only one concurrent login may hold the current connection generation. A second login must not race or create a second body. Recorded default: reject the new claim until the old generation is revoked, then allow an explicit retry.

### Logout, offline body, and controller policy

- Logout/disconnect leaves the living body in the world with its same durable player Mind still bound, while the session is detached. It stays dormant with no voluntary player control/input and no AI movement or input, even if the mob normally has AI. This is the owner's chosen local policy and broadly matches the generic SS14 `NPCSystem` guard: its HTN AI wakes after player detachment only when `MindContainer` has no mind. It is not a claim that all SS14 content has universal dormancy visuals or behavior. Cryostorage is a special case, not a general logout rule.
- Dormant does not mean frozen or invulnerable: the body remains subject to ordinary world physics, status, and health/damage effects. Preserve server ownership/retention of the offline body and its profile claim through logout and restart. After a configurable offline grace, render a synced visual marker on the offline body. The marker/grace is local policy; do not present cryostorage-specific behavior as universal SS14 logout behavior.
- A controller lease or marker must persist as offline-owned state, not be ended in a way that restores the body's previous Mob AI. In particular, `GroundedHarnessLease.close()` currently restores `priorNoAi`; M5 must explicitly redesign offline lease ownership and add a stop gate that prevents voluntary and ordinary Mob AI movement/input after logout. Do not call `close()` on logout if that wakes AI. Reconnect may resume player control only after the new connection generation is authorized; there must never be two active controllers.

### Death and ghosts

- Connected death or other eligibility loss based on **actual death**, not voluntary alive ghosting, forces the same player's stable Mind to a **new ghost body**. The dead character body remains in-world and is not silently deleted/repossessed.
- If the character dies while the account is offline, record the death claim; reconnect detects it and transfers the stable player Mind to a fresh ghost. Never repossess the corpse or grant an automatic new living body.
- Ghost requesting a role or new body is future work requiring its own policy/sprint. Default behavior is no respawn/new character body. Existing debug ghost facilities remain behind the shared default-off master gate; disclose the existing ghost collision gap and do not remove the independent legacy flag.

## Pinned reference evidence and local boundaries

References below are conceptual evidence at SS14 commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, not APIs to port or proof of local parity:

- `Content.Server/GameTicking/GameTicker.Player.cs` attaches/reconnects a player to a Mind.
- `Content.Server/Mind/MindSystem.cs` handles Mind transfer and detach.
- `Content.Server/NPC/Systems/NPCSystem.cs` controls NPC AI through HTN and wakes it after player detach only when `MindContainer` has no mind; this is not a general separate-Mind disconnect takeover.
- `Content.Shared/Bed/Cryostorage/CryostorageComponent.cs` scopes grace to cryostorage; it does not establish a universal logout timer or visual marker.
- `Content.Server/Ghost/GhostSystem.cs` contains ghost/death commands and rules; alive ghosting is not actual death eligibility.
- `Content.Server/Station/Systems/StationSpawningSystem.cs` applies a character profile during spawning.

Locally, [`CharacterIdentitySystem`](../../src/main/java/com/juicyslew/moonstation14/ms14/character/CharacterIdentitySystem.java) resolves/enrolls prototype identity, while [`CharacterData`](../../src/main/java/com/juicyslew/moonstation14/component/codec/json/CharacterData.java) is policy data, not account/profile/Mind state. The [closure record](../player-body-control-sprint/closure.md) explicitly says the bounded control experiment is not automatic join control or accepted lifecycle. Do not edit atmospherics or prototype YAML/importer as part of this plan.

## Conditional stages and independent gates

Stages are authorized for planning/implementation under this sprint, but execution advances only after the relevant prior gate passes. A blocked stage returns evidence and does not authorize workarounds or scope expansion. Authorization is not evidence of completion; record implementation and acceptance separately at each gate.

**Owner-approved narrow order exception (2026-09-27):** the custom-character Mob entity/identity-binding foundation, and only that foundation subset of M3, is an approved limited prerequisite that may be built before M2's final loaded-body proof. This exception permits establishing the custom Mob identity and explicit per-instance spawn binding needed by that proof; it does not pass or complete M3, accept renderer/appearance behavior, or otherwise waive M2 requirements. After this foundation, return to M2 and complete its gate before proceeding. Automatic join and every runtime lifecycle activation remain blocked until both M2 and full M3 gates pass. All runtime effects remain behind the existing COMMON startup-sampled `experimentalMindGhostControl` gate (default `false`) and its existing legacy movement-conflict check; this ordering exception is not runtime activation authority.

### M0 — API/source/decision investigation

Map local authenticated identity, world/entity save behavior, server-thread and join Begin/Ready/Commit seams, Mind/Harness transfer, per-instance spawn binding, appearance/render synchronization, chunk availability, and restart recovery. Review the pinned source paths above. Record decisions/defaults, fail-closed error UX, and schema migration/backup strategy in the [M0 contract](m0-contract.md).

**Gate:** written verified local hook map, no assumed UI/session/body equivalence, transition matrix, data schema and recovery design, test seams, and recorded choices for behavior needed by M1, including bounded visible appearance options for M3. No source or config change in M0. See [M0 contract](m0-contract.md).

### M1 — pure profile/claim/Mind and transition tests

After the prior gate passes, implement the versioned profile/claim model, stable Mind identity, ephemeral exclusive connection-generation rules, disconnect distinct from destructive logout, and pure state transition reducer. The required initial-creation API must create a Mind already attached to a registered eligible `CHARACTER` Harness, or provide an equivalent atomic initial attach. The current `BodyControlRegistry.createMind` only creates a Mind with an eligible `GHOST`; do not route initial character creation through a transient ghost or create an active Mind without a Harness. Test exclusive initial-character creation, duplicate/invalid body rejection, failed authorization, stale generation rejection, offline retention of the same attached player Mind, same-Mind reconnect without a replacement body/Mind, dormant/no-voluntary-control state regardless of ordinary Mob AI, actual-death eligibility versus voluntary alive ghost, and forbidden two-Minds/duplicate-body states. AI Mind adoption for never-player-owned NPCs is out of scope and requires a separate future owner decision.

**Gate:** deterministic focused tests cover legal and rejected transitions; no world spawn/persistence integration yet; no two-Mind body state is representable or accepted.

### M2 — persistence, load, crash, duplicate, and unloaded-body tests

Establish and validate the versioned durable account/profile/Mind/body identity store, appearance data, connection generation, offline status, and recovery records. Validate ordinary store save/load, generation/CAS and backup behavior, strict schema rejection, duplicate record detection, and the fail-closed boundary when durable claims disagree with available world/entity evidence. Mismatch evidence must stop lifecycle activation; never auto-repair, choose a winner, create a replacement body, or fall back to a ghost. There is no operator recovery command today. The rare cross-save crash mismatch and separately planned operator diagnostics/recovery/reconciliation are documented in the [deferred crash/save disagreement note](deferred-crash-save-disagreement.md); they are not an M2 completion criterion. Do not require end-to-end world-save crash/restart proof as a prerequisite to M4; M5 owns the normal healthy restart/reconnect proof. M2 does not promise cross-save crash atomicity or automatic crash reconciliation. Continue to prohibit forced unbounded chunk loads, duplicate spawn, corpse possession, and unauthorized ghost fallback.

**Gate:** durable-store foundation and focused fail-closed mismatch tests pass; unknown, unavailable, or ambiguous identity cannot silently become a new character or activate lifecycle control. This gate is a prerequisite to first automatic spawn, not proof of cross-save crash atomicity, operator recovery, or end-to-end world restart recovery. No automatic runtime caller exists before M4.

### M3 — custom Mob Harness, appearance, and render sync

Create one custom human character Mob Harness path and a per-instance explicit spawn binder while retaining `host_entity_types` as testing bridge and old HUMAN binding/default-off legacy route. The approved pre-M2 limited prerequisite is only the custom Mob entity/identity-binding foundation (prototype identity plus explicit per-instance binding); stop there and return to M2. It does not accept appearance, renderer, texture/model, or observer-visible behavior. After M2 passes, the remainder of M3 starts with valid default appearance and at least two bounded, visibly distinct options (for example, skin palette and hair style or color); the owner selects/finalizes which options to offer at M0. Validate choices on the server, store them, apply them to the actual body, and synchronize/render them to remote observers. This does not assume that a model or texture is already available. Test invalid choice rejection, profile persistence, body load, and observer-visible updates in owner-bound prototype/render GameTests.

**Gate:** actual custom Mob—not `ServerPlayer`, Villager, or Pig—has one correct prototype identity and distinct per-instance profile/appearance; tests establish visible synchronized appearance and persistence. No full editor required.

The limited entity/identity-binding foundation alone is not this M3 gate. Full M3 acceptance remains after M2 and requires the appearance/render proof above. M4 automatic join remains blocked until both the M2 and full M3 gates pass.

### M4 — automatic join/spawn/possession handoff

Integrate authenticated join through Begin/Ready/Commit: resolve profile, resolve known living body or create only on legitimate first join, then stage the custom body and register it as an eligible `CHARACTER` Harness before binding selected profile and appearance. Use the M1 initial-creation API to create the stable Mind already attached to that registered body, or an equivalent atomic initial attach, then commit the connection generation. Do not spawn/attach a transient ghost and do not create an active Harness-less Mind. Roll back the staged body and any partial registration/claim if spawn or body registration fails; rollback any other failed precondition as well. Test one-character versus carrier identities and ensure the carrier remains distinct; exercise duplicate/replayed join, stale commit, spawn and body-registration failures, and rollback.

**Gate:** the first actual eligible join produces exactly one custom character Mob and the authenticated player Mind automatically possesses it; failures cannot commit a partial body/session or make a carrier the selected character. M4 proves first spawn only; it does not claim logout/restart/reconnect acceptance.

### M5 — logout → durable dormant lease/indicator → same-body reconnect

End-to-end prove the bounded normal lifecycle: connected first join → custom body → automatic possession → logout → ordinary server restart → reconnect to the same living body. Disconnect leaves the living body in-world with the same durable player Mind attached and session detached; ordinary or previously enabled Mob AI does not resume, and no voluntary player control/input is accepted. The offline lease/marker/dormant stop gate survives logout and ordinary restart, then reconnect claims and possesses the same body UUID and same Mind with a new authorized connection generation. Prove ordinary world physics, status, and damage still affect the dormant body; the synced offline marker appears after the selected configurable grace; there is no duplicate body/Mind or two active controllers; and stale-generation input is rejected across logout/reconnect races. Explicitly test that logout does not call `GroundedHarnessLease.close()` in a way that restores `priorNoAi` and wakes AI: redesign offline lease ownership and prove the stop gate blocks AI movement/input until authorized reconnect. Preserve fail-closed behavior for missing, unloaded, duplicate, or ambiguous body evidence. A rare cross-save crash mismatch remains fail-closed and is separately documented in the [deferred crash/save disagreement note](deferred-crash-save-disagreement.md); cross-save crash atomicity, operator recovery, and automatic reconciliation are not M5 completion criteria. This is normal healthy restart/reconnect evidence, not a promise of power-loss atomicity or arbitrary crash recovery. AI takeover/transfer is not an MVP or M5 branch. AI Mind adoption for never-player-owned NPCs remains out of scope pending a separate owner decision.

**Gate:** connected end-to-end first-join → custom-body possession → logout → ordinary restart → same-body/same-Mind reconnect evidence demonstrates exclusive controller ownership, stop-gated dormant behavior despite ordinary Mob AI, ordinary physics/status/damage, marker sync, and no duplicate. Missing, unloaded, duplicate, or ambiguous body evidence remains fail-closed. This milestone does not establish cross-save crash atomicity, operator recovery, automatic reconciliation, arbitrary crash recovery, or power-loss atomicity. The configurable grace duration and marker appearance remain owner choices.

### M6 — death → fresh ghost, offline death, and no implicit respawn

Test connected actual death causing the same player Mind to transfer to a fresh ghost while dead body remains, and demonstrate voluntary alive ghost does not trigger death eligibility. Test offline death followed by reconnect without corpse repossession; reject automatic living respawn/new character. Keep role/new-body request paths unimplemented or explicit stubs with no grant. Existing debug ghost remains default-off until the connected lifecycle path is proven; record ghost collision gap.

**Gate:** fresh ghost identity/transfer and offline death recovery use the stable player Mind; no ghost role request or body grant bypasses future policy. Existing debug ghost remains behind the shared default-off master gate; record the ghost collision gap.

### M7 — connected owner, two-client, latency, negative, and full acceptance

Run owner-connected multi-client sessions covering initial join, reconnect, second concurrent login, logout/restart, body visibility and marker, dormant stop gate despite Mob AI, connected/offline death, stale packets/generations, duplicate attempts, chunk unload, unsupported crash-state mismatch, latency/disconnect races, rollback, and negative authorization cases. Verify no milestone was skipped and record server/client evidence and limitations.

**Gate:** owner reviews recorded end-to-end outcomes; all mandatory acceptance passes on supported modes, or proposal returns with explicit blocked cases. Unit/GameTest success alone is not connected acceptance.

## Scope exclusions

- Rounds/lobby, roles, respawn policy, or automatic new living bodies; ghost role/body requests are future separately designed work.
- Inventory/hands, broad gameplay actions, full polished character editor/menu, and the separate device UI platform spike.
- Chemistry, atmospherics, prototype YAML importer/parity, edits to atmosphere docs/source, or changes to Player Body Control closure.
- Removing `host_entity_types`, old HUMAN player binding, legacy/default-off route or ghost flag before their later replacement proof.
- Treating vanilla `ServerPlayer`, Villager, Pig, or an NPC HTN controller as the custom character body or a formal AI Mind. AI Mind adoption for never-player-owned NPCs is a separate future decision.

## Recorded owner-approved defaults

The owner accepted the recommended defaults for planning. These are proposed contract choices to implement and verify, not claims that behavior already exists:

1. **Offline grace and marker appearance:** configurable 60-second grace and a small synced visual badge. This is a provisional proposed default, not a user-specified detail; no universal SS14 logout visual is asserted.
2. **Appearance timing:** server-validated visible options can update the current body live and are persisted; no full menu is assumed.
3. **Restart/body persistence:** persist body UUID/location and reconnect across an ordinary healthy restart; never spawn a duplicate if resolution is missing or ambiguous. A rare crash-time entity-world/profile-file mismatch remains fail-closed; see the [deferred crash/save disagreement note](deferred-crash-save-disagreement.md). Operator diagnostics/recovery and automatic reconciliation are future work; cross-save crash atomicity is not promised.
4. **Concurrent login:** reject the second simultaneous claim while the existing session generation remains valid; permit retry only after revocation.

AI takeover of a player-owned body on logout is not an open decision: the selected policy is that the same player Mind remains bound while dormant and disconnected, regardless of the mob's ordinary AI. Offline death leads to a death claim and fresh ghost for that stable Mind on reconnect. Adoption of AI Minds for never-player-owned NPCs is out of scope pending a separate decision. These choices do not waive any stage gate or acceptance evidence.
