# Whole-set owner feedback — correction scope

Technical PASS `run_3b983fe361cb2e64f0c8ecf6` is not owner acceptance. The next
candidate must address the whole-set feedback rather than applying another Mk58-only
orientation patch.

## Ordered work units

1. **Shared item presentation:** verify actual imported frames for all eight guns;
   correct backward/rolled aim and replace parallel “zombie” support-arm posing with
   a support-grip consumer. Check Energy Shotgun scale and stable widest-side loose
   resting poses for guns and magazines. Preserve artist geometry, UVs and textures.
2. **Effects and shot geometry:** fix real native atlas/provider consumption for
   casings and energy-shot effects; remove discontinuous/random-looking casing roll.
   Render laser/Svalinn travel as world-space beams with appropriate width, not
   accidental camera-facing beam sprites. Adapt shotgun fan-out to 3D while retaining
   source projectile counts, per-shot costs and damage.
3. **Controls and power:** provide a direct unwield route without consuming the
   existing rack/cycle/mode use. Verify Mosin capacity, loading and every selected
   energy gun/cell's inherited recharge composition and consuming systems against
   pinned SS14; implement missing applicable behavior through existing owners.
   Give concise, tested loading and recharge instructions.
4. **Catalog integrity:** establish Python's intended status and remove the broken
   fixture-only player entry or restore a genuine existing supported baseline, based
   on project evidence. Do not silently invent a ninth artist model.

Inspector is reported mostly functional; preserve its behavior while fixing its
presentation. Keep the current atomic reload, source-hand HUD, no-empty-banner,
compact-window, spent/live-round distinction, authority and prediction boundaries.

Acceptance needs actual native consumer/registration coverage and per-family frame
tests; resource-file existence and a fabricated 180° transform are insufficient.
Automated readiness, CPU previews, native GPU inspection and owner acceptance remain
distinct. Original exclusions (notably structural health, fabrication and new
stationary charging machinery) remain unless this feedback materially changes them.
