# Cargo client check

Development diagnosis only - not cargo acceptance or M1 completion. No new
recording or log upload is requested. Existing bounded client-local capture
automatically admits at most two nearby new cargo entities per exact client
level/connection. It uses the existing single-capture tape limits (80 client
ticks / 8 seconds, 4096 rows, 7000-byte row cap); it does not scan the world,
steal an active manual capture, or preempt one. Normal owner testing requires no
manual command or UUID. Manual `/ms14_cargo_capture start` remains available.
Retrieve ordinary workspace logs; do not ask the owner to copy IDs.

At model draw, the capture now records a recomputed diagnostic light-probe
context: world point, committed frame/local point, local cell and cached sky
brightness scale where available. It records the actual draw's packed light
separately; the recomputed context is not a substitute for GPU pixels or proof
of the exact light term. Raw spawn packets remain outside coverage. Captures
remain bounded diagnostics, not visual proof.

For a concise manual check, use a normal ship/jug setup: observe initial ground
placement, a jug at rest while the ship spins, and viewport edges; then drop
ordinary cargo near a wall and in a clear area/airlock control. No post-fix
runtime/pixel evidence is available. M1 and two-client acceptance remain
outstanding; see [owner feedback](OWNER-FEEDBACK-20261105.md).
