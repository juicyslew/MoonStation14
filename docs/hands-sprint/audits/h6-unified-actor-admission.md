# H6 unified hand-actor admission

`HandActorAuthority` is a read-only server-side resolution boundary for a connected, exact `ServerPlayer` and their
currently controlled CHARACTER body. It asks both the lifecycle and experimental harness authorities for their
public authenticated snapshots and fails closed if both (conflict) or neither is present. Only a CHARACTER result
can proceed; ghost harnesses, non-spectator/Creative carriers, fake or disconnected players, stale/ineligible bodies,
and bodies without prototype-declared hands are rejected.

The hand IDs are obtained only through `HandCapability.resolve(body)`, which follows the body's own bound character
identity and prototype declaration. Carrier identity and default attachment materialization are not capability
sources. The immutable snapshot includes actor/body identity, authority source, mind, harness, epoch, and ordered hand
IDs. `revalidate` resolves current authority and capability again and compares every field; future item commits must
call it immediately beforehand. This feature does not implement commits, transfers, attachments, packets, or session
lifecycle changes.

Automated tests cover the pure conflict/absence admission helper and ordered capability mismatch comparison. A real
authenticated server-player resolution test is not feasible in the current unit-test fixture; the accessor integration
therefore still needs server/game-test coverage.
