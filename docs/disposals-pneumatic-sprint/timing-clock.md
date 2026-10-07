# Disposal timing clock

Unit scheduling uses the level's persisted `getGameTime()`, not PowerRuntime's transient solve sequence. The reported power-callback crash does not establish that the solve sequence reached the timing kernel; `updatePower` advances through the level clock.

If restored or live logical time is ahead of level time, the block-entity adapter rebases the kernel before advancing it. Rebase preserves remaining recharge, flush, visual and escape-cooldown durations, engagement and ownership. It does not reset the world, discard contents or grant readiness. One warning per block-entity instance records the old/new clock and position.

`advanceTo` remains strictly monotonic. A still-active escape attempt may have a negative past-origin timestamp after rebasing near tick zero; snapshot restore accepts that bounded case. Expired attempts are cleared during rebasing.

Regression coverage: `DisposalTubePrototypeTest` and `DisposalUnitClockGameTests`. The latter loads a future-clock snapshot, invokes the reported `updatePower(true)` path and then ordinary `serverTick`, checking preserved deadlines and cooldown.
