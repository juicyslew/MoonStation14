# Living-body external flight

`ExternalBodyFlight` now treats a resolved local NPC grounded profile as a physics
consumer without changing its voluntary movement ownership or NPC-drive marker.
Launch callers can preflight with `ExternalBodyFlight.canLaunch(body)`; launch then
sets momentum on that same entity and keeps the existing server-tick duration,
deduplication, cancellation, and grounded-settling lease.

Passive NPC flight reuses `GroundedHarnessWorldStep.stepNpcFlight`, which invokes
the existing grounded motor with zero wish and the external-flight flag. It does
not require `CharacterControlSystem.canMove`, so stun/critical voluntary-action
gates do not erase already-authorized physical momentum. Resolved NPC flight
eligibility is separate from the transient `NpcMovementDriven` marker and from
player movement ownership. NPC planning yields while a flight lease is tracked;
ordinary containment eligibility also rejects planning and world-ingestion
authority while contained.

The focused native fixtures cover NPC launch/progress, duration expiry followed
by actual landing/settling, solid-wall collision, exact-body containment
cancellation/pinning, and forced flight without voluntary movement permission.
The resolved mouse profile does not currently support `Stunnable`; a separate
typed-stun fixture exercises the shared forced-flight path on a resolved,
movement-owned human body rather than adding unsupported status composition to
the mouse. The focused `npc-flight` selector passed 6/851 fixtures (all six
passed). An adjacent `npc-flight,movement` run was blocked during `compileJava`
by concurrent hand/storage source API mismatches in `BodyHandRequestService`,
`ItemPresentation`, and `BodyHandStateService`; no flight fixtures ran in that
attempt. This does not validate hand-throw/disposal callers, which remain
separate integration work and should reuse this API after clearing containment.

Offline recovery locator persistence uses
`LifecycleStartupRuntime.recordOfflineBodyRelocation(body, proof)`. The existing
flight lease stores its last-attempted dimension/chunk boundary and samples that
inexpensive key after physical advancement; durable lifecycle reads occur only
after a boundary transition. The proof captures the exact body, flight state, and
lifecycle binding and revalidates those values at commit. Canceled or replaced
flight state, changed binding, and replacement entity do not prove custody.
ACTIVE/anonymous bodies reach the facade only at a transition; it rejects them
without enrollment. Unit coverage is added in `ExternalBodyFlightRelocationTest`
alongside existing lifecycle relocation tests; unit/runtime checks remain pending
carry-worker compile release.
