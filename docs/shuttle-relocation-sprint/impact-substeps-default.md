# Sable sublevel physics default

Every project `runServer` launch ensures six Sable substeps per tick in its
selected server directory's `config/sable-server.toml`. For the default run
this is `run/config/sable-server.toml`; with `-Pms14ServerRunDir=<dir>`, it is
`<dir>/config/sable-server.toml`. Gradle creates a missing file from the
project template, or updates only the root `sub_level_substeps_per_tick` key
in an existing file. Other config settings and comments are preserved.

The staging task runs before each `runServer` launch, before Sable loads its
configuration. It does not alter worlds, `server.properties`, or external
launches that bypass this project's Gradle `runServer` task. Existing
two-substep comparison fixtures are explicit test configurations and remain
unchanged unless the normal server launch targets their directory.

For an explicitly isolated GameTest comparison, supply both
`-Pms14GameTestDir=<fresh-dir>` and `-Pms14GameTestSableSubsteps=<positive-int>`.
This stages that value before GameTestServer starts; without the property,
GameTest configuration and SDK defaults are left unchanged.

To verify the live effective value, run `/ms14 shuttle_fixture list` and choose
a loaded ship ID, then run `/ms14 shuttle_fixture status <ship-id>`. The status
line includes `sdkSubstepsPerTick=6` when the live Sable physics system reports
six; it reports `unavailable` if the system is not available. This reads the
existing runtime configuration without initializing or changing physics.

## Bounded 40 m/s observation — 2026-10-23

Diagnostic `run_ec13f5e5c73426ecc588d283` executed the narrow
`shuttle-hull-impact-40` tag: **1/898 selected, 1 passed, 0 failed**.
The native log confirmed `effectiveSubsteps=6`, `expectedSubsteps=6`.

The furnished hull's maximum sampled terrain overlap was **0.07300552 m**
at trajectory tick 133, then 0.02647181 m at tick 134 and 0.00266628 m at
tick 140. It completed ten settled samples by tick 144. The fixture's
unchanged hull, scene-coverage and support-slab escape guards did not fail;
the server completed and saved with exit code 0.

This is transient sampled overlap followed by recovery, not reproduced
persistent burial. The existing failure latch is penetration over 0.10 m
on two consecutive samples; 0.02 m triggers detailed reporting, not that
failure latch. No penetration budget or collision behavior was changed.

This proof covers an authored, loaded stone pad and the furnished hull—not
crew/cargo continuity, arbitrary terrain loading, 80 m/s, a connected client,
or M1 acceptance. Next combine current movement/physics ownership with the
existing resident-landing/reconnect payload and native-contact recovery tests.
