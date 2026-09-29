# Ghost-session mismatch: evidence, diagnosis, and narrow fix

**Bounded guard and terminal diagnostics implemented and automated tests reported; owner-only connected checks remain open.** See [current handoff](current-handoff.md) for current evidence and remaining gates; the planning language below predates this implemented subset. This is a targeted
exception to the broader ghost-hardening deferral, limited to diagnosing and preventing false ghost-session kicks. It
does not reopen lifecycle acceptance, enable a gate, or authorize a general ghost rewrite.

## What the evidence establishes

The [lifecycle closure](../player-character-lifecycle-sprint/closure.md) records that first join reached Ready/Commit and
body control worked. After the character died, its `DEAD_CLAIM` was correctly retained. Disconnect/rejoin produced a
ghost; moving it ended ghost control twice with “Ghost session ended; your dead claim has been retained.” The exact end
reason is **unknown**: the earlier generic log did not record it, and there was no connected retest after later motion
tolerance and player-readable-message changes. The message alone does not identify a validation failure or prove that
the durable claim was damaged.

There is a separate Creative-mode incident, not the same demonstrated ghost-movement failure. The
[Creative carrier-escape audit](../player-character-lifecycle-sprint/audits/2026-09-28-creative-carrier-escape.md) records
Begin/Ready/Commit success, then `/gamemode creative`, a kick about 90 ms later with “Character session requires explicit
recovery,” and a persisted `ACTIVE` profile. That explains ordinary rejoin refusal for that incident. An operator-only
park-before-Creative escape was subsequently implemented, but has not had an owner-run connected verification. Do not
attribute the older quoted Creative message to a current source branch: it does not literally match the current
`LifecycleGhostSessionControl` end messages.

## Current code paths to discriminate

In `LifecycleGhostSessionControl`, stale-epoch or uncommitted `Intent` packets are ignored (lines 112–113); failed intent
eligibility ends with `intent_validation_failed` (114–117); exhausted intent gate ends with `intent_gate_exhausted`
(124–126); tick-time authority/eligibility loss ends with `session_authority_or_eligibility_lost` (132–142); failed
`validMotion(actual, displacement, resolved)` ends with `invalid_motion` (163–167); and an exception during movement
processing ends with `movement_processing_failed` (171–173). Other expiry/send paths have their own reasons. These are
candidate branches to instrument, not a retrospective explanation of the old kicks.

`validMotion` currently requires bounded finite coordinates and displacement, a speed bound, and agreement between
actual and resolved position within `1.0e-6` (282–303). The mismatch must be reproduced against actual collision-resolved
ghost movement before changing that validator. Preserve the owner's prior rejection of out-of-scope ghost-motion
validation and the explicit stop to its removal; do not remove it merely to suppress kicks.

The current `end()` path attempts `returnGhostToDeadClaim`, logs a reason for disconnecting ends, unregisters/discards
the ghost, and disconnects even if returning the claim succeeded (225–248). When return fails, it disconnects with
administrator-attention guidance. Reason-coded evidence should distinguish the trigger from the recovery result; a
successful `DEAD_CLAIM` return is not evidence that the trigger was benign, and a kick is not evidence by itself that
claim recovery failed.

## Owner camera/spectator decision and reported click

The owner decision is to keep vanilla `Spectator` game mode and `FOLLOW` the exact currently owned ghost/character camera
target for now. In the exact 1.21.1 source, `ServerGamePacketListenerImpl.handleTeleportToEntityPacket` handles Spectator
entity selection by teleporting the carrier with `ServerPlayer.teleportTo`, which calls `setCamera(this)`. Separately,
`ServerPlayer.attack` while Spectator calls `setCamera(target)`. These paths must not be conflated: the packet path can
move the carrier and reset the camera, rather than necessarily changing only the camera. Neither description establishes
a proximity check. A harness report said that clicking another mob moved something, based on remembered state; which
path ran, what moved, and whether ownership changed remain unproven without a log. Do not infer an ownership transfer.

The guard implementation currently in progress intercepts both the packet teleport and foreign camera switches for an
exact committed ghost/character session. It is intended to preserve the owned camera target, leave ordinary free
Spectator and actual Creative behavior unchanged, and never grant possession or transfer ownership on a click. Identity
of the committed owned target, not player-to-body proximity, governs the camera-switch policy. This implementation is
pending tests and owner-only manual acceptance; it is not a validated fix. Explicit SS14 ghost-follow-other-entity is a
separate future, owner-authorized, view-only action: no Mind/body ownership transfer, and not required for this guard.

