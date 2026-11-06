# R26: gameplay proof passed; world-save shutdown failed

## Bound result

Candidate `shuttle-feedback08-offline-unload-20261015-r26`, snapshot
`e7097240c4e4e32d4e84734398b6f272dd69ad63da037b2b4be5c0ce8f887e2e`.
Filtered2/893 selected, **2 completed, 2 gameplay passes, 0 assertion failures**.
The validation receipt is nevertheless **FAIL**, exit124, owned process tree
terminated after the worker timeout. Neither a clean stop nor completed disk
save is established. Independent read-only evidence review confirmed this split.

Receipt: `shuttle-feedback08-offline-unload-20261015-r26-gate-validation.json`.
Raw: `feedback08-offline-unload-r26-native.log`,307,667 bytes, SHA-256
`6dc5c169b7d9d166819d0b6db8b596be8736a7a12aac2d2c2928afb9449bde44`,
under `build/agent-workflow/shuttle-relocation-sprint/reports/`.
R25's abandoned fixture/plan/log and R26's failed run remain preserved.

## Established gameplay scope

Combined-case test ticks: OFFLINE23, actual old body/cargo/ship unload28,
quiet observations28–47, explicit reload47, new plot48, cargo verification50,
normal reconnect53, test pass56. The quiet phase is20 observations spanning19
intervals. Cargo-only companion also passed its own unload/reload assertions.

The combined old body had `UNLOADED_TO_CHUNK` and null UUID lookup before reload;
the reconstructed body preserved UUID, Mind, hand roots, held token/full stack,
account/profile and ACTIVE authority with actual replacement-frame tracking.
All three cargo roots reconstructed with original UUIDs, admitted stack
count/components, normal ledgers and frame/storage support. Numeric reagent
amounts remain source-derived setup rather than separately printed observations.
This is stationary same-process evidence, not flight, process restart, connected
client/Creative coverage, the owner's cargo-loss cause/fix or M1 acceptance.

## Failure boundary

Raw lines1309–1310 report the completed aggregate and both required passes.
Lines1312–1314 report `Stopping server`, `Saving players`, then `Saving worlds`.
There are no subsequent per-dimension chunk-save/all-saved or normal-exit markers.
Lines1316–1317 report timeout, confirmed owned-tree termination and exit124.
The receipt elapsed time is approximately3,607 seconds; gameplay finished in
5.444 seconds according to the raw aggregate. This is a shutdown failure, not
test admission failure or missing aggregate.

Original-wrapper closes skipped absent owners, and replacement closes retired
their exact live owners before the aggregate. Those lines do not prove the
entire chunk/entity save pipeline drained. No removed-owner exception is shown
at this boundary. The precise blocked operation and cause are not yet established;
do not blame Sable or change physics/lifecycle behavior on this evidence alone.

Read-only investigation found no captured stack identifying the blocked
operation. No causal gameplay/engine correction was justified. The subsequent
test-only diagnostic extends the existing progress observer with opt-in bounded
shutdown stack capture; it changes no worker deadline or gameplay behavior.

## R27: clean matched non-reproduction

Fresh R27 selected2/893, completed2/passed2,0 assertion failures, exit0,
normal all-dimension saves and stop. Independent evidence review confirmed the
gameplay proof and clean shutdown. Combined-case test ticks: OFFLINE22,
old body/cargo/ship unload27, quiet confirmed46, replacement plot48,
normal reconstructed-body reconnect53 and pass56. Both hand roots, Mind,
held token/full stack, body UUID and exact replacement-frame authority remained
intact; admitted cargo snapshots/ledgers/frame support were preserved.

Capture ARM appears at raw line1150, STOPPING1314, all-dimension saves1318–1328,
normal-stop CANCEL1329 and exit0 at1344. The stopping handler ran on the server
thread; its daemon cancelled normally. No SAMPLE/THREAD/FRAME capture was needed.
Thus the arm/event/cancellation path is exercised, **not** the stalled-shutdown
sampling path. This is non-reproduction, not a diagnosis or fix for R26's hang.

R27 snapshot `e4b76d8288a06ee6c4dcde7ff73e6946febb65335d91340cdc32cce2f0dd653f`;
receipt `shuttle-feedback08-shutdown-stack-20261016-r27-gate-validation.json`;
raw `feedback08-shutdown-stack-r27-native.log`,313,917 bytes, SHA-256
`db51e64e2d6297fad8b07ab86615ff1d64de36d7677257ef42bd7e8d128e40d5`.
R25/R26 evidence remains unchanged. Do not repeat a valid check merely to seek
the intermittent hang. Keep it an unresolved shutdown risk, with the opt-in
diagnostic available for future substantive native validation.

R27 does not prove flight, process restart, connected clients or Creative-body
coverage, explain the owner's cargo loss, mitigate default-two hull penetration,
or establish readiness/M1 acceptance.
