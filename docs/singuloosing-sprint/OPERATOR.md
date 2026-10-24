# Singuloosing demo

Use a disposable, already-loaded Superflat area with clear space. Stand at least five blocks above ground; build uses your position plus 4 X and 4 Z at the same Y. It clears only the footprint, never excavates. No coordinate memorization is needed.

`/ms14 singularity_fixture build singuloosing`

The fixture starts real emitters, fields and collector/bootstrap PA. Startup takes time: allow natural growth to stage 3. Level 2 PA leaves continuous normal feed; no manual climbing/flipping or hard cap. Lower containment limits vertical misses, but escape remains free 3D movement.

Check `/ms14 singularity_fixture status` nearby; it selects the nearest registered fixture. Expect STARTING before READY: stage 3 and fully allocated 5 kW beacon (100 W lamp; 5.1 kW total).

For deliberate breach, stay outside the gravity radius in a disposable world:

`/ms14 singularity_fixture breach`

This destroys the recommended corner of the nearest owned registered fixture. Coordinates are optional, advanced use only.

Observe escape, beam feeding/growth, beacon attraction, roaming and terrain damage. Fuel is finite; normal drain means stage is not held forever.

## Validation / owner check

Technical gate: ready for owner (all six checks passed: JUnit 3561/3561, interaction 60/60, focused GameTest 27/27, blocker GameTest 45/45, full GameTest 926/926, and assemble artifact); see `build/agent-workflow/singuloosing-sprint/reports/singuloosing-20261014-r45-integration-lead-review.json`.
Owner reported successful manual use and accepted/closed the sprint. Detailed visual, connected-client and stair-render coverage was not separately recorded. See `HANDOFF.md`; follow-up gravity and player-core changes belong to a separate sprint.
