# APC machine UI — current handoff

## Status

The implementation and the feedback fixes described below are present and have automated verification, but owner-connected retesting is pending. **The sprint is not complete or accepted.** Automated GameTests are not evidence of connected-client behavior.

| Milestone | Status |
| --- | --- |
| M0 — platform/API audit | Recorded in `api-audit.md`. |
| M1 — authority/session | Implemented; automated verification recorded. Connected transport retest pending. |
| M2 — visual presentation | Implemented; automated verification recorded. Connected rendering/refresh retest pending. |
| M3 — server tests | Automated coverage run; coordinator reported **161/161 isolated GameTests**. This does not verify connected behavior. |
| M4 — owner feedback fixes | Fixes are present and automated-verified; owner-connected retest pending. |

## Implemented behavior and known limits

- APC interaction opens an authenticated, server-bound menu. Breaker and battery remain owned by `PowerDeviceBlockEntity`; breaker requests are session/revision checked. Battery is a bounded 0–1000 permille projection of the existing 1 MJ capacity.
- The screen presents breaker state and a battery bar/percentage. A click may update the local presentation immediately, but this is only a local presentation prediction; it is reconciled against the authoritative server response. The UI has no server-wait label or waiting pulse.
- The open breaker does not stop charging when known MV power is available, but it does not discharge the battery or power lamps. A charge change is not required just because the breaker was toggled. External power and load are unavailable from the local runtime and are not reported as measured values.
- No claim of exact Space Station 14 behavior or electrical-semantic parity is made. External-power/load telemetry is not implemented. The licensed APC image provenance and terms are documented in [assets-and-attribution.md](assets-and-attribution.md).
- The session/menu contract is reusable, but the current 250×174 client panel is bespoke and uses a vanilla breaker `Button` and Minecraft typography rather than SS14 window/switch styling. See [visual-parity.md](visual-parity.md) for source-based comparison and a bounded future styling path. Item-range movement through the vanilla open screen remains deferred; do not infer a solution from server range checks.
- The APC breaker-switch sound is server-owned for accepted manual toggles and timed trips; no client button sound duplicates it. Its file-specific license remains unresolved and it has not been manually heard. See the [audio handoff](../apc-overload-sprint/audio-milestone.md).
- Automated coverage includes focused UI unit tests and dedicated-server GameTests. The reported 161/161 result is isolated automated evidence only, not connected menu transport, rendering, or multi-viewer acceptance.

## Owner feedback from the prior build

These are observations from the old build, not confirmation that the current fixes pass connected retest:

- The old build's waiting state after a breaker click was undesirable.
- The other viewer's breaker button appeared stale.
- Movement out of range could not be tested through the vanilla screen because movement was impossible while it was open.
- The battery seemed unaffected by breaker toggling.
- Removing the APC closed the other viewer's screen and returned that client to pause.
- The owner deferred the restart/persistence test.

The current UI removes the waiting label/pulse and the other-viewer button-refresh fix is present; both still need connected confirmation. Do not prescribe walking out of range as a vanilla-screen acceptance step. Battery behavior must be tested under meaningful power/load conditions over observable elapsed solve time, not inferred from the breaker toggle alone. The removal/pause observation also needs retest. Restart/persistence remains deferred until core connected behaviors pass.

## Owner connected retest — pending

Record build/revision and results with two real clients connected to the same server and viewing the same APC:

1. Open the APC on clients A and B. Confirm both screens open and have current state.
2. Click the breaker on A, then click it on B. Confirm there is no server-wait label/pulse, each action resolves to authoritative state, and both viewers' breaker buttons refresh. Repeat in the opposite order if useful. Record any transient prediction/reconciliation behavior rather than treating it as server confirmation.
3. Do not attempt a walk-out-of-range test through the vanilla screen if movement is blocked. Record that scenario as unavailable; use another legitimate invalid-context path only if one is actually accessible.
4. To assess battery behavior, arrange a powered input and an observable load/charge scenario, and allow observable elapsed time across power solves. Record the setup and readings. A breaker toggle alone need not change charge; external-power and load readouts remain unavailable.
5. With both viewers open, remove the APC. Confirm the resulting screen/close behavior for each client, including whether either returns to pause. Reopen only if a replacement/current APC is available and report the result.
6. Only after the core open/click/two-viewer refresh/removal behaviors pass, perform the deferred restart/persistence test and record breaker/battery state after restart.

Do not substitute screenshots, local-only views, or GameTests for this owner-connected gate. Record unavailable scenarios and failures without claiming acceptance.
