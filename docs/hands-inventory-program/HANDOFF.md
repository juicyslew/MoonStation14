# Hands, storage, controls and HUD

## STATUS

**Closed by owner acceptance on October 3, 2026.** Hands/storage/HUD interaction work and connected-feedback follow-ups are complete for this sprint. Owner closure is acceptance, not evidence that every connected checklist step was individually rerun. Changes remain uncommitted; the worktree includes unrelated changes. No commit, staging, merge or push is implied by closure.

## ARCHITECTURE

Hands and wearable slots belong to the controlled body through immutable `LiveHands`; storage grids belong to items through `GridContents`. Requests are intents, not client stacks. `HandActorAuthority` binds authenticated actor, controlled body and epoch. Body-only transfers publish one attachment revision. Body↔world and world↔world transfers stage separate owners and conditionally publish/compensate; these are not crash-durable atomic transactions.

**Active medical-correction follow-up (not covered by the acceptance above):**
`hands/live/LiveHandsCommitGateway` is now the gameplay attachment-write seam.
Use its actor-authorized or trusted server-body commit API, and scoped batches
for staged/compensated transfers. Trusted unowned bodies remain mutable;
publication is limited to the current authenticated owner. Do not write
`LIVE_HANDS` directly outside explicit initialization. A source-architecture test
enforces this boundary. Prospective validation must use the supplied `LiveHands`
for **all** hand, worn and grid contents; only equipment layout comes from the
body. Mixing candidate hands with currently attached equipment creates false
duplicate ownership and rejects valid transfers. Existing `BodyHandStateSnapshot` networking now carries
unsolicited authoritative updates without consuming pending query/action
correlations; stale replies cannot replace a newer revision. Current validation
and limitations are in `../basic-medical-treatment-sprint/VALIDATION.md`.

Windows are observations, separate from action permission. `StoragePopupLifecycle` resolves body-host windows by preserved storage token across worn/held locations. Correlated successful `BodyHandActionResult.HostTransition` metadata maps world UUIDs across pickup/drop; there are no guessed identities or persistent alias components. Rekeying preserves geometry, visibility, z-order and the three-visible-window quota. A short exact post-drop grant admits a replacement world session after fresh authority/reach checks.

World observations retain their nonce until explicit close, material unavailability or authority reset. Their 100-tick action lease can expire without closing the window; refresh revalidates the entity, current evidence and reach before renewal. `UNAVAILABLE` identifies material loss; transient query failures retain non-actionable presentation and retry. Existing-window refresh does not reactivate a dismissed cursor.

`StoragePopupWorkspace.Drag` captures source token and hand/grid origin independently of child selection. Confirmed grid snapshots validate grid sources; confirmed body snapshots validate held sources. Provisional refreshes retain proposals without granting action proof. Empty-cell insertion previews therefore survive bag snapshots that do not contain the held token. Other-hand HUD storage drops use `HandEquipmentGesture.heldStorePlan` and existing nominated `HELD_GRID_STORE`; client first-fit proposals are revalidated server-side.

`StorageSpriteGeometry` fits measured opaque pickaxe pixels, rather than guessed texture-canvas sizes. At 10-pixel cells its canvas is approximately 34.25 pixels, with upright artwork shifted approximately 2.65 pixels upward. The five-cell footprint and −45° artwork correction remain unchanged. Tests load the real PNG and generate an inspection sheet for all cardinal rotations.

## IMPORTANT FILES

Under `src/main/java/com/juicyslew/moonstation14/ms14/`:

- `hands/client/`: `BodyHandClient`, `BodyHandClientPolicy`, `StoragePopupScreen`, `StoragePopupWorkspace`, `StoragePopupLifecycle`, `WorldGridClientPolicy`, `PointerModePolicy`, `HandEquipmentGesture`, `PopupActionPlan`, `StorageDragPreviewPolicy`, `GridEntrySpriteRenderer`, `StorageSpriteGeometry`.
- `hands/live/`: `LiveHands`, `BodyHandTransfer`, `BodyHandItemTransfer`, `BodyBodyGridTransfer`, `BodyWorldWorldGridTransfer`, `BodyItemTargetProbe`.
- `hands/network/`: `BodyHandRequestService`, `BodyWorldGridService`, `BodyWorldCrossTransfer`, action payloads/results and networking.
- `chat/client/LocalSpeechReviewOverlay.java`: compact chat and cursor-screen visibility.

