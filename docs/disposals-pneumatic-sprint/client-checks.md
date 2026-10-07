# Connected client checks

Use a fresh disposable test world and the existing controlled-body fixture, not
the spectator carrier's inventory. Power the unit through a real APC. Old
floor-overlay disposal saves are not migrated by this replacement.

Build the fixture with `/ms14 disposals_fixture build ~ ~ ~`, then use bare
`/ms14 disposals_fixture initialize`, `status`, and `enter`—do not guess or
repeat coordinates. The command uses a fixed floor reference and self-raises
the authorized body after a successful build; see [the fixture contract](fixture.md).
Unit catalog placement and its not-yet-implemented crafting flow are described
in [unit access](unit-access.md).

1. Press the construction key (provisional **G**) with either hand occupied or
   empty: enter Disposal Pipes without a container menu or consumed hand slot.
   Hold G and scroll to choose a shape; move normally. Check HUD readiness,
   material/count and rebound controls. Releasing G restores normal status.
   With an empty active hand, click a visible floor item: pick it up, not the
   block behind it.
2. Hold steel and aim: check thick ghost/readiness and **R** meaningful
   orientations, preferring connections but allowing a new run. One click places
   at most one tube, including across hand refresh/lag; no auto-repeat. Repeat
   Use retains shape. **H** exits and removes HUD/ghost; steel alone must not
   open a picker. Drop an item onto a pipe: verify it falls to the ground at the
   rounded collision edge, not atop the pipe.
3. Build horizontal/vertical runs and a bend. Pipes occupy their own blocks and
   face the clicked surface; floor cannot hide them. Steel-wall crossings retain
   wall/seal. Check round barrels, readable mouths and matching ghost; rounded
   corner gaps remain clear.
4. Put a trunk below the powered unit, route facing sideways or DOWN, not UP.
   For a DOWN inlet, target the unit underside with a free cell below. Exit
   construction with H. Hold the primary-hand item and Use it on the unit to
   insert. Context key (rebindable, default **E**) → **Flush**: check two-second
   flush, complete stack arrival, sounds and charging appearance.
5. Context key → **Climb inside** with empty hands. Check half-second entry,
   unchanged equipment/camera, movement escape, then self-flush through a
   vertical run and bend. No walking during transit, camera loss or stuck body.
6. Wrench a pipe loose, screwdriver-preview and confirm rotation, then wrench
   anchored. Rotation takes one second and preserves type; try cancelling by
   movement/tool change. Remove with a lit welder. Try power loss, broken route
   and replaced wall host: no missing/duplicated payload or trapped passenger.
7. Unload/reload and restart normally; check shapes, contents and restored body
    flags. Verify a hurt landing and jump across pipes do not strand movement.
    In a blast-safe area, an armed grenade fuse must not pause/restart
   while held with panel open, inserted, transported or ejected.

Verify fixture floor reference: input Y is `minY`, standing entry is Y+1, and
ground below is untouched. Preferred-output visuals are implemented: gold
chevrons mark outputs on four radial faces in all six axes; router tagged output
is blue, tagger has a blue three-band collar (no literal T/TAG text), and trunk
has a gold UP-coupling collar. Straight/bend bidirectional paths remain unmarked.
Check role markers distinguish trunk coupling from routing port and tagger/router.
Unit appearance is implemented: yellow rim, dark intake, light cabinet, cropped
12×12 controls across all states including animated flush; verify full 0..16 UVs
and labels. Native-unit and preferred-output connected visual acceptance remains
pending; these are implementation claims, not completed checks.

These connected checks remain outstanding until performed; automated
logic/fixture results do not prove client presentation or camera behavior.
