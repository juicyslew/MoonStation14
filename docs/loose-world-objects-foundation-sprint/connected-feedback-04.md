# Fourth connected feedback

This supersedes the third pass's invisible puddle timer and event-owned Alt+E routing. The owner subsequently closed the sprint; `HANDOFF.md` is the current index.

## Shared item appearance

The old hand payload supplied only item ID/count, while grid entries supplied reagent state; rebuilding a bare stack lost the jug's appearance. `hands/network/ItemPresentation` now supplies a defensive, appearance-only stack in hand, equipment, body-grid, and world-grid snapshots, including legacy child presentation. It preserves native/mod appearance components rather than adding a reagent field separately to each UI. Ownership/container payloads are stripped; token, revision, and placement proofs remain separate. Returned stacks cannot mutate the authoritative source or the snapshot.

`hands/client/ItemUiRenderer` is the common icon entry point for hand HUD, body inventory, equipment, storage, and drag/cross-source/world previews. `ItemUiAppearance` resolves special sprite layers centrally; native item models receive the full presentation stack otherwise. Both renderer leaves are client-only. `loose_objects/appearance/JugItemAppearance` supplies common fill/tint/capacity calculations to UI and world presentation. Empty jugs stay on the same 2D tier-zero path. Pickaxe grid fitting/rotation remains, but uses the shared item renderer instead of bypassing state through a raw PNG.

The generic wire layout replaces reagent-only grid presentation. No old-network compatibility or save migration was added. Presentation stacks do not create another inventory owner.

## Input and puddle drinking

- Alt+E and Alt+left-click are intended to invoke the same alternate-world helper. Keyboard alternate use is now dispatched from its registered mapping's queued click in the tick, like the other mapped shortcuts; raw key events no longer dispatch it separately. The planner gives equipment gestures priority over alternate use, and alternate use priority over normal world use. Screen/free-cursor gating drains gestures without executing them. Alt modifier use still suppresses the Left Alt tap's pointer toggle.
- The reported client's saved mapping was unavailable, so a stale binding is not established as the cause. If its old mapping persists, reset/rebind **Alternate world use** to Alt+E. The mouse alias remains Alt+left-click.
- By explicit owner direction, puddle drinking is now **immediate, one unit per successful explicit gesture**. The invisible three-second timer and pending/tick lifecycle are removed. Authority, empty-hand admission, mouth/body/stomach eligibility, loaded reach/visibility, reagent conservation, compensation, and commit-dependent empty cleanup remain.
- Continuous drinking is not implemented. Future repeat/start/stop behavior and visible timed interactions are indexed in `docs/hands-inventory-program/future-extensions.md`. Immediate puddle ingestion is a deliberate temporary departure from the researched SS14 delay, not a parity claim.

## Validation

All **1,910 unit tests** and **440 required GameTests** pass (`build/loose-feedback-04-unit-final.log`, `build/loose-feedback-04-gametest-first.log`); `git diff --check` passes. Test compilation was refreshed after moving the common appearance tests, and their two fill/tint regressions also passed independently (`build/loose-feedback-04-appearance-focused.log`). Independent reviews accepted shared presentation/UI coverage and immediate ingestion/mapped alternate dispatch.

Presentation wire coverage includes jug reagent state, native custom-model/glint components, detached copies, body/world grids, and stripping bag ownership without modifying the source. The immediate-sip and tolerant-target fixtures check source-to-stomach transfer at request completion, without waiting for a timer. Successful disposable server fixtures are removed; logs are retained.

## Connected recheck

1. Compare a colored, partially filled jug in a hand, storage, and drag preview; drain it and confirm the empty icon remains a flat sprite.
2. Confirm Alt+E and Alt+left-click both perform the same jug action without opening inventory/free cursor; reset the alternate binding if necessary.
3. With an empty hand aimed at a puddle, confirm one gesture immediately drinks one unit, with no automatic repetition.

The owner has closed the sprint. A passing server fixture still does not establish a separate connected pixel/input capture.

Restart the server and client before the connected recheck: the snapshot wire layout changed, and an already-running development server still has its previously loaded classes.
