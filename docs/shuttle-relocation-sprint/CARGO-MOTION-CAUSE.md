# Cargo motion-cause diagnostic

Cause investigation, not narrow-fix verification. Owner reports whole-item disappearance and floor clipping. Cargo-outline single-camera-ray versus captured-render-geometry work: normal compile, 72 JVM tests/12 classes, source review passed (`ses_e5b3a86a0ffeIvKtYDCJ1l4UUY`). Visuals **unverified**.

SDK checks rule out blanket double-quaternion application, normal stable-client-tick desynchronization, and an under-height frustum bound for the upright yaw-spinning jug. No speculative orientation/culling override. Does an actual hand-placed jug have server XYZ support-local jitter under combined linear/angular motion, or does evidence point to client presentation?

Shared `BodyHandReleaseNativeGameTests` tag `cargo-placement-motion-cause-diagnostic`: stationary hover and 1 m/s world-X + 3.2 rad/s yaw. Authenticated walking-body release checks reagents; samples row 0 plus 160 ticks (161 max). Registered velocity controller; no pose/gravity overrides. Original eight-tick case unchanged. Invalid finite XYZ/quaternion/frame/motion/cadence coverage fails. Strict 0.06-step/0.03-total XZ checks; Y measured without arbitrary tolerance. Full-precision records dump once on termination, with cleanup.

Fixture/recorder review passed (`ses_e5b36ddfbffeWed5noG4MWO5Ho`), with 13 focused tests passing. The source-bound precheck passed 607 tests/74 classes.

## Measured result

`run_006d6ee1d694dfe9d36f6d7f` passed its filtered **2/994** cases; result acknowledged and cleanup confirmed. Both tapes contain 161 rows with complete coverage. Moving cargo had exactly zero local X/Z range and 0.999476 mm Y range, including its first-tick descent from the 1 mm placement clearance. Stationary cargo's X/Z ranges were below 0.0002 mm; Y range was 0.999653 mm. No significant server-local placement jitter was reproduced in these cases; no server carry workaround is justified.

Console records are in managed artifact `artifact_task_006d6ee1d694dfe9d36f6d7f_log_62b9ff50b7b858f16134` (SHA-256 `ab1e7a49672535867f850e4627cb6c177471d0027cad2340ec26fe2d7f153014`), not the local Log4j `latest.log`. These measurements do not establish packet-anchor, client-render, culling or sub-tick correctness. Subsequent outline and jug entity-render-layer corrections are indexed in `CARGO-ORIENTATION.md`; their visual outcome remains unverified. The planned two-client check is owner-run, not automatic. This diagnostic PASS is not feature readiness or M1 acceptance.
