# Dynamic PA and emitter power demand

PA strength/enable and emitter requested-on are live demand changes, not device
composition changes. Their block entities now update their controller-mirrored
state and visuals without calling `PowerRuntime.deviceChanged`. The runtime's
existing periodic solve samples `PowerRuntimeNode.mvPowerDemandWatts()`; meter
freshness still requires an observation for the current demand, so a raised
demand is UNKNOWN until that solve and cannot grant power or fire early.

Structural registration remains unchanged: block-entity load/removal and real
placement/profile/port changes continue to invalidate power topology. This
prevents a PA operator action from retiring unrelated emitter observations and
restarting their powered first-burst delay. It does not suppress invalidation
for a real network outage or topology change.

`ParticleAcceleratorFiringGameTests.realPoweredControlEmitsThreeParticlesAfterResolvedDelay`
checks that increasing strength immediately makes the old MV observation
unknown; its natural-power wait then proves firing resumes only after a real
solve. `resolvedAssemblyProjectsEveryPartAcrossNaturalPowerAndStrengthTransitions`
(tags `reactor-models`, `pa-session-demand`, `reactor-interaction`) verifies
stable topology generation and continued known 250 W APC meter after each
strength transition, then waits for real MV delivery before asserting eligibility.
PA profile demand (including disabled STANDBY baseline) remains the source.

Coverage boundary: this repository has `PaControlSessionService`, but no
existing GameTest exercises it with an authenticated connected server player;
the available PA firing fixture has no actor/session setup. Thus these tests
exercise actual server-side control transitions and power authority, not session
ID publication/open/handle lifetime. Emitter cross-device burst continuity is
also not asserted by these PA fixtures; no emitter exists in their assembly
fixture. Those two integration assertions require an authenticated actor fixture
and a shared PA+emitter natural-power fixture respectively.
