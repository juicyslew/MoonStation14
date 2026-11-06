# Automatic medical movement observation

Diagnostic only; the reported post-crash corpse/camera drift is not fixed or explained yet.

Player-character transitions into Critical/Dead start a bounded server trail automatically. Client medical-state and CORPSE/GHOST observation edges start the matching client trail without commands or IDs. Windows are scoped to exact level/body references, capped at 160 tick samples (four server windows, one client window), and close on recovery, replacement, teardown or expiry. Same-corpse CORPSE-to-GHOST continuity remains observable.

Normal logs use `[medical movement trace]`. Rows distinguish server ticks, client-local ticks and world times; record body position/velocity, medical/ownership/frame markers, and client interpolation history. Raw lerp targets and position-codec bases are explicitly not assumed world coordinates. Client rows distinguish camera entity, anchor, already-computed eye endpoints and cached last-rendered viewport position.

The trace does not call stateful Sable plot-position getters, recompute eye positions, or change movement, gravity, camera binding, prediction or authority. Missing/non-finite values and truncation remain explicit. Logs are collected locally by the project team. No deliberate crash/death, UUID copying or log upload is requested.
