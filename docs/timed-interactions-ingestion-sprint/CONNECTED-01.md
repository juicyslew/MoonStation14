# Connected feedback 01

Reported: tossed objects stutter; drinking bicaridine produced no apparent effect;
progress overlaps the center/head and fills right-to-left.

## Investigation

- Loose-item entity, renderer and tracking interval were unchanged between accepted
  foundation `e543f49`, alignment `5d55c01` and the initial timed-interaction work.
  This does not dismiss the observed regression. Client interpolation history is
  reset without local motion. Minecraft 1.21.1's base `Entity.lerpTo` immediately
  sets position; the custom client tick then erased frame interpolation history.
  The new progress renderer is
  another render-path change, but there is no evidence its batch flushes cause the
  reported motion problem.
- Bicaridine is bloodstream-only medicine, not immediate stomach healing. Verified
  pinned SS14/local uptake is up to .25 units per one-second digestion pass at .5
  efficacy (.125 into blood); bloodstream removal is .5 per pass. Normal oral
  uptake therefore does not imply buildup to the 15/30-unit overdose thresholds.
  Brute healing scales to roughly .375 typed damage per full oral absorption pass.
  No brute injury means no injury to repair. The unconditional Drunk effect lasts
  about .75 seconds at that scale; full drunk visual/speech presentation remains
  outside the existing local status boundary. A bicaridine-specific live ingestion
  regression fixture now verifies actual timed drinking, stomach admission and
  scheduled brute healing against a matched untreated body; it passed.

## Corrections

- Client interpolation only: buffer the latest authoritative target until the next
  client tick, retaining old/current render positions. Rotation-only packets use
  pending target getters, not the lagging rendered position. No vanilla item tick
  or client physics is enabled. Four-block corrections deliberately snap both
  position histories; initial spawn uses Minecraft's `moveTo`. Idle objects skip
  interpolation work. Server physics, tracking interval and ownership are unchanged.
- First-person progress uses the lower view (projection-derived placement, including
  low/high FOV). Third-person bars have .30-block anchor clearance, leaving the
  bottom edge about .175 blocks above the actor. Removed the extra billboard
  half-turn that mirrored progress; fill now runs left-to-right.
- Keep unrelated timed-operation capabilities and chemistry presentation expansion
  outside these fixes. Record any confirmed ingestion/metabolism bug separately.

## Validation / retest

Independent UI and client-motion reviews passed. **2,125 unit tests passed**;
focused `timed-interaction,loose-objects` run: **31/515 selected, all 31 passed**,
zero untagged. Evidence: `build/timed-connected-01-unit.log` and
`build/timed-connected-01-fixture.log`. Full suite not rerun for this narrow fix;
the sprint's earlier unresolved full-run failures remain recorded in `SPRINT.md`.

Restart the client/server. Retest thrown movement while stationary, first-/third-
person bar placement and left-to-right fill. Test bicaridine on known brute injury
and allow time for absorption; do not expect swallowed volume to be immediate
bloodstream concentration. Full intoxication visuals remain an existing gap.
Connected confirmation is still pending.
