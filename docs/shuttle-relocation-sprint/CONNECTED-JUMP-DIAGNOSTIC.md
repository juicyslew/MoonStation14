# One-jump diagnostic capture

**Completed 2026-10-31 — do not repeat yet.** Evidence is preserved in
`build/owner-jump-regression-feedback01/`. The client window ended prematurely
with mixed cadence/world clocks; the server's earlier automatic episodes do not
pair with the client trigger. Neither captures the reported post-landing rewind.
The procedure below is historical, not a new testing request.

This collects evidence for the initial visible jut. It is not M1 acceptance or a
request for two-client testing. Capture usefulness has not yet been verified in a
connected run.

1. Restart through your normal launcher with a fresh world. Stand in the manual
   ship's clear jump bay, resting relative to the floor, with straight **2 m/s**
   motion and no yaw target.
2. Run `/ms14_connectedwall jump`, close chat, release horizontal movement keys,
   and jump once within ten seconds.
3. Let the game run for another second. Report whether the jut occurred and that
   the capture is finished.

The existing `arm` wall mode is unchanged. No configuration changes are needed.
Logs use `[CONNECTED_JUMP]` on the client and server; retain the normal launcher
logs for inspection. The capture is bounded and adds no movement-policy change;
its timing impact has not been established.
