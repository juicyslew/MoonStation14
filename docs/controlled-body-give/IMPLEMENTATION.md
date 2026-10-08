# Controlled-body `/give` core

`BodyGiveService` is the trusted core consumer for parsed vanilla give chunks. Its player-facing wrapper resolves and revalidates `BodyActionAuthority` (a live controlled body and epoch, without requiring hand capability); it does not grant authority to `FakePlayer` or client requests. The core fixture exercises only this trusted consumer and is not evidence of command authentication.

The core tries available hands active-first, merging equal item/components up to the item's native maximum before filling an empty hand. Reservations make a hand unavailable. A candidate `LiveHands` snapshot is admitted through `BodyHandStateService.representable` and published through `LiveHandsCommitGateway`; if no compatible hand snapshot exists, or the candidate is not representable, the complete grant goes to the controlled body's world position (or the exact containment owner's physical position). Successful handling clears the parsed vanilla chunk only after its body/world destination succeeds. Existing body-hand drops and minted grants share the physical placement and loose-entity creation path in `BodyHandItemTransfer`.

The registered GiveCommand mixin redirects only `Inventory.add(ItemStack)` to the body grant for a currently admitted `BodyActionAuthority`. `NOT_CONTROLLED` preserves vanilla behavior; a controlled grant failure throws rather than allowing a carrier inventory/overflow fallback. Its existing ordinal-0 fake-drop redirect suppresses the fake visual for controlled actors for every item, while preserving the prior Creative Moonstation item behavior for ordinary recipients. Native bounded chunking, request limits, output success handling, and component parsing remain in vanilla GiveCommand.

The small `LiveHands.grant` primitive is justified because there was no existing partial insertion/merge operation to reuse; it uses the existing occupant/token representation and native item/component matching. There is no locker prototype or registration locally, and the upstream locker chain has no Item component. Consequently this adaptation uses currently registered Minecraft item representation and current hand-state representability; it deliberately adds no locker-name blacklist or speculative holdability framework. Ordinary Minecraft construction-block items retain their existing local item representation.

Core coverage: `BodyGiveCoreGameTests.emptyActiveHandReceivesIndependentCopyBeforeInputClears`; `occupiedActiveRoutesToOtherAvailableHand`; `fullHandsRouteRemainderToBodyWorldAndMergeToNativeCap`; `nativeTenItemChunksConserveThirteenBrutepacksAcrossHands`; `unsupportedNativeContainerCandidateFallsBackWholeToWorld`; `noHandPigReceivesWorldGrantWithoutMaterializingHands`; and `failedPrecommitRevalidationLeavesHandsWorldAndInputUnchanged`. Trusted-body fixtures verify the consumer, not the authenticated `/give` dispatcher branch. The existing FakePlayer command fixture covers the ordinary vanilla branch only; no authenticated live-player command fixture is claimed.

## Final validation (2026-10-07)

Final GameTest Java compilation included all seven `BodyGiveCoreGameTests` methods.
Focused tags `give,health-analyzer,medical-treatment`: **27/27 selected; all 27
passed** (`build/validation-logs/medical-ui-give-focused.log`). Full native run:
**659/659 selected; 646 passed, 13 failed** (`build/validation-logs/medical-ui-give-full.log`;
failed worlds retained at `build/gametest-medical-ui-give-fixture`). Failed method names
and neutral attribution are listed in `docs/basic-medical-treatment-sprint/VALIDATION.md`.
The initial unit command recorded four failures in test expectations/classification;
subsequent test-only corrections resolved them. Final `test build compileGameTestJava`
succeeded: **2,590 JUnit tests, 0 failures, 0 errors, 0 skips**, with GameTest Java
compiled and build successful (`build/validation-logs/medical-ui-give-final-unit.log`).
Artifact `build/libs/moonstation14-0.0.1.jar`: **6,198,443 bytes**, timestamp
`2026-10-08T02:18:32Z`. Native run counts above remain the latest; the intervening
fixes changed tests only, so no additional native run was required.

These core fixtures do not validate authenticated connected `/give`; the existing
FakePlayer command fixture covers the ordinary branch only. Connected UI/render/
keyboard behavior is likewise not established.
