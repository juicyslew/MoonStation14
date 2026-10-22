# Portable-body throw and disposal integration

## Scope/status

Portable-key hand throw and held-key disposal now have source integration through the existing `BodyLooseThrow` and shared release transfer seams. Source-level native fixtures added, not run in this source-only pass.

## Reused foundations

- The pinned SS14 `MobMouse` composition and local resolved `PortableBodyComponent`/`mouse_tiny` mapping are documented in `FOUNDATION.md`; Minecraft portable keys are an inventory adaptation, not copied mouse entities.
- Ordinary throw remains server-authoritative in `BodyLooseThrow`, with hand token/revision/actor checks, loaded trajectory/box checks and resolved `LooseObjectProfile` speed/range/cooldown. It must retain those rules but launch the referenced original body, not create a loose item proxy.
- `ExternalBodyFlight.canLaunch(Mob)` and `launch(Mob, velocity, durationTicks)` are the shared flight consumer. The existing `PortableBodyReleaseTransaction` captures exact original body/root/flags and releases through a prepared root mutation; its built-in operation restores the body to world ownership and cannot atomically transfer custody to a disposal unit.
- `DisposalUnitBlockEntity.insertBody` remains the existing self-entry body admission path; portable held-key intake now has a dedicated bridge sharing that finalizer and exact-root transaction.
- `DisposalUnitHandAdapter.insertPortableBody` validates the authenticated actor and exact direct portable key/root, then uses a transition-aware source proof: the original exact hand state is accepted before removal; only the exact `revision + 1` state with that hand empty and all other hand state unchanged is accepted after the transaction's prepared removal. Actor authority/action permission, carrier position, unit identity/runtime, and the requested loaded collider ray are revalidated around transfer. This permits canonical transaction post-removal gates without weakening root CAS. `DisposalUnitBlockEntity.insertPortableBody` reuses loaded-unit/body admission and the common body-admission finalizer, recording original flags and playing normal insert audio only after transfer succeeds. Existing held `USE` routes portable keys here rather than consuming them as item payloads. No new channel, owner registry, or capacity cap was added.
- The portable-body profile now uses local generic adapter throw parameters: speed `0.75`, range `8`, minimum `0.1`, cooldown `10`. This adapts SS14's holder-scoped hands range/cooldown to the existing caller cooldown ledger; it is not an upstream mouse-specific throw component/default. No mass-based impact damage or grounded launch multiplier is applied.

## Required follow-up/API gap

Please extend the shared release transaction (carry-worker owned) with a disposal custody target/commit plan that atomically removes the exact portable key and transfers the original body's captured containment flags/ownership to the admitted unit. The unit must preflight loaded/capacity/removability and revalidate before commit. Do not emulate this with a second hand remover, direct root mutation, spawning a replacement key, or bind-after-release workaround. Throw can use the existing world-release target once an integration caller can safely sequence its exact-key release, preflight physics, and flight launch without a failed launch losing custody.

The `portable-body-throw` fixture source exercises stale-key conservation, same-UUID release, stomach preservation, actual post-level flight displacement, wall collision/landing, and cooldown denial/conservation after an ordinary throw. The `DisposalPortableBodyHandUseGameTests` fixture source exercises authenticated held-use dispatch, preflight and post-removal rejection conservation/compensation, real unit insertion, and flush/outlet same-UUID/stomach/flag preservation. Both require execution; existing unrelated selectors do not cover these branches. Mind/control identity checks through the outlet and player-owned contained-body camera behavior remain unverified.

## Parity/adaptation

SS14 mouse remains one live entity through containment and transit; disposal holder intake/exit preserves body identity. The Minecraft key is only a reference. Existing local resolved throw profiles and generic flight are the intended physics sources; no mouse-specific speed, copied physiology, impact damage, or new channel/audio is justified. Detailed pinned composition and disposal correspondences are recorded in `FOUNDATION.md` and `../disposals-pneumatic-sprint/HANDOFF.md`.
