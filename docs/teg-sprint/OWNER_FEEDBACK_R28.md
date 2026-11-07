# R28 owner feedback candidate

This candidate is the intended replacement for settled R26 `run_8f3f2254ed1201e22b5cfb5d`. No R27 submission was made. This is a candidate evidence summary, **not** a technical-readiness result, feature-gate PASS, or owner acceptance.

## Pipe-network request and scope

The requested model is one authoritative gas mixture per connected PipeNet: nodes carry metadata and membership, topology changes dirty and reconcile affected graphs, and ordinary gas processing does not do per-tick pipe BFS or fanout. Consumers use the network's pressure and temperature; Analyzer moles are a node-volume share of network gas. The world-atmosphere spatial solver remains a separate owner. Implementation boundaries and deliberate departures are recorded in [PIPE_NET_CONTRACT.md](PIPE_NET_CONTRACT.md); pinned upstream composition and mapping are in [PARITY.md](PARITY.md).

Minecraft adaptations are explicit: finite retired-node shares are archived rather than discarded as in upstream deletion; physical adjacency uses Minecraft's six block faces and mutations use server-thread transactions; known membership and shared gas persist across dormancy. Unknown geometry blocks only the affected network until a full load/wake reconciles it through the existing AtmosphereService. No chunk forcing or guessed links. Schema 2 requires a fresh disposable world. This is not a migration claim and does not authorize resetting an owner's world. Existing R27 fixture corrections are preserved. Approved R6 assets, UI behavior, TEG coefficients, and the 2 J / 1,000 J / 1,200-tick guards are unchanged.

## Evidence and limits

Final local validation passes **175 classes / 1,008 tests**, with zero failures, errors, or skips, all three compiles passing, and 10,102 source/resource files unchanged during the check. Evidence: `build/agent-workflow/teg-sprint/shared-pipenet/floor-r28-final-r2/20261107T014803Z-9738f51c/`. The focused 22-class batch separately passes 119 tests (`focused-core-r6/`). Regressions cover shared identity, gas-update metadata stability, merge/split, physical transfer/recombination volumes, empty-network temperature, fractional-volume retirement, persistence, and rollback. Earlier 1,002-test results predate the final fixes and are history, not current proof.

All 113 R6 destinations match approved digest `7cb9fa96a1501894420f838e569af8f65cdd44d4f6080a0ebbfd9cb3ffdbd38c`; all 405 frozen R5 source guards match. Protected `TegContinuousOperatingPlan` and approved `PowerRuntime` hashes also match. See [asset contract](ASSET_RUNTIME_CONTRACT.md). Independent CPU review opened 12 current Analyzer preview images and the contact sheet; the complete manifest contains 78 cases. This does not certify the remaining images or GPU presentation.

Compiled tag preflight selects **248 / 1,432** fixtures with 42 tags (the retained 40 plus `ventilation` and `atmosphere`), including all four new `SharedPipeNetworkGameTests` methods. This is discovery, **not native execution**. Native sharing, valve merge/split, dormant reactivation, and empty-frontier recovery remain unexecuted pending the serialized gate. Under the workflow's default authorization, the broad full native suite remains a post-owner-acceptance/pre-merge requirement; no pre-acceptance full-suite exception is claimed. Local checks are not a gate verdict or merge authorization.

For owner review, use the four-step [MANUAL.md](MANUAL.md) on a fresh disposable Overworld world. It contains the fixture, machine, Analyzer, and connected-node/valve observations; do not reset an existing owner world. Review and acceptance remain with the owner.
