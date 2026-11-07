# R29 complete-candidate corrections

R28's two native timeouts and later diagnostic results remain historical evidence. Its exhausted ERROR head was retired through owner control; the next normal candidate is fresh, not a replacement or replay of that ERROR.

## Material corrections

- `GasMixture` now rejects a finite double volume that narrows to nonfinite float storage. MAIN reproduced the constructor defect and validated the 14-test class. This establishes the invalid-storage path, not the original crashing cell's value or producer.
- Pipe retirement assigns archived gas its retiring node's physical volume, preserving the ledger share's moles and temperature. A valid 1.3 m³ network reproduced the previous `invalid retired owner state` exception: six tests / one failure RED, then six / zero GREEN. Generic splitting and strict archive validation are unchanged.
- Native fixture seeds use their current network volume. The vent fixture compares stored volume with the canonical float representation; ownership, CAS, topology, gas-revision and gas-content assertions remain.
- The power fixture queries from a controlled UNKNOWN state and accepts either legitimate bounded recovery schedule. Precise stored-frontier admission remains covered by the shared-owner JVM regression; no power gameplay code or timeout changed.
- The exterior fixture awaits real ownership proof within its original deadline rather than treating arbitrary passable air as immediately proven exterior.

## Current validation

The complete current-source floor passes **175 classes / 1,013 tests**, zero failures/errors/skips, all three compile tasks passing, and 10,102 unchanged source/resource hashes. The focused batch separately passes **83 tests across six classes**. Current floor: `build/agent-workflow/teg-sprint/native-timeout-repair/floor-r29-final-069c1047-b6a0-48c8-8f7e-705eaeee66f3/`.

No diagnostic is substituted for normal feature validation. The isolated wall-opening case passed; the context-qualified diagnostic advanced beyond it, then crashed on invalid volume and reported additional assertions. Neither result establishes the old hang as fixed. Normal serialized native/readiness/package validation must establish completion and expose any remaining failures.

Approved R6 assets, upstream TEG coefficients, paid cadence, finite accounting, and the 2 J / 1,000 J / 1,200-tick guards are retained. The shared-network model and explicit Minecraft adaptations are indexed in [PIPE_NET_CONTRACT.md](PIPE_NET_CONTRACT.md). Owner testing uses [MANUAL.md](MANUAL.md), including a fresh disposable world for schema 2. No feature-gate PASS, merge authorization, full-native-suite result, GPU/audio review, or owner acceptance is claimed here.
