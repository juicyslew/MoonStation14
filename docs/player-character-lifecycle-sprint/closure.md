# Player Character Lifecycle Sprint — closure

**Closed as a handoff, not accepted.** M0/M1 remain accepted only as pure-model work. M2–M7 are unaccepted; connected
evidence did not establish the complete lifecycle. The default-off gate remains in place. This records the sprint boundary,
not authorization for additional implementation or testing.

## Connected observations

- First join completed Ready/Commit and body control worked. `/gamemode creative` also worked.
- `/ms14dev return` after the character body actually died refused to reclaim it; that refusal was correct for a dead body.
- Disconnect/rejoin produced a ghost. Moving it ended the ghost session with “Ghost session ended; your dead claim has
  been retained” twice. The profile was `DEAD_CLAIM`. The exact ending reason is unknown because the prior generic log
  did not capture it.
- No connected retest followed the later ghost motion-tolerance and player-readable messaging changes. The first-world
  same-server `UNINITIALIZED` reconnect-gating fix was implemented and its tests passed, but final connected success was
  not established. The code still contains an additional `validMotion` check: the owner rejected out-of-scope ghost
  movement validation and explicitly stopped the requested removal. It was not removed.

## Automated evidence and boundaries

Focused lifecycle tests and the latest `gradlew.bat build --no-daemon` passed after the ghost tolerance/messaging changes.
An earlier isolated run had 159 GameTests and one unrelated atmos failure; it was not a full GameTest pass. See the
[connected acceptance checklist](connected-acceptance-checklist.md); its unchecked boxes remain unchecked. The gate stays
default-off. Future lobby work and crash/save disagreement repair are deferred, not approved by this closure.

## Minimal next-sprint handoff

Only with explicit owner authorization for any out-of-scope work: inspect/resolve the post-movement validator; capture
ghost-session disconnect reasons in logs; then connected-verify death/reconnect, healthy restart, appearance, and offline
observers. Do no further work in this sprint. The existing [current handoff](current-handoff.md) and
[connected checklist](connected-acceptance-checklist.md) retain implementation and test details.
