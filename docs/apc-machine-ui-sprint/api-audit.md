# APC menu M0 API audit (Minecraft 1.21.1 / NeoForge 21.1.224)

## Selected server route

- `MenuType` is registered from `ModMenus` using NeoForge's
  `IMenuTypeExtension.create(IContainerFactory)`. This is the 21.1.224 extension
  that supplies the extra `RegistryFriendlyByteBuf` passed by `openMenu`.
- `PowerDeviceBlock.useWithoutItem` opens a `SimpleMenuProvider` only on the
  logical server for an authenticated `ServerPlayer`. `ServerPlayer.openMenu`
  sends the position and server-generated UUID session token to the client menu
  constructor. Client-supplied coordinates are never used for resolution.
- `ApcMenu` retains the exact `ServerLevel` and `PowerDeviceBlockEntity`
  references. Both `stillValid` and every action verify player level identity,
  `hasChunkAt` before lookup, exact BE identity, APC block kind, interaction
  range, and `mayBuild`. There is no chunk ticket or force-load path.
- Breaker state, battery energy and breaker revision remain BE-owned. Six
  vanilla menu data values expose breaker state, four unsigned 16-bit words for
  the complete 64-bit revision, and battery percentage as 0..1000 permille.
  This avoids narrowing the long revision through vanilla's short-encoded menu
  data transport. Menu data synchronization is driven by vanilla
  `AbstractContainerMenu` broadcast changes for each open viewer. Battery NBT
  remains untouched/BE-owned.
- Actions use a small serverbound `CustomPacketPayload` registered through
  `RegisterPayloadHandlersEvent`, `HandlerThread.MAIN`, and authenticated
  `IPayloadContext.player()`. It carries only current menu ID, opaque session
  UUID, and expected revision. Current-menu identity, token, revision, loaded
  BE identity, level, range and permission are checked before invoking the
  existing BE toggle. Thus duplicates and reordered requests at a revision are
  rejected after the first accepted change. The payload carries no position,
  breaker state or battery value.
- Vanilla menu replacement/close changes `player.containerMenu`; dimension
  changes fail the retained-level check; removal/replacement fails exact BE
  identity; chunk unload is checked without loading it. Late requests cannot
  mutate another device. A per-tick scan/event listener is not added.
- There is no Screen registration in M1, intentionally. `ApcMenu` has only the
  client-side constructor required by vanilla's menu handshake and references
  no client classes, so common menu/network registration is dedicated-server
  safe. The forthcoming client Screen must send `ApcToggleRequest` using the
  received menu UUID and revision, and ignore older snapshots.

## Scope / evidence limits

- Breaker is the only M1 actionable state; battery state is available as a
  bounded authoritative snapshot. External power and load remain out of scope.
- The existing `PowerDeviceRules.INTERACTION_RANGE_SQUARED` (64) and
  `mayBuild()` policy are retained for both open and mutation.
- No connected-client menu lifecycle test was run in M1; helper/FakePlayer
  coverage cannot prove the real open-menu handshake or viewer fan-out. M3/M4
  must add/record that evidence. The vanilla state transport is bounded to three
  integer values and broadcast only through active menus.

## M3 validation notes

- APC right-click is routed through `useWithoutItem`: the server opens the menu
  when the player interacts with an empty hand. A held item first participates
  in vanilla's item-use interaction path; this implementation does not force
  APC screen opening over an item's own use behavior. Empty-hand interaction is
  the reliable documented way to open the APC UI.
- GameTests install a real `ApcMenu` on joined FakePlayers and exercise the
  bound menu/action validation seam. NeoForge's FakePlayer open-menu path does
  not perform the normal `ServerPlayer.openMenu` installation, so these tests
  do not claim to exercise right-click dispatch or the authenticated
  connected-client handshake.
  Manual two-client acceptance remains pending. GameTest exercises stale
  revision replay, range rejection, replacement/removal, and viewer state
  refresh; unloaded chunk and cross-dimension menu transitions are not
  faithfully constructible in this harness and remain unproven.
- Full-width revisions are carried across vanilla's signed-short menu data
  slots as four words and reassembled unsigned. Revision stops advancing at
  `Long.MAX_VALUE`, failing closed rather than wrapping and making stale action
  replay possible. The session UUID remains immutable per opened menu and is
  independently checked on every action.
