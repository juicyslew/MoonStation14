# Airlock wire UI

The SS14 reference checkout is pinned to `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Its `WiresSystem` caches a shared layout by layout ID and sends each client an
opaque integer selector, cut state, color and letter. The pinned wire palette
and letter set are used here as cosmetic choices; local roots share the
randomized assignment by resolved `Wires.layoutId()` for the lifetime of the
server. The cache clears on `ServerStoppedEvent`; mapping changes after restart
are deliberate because functional mapping is not persisted. The per-door
renderer identity/seed is unrelated to function assignment.

Wirecutter/multitool USE is routed after body-hand admission and before
the timed panel-tool route, but only when the active tool does not match the
resolved panel-opening quality. The opener remains the existing data-driven
timed screwdriver path. A wire-tool window opens only for an already-open,
visible, accessible panel. Its dedicated session is bound to the actor/body epoch,
window/session IDs, root UUID, loaded two-half door, body reach/LOS and server
permissions; it stays usable without power. Closing/hiding the panel, disabling
wire accessibility, losing body admission/reach or replacing the root closes it.

Commands contain the exact window ID/session, monotonic request ID, expected root
revision, opaque layout row ID and CLICK intent. On each command the
server revalidates the current body-owned active hand and current cutting/pulsing
quality, chooses cut/mend or pulse from the currently held tool, resolves row IDs
only in the server cache and calls the lower
controller's UUID/revision-guarded `applyWire`. A stale revision or rejected
wire returns a fresh snapshot while leaving a still-valid window open. Snapshot
rows include only ID, ARGB, letter and cut appearance; functional wire names
never go to clients. Power-off does not disable UI or mending, and cut wires
cannot be pulsed.

The client wire view is an internal retained view owned by the active wire-session
bridge; it is not installed as a modal Minecraft screen. Opening it activates the
existing `StoragePopupScreen` pointer host, which owns wire and storage workspace
drawing and routes wire-bounds clicks to the wire view before normal storage/hand
hit-testing. Outside those bounds, existing storage-host routes remain in force.
Before rendering in this embedded host, the view is initialized through the normal
Minecraft `Screen.init(Minecraft, width, height)` lifecycle so its inherited font
and other screen state exist; resizing preserves and clamps the draggable position.
LAlt uses the shared manual pointer-intent toggle; removing the host for captured
gameplay does not close the wire session. The central client tick keeps queries
running while the host is removed, while chat is open, or while the pointer is
captured. X/ESC and validated server close still close only the wire capability.

The client uses the prepared SS14 wire PNGs, tinted with the assigned wire color,
in eight vertical wire/contact columns below a two-row status panel. The existing
machine-window renderer supplies a compact draggable window without Cut/Pulse
buttons. Shared layout bounds drive drawing and wire hit-testing, so indicator
or blank-space clicks cannot send wire actions. Five functional indicators use the pinned
`BaseWireAction` power-off wrapper and power/door action light-state rules
(`OFF`, `ON`, `BLINK_SLOW`); indicator status is distinct from receiver supply
availability. Slow blinking is animated client-side using the SS14 on/off
light-base PNGs. Log pulse temporarily disables the local circuit indicator for
its resolved timeout (30 seconds by default); it does not create access-log entries because no
local AccessReader/logging service exists. AIA pulse is an intentional no-op,
matching the pinned upstream empty action. Clicking anywhere on
a wire chooses the held-tool action, an intentional adaptation from upstream's
separate wire-body/contact hit regions.

Stun blocks new wire actions at the authoritative pre-effect action gate, but
does not invalidate the already-open UI session: passive queries remain valid
and do not clear the displayed stun reason. This is distinct from opening a new
machine UI while stunned, which is denied by ordinary action admission.

The server leaves the actor's normal inventory menu active and revalidates
ordinary body authority, rather than granting a menu-only hand exception.
The shared `GameplayWindow` contract supplies movement and keyboard capture;
normal hand actions and outside-window world clicks use existing body requests.
The body HUD (hands, backpack/storage and local chat overlay) is visible in the
shared storage host, captured gameplay, and project chat screen; ordinary
settings/pause screens retain vanilla ownership. LAlt is the global manual
pointer toggle, not a wire-specific command. Capture transitions preserve the
active session and window placement. Outside left-clicks preserve their normal
modifier actions. Explicit X/ESC closure, server closure, and authority loss end
the session; capture transitions do not. Switch held tools without closing the
window. Only a focused text editor should capture gameplay keys; this window has
no search field.
Public redistribution of the original
wire PNG/SVG art remains subject to the provenance note in `ASSETS.md`.

No authenticated connected-player fixture is available for this new UI route;
do not substitute a FakePlayer or authority bypass. Existing hand-authority
revalidation is used in production; connected manual acceptance remains a
separate check.

Source-policy tests cover which render phase owns the HUD/workspace and shared
pointer retention, but do not substitute for connected gameplay verification of
mouse precedence, chat composition, or server-authoritative tool actions.
Existing body-action gates are unchanged: a shock-stun may prevent a subsequent
hand/use request for its normal duration; the UI does not bypass it. For an
otherwise admitted wire-menu opening request, the menu admission/open happens
before its contact shock, so that shock does not retroactively cancel the open.
