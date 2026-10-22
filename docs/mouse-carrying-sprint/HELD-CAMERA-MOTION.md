# Held camera motion correction

Status: implemented, awaiting independent review and fresh gate; r36 remains the
last validated build.
The owner confirmed r36 critical-facing recovery and easier mouse catches, but
held-camera jitter and lead remain a required correction, not an optional boundary.

## Cause and chosen boundary

Owned character snapshots update body world position directly. The visible remote
carrier follows native interpolation. Independently sampling those two positions
causes a held camera to lead the rendered carrier and react to different timing.

Verified Minecraft 1.21.1 source in the local NeoForge sources archive:

- `Camera.java:57–61`: base camera interpolates `xo/yo/zo` and adds native smoothed
  eye height before third-person zoom/collision.
- `LevelRenderer.java:1259–1264`: the entity model interpolates `xOld/yOld/zOld`.
- `LevelRenderer.java:1019–1024`: a newly spawned entity uses its current position
  for render history. `Entity.getPosition(partial)` uses `xo`, not model history.

The correction must use the visible carrier's model-history sample at the same
partial tick. It translates the existing camera base by:

`rendered carrier + authoritative body-local offset - native camera-body sample`

It must not introduce a second smoothing clock, mutate body physics, force the
camera to the carrier's eye, rotate the offset with carrier yaw, or replace native
eye height, orientation and third-person distance. Translation belongs before
native third-person collision so collision uses the corrected origin.

## Shared data and reuse

Extend the existing `ContainedBodyView` presentation contract and snapshot codec
with an optional explicit render parent. `ownerIdentity` remains the custody token;
it must never be interpreted as the carrier UUID. Carrier identity and body-local
position come from `PortableBodyCarrierOwner.positionFor(body)` and the actual
carrier. The existing `owner.viewFor(body)` snapshot consumer supplies the relation.

Use the existing `ContainedCharacterClientView` binding/epoch lifecycle and
`ContainedBodyCameraMixin` camera boundary. Resolve only a currently loaded parent;
missing, removed, mismatched or cleared associations fall back to the native path.
Disposal-unit and transit views do not acquire carrier-relative motion metadata.

Contained binding now uses the existing stable presentation identity, not tracker
ownership. Critical/pulled characters may deliberately lack prediction-tracker
ownership without losing their contained camera presentation.

## Related catch nomination correction

The client portable-target ray previously used block-use reach (6) while pickup
authority used 5. Aim margins could also nominate a target whose physical bounds
were outside pickup reach. Client item and living-target nomination now reuse the
server's finite-checked physical closest-AABB reach predicate; aim tolerance and
authoritative reach remain unchanged. This removes a concrete disagreement, not
motion or latency: a target can still leave reach before a request arrives.

## Parity and validation limits

This is a Minecraft 3D presentation adaptation. Upstream SS14 checkout revision
`c9df5ef5d675b0d1d226828bddf6b78c28502d91` was verified, but the exact held-mouse
eye/parent interpolation path has not been established. Do not claim exact SS14
camera parity. No new gameplay prototype capability is intended.

Required evidence: snapshot relation/codec live consumer; frame-sampling tests
with holder render history differing from camera history; lead/noise invariance
across partial ticks; native-offset preservation; missing/released/epoch guards;
and unchanged disposal/transit behavior. Passing math/native fixtures is not GPU
observation. After a validated build, check moving-holder stability and relative
placement, turning, jump/start-stop motion, and pickup/drop/re-pickup transitions.
