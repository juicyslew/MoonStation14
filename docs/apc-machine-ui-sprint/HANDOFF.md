# APC machine UI

## STATUS

Reconstructed 2026-10-03. Implemented with historical automated verification; this sprint's records do not establish full owner-connected acceptance. Later visual-kit acceptance is separate from APC transport/lifecycle acceptance.

## ARCHITECTURE

Authenticated server menus bind exact level/block entity, session UUID, menu ID, revision, range, loaded state, and permission. Breaker/battery remain block-entity-owned. Client switch prediction is presentation only and reconciles server state; battery is projected as bounded permille. Subsequent body-authorized devices use additional authority, not menu transport alone.

## IMPORTANT FILES

`current-handoff.md`, `api-audit.md`, `visual-parity.md`, `assets-and-attribution.md`; `src/main/java/com/juicyslew/moonstation14/ms14/power/ui/{ApcMenu,ApcMenuService}.java`, `ui/client/ApcScreen.java`, and power device blocks.

## SS14 CORRESPONDENCE

Local NeoForge transport is audited, not exact SS14 electrical/UI parity. External-power/load telemetry is unavailable. Later `../machine-ui-visual-kit-sprint/HANDOFF.md` describes adopted visual components.

## DECISIONS / WHY

Open breaker may charge from known MV input but neither supplies output nor discharges battery. No duplicate client switch sound. Vanilla screen blocks movement; walking out of range while it is open is not an appropriate manual test.

## KNOWN GAPS

Later visual-kit closure records owner-confirmed two-real-client switch updates, visible overload trip, screen close, and one server-only breaker sound. These bounded observations supersede those specific pending checks, not the full refresh/removal/pause, elapsed-time battery, or restart/persistence checklist. File-specific switch-audio license was unresolved; preserve provenance records.

## VALIDATION

Coordinator-reported isolated 161/161 GameTests plus focused UI tests. Historical evidence, not fresh connected-client acceptance; no suites rerun for reconstruction.

## NEXT WORK

Use `current-handoff.md`'s bounded two-client checklist, reconcile later visual/body-authority changes first, then test persistence. Do not infer APC acceptance from visual-kit closure.
