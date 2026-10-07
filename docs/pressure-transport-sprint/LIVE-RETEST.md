# Live pressure/front retest

Use the rebuilt JAR identified in `VALIDATION.md` once that gate is complete.

1. Reproduce the same hot-tritium incident in the same station room.
2. At a fixed source cell and a distant cell, capture `/ms14atmoscheck x y z` before ignition, during initial spread, and after the room responds. Keep coordinates and elapsed real time with each capture.
3. Note whether gas/fire arrives gradually, whether a room-wide jump still occurs, and whether the server stalls. Include the new pressure-span/work/time line from the diagnostic output.

Compare counter deltas rather than lifetime-average rates. Automated fixture convergence is not proof of live station TPS or visible front speed. Momentum/wind and expansion work are not modeled by this transport prototype.

The pressure span/work/time line describes the last committed normal patch anywhere in the level, not necessarily the queried cell or the whole room. Search work already includes preview steps; do not add those counters together.

Committed transport-operation counters are cumulative. Capture deltas to distinguish pressure face transfers, LINDA sharing and group averaging during the incident.
