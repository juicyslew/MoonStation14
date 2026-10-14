# ChemMaster session/action parity note

Verified against Space Station 14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `Content.Client/Chemistry/UI/ChemMasterBoundUserInterface.cs:26-63` opens a persistent window and sends mode, sort, reagent, and slot commands independently; stun is not a window-close event.
- `Content.Server/Chemistry/EntitySystems/ChemMasterSystem.cs:49-64,90-152` receives those action messages and updates authoritative UI state. `RobustToolbox/Robust.Shared/GameObjects/Systems/SharedUserInterfaceSystem.cs:110-143` applies `RequireInputValidation` to incoming UI messages, but exempts close messages; canceled actions do not implicitly close the subscribed UI. `Content.Shared/Interaction/SharedInteractionSystem.cs:151-175` denies a blocked actor's non-open BUI message, while its UI-range check is separate and frame updates are expected to close out-of-range UIs.
- Local adaptation: `ChemMasterSessionService.validSession` owns lifecycle/body epoch, target identity, resolved range, and power retention. It no longer folds action permission into observation validity. After request correlation/revision checks, non-QUERY mutations separately require `CharacterControlSystem.canAct` and `BodyActionAuthority.eligibleForComplexInteraction`; denial returns the normal correlated open-state snapshot without mutation or session invalidation. Query remains read-only while stunned; normal logout, authority/target/range, and power invalidation remain in place.
- `ChemMasterOpenRequestGameTests` drives the registered Ready/proof path, applies a real resolved finite stun, observes session retention over server ticks and QUERY, verifies a correlated denied mode change conserves state/revisions, waits for normal status expiry and verifies a same-session mode change succeeds, then verifies normal range invalidation closes the window.
- Focused native validation selected 4/787 `feature-readiness` fixtures: 4 passed, 0 failed, 0 incomplete. Existing `ChemMasterSessionAckTest` passed 2/2; `git diff --check` passed.

This validates the server request/session service path through the socket-free registered-player fixture, not physical input or real-client transport.