Unit tests live under `src/test/java/.../ms14/hands/`; live transfer/publication fixtures under `src/gametest/java/.../ms14/hands/`. `BodyWorldCrossPublicationGameTests` provides a trusted spawned-body fixture seam; production supplies authenticated revalidation. Asset provenance is indexed by `ss14-item-assets.md`. Older control/history documents are historical, not current-state authority.

## SS14 CORRESPONDENCE

Storage UI lifetime follows the host rather than worn/held/world location. Quick insertion uses an empty-cell click; extraction uses an eligible empty hand. Explicit either-hand dragging intentionally nominates the dragged source rather than upstream's active-hand interaction helper. Direct container-child transfers deliberately avoid SS14's intermediate-hand behavior. Container nesting remains unsupported.

R and right-click share cardinal storage rotation. Q placement preserves SS14's obstruction/crowding fallback principle; aimed ground placement is shortened to 3.5 blocks while pickup/storage reach remains 5. Measured pickaxe fitting is an owner-requested presentation adjustment, not upstream's native sprite/cell ratio. Retain SS14 sprite/audio attribution.

## DECISIONS / WHY

- Server authority, stale-token/revision checks and failed-transfer conservation remain mandatory; presentation retention never permits stale actions.
- Hand↔hand transfers move the whole occupant into an explicitly empty destination without changing active selection. Occupied non-storage and full destinations deny unchanged.
- Storage cells are 10 GUI pixels. Windows default above the hotbar, remember worn-host positions within the body session, and clip lower contents/previews beneath upper windows. The entire left tab except X is draggable.
- Manual L-alt cursor intent persists with no windows; automatic popup cursor lifetime is separate. Cursor-mode movement remains usable. Contextual E and L-alt remain rebindable provisional defaults.
- Chat uses 0.5 scale and six-pixel rows with a taller panel, visible in free-cursor mode.
- Creative/non-spectator transitions that lose controlled-body authority clear the workspace and remembered positions. The owner accepted leaving this reset unchanged.
- Pickup selects the nearest visible eligible world item authoritatively; packet target is a hint. Q fallback cannot bypass ownership or failed-spawn conservation.
- The retired pouch has no playable item/UI; `PouchContents` survives only as an internal legacy name.

## KNOWN GAPS

Trusted Villager fixtures do not prove every authenticated player route or actual mouse sequence. Owner acceptance closes the sprint without inventing per-step connected results. Drop collision uses bounded AABB samples rather than a continuous sweep; fallback may overlap items and rely on Minecraft physics. Nested storage, universal throwing, world-item entity redesign, construction and clothing are outside this sprint.

## VALIDATION

- Latest unit run: **1,821 tests, zero failures/errors**, `build/inventory-pickaxe-fit-unit.log`.
- Latest full server run: **all 401 GameTests passed**, `build/inventory-held-source-gametest-final.log`; final renderer-only changes were subsequently unit-tested, not server-rerun.
- `git diff --check` passed; independent reviews covered authority, lifecycle, exact empty-cell release, clipping and measured sprite geometry.
- Sprite diagnostic: `build/minecraft-junit/build/reports/storage-sprite-fit/pickaxe.png`. Reuse its real-asset assertions and before/after sheet instead of in-game sizing guesses.
- Passing disposable server fixtures were removed. Earlier full runs each encountered different non-storage fixture failures (timed stun expiry and atmosphere isolation); logs remain in `build/inventory-lifecycle-gametest*.log`. No unrelated subsystem repair was applied.

## NEXT WORK

### Authority snapshot follow-up

Issued full-state proof binds the exact actor/body/epoch, not a hand revision.
Successful gateway publications send and issue a fresh full snapshot;
`requireFreshQuery` retains its legacy name but now pushes that snapshot. Replay
and rate-limit rejection do not revoke still-current proof. Lost authority or a
changed body/epoch invalidates it. Query-gate unit coverage is not connected
player/transport validation.

See [future-extensions.md](future-extensions.md) for the owner's four proposed extensions and recommended sequencing. They are recorded opportunities, not started work or finalized designs. `connected-follow-up-test.md` remains a regression checklist. Keep Gradle/server runs sequential to avoid shared-output locks.
