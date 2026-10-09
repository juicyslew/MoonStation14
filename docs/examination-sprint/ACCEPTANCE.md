# Examination acceptance gates

## Current status

Accepted and closed by the owner on 2026-10-07. Owner confirms ordinary objects, Space Lube localization, filled
jugs, Sun/Earth examination and world-target precedence work, with no regressions
in the pin/tooltip, adaptive width, orange preview, or smooth moving outline.
Bicaridine remains unrecognizable as intended. The final status-column placement,
responsive detail-range refresh, and pause-menu priority refinement was accepted
with closure. Earlier no-popup reproductions are historical. See `HANDOFF.md`.

## Established fixes and evidence

- Popup lifetime is bound to the clicked target, not current aim. Correlated
  denials display a safe header rather than silently disappearing; accepted
  controller results still require incarnation validation.
- Airlock selection and rendering consume shared frame/leaf geometry. The
  client delegates to the common world selector exercised by server fixtures.
- The range fixture now uses a horizontal ray inside the loaded template's
  actual 36×5×8 bounds. The former vertical ray crossed a framework barrier.
- Focused server validation passed **12/668 selected tests**, including physical
  range/detail boundaries, glass visibility, and airlock selection.
- The final broad server run completed **668/668 tests: 655 passed, 13 failed**.
  All twelve examination fixtures passed. The remaining failures are not
  established as harmless or pre-existing.
- The aperture fixture's poll uses test-relative `runAfterDelay(1)` rather than
  global world time; its corrected behavior passed the full native run.
- Final build, GameTest compilation, and assembly passed; **2,676 JUnit tests**
  passed with no failures/errors/skips.
- Examination now uses the medical reducer's shared healthy default when a
  fresh resolved character has no materialized medical-state attachment. A live
  fixture covers this production actor-adapter case without pre-seeding state;
  it does not claim authenticated-player packet or GUI coverage.
- Native wall-grid coverage exposed and verified a fix for negative-direction
  rays terminating exactly on voxel boundaries. Endpoint-containing shapes use
  the pinned SS14 exemption; opaque intervening shapes remain blockers.
- Puddle examination consumes resolved inherited profiles and live reagent
  storage, with detail-gated approximate volume/physical description and only
  recognizable reagent names. Unsupported slip/evaporation text is omitted.
- Shared solution formatting preserves nested translation parameters and colors
  through the response codec. All 98 authored physical-description tokens and
  their local article labels have resource coverage. A live mixed-puddle fixture
  checks recognizable-only output, mixture-colored description, inherited
  capacity, detail gating, and unchanged storage; individual-name color is
  established by formatter/codec source coverage rather than a native assertion.
- UI refinements retain the 10-second unpinned timer, independent live name
  preview, simultaneous active/preview outlines, and automatic glass look-through.
  Latest presentation changes add render-time entity interpolation, an explicit
  pixel thumbtack/tooltip, adaptive 56–112-pixel details width, stable
  right-edge placement below seven status rows, and a single-line preview centered
  above the cursor. Long details can scroll within the reserved layout region.
  Owner acceptance of these latest changes is established by sprint closure.

## Accepted manual gates

1. Details align to the shared right border below seven complete status rows.
   Long readouts remain accessible through scrolling without status/handbar overlap.
2. Local detail-range exit hides private rows immediately; entry requests fresh
   authorized information without waiting for the normal periodic refresh. A
   server round trip is still required. Explicit retargeting rejects old replies.
3. Pause/Options and other non-world-host screens retain click/wheel priority;
   examine must not dismiss, pin, scroll, or consume those inputs.
4. Accepted hover placement, chemistry, celestial selection, popup lifetime,
   dismissal, and storage/chat/input ownership remain intact.
5. Preserve the broad suite's unresolved failures. Headless selector tests do
   not prove authenticated player RPC, mouse input, or GUI acceptance.

An isolated server started, but the connected smoke produced no player join or
popup evidence. The owner declined automated GUI input; no automated GUI check
is authorized. Connected delivery was accepted by the owner through manual testing
and final sprint closure; automated fixtures alone do not establish that coverage.

See `VALIDATION.md` for artifacts.
