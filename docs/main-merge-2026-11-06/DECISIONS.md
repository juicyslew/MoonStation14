# Main into agent/03 — merge decisions

## Status and baselines

**In progress; not accepted or technically ready.** M1 was owner-accepted before this merge. Its historical gate does not validate the combined tree.

- Our parent: `2c38bdff3a2716df2f7e86bd09bc3c68aed2bd39`.
- Incoming parent: `b606b6bddbdcce0ff32bb43fe726bdf45f890635`.
- Merge base: `a5e72a15582725c79b36358fe84028a6450a98fe`.
- No commit, push, automatic client launch, or owner configuration change is authorized by this document. M2 shuttle development is not part of this task.

## Decisions

| Area | Resolution and consequences |
| --- | --- |
| Physical movement | Retain one native collision/movement path. Incoming control, flight, status and role policies feed it rather than introducing another simulation controller. Preserve six Sable substeps, collision witnesses, frozen XYZ launch references and once-only point-velocity inheritance. |
| Vanilla AI normalization | Preserve M1's two narrowly targeted `aiStep` velocity-write redirects and include incoming NPC movement ownership in their shared predicate. The extra main `.scale()` redirect is redundant because the protected Vec3 setter receives that result; do not stack broad normalization bypasses. |
| Input batches | Both parents fold an ordered queued prefix into held controls before one physical step. Preserve that behavior and jump-edge latching; do not reinterpret FIFO as a separate physical move for every packet. |
| Acknowledgements | `acknowledgedAppliedSequence` identifies actually applied input. `settledThroughSequence` separately retires applied or permanently skipped inputs. Dequeue, rejection and discard must not manufacture applied facts. Ordinary queue pressure rejects input without pretending sequence-number exhaustion occurred. |
| Time coordinates | Native completed simulation steps remain physical time. Server/application ticks schedule input. Restore both coordinates explicitly; a sequence number must not become elapsed physical time. Held input can advance another application without inventing a fresh input ID. |
| Replay evidence | Keep original supported-movement references. Contact/angular evidence belongs to its exact input and application interval; held repeats at another interval cannot reuse or overwrite it. Safe authority barriers retire failed input through settlement, without requiring false applied acknowledgement. |
| Presentation | Preserve actor-primary versus scene-complete publication boundaries, render-origin writes, and bridge-only heading invalidation on accepted authority restores. No camera-offset, smoothing or forced-grounding workaround. |
| Ground movement arithmetic | **Conditional candidate, not an accepted replacement.** The owner prefers smoother movement only if it does not jeopardize accepted shuttle behavior. Current candidate retains incoming finer horizontal friction/acceleration arithmetic within each Minecraft tick, with one collision sweep. This changes acceleration and release damping relative to M1's former single arithmetic step. Keep it only after the unchanged shuttle regression requirements pass and the owner rechecks the established shuttle scenario; otherwise retain M1 ground arithmetic. No relaxed collision bounds, extra movement passes, forced grounding or camera smoothing to make it pass. Do not claim the finer interval is a proven SS14 simulation cadence. |
| External flight | Preserve M1's scripted slip-impulse lease separately from incoming ballistic portable-body throws. `ExternalFlightPhysics` selects the existing caller's policy through the same motor and is synchronized in snapshots. Scripted leases retain their prior vertical behavior; ballistic throws receive ordinary gravity/drag, including when a stale natural-flight flag remains. |
| Queries and ownership | Reuse shared world/frame/unknown queries, physical-box clearance and transfer transactions. Invalid present nominations fail closed; ordinary DROP keeps its authoritative point/reach/clearance checks. Exclude only authorized exact occupants. Portable bodies remain original entities, not loose-item proxies; canonical release owns hand/root mutation. |
| Contact episodes | One `StepContactEpisodes` owner serves puddles and items. Puddle identity includes frame ID and storage cell. Reconciliation uses captured shared-query contacts, never a world block lookup at frame-storage coordinates. Unknown contact queries are not proof of exit. |
| Item rendering | Reuse `WorldItemPresentationRenderer` for item-specific mesh drawing, including produce scale and damage tint. Preserve M1 placement, outlines/culling and exact draw-pose observations through a shared before-draw hook. Ordinary opaque jugs explicitly retain M1's `Sheets.cutoutBlockSheet()` material; existing other callers retain their default material. Packed-light accounting is unchanged. Native visual validation remains outstanding. |
| Prototypes and registration | Preserve both parents' supported typed components, resolved consumers, mixins and callbacks, once each. Compile success is not proof that a command or lifecycle callback is registered. A review reported missing shuttle hooks; subsequent bounded current-source and parent inspection found each ticket, command and level-lifecycle hook already present once. Do not add duplicates on the strength of an unverified review claim. |
| Tests | Preserve meaningful coverage, not contradictory assertions or obsolete packet layouts. Update arithmetic expectations analytically and validate current wire schemas. Do not loosen collision, energy, ownership or timing bounds merely to obtain a pass. |
| Licensing | Retain current M1 acceptance and the incoming bounded M0–M3 license-risk authorization. Neither is legal clearance or automatic public-distribution approval. |

## Parity boundary

Pinned SS14 source `c9df5ef5d675b0d1d226828bddf6b78c28502d91` confirms frame-time-scaled friction/acceleration and movement-component defaults. Effective friction 20 is consistent with base friction 2.5 and tile-friction multiplier 8. A guaranteed 60 Hz upstream cadence was **not** established by the targeted audit. Arithmetic subdivision and one Minecraft collision sweep remain port adaptations.

## Validation and remaining work

The final forced focused JVM run executed **30 classes and 328 tests with zero failures/errors/skips**. Main, GameTest and JVM test sources compile. The core `SableOwnedBodyCarryGameTests` assertions remain unchanged from the accepted M1 parent; their native execution against the combined candidate is still required. These JVM checks are not merged-source native/runtime readiness.

First serialized gate `run_d91d42fa0a8dfbf0705c6913` failed during its broader JVM batch: **773 tests, five failures; native checks did not run**. It exposed missing owned-AI write protection plus test fixtures/proofs still assuming old resource-ID or consumer wiring. The protection was restored without changing ground arithmetic; prototype tests now use canonical resource/inheritance resolution, and angular/diagnostic proofs follow the application-scoped scheduled consumer. A fresh focused remediation run passed **five classes / 23 tests**, with no skips. The broader gate must be replaced and rerun; this is not a native movement verdict.

The configured workflow currently exposes no trusted full-GameTest exception. Its full pre-acceptance pack requires an exception bound to the sprint, worktree and exact source digest; the author cannot fabricate one. The serialized focused gate must cover the relevant shuttle and shared integration cases. Full broad regression remains required via the authorized owner-exception or post-acceptance route; do not describe focused coverage as a full-suite pass.

Before handoff: finish the identified integrations, validate the conditional movement-arithmetic candidate, review both-parent coverage and registration, mark reviewed Git resolutions, then run the required serialized broad regression, applicable readiness selectors, package and provenance checks. Native/client visual behavior and owner acceptance must not be inferred from unit tests. See the existing shuttle `HANDOFF.md` for accepted M1 invariants and known manual limitations.
