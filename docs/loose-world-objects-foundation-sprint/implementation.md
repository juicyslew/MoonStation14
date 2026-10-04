# Foundation + Jug — implementation state

Connected review reported usability failures beyond trusted fixtures. See [second connected feedback](connected-feedback-02.md) for the latest corrections/evidence and short recheck; [first feedback](connected-feedback.md) includes pickup-animation crash analysis. This is **not owner-accepted sprint closure**.

## Delivered

- Registered persistent loose-item entities with sole stack/component ownership, fractional placement, stable resting orientation, bounded collision/motion and normal save/load. Unrelated vanilla drops retain normal behavior. The feedback pass extends mod-item Creative drops/pickup explicitly.
- Prototype-inherited physical/presentation profiles. Jug uses its existing centered 3D six-fill model, actual reagent tint and .5×.75-block geometry—not a thrown item icon.
- Exact bounded pile selection, cycling/in-world outline, authoritative whole-stack pickup/drop/throw and preserved storage-host transition/window behavior. The old pile text cue is removed.
- Shared held/loose filling and sipping with actual admitted amounts, mouth/stomach eligibility, cooldown, verified publication and bounded known-failure compensation.
- Generic use/throw intents, including fresh-proof use-then-throw queuing. Grenade aliases share the same loose throw owner and preserve fuse deadlines on the ground.
- Contact-only jug break/spill, including filled-jug floor/puddle conservation. Failed or partial spillage retains unadmitted contents rather than deleting them.
- Server pull constraints, separate transient hand reservations, other-hand usability, .95 voluntary movement factor and exact-target invalidation/cleanup. Target identity never becomes a hand stack.
- Pull slowdown is stamped into movement snapshots and mirrored by controlled-body prediction before replay; loose target physics remains server-authoritative.
- SS14 drinking audio with unresolved individual provenance explicitly recorded.

## Validation

**Latest feedback pass:** 1,904 units and all 432 required GameTests pass. Current logs, coordinate-conversion correction, item-only jug/snapshot appearance, contextual equipment/liquids, command ingress and puddle ingestion are indexed in `connected-feedback-02.md`. First-pass/crash evidence is retained in `connected-feedback.md`. The figures below describe the original foundation acceptance run, not the newer enlarged-footprint workload.

The final shared server run passed **all 426 GameTests** (`build/loose-foundation-gametest-final-prediction.log`), including actual pull-service→impulse→entity movement, jug fill in both directions, empty-hand world sip, filled-jug spillage, retention/reload and ground-fuse behavior. It includes the synchronized pull flag and matched client/server movement factor. Trusted body fixtures are not authenticated connected clients.

Full units passed **1,896 tests, zero failures/errors** (`build/loose-foundation-unit-final.log`). The real-asset software diagnostic is `build/reports/loose-object-visuals/jug.png`; it verifies mesh/UV/tint/pivot correspondence but is not a Minecraft viewport capture. Diff checks passed. Successful disposable fixtures are removed after artifact retention; root validation logs remain.

## Measurements and limits

On OpenJDK 21.0.10, Windows 10/amd64, 12 reported processors, the seeded three-floor pile fixture used 1,024 live objects, 10 warm-up batches and 30 samples per workload. Direct work per population tick:

| Workload | p50 | p95 | p99 |
|---|---:|---:|---:|
| 64 settled | 0.007 ms | 0.067 ms | 0.071 ms |
| 256 settled | 0.029 ms | 0.114 ms | 0.128 ms |
| 1,024 settled | 0.119 ms | 0.468 ms | 13.064 ms |
| 256 continually jostled | 3.484 ms | 4.761 ms | 5.017 ms |

These are **direct synthetic object-work measurements**, not whole-server tick, authenticated player, network, memory or client frame-time results. Raw timings, summary and environment are retained in `build/reports/loose-object-measurements/` after fixture cleanup. No 20/100-player guarantee is established.

The previous successful run had active p95 5.633 ms, exceeding the provisional 5 ms object-work guardrail; the final active p95 is 4.761 ms, with a substantial settled-workload tail outlier. This variability and the absence of a combined active+idle total prevent a performance acceptance claim. Keep these limits visible rather than retroactively inflating the threshold. Retain the existing Minecraft collision path pending actual cost attribution, rather than introducing a new collision solver merely from a plausible entity-query hypothesis. The 50 ms tick interval is a total-server reference, not this subsystem's allowance.

## Remaining judgement / boundaries

- The owner ran an initial connected check and reported failures documented in `connected-feedback.md`. A connected recheck of corrections and two-actor/storage-continuity acceptance remains outstanding.
- The bounded velocity pull solver is not a rigid joint or client-predicted physics. Its lag allowance and 3D throw/contact tuning need gameplay judgement.
- P and ] are Minecraft usability adaptations; Ctrl+Q follows SS14. N/M remain transitional aliases.
- Mouth is an explicit capability atop existing prototype stomach eligibility, not removable mouth/stomach organs.
- Small objects only: large body-blocking movable machines, anchoring, construction and clothing are not implemented.
- No new pressure-wind or explosion impulse producer was introduced; existing explosion code has no suitable impulse path. The loose-object impulse entry is ready for those future systems.
- Loaded probes/motion precheck chunks; arbitrary shape callbacks are not claimed universally incapable of loading chunks.
- Audio author/license for the copied drinking sound remains unresolved in `docs/audio-unknown-provenance.md`.
- Stored armed grenade child stacks still lack independent fuse processing (unlike SS14). Direct held/loose-root deadlines are covered; contained-item timers and offline expiry are not promised.

Changes are uncommitted and unstaged by the assistant. Do not treat this document as owner closure.
