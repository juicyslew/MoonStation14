# Player/character lifecycle experiment

## STATUS

Reconstructed 2026-10-03. **Closed as handoff, not accepted.** Only M0/M1 pure-model work was accepted; M2–M7 remain unaccepted. Runtime gate remains default-off. Closure directs no further work in this sprint.

## ARCHITECTURE

Startup-sampled shared gate controls effects. Implemented paths include authenticated Begin/Ready/Commit, persistent profile/character claims, exact-body return, offline/death claims, and staged ghost control. Missing/ambiguous evidence fails closed, with no automatic replacement body or production lobby.

## IMPORTANT FILES

`current-handoff.md`, `closure.md`, `connected-acceptance-checklist.md`, `deferred-crash-save-disagreement.md`; implementation under `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/`. Proposal/milestones are history, not acceptance.

## SS14 CORRESPONDENCE

Pinned mind transfer, player attachment, NPC AI and cryostorage references inform design. Local offline-marker/grace and persistence policies are adaptations, not general SS14 logout parity. SharedMindSystem/MindSystem remain upstream ownership anchors.

## DECISIONS / WHY

Keep default-off effects and fail closed on crash/save disagreement. No production lobby/role selection, automatic respawn, or operator recovery tool is claimed. Later successful hands/device tests do not certify lifecycle.

## KNOWN GAPS

Connected death/reconnect ghost movement ended sessions twice with retained DEAD_CLAIM; precise cause was not captured. Later tolerance/message changes lack connected retest. Cross-save disagreement remains unresolved.

## VALIDATION

Historical focused lifecycle tests/latest build passed after ghost changes. Earlier isolated 159-test run had one unrelated atmosphere failure. Connected observations are partial; no fresh run or acceptance invented.

## NEXT WORK

Do not resume from old proposal priority language. Any separately authorized follow-up starts with closure, retained failure evidence and unchecked connected checklist.
