# Client movement presentation repair

## Scope

The owner authorized immediate repair of two manual-test regressions on 2026-11-07: choppy ordinary character movement and walking animation caused by a translating/rotating ship. This is a bounded repair before consolidation, not resumption or acceptance of the entire merge or an ECS architecture refactor.

## Implementation and validation limits

The correction reuses `OwnedPredictionApplicationSchedule` for exact-session queued snapshots and shared planned-tick dispatch, `GhostPredictionHistory` for held controls and the known applied movement reference, and the existing owned native clock, temporal simulation, render-origin, and accepted-own gait paths. No movement-physics or native-backend change is part of this repair.

The latest focused development check compiled main/GameTest/test sources and passed **182 JVM tests across 16 selected classes**, with zero failures/errors/skips. Compiled-client wiring checks accompany the functional scheduling/history/clock checks and fixture-phase safeguards. Independent read-only review passed after verifying that reconstruction resets the native/application clock to the authoritative base before replay. These checks do not establish rendered smoothness or visual acceptance. The bounded serialized gate uses sprint ID `main-merge-client-presentation-20261107`; its result must be distinguished from the older, still-failed broad merge gate.

Its first run, `run_f81ae49cae0d017feb3ede99`, failed 22 of 180 JVM tests before readiness, native, or package execution. The corrections reconcile analytical ground tests with the already-selected M1 single-step policy and repair compiled-consumer assertions that selected a convenience overload or the old immediate snapshot callback. Ground/air/flight, collision-order, threshold, support-probe, and frame-publication safeguards remain; no physics change or tolerance relaxation was made to clear this failure. Fresh focused execution of all 14 classes and independent review passed afterward. Preserve the original FAIL; a replacement requires fresh source-bound scope/evidence.

Replacement `run_1a664d8914ea2e2eaddcd157` passed the 180-test JVM stage and prediction readiness (**166 tests / 20 classes**, no failures/errors/skips). Native discovery selected **5/1658** cases. The run then failed a fixture that read a snapshot before the real SERVER_POST publication, and crashed when another asynchronous observer polled an already-closed mock connection. No terminal native pass count or package pass was established. Fixture repairs must preserve exact completed-tick/ACK matching, actual publication, duplicate-step checks, and listener/resource cleanup; silently ignoring the channel error or dropping the tests is not a correction.

The fixture correction samples physical results before waiting for their published snapshot, preserves exact completed-tick/ACK matching, and keeps fixture resources alive through asynchronous observation. Terminal cleanup is identity-scoped; checked callback exceptions follow the existing unregister-and-scheduled-failure path instead of escaping into the server event bus. Fresh 16-class focused execution and independent review passed afterward. Production presentation/physics code and native selectors were not changed for this correction; a fresh serialized run is still required.

## Established defects

- The existing snapshot-application schedule was not wired to receipt: network callbacks could overwrite the owned pose and interpolation origins between client ticks.
- The client could restore authority on ticks with no queued authority application. Planned held controls were also skipped when their original packet had already been acknowledged and removed from pending history.
- Reconciliation updated gait from total world-position change, bypassing the existing accepted-own-displacement animation path and counting carrier/correction motion as walking.

The existing native temporal movement path remains present. These findings do not justify changing movement physics, native collision bounds, camera smoothing, stock Sable, prototype values, or actor-primary/scene-complete publication ownership.

## Acceptance boundaries

- Packet arrivals queue exact-session authority without rewriting the presented body pose. Apply the latest accepted authority once at the client tick boundary; continue new fixed prediction ticks without reapplying authority.
- Acknowledged held controls remain valid input for later physical application intervals. Applied and settled sequence numbers stay distinct; captured references and contact/angular evidence retain their proper ownership.
- Render origins and the accepted native frame interval remain coherent. Replayed corrections do not count as new walking. Only newly accepted own movement drives local gait.
- Idle ship translation/rotation does not animate walking; own walking aboard still animates. Keep remote/NPC presentation and existing shuttle behavior intact.

## Owner recheck

Use matching client/server builds.

1. On stationary ground, walk, strafe, turn and stop. Check that camera/body motion is smooth.
2. Stand still aboard a translating and rotating ship. Check in third person that the character does not walk in place.
3. Walk and stop aboard that ship. Legs should follow own movement, not continuing ship motion.

Automated checks do not establish visual acceptance. The broader merge retains its separate unfinished validation and architecture review.
