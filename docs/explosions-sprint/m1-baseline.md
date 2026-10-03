# M1 bounded blast reference baseline

## Scope and method

This freezes observations of the local pure `BlastPlanner`, not a gameplay tune or an SS14 parity result. The proposal copies the pinned SS14 `ExGrenade` values (budget 120, slope 3, cap 10); its six-cardinal-neighbor 3D shell rule and shared half-slope steps are local implementation choices. The test uses `BlastPlanner.plan` with an all-OPEN sampler, then runs an optional planar comparison with `y != 0` sampled as UNKNOWN. A separate tolerance sampler puts a 2.0-tolerance structural barrier on the positive X, Y, or Z axis in turn. The latter axes represent wall/floor/ceiling orientations only; they use identical synthetic samples, not Minecraft blocks.

Counts distinguish sampled/visited positions from affected positions (`intensities`); UNKNOWN samples are visited but have no intensity. Manhattan span is the maximum `|x|+|y|+|z|` among affected positions; axis extents are maximum absolute affected coordinate. Intensity sums use the planner's output map. The test checks exact stable counts, symmetry, energy conservation and upper bounds (floating comparisons use `1e-9`/`1e-12` tolerances). Its sampler only records queried coordinates in a local test set; there is no world/API handle to mutate. This is a pure unit test, **not a GameTest**, and no GameTest execution is claimed.

## Observed baseline

| Scenario | Result | Sampled / visited | Affected | Manhattan span | Axis extents (x,y,z) | Intensity used |
|---|---:|---:|---:|---:|---:|---:|
| Open 3D | COMPLETE | 25 / 25 | 25 | 2 | 2, 2, 2 | 120.0 |
| Barrier, X orientation | COMPLETE | 25 / 25 | 25 | 2 | — | 120.0 |
| Barrier, Y orientation | COMPLETE | 25 / 25 | 25 | 2 | — | 120.0 |
| Barrier, Z orientation | COMPLETE | 25 / 25 | 25 | 2 | — | 120.0 |
| Planar comparison (`y=0`, elsewhere UNKNOWN) | COMPLETE | 51 / 51 | 25 | — | — | 120.0 |

Open volume takes 6 expansion iterations. Its origin receives 10.0; every affected Manhattan-radius-1 cell receives 7.5 (6 cells), and every radius-2 cell receives approximately 3.611111111111 (18 cells). All three corridor orientations breach 5 sampled barrier cells at the 2.0 threshold and produce identical plans under their corresponding axis transforms. The planar comparison takes 7 iterations; its 51 visited positions include off-plane UNKNOWN candidates, while only 25 on-plane positions are affected.

**Interpretation of the bounds:** the local 128-visited and 32-iteration caps do not reject these runs. The 3D affected footprint is nevertheless only two Manhattan steps: this is an observed budget exhaustion/termination result, not evidence of a radius of two in other environments and not a claim of a fully explored geometric radius. Do not extrapolate a radius from these values. The optional planar result is the same local planner with an off-plane UNKNOWN mask; it is neither the upstream SS14 algorithm nor a parity comparison.

## Reference and caveats

The pinned checkout recorded by M0 is `C:\Users\William\Documents\SS14-dev\space-station-14`, HEAD `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. The M0 source audit identifies `Resources/Prototypes/Entities/Objects/Weapons/Throwable/grenades.yml` `ExGrenade` as 120/3/10 and SS14 tile filling as 2D, with cardinal/diagonal iteration behavior. These measurements do not execute that checkout and do not measure actual SS14 accepted/rejected cells, visited/sampled counts, footprint, intensity rings, or energy accounting. Upstream defaults such as 500 iterations and area/tick limits are not local caps. Local structural tolerance 2.0 is a synthetic M0 fixture and is not canonical SS14 block health.

The baseline does not establish destructive gameplay acceptance, world protection, damage balance, floor peeling, stochastic outcomes, chunks, entity effects, or lifecycle/world mutation behavior. It makes no claim that an actual world adapter is safe or that production performance is acceptable.

## Follow-up before any tuning

Keep 120/3/10 unchanged pending review. Next calibrate against explicitly measured, representative 3D corridors/open rooms and actual SS14 2D reference traces with an agreed mapping of geometry, obstruction tolerance, and sampling semantics. Record per-case acceptance/rejection, visited work, energy and footprint; include barrier thickness, corners, open shafts and boundaries. Then obtain explicit approval for any numeric proposal. No numeric adjustment is made by this baseline.
