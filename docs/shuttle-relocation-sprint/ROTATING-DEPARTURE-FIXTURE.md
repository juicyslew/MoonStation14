# Rotating human departure: support premise correction

R20's `boundHumanDeparturePreservesRotatingFramePointVelocity` failed before
rotation/departure: after30 support ticks the human had fallen to
`(1042,277.2103235260935,643)`, velocity `(0,-1.3628888116333076,0)`.
Raw `feedback08-pry-full-context-r20-native.log` lines22459,22575–22576,25707
in the existing reports directory. It records no velocity-continuity assertion
for this case; do not infer loss of inherited angular point velocity.

The fixture used `withDeck`'s default zero-corridor overload, which authors no
physical support pad. The neighboring translating departure uses the
existing eight-block corridor variant. R22 gives the rotating case that same
owned stone support/landing corridor and reuses the stronger existing support
wait (fresh exact-frame native collision, selected stone floor/storageY,
onGround/tracking and complete trace), not just an onGround bit.

Only this fixture changes, plus a method tag. Shared scene/motor/collision helpers
and production code do not change. Coordinates and native linear nudge,
non-vacuous angular point-velocity requirement,0.02m/s continuity bound,
body/Mind/hands identity and actual landing assertions remain. No gravity change,
hover, rotation freeze, cap, teleport recovery or Sable internal modifications.

Source review passed; current exact plan:
`GATE-PLAN-08-ROTATING-LAUNCH-R23.json`, one existing case selected by
`shuttle-rotating-departure-regression` out of891 current native cases in a fresh
default-two fixture. Native launch compiles the fixture; unchanged runtime JVM
checks are not repeated. Evidence scope:
`build/shuttle-feedback-08-author-rotating-support-r22.log`.
This is a test-premise correction, not an asserted production velocity fix or
sprint readiness. Keep R20 failure artifacts and M1 acceptance boundary intact.

## R22 outcome and R23 stimulus

R22 selected1/891, completed1/failed1, normal saves/stop. It established strong
native support and reached departure. Horizontal velocity continuity error was
**0.0m/s**; expected/actual `(0.14410392299735636,0.2404584527134998)`.
Point-versus-COM horizontal difference0.09022177697676755>.05, but measured
angularspeed~0.05663rad/s was below the unchanged0.1 minimum. It failed angular
nonvacuity, not continuity. Raw `feedback08-rotating-support-r22-native.log`,
SHA-256 `a7a88c5b0e18e029b0549f3bae55b91110518bf3f47be99115c36cd0c37d7d68`,
lines1214–1239. Snapshot:
`2431655408af3ead8f80914b2058a5344ce09cdabcc1222b0417c1c213dc1a0b`.

R23 strengthens this fixture's **single** angular launch from0.8 to2rad/s and logs
immediate actual native launch state. Existing measured angular/point contribution,
continuity and landing assertions remain. No feedback force loop or runtime
physics alteration. The reduction's precise force cause is not established;
stronger stimulus is a test-premise change, not a production velocity correction.
Author scope `build/shuttle-feedback-08-author-rotating-launch-r23.log`.

## R23 verified result

Filtered **1/891 selected, 1 completed/passed, 0 failed**, default substeps2,
normal stop and all dimensions saved. Independent review checked raw evidence,
generated configuration and the guarded landing/identity assertions.

- Launch admitted angularY approximately2rad/s with the human grounded.
- Departure measured angularY approximately1.072rad/s; point-versus-COM
  horizontal difference1.718385866538371m/s, satisfying both nonvacuity guards.
- Expected and actual horizontal velocity:
  `(0.4196302592169499,2.1666878065994233)`m/s; error **0.0m/s**.
- Fresh native evidence accompanied world-frame airborne follow ticks0–5.
  At follow tick6 the body landed **back on the original moving deck**. The
  passing guarded path requires the exact entity, character binding and hands,
  fresh collision evidence and matching authoritative landed velocity; these
  identity values are assertions, not separately printed raw observations.

Correction to earlier R22/R23 plan rationale: despite its timeout message
mentioning a world-floor corridor, `verifyDepartureAndLanding` actually requires
landing on the original native deck. The stone corridor supports the physical
deck; this result does **not** prove landing on world terrain. The historical
bound plans/receipts remain unchanged.

Raw `feedback08-rotating-launch-r23-native.log`, SHA-256
`23e4ee063da7328252c0c7ebd7525f747efef7d5cc93d484c29f07a35f6d2f25`,
lines1207,1215–1225,1230–1248. Bound snapshot
`498caf58b378526a3f3cea5de54e5c9171d74c4435fc5d15970a4938dfe006f8`.
Receipt `shuttle-feedback08-rotating-launch-20261015-r23-gate-validation.json`.
This resolves focused fixture validation only: no production movement change,
full-suite pass, owner readiness or M1 acceptance is claimed.
