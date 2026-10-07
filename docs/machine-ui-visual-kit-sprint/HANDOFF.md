# Machine UI visual kit

## STATUS

Reconstructed 2026-10-03. **Owner accepted the agreed visual-kit scope.** This does not close APC machine lifecycle acceptance or establish authoritative gameplay for gallery examples.

## ARCHITECTURE

Reusable client models/widgets live under `ms14/ui`; gallery is client-only. APC retains server menu/session/revision and block-entity authority. ApcMenu projects readiness/committed state; ApcScreen gates input and layers local preview. No new server rendering/tick loop. Later air alarms reuse the toolkit.

## IMPORTANT FILES

`implementation-handoff.md`, `completion.md`, `owner-feedback-and-next-slice.md`, `manual-gallery.md`, `apc-animation-assets.md`; `src/main/java/com/juicyslew/moonstation14/ms14/ui/client/`, `power/ui/ApcMenu.java`, `power/ui/client/ApcScreen.java`.

## SS14 CORRESPONDENCE

SS14-styled presentation, not exact parity or measured render capacity. APC animation provenance is recorded; static attributed PNG is UNKNOWN fallback. Retain attribution.

## DECISIONS / WHY

Widgets are separate from machine authority/transport. Authenticated menus do not automatically grant controlled-body actions. Do not invent telemetry or turn gallery examples into a speculative gameplay framework.

## KNOWN GAPS

Owner confirmed two-real-client switch updates/trip, title drag, resize centering, single server sound and a functional gallery. Immediate animation/contrast checks were waived as accepted risks, not observations. Later text/search-row fixes lack visual retest; rendering/allocation cost is unmeasured. Broader APC two-client lifecycle acceptance remains separate.

## VALIDATION

Historical final isolated run h passed 171/171 required tests, with compile/unit green; runs f/g had varying failures. A cable test assertion was corrected, not runtime behavior. No new GUI/test validation performed.

## NEXT WORK

Closed scope needs no resumption. If extending, use the existing toolkit, perform deferred GUI-scale/animation checks, and profile actual rendering. Consult APC and powered-ventilation handoffs for their consumers.
