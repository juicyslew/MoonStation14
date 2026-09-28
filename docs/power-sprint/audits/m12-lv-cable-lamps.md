# M12 cable coil names

## Change

The user-facing cable coil names now match the pinned SS14 English `cable_coils.yml` display names exactly: `HV cable coil`, `MV cable coil`, and `LV cable coil`. The registered `apc_cable_spool` item and its `CableTier.APC` tier intentionally remain unchanged for compatibility with saved items and chunk data; internally, APC denotes the LV voltage group and is displayed as LV. Laid cables use the distinct display names `HV power cable`, `MV power cable`, and `LV power cable`.

No runtime or renderer behavior was changed.

## Issue 4: LV cable lamp extension receivers

Power lamps use one server-owned nearest-cable extension receiver on the LV (`CableTier.APC`) graph, with the pinned SS14 receiver range of three blocks measured between block centers in 3D. The selected cable must be in a known loaded graph component; unknown/unloaded graph areas, other cable tiers, and candidates outside the radius fail closed, leaving the lamp dark. A lamp does not need to face the cable. The nearest known eligible APC internal-tier provider within range is selected. Other device kinds retain their exact opposing-face ports, and the generated station circuit remains unchanged.

The graph performs local indexed lookups and caches each lamp choice against the stable graph revision. Among nearby candidates it chooses the nearest one whose component state is `KNOWN`, rather than allowing a nearer unknown component to mask a farther known provider. If the bounded node query has an unloaded search frontier, or no candidate has known component state, the lookup fails closed. Cable edits, graph rebuilds, and chunk lifecycle changes invalidate the choice; the query never loads chunks. Equal-distance nodes use stable host-coordinate/face ordering. Graph and in-world test coverage was added; automated GameTest server execution remains subject to the repository's runServer/build concurrency requirements.

The radius GameTest fixture powers its LV cable from the APC's actual EAST-facing output (with the APC facing WEST), using a precharged battery with the breaker closed. It continues from wall-hosted LV nodes onto a station-floor node under the first lamp, and checks that output component knowledge is `KNOWN` before evaluating the lamp assertions. This avoids treating an unconnected APC MV input as a valid power provider. The first run initially failed this new LV lamp fixture; after correction to source it from actual charged APC output, the fixture passed in the verified 157-test GameTest run. This is automated server evidence, not a manual client test.
