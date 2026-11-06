# Shared frame corrections — current contract and source index

The owner-reported candidate passed readiness (`run_07740820117d8c701c12c876`: 695 JVM tests across 92 classes; 106/998 selected native cases; package/provenance checks). Owner feedback subsequently exposed a serious movement regression and cursor-placement mismatch. That PASS is not acceptance or proof of the revised source. No gameplay prototypes or assets changed.

## Preserved corrections

- **Lighting accounting:** `ms14/shuttle/sable/PackedLightAccountingScope`, `SableEntityRendererLightingMixin`, and `SableRemotePlotProjectionPlugin` suppress only the SDK's erroneous first rounded-cell frame conversion during packed-light computation. Precise SDK accounting still runs once; fire, overrides, sky scale, world/plot min-max and occlusion remain. The owner confirms flicker is gone. Do not revert this correction or substitute fullbright/smoothing.
- **Descending-deck landing:** `NativeNormalContactFinalizer`, shared by `GroundedHarnessWorldStep` and `GhostControlClient`, applies a fresh destination-floor normal response after rebasing. This prevents WORLD-to-descending-frame rebasing from creating upward deck-relative speed. Tangent velocity and legitimate separation remain; no forced ground, velocity wipe, collision-tolerance change or launch-reference change.
- **Cargo grant:** `BodyHandItemTransfer.spawnAuthorizedGrant` retains the exact authority snapshot's authorized carrier through the existing staging/clearance path. Other occupants and walls remain obstacles; trusted body-only grants keep their existing path.

## Current regression repairs

- **Actor/scene isolation:** `ClientFrameTimelineStore` now publishes two immutable views at the same interval. Actor readers receive only the original primary pairs; scene rendering separately receives frozen known-frame history, with primary pairs taking precedence. `OwnedFramePresentationContext` endpoint readers never use the scene view. WORLD actors with empty primary pairs therefore cannot acquire unrelated ship endpoints through this publication. Production `publishViews` and actor-reader tests passed focused checks and independent review. Existing `GhostControlClient` render-origin writes are unchanged. This restores the data boundary; post-repair ordinary-world walking smoothness remains visually unverified.
- **Captured floor DROP:** the client uses `BodyHandRaySnapshot` and `LoadedFrameRayQuery.capture` to nominate an UP-facing cursor surface. Unavailable/UNKNOWN capture fails closed; a determinate miss or non-floor hit retains plain DROP. The existing request carries world coordinates or frame-storage coordinates, and dispatch/transfer pass them to authoritative surface revalidation. Invalid present nominations deny placement rather than falling back. Automatic and nominated surface placement share one clearance/anchor planner; nominated eye reach remains at most 3.5 blocks. THROW and trusted grants retain their existing semantics. Focused tests and production review passed. `CapturedFrameDropPlacementGameTests` covers moved-frame placement and invalid, removed-frame, deleted-surface, blocked and unloaded-target denial; managed native execution is required by the replacement gate, not claimed from compilation.

Accurate outlines, viewport-edge visibility and resolved initial placement jitter must remain intact. Already-contained DROP must keep its existing containing-owner route, without requiring a world cursor ray. Stock Sable, six substeps, world-down gravity, ordinary fall damage, frozen XYZ launch references and once-only `v + ω × r` inheritance remain invariants.

## Status and owner checks

Repair gate `run_fd466ddca59c7d4036551983` passed. The owner now confirms stable cargo lighting and almost everything working; the residual cursor offset is acceptable. Crowding fixtures enforce exact carrier exclusion, safe denial/ownership retention and release after the unrelated blocker clears; their ownership oracle compares canonical token strings and full stack components and has a focused JVM guard. Production clearance was not weakened.

The next scoped follow-up is compound-acceleration orientation, a heavy-freight diagnostic flight profile and a normal Minecraft bay light; see [Nov 6 feedback](OWNER-FEEDBACK-20261106.md). Its current source requires a fresh gate; the preceding PASS is not proof of these additions.

See [owner feedback](OWNER-FEEDBACK-20261105.md) and [cargo orientation](CARGO-ORIENTATION.md). Regression-session logs are preserved under ignored `build/owner-shuttle-feedback-20261105/session04/`; those captures do not establish ordinary-world walking coverage. M1 remains unaccepted; Critical/Dead drift and two-client acceptance remain unverified. No merge or M2 authorization follows.

After replacement readiness passes: walk normally on world ground, then aboard/off a moving ship; place a jug at the cursor on a rotating floor; confirm lighting, outlines and viewport edges remain correct. No recording, capture ID or upload is required.
