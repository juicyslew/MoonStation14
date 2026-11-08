# Tesla fixture operator notes

Use a disposable Superflat world and a fully loaded, clear 29-by-12-by-21-block volume. Stay outside the cage and arc range; fixture commands require operator permission.

1. `/ms14 tesla_fixture build` builds the inactive cage, PA, emitters, Tesla generator, three coils, and four real grounding rods: the original collector-lane rod plus three supplemental rods. The three supplemental rods cover all eight compact MAIN source-bound corners within range; existing power/export wiring is retained.
2. `/ms14 tesla_fixture start` starts normal simulated containment and Level 1 PA commissioning. It injects neither source energy nor coil charge; PA stops after the tracked Tesla is created.
3. Observe normal strikes: coils charge and export to the isolated load; eligible direct bolts can be absorbed by rods while emitters survive the nominal main phase. This is not protection from contact consumption, terrain blast, or living-target beams. Fixture status is diagnostic only, not damage-examine evidence.
4. For an optional qualitative A/B, remove one then all three supplemental rods through ordinary block interaction in the disposable build. More exposure is expected, not an immediate hit or guaranteed damage; natural misses, target order, and movement remain. The original collector-lane rod remains. Rebuild/reset afterward.

For damage examine, use ordinary EXAMINE on a damaged coil or rod: observe the public crack/damage wording (not a status counter), then use a lit welder to repair and confirm the message returns to healthy. Crack-overlay visuals are not implemented. For ambient audio, leave the 15-block range, return, and listen; client audibility remains manual validation. Do not use `breach` outside a disposable world.

Sprint accepted and closed by the owner; see `HANDOFF.md` for final gate evidence and remaining limits. These steps are a reusable manual checklist, not a recording of connected-client observations.
