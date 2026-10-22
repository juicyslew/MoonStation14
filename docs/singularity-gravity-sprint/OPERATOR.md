# Singularity gravity owner check

Use a disposable, already-loaded clear Superflat area with at least five blocks of open space above ground. The command uses your horizontal look direction and creates a real, wandering singularity 3 blocks up. Horizontal spawn separation is the effective pull radius plus 4 blocks of extra horizontal clearance:

| Stage | Effective pull radius | Extra horizontal clearance | Horizontal spawn separation from operator |
| --- | ---: | ---: | ---: |
| 1 | 12 blocks | 4 blocks | 16 blocks |
| 4 | 30 blocks | 4 blocks | 34 blocks |
| 6 | 42 blocks | 4 blocks | 46 blocks |

The 3-block vertical offset is separate; it is not a pull-radius modifier or a 3D-distance guarantee. Stage 6 is dangerous: retreat before issuing the command. No six-minute PA/cage/power commissioning is needed for this gravity-only test.

1. Run `/ms14 singularity_fixture gravity 1` (repeat with `2` through `6` to select any supported stage). With the current resolved profile, starting energies are approximately 170, 350, 700, 1400, 4999, and 5001 respectively. The response gives actual stage, energy, selected threshold, position, and warns that the source wanders and uses normal profile drain; it does not claim powered readiness. Repeat this command to replace only your previous owned fixture source.
2. Run `/ms14 singularity_fixture gravity_status` for live stage, energy, and distance from you. The selected source starts outside its expanded pull radius (12 blocks at stage 1, 30 at stage 4); approach gradually into the warning band, and retreat if it wanders closer than intended or status reports a stage change. Observe gradual inward drift while neutral in the warning band, outward progress after delayed ordinary runout, upward lift when overhead closer in, and inward travel before the 0.35-block player core. Do not fly, immobilize, or feed/freeze the source.
3. Run `/ms14 singularity_fixture gravity_clear` to remove only your tracked source. Normal `/ms14 singularity_fixture build running` and its ordinary `/ms14 singularity_fixture status` commissioning remain unchanged.

Energy is profile-derived: stages below 6 begin about 30 seconds of natural drain below the next threshold; stage 6 receives a finite amount above its threshold. Terrain consumption can increase reserve. Stages 5–6 have zero configured drain, so don't assume they'll naturally drop stages. This is a practical selectable-stage debug fixture, not a stage lock. Connected-client feel remains unverified; general hand-prediction readiness is owner-deferred. No gravity values are changed by this setup.

**Manual check (NOT RUN):** use a disposable loaded clear Superflat world with five blocks of overhead clearance. Spawn separation is effective pull radius plus 4 blocks as listed above; each source is 3 blocks above the caller. Approach gradually and observe warning-band inward drift and ordinary outward runout; retreat if it wanders closer. The outer band should remain escapable; do not expect escape from the inner capture core. `gravity_clear` removes only the tracked source. Separately, after ordinary fatal injury while the linked body remains available, run `/ms14 revive`, then press R; expect return to that body. This is not revival of a singularity-consumed body. Camera and movement should remain unchanged. These are expected observations, not results; manual feel/owner acceptance remain unverified.
