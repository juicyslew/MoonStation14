# APC wired overload GameTest proof

`ApcOverloadGameTests.connectedCablePathLampLoadTripsApcAndManualRecloseRestoresIt`
uses the real loaded-device runtime and indexed cable graph. It places an HV source,
substation, APC, an APC output lead, and a 10 x 10 APC-tier cable floor grid, then
places 298 actual lamp block entities one to three blocks above that grid. Each lamp
demands 100 W, so the connected load is 29.8 kW and the available source path can
deliver more than the APC's 20 kW protection threshold. The APC battery starts empty;
no meter input, runtime hook, or pure-solver projection is used by this proof.

The test waits for cable graph indexing and initial convergence, gives the normal
per-server-tick output protection more than three seconds, and asserts the breaker
trip/latch, dark lamps after the scheduled re-solve, authorized manual reclose,
cleared latch and serialized breaker state, then lit lamps after the re-solve.

This fixture intentionally proves one wired circuit in isolation. It does not claim
an independent circuit survives the trip; the existing synthetic projection test
continues to cover that separate behavior.
