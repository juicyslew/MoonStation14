# 2026-09-24 re-slip parity correction

**Status:** the local continuous-slide re-slip behavior was corrected against pinned SS14 source and focused automated checks pass. This is not a connected-player retest, a three-puddle traversal result, or acceptance of the movement sprint. The earlier multi-source launch numbers in the [connected movement-feel audit](2026-09-24-connected-movement-feel.md) are historical evidence of a local defect and are superseded by the correction notice at that audit's top.

## Pinned SS14 behavior

Reference: SS14 commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `Content.Shared/Slippery/SlipperySystem.cs:98-153`: `superSlippery` allows a `SlipEvent` while the entity is already knocked down. It does **not** mean every such admission launches again: physics velocity is multiplied only when the entity does not have `SlidingComponent` (`120-126`).
- Sound and stun are applied only when the entity is not already knocked down (`128-150`); knockdown is refreshed on any admitted slip. `SharedStunSystem` blocks voluntary mover input while Stunned, whose default duration is 0.5 seconds (10 ticks), versus 1.5 seconds (30 ticks) for knockdown.
- `Content.Shared/Slippery/SlidingSystem.cs:62-85` removes `SlidingComponent` when the entity exits its last qualifying slippery contact, even if knockdown continues. Sliding is contact-latched, not held until knockdown expires.
- Client camera freeze is associated with local `SlidingAttachment`; its possible duration beyond stun while contact continues is not evidence of a pinned SS14 look lock. The requested longer control restriction remains a policy question, not something to claim as upstream parity.

Thus adjacent qualifying sources contacted continuously while already sliding may admit another event without another launch, sound, or fresh stun. If a real gap removes the last qualifying contact, Sliding can clear while knockdown remains. Retained momentum can then admit a subsequent source without new voluntary input and relaunch because the entity is no longer sliding; if still knocked down, that admission does not repeat sound/stun. Do not interpret admission/event count as launch count or puddle count.

## Local correction and regression evidence

The prior implementation multiplied velocity and reapplied sound/stun for every super-slippery admission while Sliding, and left its sliding marker in place until knockdown ended. Its controlled three-tile GameTest observed launch-speed increments `0.6 -> 0.9 -> 1.35` and velocities `0.9 -> 1.35 -> 2.025` while still sliding. This was a **local bug**, not intended continuous SS14 behavior; it did not demonstrate three intended upstream launches, a connected incident's cause, or a successful chain lane.

The current implementation now guards launch with the already-sliding state, conditions sound/stun on not already knocked down, and reconciles sliding against qualifying contact exit. It applies no new speed cap. On adjacent sources while continuously sliding, an extra admitted event does not relaunch, and there is no new Touch when the entity was already sliding. After a genuine contact exit clears Sliding, momentum may cause a later source admission and relaunch without voluntary input; Touch behavior is separately gated on whether the entity was sliding.

Focused automated scenarios cover: (1) controlled continuous two-source sliding with no second launch; (2) separated contact exit followed by relaunch while knockdown remains; and (3) reactive Touch on adjacent contact while already sliding versus fresh contact after exit. Latest reported validation after the fix:

- `test --rerun-tasks --no-daemon` — passed, 34s.
- `build --no-daemon` — passed, 6s.
- `runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-run` — passed, 23s.
- `build/gametest-run/logs/latest.log`: 112 started at line 35 (`2026-09-24 17:58:43.873`), 112 complete at line 237, and required pass at line 238 (`17:58:46.874`).

These are automated checks only. No connected retest was performed after the fix. In particular, do not claim the three-puddle lane/chain passes, do not claim the 112 tests prove connected movement, and do not claim backwards-compatible migration behavior. Root data-only `CharacterData` is unchanged.

## Owner connected follow-up

Use matching rebuilt client/server and a backed-up world; follow the [experimental connected smoke checklist](experimental-connected-smoke.md). Compare multiple adjacent sources under continuous sliding with sources separated by a genuine gap. Capture source/contact order, amounts, launch diagnostic, speed, Sliding and knockdown status; confirm the diagnostic tracks actual launches rather than every event. Test whether the player can steer after the 10-tick stun has elapsed while sliding/knockdown remains, and observe camera lock during both sliding and knockdown. These observations should inform the outstanding control-duration policy question; do not describe local camera freeze as an SS14 look lock. The connected chain lane, M2/M3 handoff gates, and M4 numeric traversal bound remain open.