## Proposed diagnosis and test gates (not completion claims)

Keep the lifecycle and experimental capable-CHARACTER paths in scope; do not use this camera guard as authorization to enable either path or rewrite lifecycle closure. Body actions stay prototype-driven and SS14-faithful where inspected. Add a planned, reason-coded diagnostic that records which vanilla path was attempted (packet teleport or camera switch), the exact camera target identity and expected owned ghost/character identity, committed-session state, mode, and whether it was suppressed. Distinguish this from existing ghost-session terminal/recovery reason codes; avoid client-controlled log text and sensitive data. This evidence is needed to establish what occurred in the reported click, including carrier position, camera target, and ownership.

Concrete gates before claiming a fix:

1. Controlled tests prove packet teleport and foreign `setCamera` attempts are suppressed/fail closed during an exact committed controlled ghost/character session, without moving the carrier or replacing its owned camera. A mismatched/missing owned target must not select the clicked entity.
2. Verify vanilla packet teleport and camera selection remain available to an ordinary free Spectator and that actual Creative behavior is unchanged; cover both lifecycle and experimental paths without enabling an unapproved gate.
3. Verify the owner can keep/follow their own camera target, then exercise reconnect and post-death ghost movement. Record camera identity and reason-coded terminal/recovery diagnostics separately; retain claim/ownership assertions.
4. Request owner-only connected-client checks for the camera guard, ordinary Spectator/Creative controls, reconnect, and post-death ghost movement. These remain unchecked until run by the owner; the assistant does not perform manual gameplay.

This camera work does not establish a ghost-motion cause. Keep the existing `validMotion` bounds and ownership checks until evidence demonstrates a benign mismatch and the owner approves a precise policy; do not remove `validMotion` merely to suppress kicks. No lifecycle closure is changed or claimed complete here.

## Narrow proposed work, early in this sprint

Add an early **M0G — ghost-session mismatch diagnosis** stage (before M1 in `milestones.md`) with these bounded outcomes:

1. Give every terminal branch a stable, specific reason code, including relevant server-side facts needed to discriminate
   the branch (for motion: before/actual/resolved position, displacement, bound result and tolerance result; for authority:
   expected/observed epoch, profile state and exact-session/body checks). For the separate camera-click branch, capture the
    attempted vanilla path, exact camera target and expected owned target plus committed-session/mode/suppression state. Avoid client-controlled log
   text and sensitive data. Keep one structured terminal diagnostic per session and record recovery result separately.
2. Define a narrow response policy: stale/duplicate client actions are rejected or resynchronized, not grounds by
   themselves for ending the session; correctable prediction/motion mismatch may reconcile to the server-authoritative
   position only when exact session, epoch, durable ownership and body authority remain sound; ambiguous durable ownership,
   duplicate ownership, epoch disagreement or save/CAS disagreement must preserve the dead claim and fail closed with a
   diagnostic. Never invent/reanimate a body to make recovery appear successful and never permit two owners.
3. Reproduce `validMotion` behavior empirically with real ghost locomotion/action input and collision conditions. Retain
   bounds and ownership checks. Change or narrow the motion rejection only if evidence demonstrates a benign mismatch and
   the owner approves the precise policy; do not blanket-remove validation.
4. Add controlled failure-branch GameTests proving benign ghost movement does not falsely disconnect while invalid or
    ambiguous ownership still fails closed. Separately test both spectator paths above, including fail-closed behavior in
   committed sessions versus vanilla free Spectator and unchanged actual Creative, and ability to retain the owned camera.
   Then request owner-only connected-client movement checks after death/reconnect and Creative park/return, plus camera
   checks after reconnect and post-death ghost movement. The assistant does not perform manual gameplay; no connected
   success is assumed.

This is diagnostic and policy planning, not authorization to edit lifecycle ownership, recovery, or movement code. If the
targeted work requires changing lifecycle ownership or previously closed lifecycle-sprint code, stop and obtain owner
authorization and isolation for that implementation first. Broader ghost hardening remains deferred. The Creative
park-before-mode-change fix is separately unverified; include its owner-only connected case, but do not expand this work
into a Creative or lifecycle rewrite.

## Exit evidence and remaining risk

Record exact trigger and recovery reason separately for each controlled branch; pass deterministic tests for benign motion,
stale/duplicate actions, invalid motion and authority/persistence disagreement; and retain owner-only connected checks as
unchecked until the owner runs them. Until then, the historical movement kick's cause remains unknown, the proposed
reconciliation policy is unimplemented, and lifecycle ghost behavior is not accepted.
