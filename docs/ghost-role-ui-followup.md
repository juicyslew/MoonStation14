# Ghost-role expansion side fix

Separate owner-requested UI bugfix, accepted with sprint closure on November 1,
2026. See `melee-combat-sprint/HANDOFF.md` for technical validation boundaries.

`GhostRoleMenuWindow` captured title drags but did not advertise capture through
`PointerOverlayWindow.ownsPointerCapture`. The registry therefore skipped release;
captured geometry could suppress later content-height changes. The window now
reports its actual capture, reusing registry release and shared relayout ownership.
Role activation/network behavior is unchanged.

Focused coverage: `WindowGeometryControllerTest`, `GhostRoleMenuPresentationTest`
and `PointerOverlayRegistryTest`; actual window-to-registry integration and GPU
appearance remain owner-observation boundaries.

After validation: open Ghost Roles, drag and release its title, then expand,
collapse and expand the same mob group again. Its entries should remain visible.
