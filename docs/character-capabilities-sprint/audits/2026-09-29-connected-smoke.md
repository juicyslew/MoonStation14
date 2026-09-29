# 2026-09-29 owner connected smoke — bounded evidence

This is an owner-run, two-client observation on agent-02, **not** acceptance of connected ghost control, lifecycle closure, cross-path camera guarding, or scale. The log was inspected read-only at `run/logs/latest.log`; visual observations below are attributed to the owner rather than inferred from server logs. This run preceded the narrow DEAD_CLAIM staging correction.

## Ten owner observations

1. Clicking another harness while committed to a CHARACTER did not switch the camera/controlled body; the committed-character click was blocked without a reported kick. This is a **provisional pass for this single click observation**, not a proof across routes or 20 players.
2. The first client died and was immediately kicked instead of entering a ghost.
3. The second client died and was immediately kicked instead of entering a ghost.
4. Neither client obtained a controllable ghost on death; reconnect of the second client also failed ghost entry.
5. The owner saw an **Offline** tag in Creative.
6. A dead body was retained after death.
7. `/ms14dev return` from Creative returned the carrier to its existing character.
8. After Creative was changed to free Spectator, vanilla target follow was allowed. This is **free Spectator**, not an implemented production ghost-follow feature.
9. `/ms14dev return` from free Spectator refused with the exact message `No eligible parked lifecycle character is attached to this Creative carrier.` Switching back to Creative allowed return. This is the intentional Creative-only dev escape mode gate, not a production ghost-follow result.
10. The owner noticed unusually excessive movement speed around jumping against blocks and falling; no movement cause was diagnosed.

## Read-only log and code correlation

- `run/logs/latest.log` at 00:01:43 and repeatedly through 00:12:53 logs `Committed spectator guard suppressed route=set-camera reason=foreign-camera-target` with committed owner counts. This corroborates suppression on that camera route; it does **not** establish that a possession or ownership transfer occurred, or prove every possible click path is guarded.
- At 00:06:13 (Dev) and 00:13:57 (TestPlayer2), the server reported `initialized lifecycle context is unavailable` while handing saved character death off to a ghost, then disconnected each client with `Your character has died, but ghost entry could not be prepared safely. Log out and reconnect to retry, or contact an administrator.` Both historical death kicks follow this logged **staging diagnostic, not ghost motion**. At 00:14:58 TestPlayer2 reconnected, the same staging error appeared on ghost login, and the client disconnected again. Retained corpse and death claim were preserved; these failures prevented any connected ghost-motion result in this run.
- The log shows a successful return message at 00:09:34 and again at 00:12:30; Creative/Spectator changes at 00:10:15, 00:10:18 and 00:11:11; the exact free-Spectator return refusal at 00:11:00. The Offline tag, retained body, follow behavior and movement feel remain owner observations rather than claims proved by these log lines.
- Code root in the failed build: on fresh startup `LifecycleServerContext.initialized` was a final value set by `envelope != null`. First enrollment could create the primary while that startup flag stayed false; the earlier `LifecycleDeadClaimGhostStager.prepare` gate checked the stale flag. The targeted correction now in the worktree removes **only** that stale startup `initialized()` gate, re-reading and validating the exact current-primary `DEAD_CLAIM` immediately before staging for both connected death and reconnect; absent, stale and corrupt evidence fail closed. A further real-store JUnit test starts with a null envelope, enrolls, promotes and claims death, then validates a fresh primary read while `initialized()` still remains false; it also checks absent, stale and corrupt cases. Coordinator/worker reported `gradlew.bat test compileGametestJava --no-daemon` passing and `git diff --check` clean (line-ending warnings only). This is claim-validation evidence, **not actual ghost acceptance**. **No connected retest of the corrected build has occurred.**

## Next owner gate

After rebuilding and restarting the **rebuilt agent-02 server and both clients**, only the owner should retest in a **disposable world**: connected death to ghost entry and motion, reconnect to ghost entry and motion, then entity-click/camera behavior without ownership changes or kicks. Do **not** assume the old dead world is safe. If staging still fails, capture stable reason-coded logs and store/profile state **read-only**; do not edit saved profiles or attempt repair. Keep the current click result provisional and the death/reconnect gate open until this retest. Free Spectator return refusal is the intentional current Creative-only gate. For the speed report, first run focused jumping-against-blocks/falling tests and review SS14 movement fidelity: the shared motor may cap projected wish rather than resultant velocity and may retain airborne momentum, but this is a hypothesis only; movement remains open and must not be changed on this evidence.
