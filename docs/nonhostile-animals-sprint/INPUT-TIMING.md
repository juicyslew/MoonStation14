# Input timing correction (selected diagnostics passed)

The model below is wired into all three server owners and client prediction.
Earlier focused JVM XML reported 411 tests across 55 suites with no failures or
errors; the earlier 525/54 report was not corroborated. Main/GameTest compilation
and independent client/server source reviews passed. Diagnostic
`run_28445f8a9c193569cb5a5bb0` passed the selected headless native, explicit-class
JUnit, and UI/interaction/prediction readiness checks; acknowledged, cleanup
CONFIRMED. This is not feature readiness or owner acceptance. No post-change
connected owner test has run.

The preceding diagnostic failed its cadence fixture: Ready's bootstrap snapshot
and the attempted burst snapshot shared a world tick; release was also attempted
after that tick's step. The corrected fixture observes burst, held movement, and
release on distinct ticks, retaining strict ACK/state and duplicate-step checks.
No production behavior was changed for that fixture correction.

## Findings

The original gate discarded valid increasing inputs sharing a server tick.
The interim bounded FIFO preserves them, but consuming one message per tick can
retain a backlog after a stall. The owner reported fewer small corrections but
occasional larger ones. That report does not establish incident causality.

The existing client replays one movement step per unacknowledged message. Merely
holding input on the server would therefore be incorrect: authoritative world
time can advance without advancing the input acknowledgement, and message-count
replay can simulate that elapsed time again.

Pinned upstream: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, Robust
`3136118b5338ef2d9580178caf5c723e65eb76e7`. Relevant owners are Robust server
`InputSystem.InputMessageHandler` and client
`ClientGameStateManager.ApplyGameState/PredictTicks`. Commands carry simulation
tick and sequence separately. Replay applies transitions before each simulated
tick, rather than treating command count as elapsed time.

## Chosen local model

- Keep the existing session, authority, movement motor, and transport owners.
  Add intended simulation tick to intents and the held control state to snapshots.
  Keep authoritative world tick and settled input sequence distinct.
- Fold accepted full-state inputs in sequence order. Continuous wishes, view,
  sprint, sneak, and jump-held state persist until updated or reset. Preserve a
  jump press/release pair received in one tick as one pending jump pulse; retain
  existing held-jump behavior. Share this accumulator between server and replay.
- Each server owner samples that state at most once per world tick. Processing
  multiple transitions must not produce multiple physics steps. No persistent
  FIFO of movement steps remains. Acknowledgement settles the updates used by the
  authoritative step, not one physics step per input message.
- Initial authoritative snapshots must be available without first receiving an
  intent. Client movement prediction waits for this clock/state anchor.
- Estimate the prediction target from the latest authoritative tick, elapsed
  monotonic time, and a bounded lead (one tick plus existing connection-latency
  ticks). Bound the horizon by the existing 64-entry prediction window. Do not
  derive elapsed simulation time from the number of client post-tick callbacks.
- Each client update rebuilds prediction from the latest authoritative state to
  that bounded target. Apply all pending transitions due at a simulated tick,
  then simulate once. Several callbacks at the same estimated tick may revise
  that prediction, but must not cumulatively advance time. Preserve monotonic
  input-tick association within the session.
- Seed replay from the snapshot's held state, discard settled sequences, and
  retain existing history-loss and authority barriers. Reset timing/state on
  session changes, stop, teleport, dimension changes, and relevant mode barriers.
- Retain bounded admission and history. Neither client tick metadata nor packet
  frequency authorizes extra server physics. Preserve all three current owners:
  lifecycle character, lifecycle ghost, and the older harness controller.

This is a Minecraft adaptation of upstream's separation of command processing
and simulation time, not a claim of identical networking or prediction frequency.

## Integration and remaining acceptance

1. Shared control-state, tick-clock/replay primitives and wire fields are wired.
   Snapshots also seed natural-flight state before replay; attachment sync order
   must not determine the reconstructed flight mode.
2. Server state folding and client replay replace the interim per-message
   physics-step FIFO. Deterministic tests cover grouping, held state, release,
   jump taps, barriers, acknowledgements, latency, and bounded work.
3. Independent client/server reviews passed. Run fresh focused headless
   diagnostics through the coordinator using an empty managed fixture.
4. Retest connected behavior only when the owner authorizes it. Kobolds and
   observer checks remain outstanding. Existing captures predate this correction.

Separate physics finding: projected-wish acceleration without airborne friction
can build excessive speed while turning during Minecraft jumps. Preserve SS14's
ground snaking and momentum paths; constrain only additional voluntary energy
in ordinary gravity-air steering. This is a deliberate jump adaptation, not an
upstream universal speed cap. Its implementation and tests are separate from
the timing change.
