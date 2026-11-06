# Connected shuttle feedback — 2026-10-13

**M1 remains unaccepted.** The prior feedback-05 r5 technical gate passed, but
connected testing still reports wall head-bumping/gliding. Do not reuse its
passing verdict for subsequent source changes.

## Owner observations

- Wall movement still fails; the owner suggests a small angular wall-classification
  band rather than treating slightly tilted walls as floors or ceilings.
- Cargo translation is mostly good, but jugs retain global orientation when the
  shuttle turns, changing orientation relative to the cabin.
- The living body stayed aboard while not controlled.
- Both logout/rejoin attempts retained the body aboard. On the second attempt,
  the shuttle hit the ground before login and the body appeared one block too low.
- After rejoin, the hatch responded to neither contact nor E. Lost power is a
  possibility, not an established diagnosis.

## Captured evidence

Read-only snapshots: `build/shuttle-owner-feedback-06/latest.log` and `debug.log`.
The originals identify a `forgeserverdev` launch from agent-03's compiled classes
and resources (`debug.log:1–12`). This establishes the server workspace, not the
client binary hash or the exact class bytes loaded at startup.

Sequence 2920, epoch 10, body 41 (`latest.log:186–188`, `debug.log:1340`):

- Requested own delta: approximately `(-0.2722214, -0.001, -0.0706900)`.
- Accepted delta: approximately `(-0.2977776, +0.2499002, -0.1155004)`.
- Rejection: collision resolution must be finite and bounded by requested
  displacement. The server then rebases ownership.
- Grounded before/after, maximum native step height `0.6`, no jump requested.
- The first contact is steel wall with an upward normal, not a small-Y side normal.
  The ordered trace contains small vertical responses followed by four horizontal
  contact iterations with approximately `1/16` upward response increments.

The pinned SDK already distinguishes vertical contacts at
`abs(normalizedMTV dot entityUp) > 0.6`. A tiny wall-normal tilt alone therefore
does not explain this captured rejection. The pinned `tryStepUp` probe starts
from `entityBoundsCenter` without accumulated `collisionMotion`, despite the
current collision actor being at their sum. The scoped owned-character correction
tests that current candidate instead; it does not disable stepping or widen
movement tolerances. Pinned source copies are under
`build/shuttle-sdk-source-06/`. See `WALL-RESPONSE-06.md` for implementation and
control coverage. Runtime validation is still required; the earlier exterior-face
fixtures did not establish supported interior floor/wall/ceiling behavior.

The connected logs do not establish the hatch cause. The new offline-landing
fixture did reproduce an authenticated E denial with fresh paid power and valid
reach: the new connection lacked issued owner-hand state after Ready. The shared
Ready path now publishes through the existing hand-state service after Commit,
preserving the same body, Mind, hands and items. See `RECONNECT-LANDING-06.md`.

Feedback-06 r7 passed **42/42 selected, 877 total** focused native fixtures.
Its full run completed **876/877 passed**, with one button-to-airlock transaction
failure and clean saving/shutdown. Wall, cargo and offline landing/Ready plus
normal E/bump passed headlessly. See `POWER-SIGNAL-PHASE-06.md` for the remaining
transaction boundary and explicit phase ordering. This is not technical readiness
or connected acceptance.

## Ordered work and acceptance boundaries

1. Correct the demonstrated cabin-wall response through existing native collision
   ownership, with supported interior walls and genuine floor/ceiling/step controls.
   Preserve generic bounds, real grounding and the single physical move.
2. Extend existing cargo frame presentation to include authoritative local
   orientation; preserve world orientation on parent changes and late-join state.
   SS14 grid-parent yaw inheritance is verified; pitch/roll is a 3D adaptation.
3. Reproduce offline landing followed by Ready and both hatch interaction routes;
   assert same body/Mind/items, pose, reach and fresh paid power independently.

Focused author checks precede the central serialized gate. No client launch,
owner-world/configuration change, installation, private packaging or public
distribution is authorized by these fixes. Connected appearance and owner
acceptance remain separate from headless technical validation.
