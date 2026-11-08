# Controlled-body E reach timing

## Status

The safe target-denial retry latency fix is implemented; the initial pose gap is unresolved. A controlled-body block-use request is validated against the authoritative body pose at request admission, while the latest client movement intent is integrated later in the server player tick. Therefore a legitimate client-side ray at the six-block boundary can still fail against the prior server pose. This is sequencing latency, not evidence that the emitter needs a special range or that the shared six-block limit should grow.

## Evidence and current behavior

- `hands/client/BodyHandClient.buildBlockUseProof` derives the nominated target and face from the locally predicted body's eye/look ray (six blocks).
- `player_body_control/client/GhostControlClient` sends controlled-body movement intent each tick.
- `hands/network/BodyHandRequestService.admitBlockUse` resolves the owned body and `BodyBlockUseTargetProbe` checks the exact nominated target/face from its current server pose, also within six blocks.
- `player_body_control/server/GhostMobHarnessControl` integrates intent in the server player-tick path, after packet admission can already have run. Packet ordering alone does not establish that this integration occurred before use admission.
- On `DENIED`, `BodyHandClientPolicy.blockUseResult` discards the hand snapshot and forces a refresh, adding avoidable feedback latency even when the denial was only a reach miss.

Admission now preserves the existing gate/proof checks and classifies `TARGET_NOT_ADMITTED` only after valid hand/context/sequence proof and final authority, hands-object, revision, and token revalidation. This reply is distinct from generic denial. The client releases the outstanding-use barrier while retaining its confirmed/presentation proof, allowing an immediate next E attempt; all generic denials still invalidate proof and refresh. No inventory mutation or dispatch runs on this classification.

## Not changed / required for a correction

Do not alter emitter-specific range, trusted client eye/look, exact target/face validation, authority/epoch checks, or call the body movement integrator from packet reception. A correction to the initial pose gap needs a proven shared movement-sequence contract and a bounded per-current-epoch pending-use path that executes after that sequence is actually integrated, without integrating movement twice. The current block-use request contains no movement sequence or aim witness and is not consumable by the harness tick queue. Adding that contract safely requires coordination with the body-control protocol owner; no speculative queue or unverified sequence mapping is introduced here.

Likewise, preserving a hand proof after `DENIED` cannot be safely distinguished from stale ownership with the current result (outcome only, no authoritative hands revision/denial category). Do not broadly retain proof based on an ambiguous denial. A future protocol extension should distinguish target/reach denial from stale hand/authority and retain proof only when the server attests the matching hands revision.

## Validation boundary

The pose timing explanation is source-derived, not a runtime latency test. The safe denial classification/client retry behavior has focused policy coverage; the server classification still needs authenticated runtime-fixture coverage. Follow-up acceptance should exercise server movement integration and authenticated target validation at the six-block boundary with movement/aim changes, plus genuinely out-of-range, stale epoch/identity, duplicate request, and no-double-integration cases. Real-client latency remains a manual validation item.
