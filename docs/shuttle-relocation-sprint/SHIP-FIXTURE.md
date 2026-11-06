# M1 cabin check

Targeted owner-requested connected check, not M1 acceptance. Focused stock-Sable
diagnostics now pass for bulk mass edits, splitting, occupied impact, support loss
and 0.5/1/3-rad/s yaw (separate results indexed in `OWNER-FEEDBACK.md`). Earlier
patched-native results remain historical. Begin only after the current candidate's
serialized OWNER_READY gate is reported PASS; diagnostic PASS alone is not readiness.
Use a disposable admin-controlled world, permission 2, and a live controlled body.
No client or owner-world setup is performed automatically.
Shorthand commands below use the `/ms14 shuttle_fixture` prefix. Replace `<id>`
with the ship ID returned by `build` (also available from `list`/tab completion).

## Normal launch (owner runs this command)

From this worktree, use your normal launch:

```powershell
.\gradlew.bat runClient
```

Use the unmodified pinned official Sable release without a native-candidate
override. Create a **fresh Creative world with cheats enabled**, as usual. Confirm
six substeps in fixture status before motion testing. No custom run directory or
automatic client launch is required; normal owner configuration is not modified.

## Checks in one session

For the current short freight-flight/orientation follow-up, use [MANUAL.md](MANUAL.md).
`flight <id> [seconds]` defaults to 90 seconds (1–120), with low commanded acceleration
and jerk limits; it explicitly arms the selected loaded ship without rebuilding it.
`bay_light <id>` retrofits the existing manual bay ceiling with a normal sea lantern.
The profile's 64 m horizontal / 8 m vertical native-COM envelope disengages feedback
if exceeded; it does not stop native momentum or disable gravity/collisions. No
automatic obstacles or guaranteed bump contacts are supplied.

1. **Build and board.** Choose an already-loaded, empty area above a wide clear
   pad, with a safe route across two chunk seams and a clear **20×7×13** footprint.
   Run `/ms14 shuttle_fixture build`
   (or `build <x> <y> <z>`), record the ship ID, and run
   `/ms14 shuttle_fixture status <id>`. Confirm `sdkSubstepsPerTick=6`; enter through
   the front airlock normally. Build refuses occupied/unloaded space; it never
    clears terrain. Legacy motion registration is runtime-only; the new `flight`
    command can explicitly arm an existing selected loaded fixture after restart.
   For movement/jumping, board the adjoining **10×11 bay** through its wide open
   front entrance; it has five blocks of clear height. The original airlocked
   room remains available for door/atmosphere checks but has a low ceiling.
2. **Door and stationary actions.** Walk into the powered front airlock: it should
   open and let your whole body through without clipping/snapback. Stand in the
   open doorway while it tries to close: it should stay open or reopen rather
   than crush you. This is the safety check; department-access denial and welding
   are not supported tests. Run `/ms14 shuttle_fixture cargo <id>` aboard: it grants
   one capacity-checked jug with **20 water units**. Throw toward the crosshair,
   pick it up normally, and check its contents. Stand, walk, jump and use a target;
   watch for camera/body mismatch, wrong hits or snapbacks.
3. **Moving checks.** Run `/ms14 shuttle_fixture motion <id> .5 0 0 0 30`.
   Check measured `fixtureTransportVelocity` in status, then stand/walk/jump/use aboard,
   cross the moving hatch both ways when safe, and observe two chunk seams.
    Do not attempt an airborne hatch crossing; stop on drift or snapback.
4. **Original cross-axis regression: yaw only.** Lift with
   `motion <id> 0 2 0 0 15`, then run these targets in order:
   `motion <id> 0 0 0 .5 30`, `motion <id> 0 0 0 1 30`, and
   `motion <id> 0 0 0 3 30`. During each, stand, walk and jump in the spacious bay.
   Check camera continuity, no renewed airborne curving, and no growing unwanted
   roll/pitch. `status <id>` reports `bodyUp`, `tiltDegrees` and angular velocity;
    this is the primary check, before deliberately tilting the ship.
5. **Secondary tilted-camera/support check.** Retarget
   `motion <id> 0 0 0 0 30` and let rotation settle. Run `roll <id> .75`, wait
   roughly one second, then `brake <id>`. Check the ship stops spinning without
   snapping upright and your supported body/camera does not jump or drift downhill.
   Braking releases the motion controller: **gravity resumes**. Once the brake
   takes effect, promptly run `motion <id> 0 0 0 0 30` to restore velocity feedback.
   This preserves the tilt; it does not level the ship. `pitch <id> .5` followed
   by `brake <id>` provides the other axis if desired. Keep clear of terrain;
   use a fresh cabin if you need an upright baseline again.
6. **Water and landing.** Retarget `motion <id> 0 0 0 0 30` and wait for measured
   transport velocity to settle. Pour **more than 15 units onto one floor tile**, then run
   through it without slip-protecting equipment. Water requires own speed of at
   least **3.5 m/s**; walking slowly or merely riding
   the ship is not a slip test.
   Descend over the clear pad with `motion <id> 0 -.5 0 0 30`; when physically
   supported and settled, run `/ms14 shuttle_fixture motion_stop <id>`.

Motion requires `motion <id> <vx> <vy> <vz> <yaw_rate> <seconds>`: world **m/s**,
world-Y **rad/s**, then an explicit duration of **1–120 game seconds**. Requests use
bounded physical impulses, not pose/velocity resets. There is no implicit duration;
retargeting starts a new deadline. `motion_stop` releases control—it does not instantly halt
the ship, and normal gravity continues. Do not leave an airborne fixture unattended.
Status separately shows SDK instantaneous velocity; use transport velocity for
fixture travel. This is velocity feedback, not an altitude lock.
Legacy `nudge`/`yaw` are one-shot deltas, not the sustained-motion procedure.
`roll <id> <delta>` adds body-local +Z angular velocity and `pitch <id> <delta>`
adds body-local +X (rad/s); `yaw` remains world-Y. `brake <id>` releases any
feedback target and queues one physical counter-impulse for the next completed
physics-step callback, cancelling current linear/angular velocity without changing pose.
Gravity and collisions continue immediately afterward; it is not an upright snap.
Status reports native/logical body-up and tilt from world-up for observing yaw-only
cross-axis drift; there is no automatic stabilization or tilt threshold. Active
yaw feedback drives angular velocity toward world (0,target,0), so it may oppose
roll/pitch deltas. Brake releases that target; restarting `motion <id> 0 0 0 0 30`
after braking provides velocity feedback without an upright-restoring controller.

Report the clock time and action if a check fails. Connected observations remain
owner verification, not results established by native tests. Piloting, FTL,
save/restart and high-speed collision acceptance are outside this check.
