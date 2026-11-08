# Singularity operation

**Current visual check:** owner-approved R8 generator field art is imported and
wired. GPU acceptance is pending; see
`singularity-power-sprint/GENERATOR-FEEDBACK-GEOMETRY.md` for its texture-only
emissivity limitation and approval/provenance record.

## Start the supplied fixture

1. Use `/ms14 singularity_fixture build inactive [<x> <y> <z>]` (permission 2)
   for the default compact four-block enclosure. Alternatively, `build running`
   uses the same compact layout and commissions automatically; wait for READY.
   Existing compact worlds are not migrated. The build never clears an existing
   fixture.
2. Equip the radiation suit **and** hood. Keep clear of emitter lanes and the
   singularity: radiation gear does not protect against contact.
3. With an empty active hand, press **E** once on each of the inactive fixture's
    eight corner field generators. Before feeding in step 4, observe one corner:
    disabled is dark and enabled-at-zero-charge shows the cyan a1 lower third.
    Use the stairs for upper corners. Enabled at zero is not connected; a
    connected generator cannot be switched off.
4. Enable all eight emitters with **E**. Allow the ten-second initial firing
   delay. Wait for all six containment faces, including ceiling and floor.
5. Enable the four collectors with **E**. The supplied collectors contain filled
   plasma tanks; these are finite fuel supplies.
6. Open the PA control with **E**, confirm assembly, select **Level0** and switch
   it on. STANDBY does not fire. Wait for the startup generator to create the
   singularity.
7. The compact `build running` preset performs its established one-time sequence:
   **Level0** creates the singularity, **Level2** raises reserve to **260**, then
   **Level0** is restored at READY. Running mode does not overwrite later PA
   strength changes. Keep this known compact commissioning flow for ordinary
   operation.

## Optional wide, inactive enclosure

Use `/ms14 singularity_fixture build inactive_wide <x> <y> <z>` in empty space
when a four-block compact enclosure cannot provide the desired stage-3/4 movement
coverage. This explicit inactive preset has eight-block faces and leaves a
positive stage-4 movement corridor. It does not start the PA, charge fields,
inject reserve, or commission automatically. A single PA's normal Level2 startup
can lose the core before the 260 reserve target in this wider geometry; do not
use `build running` to commission a wide fixture. Wider-core commissioning and
sustained feeding require additional normal feeding supplied by the operator.
Singularity speed, radius, drain, and SS14-derived values remain unchanged.

## Keep it operating

- Leave containment generators and emitters enabled. Normal emitter bursts have
  four shots, two seconds apart, followed by a four-to-ten-second pause. Actual
  power loss restarts the ten-second initial delay.
- Monitor singularity reserve, containment and collector tank pressure. Adjust
  PA feeding if reserve keeps falling or growth becomes unsafe. The PA does not
  have to be shut off merely because creation succeeded. The local default PA
  fires three projectiles per six seconds; at Level2, an all-hit burst sequence
  supplies 15 reserve/second. Stage4 drains 20/second, so one default PA cannot
  maintain stage4. PA Level3 supplies 30 reserve/second upstream only after its limiter is
  altered; the local PA does not implement that limiter control, so do not expect
  or attempt that adjustment here.
- **Reserve is an internal singularity variable, not watts.** PA particles add
  reserve; natural decay removes it. PA/emitter electrical consumption and
  collector electrical output are separate measurements.
- Collectors consume **plasma gas** while operating. To refuel, right-click a
  collector and choose **Gas tank eject**, then use a filled spare plasma tank on
  it. Spare tanks and a finite plasma canister are supplied; they are not infinite
  fuel sources.
- The fixture's auxiliary HV supplies startup machinery. Collector export feeds
  a separate real 12 kW load; it is not a self-starting, self-powered engine.

## Deliberate growth / release — disposable worlds only

Keep the PA firing and raise its strength to add reserve faster. Stage thresholds
are 200, 500, 1,000, 2,000 and 5,000 for stages 2 through 6. At **stage 5** the
singularity can breach fields. This is dangerous: body contact is lethal.
Stage4 has a 3.5-block horizon radius; radiation gear does not prevent its
instant-death contact. Locally, body contact uses sphere-versus-body-volume
intersection and corpses are retained; SS14 deletes consumed entities, including
contained contents, within the horizon. Terrain, loose-item and recursive
container consumption are not implemented locally. Containment limits movement,
not the lethal horizon: compact worlds have four-block face spans and two
blocks of midpoint clearance; only explicit wide builds have eight-block spans.
At stage 4 the 3.5-block horizon leaves only 0.5 blocks of midpoint clearance
in an inactive_wide fixture; standing just outside a field does not ensure safety.

## Connected-client recheck

Restart the client and server with the new JAR, then check these results (no
screenshot is needed):

1. Keep camera position fixed and turn your head; verify a single transformed
   world surface (no unchanged foreground copy) and stable shape. Orbit the camera;
   the source sprite should retain its existing camera-facing/world-up basis.
2. Check strong continuous lensing through the viewport edge with no fullscreen
   block or edge clamp, and check offscreen affected geometry within source support.
   Opaque surfaces must keep native depth occlusion. CPU tests do not establish
   finite-mesh faceting, GPU cost, or live appearance.
3. Hold Shift and look at the entity; its name should read “Singularity.”
4. Optionally check quiet-source gravity pulse and eligible body movement/contact.
   Prior PA/R8 acceptance checks do not need repeating.

For stage 3/4 visual checks, stand at least 12 blocks from the core: stage 4
gravity reaches 10 blocks. Lens support is the resolved 20-world-unit source
cutoff, not a render-distance or sprite-radius rule. Reduced motion disables the
world lens. Radiation gear does not protect against horizon contact; stay out of
emitter beam lanes.

Report pass/fail for the results above. Compact fixtures' four-block faces do
not fit stage 3/4; use `inactive_wide` with normal additional feeding for those
checks. Wide operation is manual only; do not use compact `build running` as a
wide auto-commissioning claim. Keep the general operating, fuel and safety
guidance above unchanged.
