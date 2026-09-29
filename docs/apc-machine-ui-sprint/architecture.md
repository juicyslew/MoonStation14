# APC machine UI — architecture and invariants

## Ownership and package boundaries

The first UI is an APC screen attached to existing APC behavior, not a general menu platform project. Preserve system-oriented organization under `com.juicyslew.moonstation14.ms14.power` for power behavior, and use the project's normal Minecraft block/BlockEntity and client-only UI registration locations for their respective responsibilities. Locate and follow existing registration/event patterns before adding classes.

```text
Authenticated ServerPlayer
  └─ server-opened APC menu/session (identity bound to clicked device)
       ├─ server validates every request and owns authoritative reply
       └─ client Screen renders snapshot; emits bounded intent only

PowerDeviceBlockEntity (single persistent APC owner)
  ├─ energyJoules, breakerClosed, save/load, server mutation
  └─ PowerRuntime (20-tick server solve; updates battery/power projection)
```

`PowerDeviceBlockEntity` remains authoritative for APC breaker and battery, using its existing NBT and dirty/update behavior. A menu may hold a server-bound reference/identity to the APC while open, but it must not become another persistence owner. `MS14Provider` and `MS14Bridges` support dual holder traits and do not own this BE state; no provider bridge, entity attachment, or duplicated menu-side mutable value is appropriate here.

## Contract to audit before selecting APIs

Pinned local platform: Minecraft 1.21.1, NeoForge 21.1.224. Before coding, write down the exact locally available API route for:

- `MenuType`/`MenuProvider` construction and authenticated server open;
- menu validity and server-side `stillValid`/range/permission checks;
- serverbound bounded action transport and clientbound snapshot/update transport;
- client Screen registration and dedicated-server class isolation;
- menu close, player dimension change/disconnect, device removal/unload, and menu replacement;
- two simultaneous viewers and how they receive updates after BE/runtime state changes.

Names above describe concepts to verify, not a presumption that a particular method/handshake works in this version. Follow current repository conventions after audit. Avoid a speculative generic session framework. If exact APIs do not provide a safe route, stop at the audit and return the concrete blocker for owner review.

## Open and request flow

1. Server-side APC right-click resolves the currently loaded `PowerDeviceBlockEntity` and opens the menu bound to that instance/position in the server's current level. Client interaction may predict that a screen should open only if the audited normal menu handshake safely gates it; client never constructs an authoritative target.
2. On each action, validate the authenticated sender and the actual currently open menu/session; confirm its bound device still exists, is an APC in the same dimension/level and loaded context, and the player is currently in valid range with the existing build permission policy. Check any menu/session token or revision used by the transport. Reject stale, malformed, late, mismatched, or unauthorized messages without changing state.
3. Do not accept the packet's coordinate, device ID, battery value, breaker value, or revision as authority. Do not force-load a chunk. Unknown/unloaded context fails closed; session is invalidated/closed safely.
4. Mutate only through the BE's validated server operation (preserve or refine its existing `mayBuild`, range, persistence, and block-update semantics). Keep interaction permission rules consistent between open and mutation; opening UI does not grant a mutation permission.
5. Send a fresh authoritative snapshot to the requester and every current viewer after mutation or relevant runtime state changes. A client may not roll the UI back with an old response. Close/invalidate on BE replacement/removal, unload, dimension/context mismatch, or menu close. Revalidate on action regardless of client close notifications.

Session identity should be generated/bound server-side using the audited normal menu lifecycle. If the selected API already strictly binds packets to the open menu, do not invent redundant identity machinery; still ensure mismatched/replayed/late requests are harmless. Snapshot revisions are useful for ordering refreshes, but must be server-generated and monotonic for the session/device lifetime as supported by the design. Do not make a packet-selected revision authoritative.

## Screen and truthful state model

SS14's `ApcMenu.xaml` is a visual reference for a compact machine panel: APC sprite, main breaker, external-power state, optional load readout, battery progress bar and percentage. Recreate the useful hierarchy with available Minecraft UI widgets; no need to mimic toolkit-specific layout or logos. A static icon/block view may substitute for the SS14 entity preview.

Minimum reliable fields:

- main breaker: authoritative open/closed state and an actionable control only when the server will permit the player to change it;
- battery: energy as a finite nonnegative quantity bounded by the 1 MJ capacity and a clamped percentage derived from the authoritative BE value;
- unavailable/error state if device/session or telemetry validity is unknown.

External power and load are **conditional**. `PowerRuntime` runs its power solve every 20 ticks and its graph can report unknown port knowledge. Before presenting external power, prove what known/unknown means, when it refreshes, and what “external” would signify in this runtime. Before presenting load, establish a reliable current measured/calculated quantity, unit, sample time and coverage. Do not label APC transfer watts, configured capacity/rates, diagnostics, or stale estimates as current load. If not truthful, omit the row rather than guessing. A connection being present is not automatically power availability. No claim of full SS14 power behavior.

Use existing APC/block sprites where suitable. A static breaker/APC indicator is sufficient; animate only if existing usable 2D sprites and the local render path make it simple and non-misleading. No new models, sprites, or copied UI logos. A small 1 MJ bar percentage must clamp safely for malformed/non-finite values; the BE's existing rules remain the validation authority.

## Concurrency, lifecycle, and no-authority leak

- Multiple authenticated players may view the same APC; all screens converge to server snapshots. A viewer without mutation permission can view if opening is allowed but cannot actuate. Never infer MobHarness possession/action authority from `ServerPlayer` transport.
- Breaker persistence must survive close/reopen and world save/reload through current BE NBT. Screen close must not reset or own APC state.
- Runtime battery changes occur on the 20-tick power cadence. Notify/open-viewer refresh at a bounded appropriate cadence or on state change; avoid per-tick network spam. Confirm runtime's BE update notifications are sufficient; add the minimum explicit UI update seam needed.
- A device removal, chunk unload, level change/unload, player disconnect, or menu replacement invalidates the session. Any later packet is rejected, cannot load a chunk, and cannot mutate another APC.
- Keep UI code client-only by registration/package boundary and verify dedicated-server classloading in automated validation.

## Explicitly not reproduced from SS14

No APC channels, per-channel breakers, emag state, engineering ID restrictions, charging policies, grid-wide SS14 power-state semantics, or other controls not implemented locally. Do not import the upstream system's assumptions as data or code. No prediction of breaker/battery or power state; display server snapshots only.
