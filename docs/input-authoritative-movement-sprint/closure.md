# Input-Authoritative Movement Sprint Closure — 2026-09-24

**Authoritative disposition:** At the owner's direction, close this sprint as bounded experimental work delivered, **not** as full SS14 acceptance or proof that every connected session used the experimental motor. The implementation remains behind the default-disabled, server-startup `experimentalVerticalSliceMovement` gate; vanilla remains the default. This closure records the delivered slice, previously reviewed automated evidence, and the owner's latest limited anecdotal smoke without upgrading unverified behavior into a pass. No implementation, tuning, or further acceptance work is authorized by this closure.

## Delivered bounded slice

The implementation and reviewed tests deliver an experimental input-authoritative movement path for the supported grounded `moonstation14:human` profile:

- A shared human movement motor is used for the server path and client prediction, with explicit input intent and sequenced authority protocol rather than client-reported destination authority. The protocol includes Begin/Ack/Commit and snapshots/acknowledgements; prediction applies collision locally and processes authoritative snapshots. Correction currently preserves bounded grounded displacement arithmetically; it is not complete pending-input replay.
- The motor preserves SS14's projected-wish acceleration math. Source-derived normal HUMAN friction is 20, and qualifying Space Lube uses the pinned 0.05 factor for both friction and acceleration (effective values 1). These factors were not retuned in response to the owner's subjective feel report.
- Authoritative movement is integrated with existing slip, stun, and reactive Touch systems, with movement/surface diagnostics. Player slip contacts are tested against the collision-resolved endpoint AABB, not a swept path; very fast path-crossing contacts may be missed, a bounded limitation the owner accepted for this experimental slice.
- The path is experimental and opt-in only. Implementation presence and automated coverage do not establish live single-owner operation or general mode handoff correctness.

See the [sprint design and historical milestone record](Instructions.md), [connected smoke checklist and bounded owner update](audits/experimental-connected-smoke.md), [two-session owner log and grounded-support addendum](audits/2026-09-24-two-session-owner-log-and-grounded-support.md), and [ground-friction audit](audits/2026-09-24-ground-friction-and-client-presentation.md).

## Validation evidence and owner report

The reviewed pre-commit automated evidence was:

- `gradlew test --rerun-tasks --no-daemon` — successful.
- `gradlew build --no-daemon` — successful.
- GameTests — 116/116; `build/gametest-run/logs/latest.log` lines 35, 240, and 241 record 116 started, 116 completed, and required tests passed.

These checks were recorded before the owner's commit. No new validation run after that commit is claimed here. Automated tests/builds are not connected-client acceptance.

The owner reported a later connected playtest and then said they committed the changes, saying that everything else looked working, while reporting that some turning animations repeat and sprint particles continue when jumping during a sprint. The owner suspected Creative mode and decided to make the server default Survival. No logs or video for this latest test were supplied. Accordingly, this report does **not** establish the effective mode or build, Begin/Commit, custom motor ownership, handoff, timing, measured movement/speed, correction behavior, or three-puddle bound. The reported visual details remain anecdotal observations, not diagnosed causes or verified regressions.

Space Lube felt subjectively too frictional to the owner, who chose to trust pinned official upstream factors and defer tuning. This is not a measured parity discrepancy and does not authorize changing the pinned friction/acceleration factors. Revisit only with source-supported, measured evidence.

## Explicitly deferred and unverified

Closure does not imply any of the following passed:

- Latest-session effective mode, matching build, Begin/Commit, exact movement authority, or the complete authenticated connected acceptance sequence.
- Supported/unsupported-mode handoff, teleport acknowledgement, dimension/respawn transitions, adverse latency/reorder/loss, reconnect, and related single-owner behavior.
- The M4 three-puddle numeric traversal bound or connected chain-lane result. No numeric bound is accepted.
- Villager AI wish adapter behavior as an equivalent movement proof; 20/50-player performance benchmarks; or full pending-input replay for client prediction.
- Physical prone/hand occupancy, and HUD/audio synchronization and timing.
- The owner's explicitly deferred minor turning-animation repeats and sprint particles continuing during a sprint jump. These visual observations are not represented as fixed.

The older open milestone and acceptance wording in `Instructions.md` and dated audits is historical evidence/specification, not a directive to reopen this sprint and not proof that its gates passed. This closure supersedes that wording only for sprint disposition; it does not rewrite historical audit facts, waive the deferred limitations, or revise the preceding slip/stun sprint's separate closure. See the [*proposed next systems, not authorization for implementation*](../next-systems-roadmap.md).
