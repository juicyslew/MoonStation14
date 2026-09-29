# Client visual kit — architecture and original proposal

The agreed visual-kit scope is implemented and owner accepted; the table below preserves the original proposed widget contracts and boundaries. Not every proposed evidence gate has been empirically verified. See [completion](completion.md) for deferred checks and debt.

## Ownership boundaries

```
server PowerDeviceBlockEntity -> ApcMenuService / ApcMenu / packets -> client APC adapter
                                                              -> immutable display snapshot
client input -> widget intent callback -> APC adapter -> existing packet (only when valid)
theme tokens + layout primitives -> reusable widgets <- display snapshot / local UI state
```

Put colors, spacing, nine-slice/primitive drawing and responsive layout rules in a theme/layout layer; put pressed/disabled/hover/focus/selection, tooltip, narration, validation and scroll state in reusable client widgets. Machine adapters translate *existing* server snapshots and allowed intents; widgets never construct packets, authenticate actors, own device state, or decide access. Never widen `ApcMenuService` or assume MobHarness action rights from authenticated menu access. Avoid a universal server menu abstraction. Do not ship mock machine screens as functional gameplay.

| Client widget seam | Minimum functional behavior and ownership |
| --- | --- |
| Window frame | Drag and resize when opted in, title, close, footer/help region, safe screen bounds and min size; APC opts out of resizing to match SS14 reference. No logo without cleared provenance. |
| Actions | Reusable button, toggle and SS14-inspired switch with keyboard activate, disabled/access tooltip, focus indication and screen-reader label; optimistic *presentation* only when adapter explicitly allows it, reconcile on fresh authority. |
| Readouts | Label/status/value table, bounded bar/meter with value inside, unknown/stale treatment; bar hue from bounded actual charge, never animate toward a guessed output. |
| Navigation | Tabs, category/option selector, scrollable search-filtered item list/rows; retain focus and selection across revision changes where stable IDs exist, otherwise reset safely. |
| Inputs | Text and numeric editors with caret/selection, tab traversal, locale-appropriate display, range/parse validation and disabled submit. Local validation is convenience; server must revalidate. |
| Visuals | Image/preview with size/clip/fallback and per-frame-free texture use; APC uses a fixed south-facing layered animation for known visual states and an attributed static PNG for UNKNOWN. Neither is a live entity preview. Icons and slot grids display owner-provided item identity/count/availability. |
| Item slots | API shape: `SlotView(id, itemPresentation, count, enabled, revision)` and `onSlotIntent(sourceId, targetId, requestedAction, observedRevision)` to an owner adapter. Drag visual/hover/cancel/keyboard selection are client-only. **Do not enable item move/transfer** without an authoritative owner inventory contract for source/target, count, permissions, session, revision, rejection and resync. No client-owned inventory, invented transfer or pseudo-functionality; disabled/read-only slots explain why. |
| Queue and presets | Stable-ID queue rows, optional reorder gesture and preset selector; emit intent only with real owner-defined protocol. Local-only ordering of a client list is not a lathe/cargo order. |
| Specialty | Map/graph *adapter interface* for owner data with bounded samples/viewport; no fabricated telemetry, map topology, or general-purpose gameplay integration. |

APC adapter consumes `ApcMenu` breaker/revision/charge/trip and visual-state data and existing correlated `ApcToggleResponse`; keep client presentation pending distinct from confirmed breaker state. Handle late/replayed responses, close/reopen, timeouts, absent/uninitialized and stale snapshots. Show disabled/unknown rather than asserting power/load values or access from absent data. The original proposal called for a dark frame + static sprite panel + two-column labels/switch + charge bar (red→orange→green, percentage inside) + divider/footer with truthful localized copy, no unlicensed logo. The delivered sprite panel instead layers attributed CC-BY-SA-3.0 base/display sheets for FULL/CHARGING/LACK, with the existing attributed static image for UNKNOWN; see [asset provenance](apc-animation-assets.md). This is a fixed 2D GUI preview, not a dynamic 3D map or entity. If the current protocol lacks a trustworthy disabled/access flag, document that limitation; do not infer SS14 access semantics. No charge animation implying current change from absent/frozen telemetry; if eased bar is retained, only animate between observed snapshots and expose accurate numeric truth.

Keyboard and mouse must both operate each available widget, including tab/shift-tab focus, Enter/Space activation, scroll and escape/cancel; test hit targets at GUI scales, scissoring/clipping and tooltip bounds. Narration exposes name, value, availability and pending state; colors alone cannot communicate status. Use cacheable textures/layout and bounded collections; no per-frame texture creation, unbounded allocation or avoidable GC churn. Establish a measured 20-player/multiple-open-screens budget for render/UI ticks and allocations before claiming performance. GUI scale, resizing, lifecycle disposal and dedicated-server class isolation are acceptance constraints, not polish.

Game widgets needing real server owners have a **contract-first dependency gate**: specify snapshot fields, stable IDs, supported intents, authorization, freshness/revision, rejection and recovery with the owning feature team, then test the adapter. Without that contract, provide reusable display/read-only behavior and interaction primitives with no fake gameplay effect. This kit does not itself supply chemistry, vending, storage, cargo, lathe or medical server owners.
