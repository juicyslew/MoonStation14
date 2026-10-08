# Client selection and presentation

## Input and targeting contract

Examine is a view-directed action, separate from hand interaction targeting. The
SS14 baseline binding is Shift + primary mouse button. The client must intercept
only that gesture when no modifier other than Shift is held, and only when the
pointer is in the world (or outside the storage popup's interactive area). UI
slot transfers, chat, pause/other screens, and ordinary click actions retain
their existing routing. Shift + E remains the bag/equipment gesture; no examine
keyboard binding is added.

Selection uses the rendered camera's origin and forward ray. For a detached
pointer in the storage-popup host area, the ray instead comes from the actual
pointer's screen coordinates unprojected through the active camera projection
and orientation. It must not use the interaction probe's six-block reach or
camera-independent body look vector. Candidate collection is limited to loaded
blocks and entities, capped at 128 blocks (also bounded by render distance) and
32 nearest candidates; it must never load chunks or scan the whole level.

`ExamineVision.isTransparent(BlockState)` is the shared explicit visual-pass
classifier. Vanilla clear and stained glass (including panes) are included in
`data/moonstation14/tags/block/examine_transparent.json`; tinted glass remains
opaque. Transparent windows are candidates in their own right, then ray traversal
continues to allow deliberate examination of visible objects behind them.
Ordinary opaque collision shapes terminate traversal. This visual rule does not
change physical interaction or block use. When multiple candidates overlap the
ray, the nearest nontransparent target is selected by default (falling back to
the window itself); automatic selection updates as eligible opaque candidates
arrive. Shift + scroll cycles from its stable transparent hit anchor and makes a
manual choice that stays stable across ray-order changes. A new Shift-hold resets
to automatic preference; glass is fallback only when nothing eligible is behind
it. Ctrl pull and the
existing G-held construction/disposal scroll gestures retain priority.

Entity identity is the entity UUID paired with its current runtime id; blocks use
their position. Composite objects must resolve to canonical roots where their
existing geometry support makes that possible. Client selection is only intent:
the server revalidates actor authority, epoch, range, visibility, and current
target identity before returning any examined data. Public rendered naming is
distinct from authoritative response entries. Delayed replies are accepted only
for the latest matching request against the clicked/latched target, body UUID,
and body epoch. Shift+scroll changes only the preview; releasing Shift or moving
aim/pointer does not retarget or dismiss an open examination. A subsequent
Shift-click replaces the latch.

## Compact result policy

The examination result belongs in a compact transient HUD popup reused and
aligned to the shared right border below seven reserved status rows. The details
panel measures unwrapped text at its 0.75 render scale, then expands from 56 to
112 GUI pixels before wrapping. The shared layout computes the rendered/hit-test
rectangle directly; obsolete click-anchor/bottom-right placement code is removed. The
single-line preview sizes to its text and sits centered above the current cursor
with a 24 GUI-pixel gap;
names wider than the viewport scale to fit. Outside world
click dismisses and consumes it by default; Pin keeps it open across outside
clicks. Escape and × always dismiss. It is non-modal: it does not install a
Minecraft `Screen`, pause input, or suppress movement, hands, chat, speech, or
retained storage. Show public name/description and styled response text; internal
group identifiers control ordering, not visible labels. Render detail entries
only when `detailsAllowed` is true.
The panel shares the top-right status column's right edge and begins below seven
complete reserved status rows, whether or not all seven are active. Its line
budget is clipped to remaining space below that reservation; it never moves up
over status rows. Pause, options, inventory, chat, and other non-world-host
screens retain click priority, including outside-panel clicks. Storage remains
a world host outside interactive windows. Local detail revocation masks
immediately; newly allowed rows require an authoritative server round trip.
Range-transition refreshes coalesce behind an in-flight request and send one
follow-up after a correlated reply. Disagreement retries are four client ticks
apart only when no request is outstanding; matched content refresh remains at
the normal twenty-tick cadence.
When reserved space cannot show every wrapped result row, non-Shift wheel over
the panel scrolls its clipped readout and a header counter indicates more rows.
Shift-wheel remains candidate cycling; other screens and storage slots retain
their existing scroll priority.
Clear authoritative text when body/epoch/level changes, target identity becomes
stale, or the controlled body leaves ordinary examine range. Public visible
identification is not itself proof of access to examination details. Refresh
authoritative state at a bounded interval rather than issuing requests per frame.
An unpinned popup expires ten monotonic real-time seconds after the explicit
examination click; rendering and periodic refreshes do not restart its age. Pin
suspends timeout only, and unpin starts a fresh ten seconds. Range/visibility,
authority/epoch, level, and target identity remain mandatory while pinned;
looking away alone does not dismiss. A Shift-held preview row shows only the
currently eligible candidate's local public name, independent of the result.
Entity outlines offset current bounds by the render-time interpolated position,
while logical selection and eligibility continue using current authoritative
geometry. Pin is an explicit pixel thumbtack with a localized Pin/Unpin tooltip.
Client eligibility uses the resolved examiner projection and shared examination
policy with the controlled actor's physical eye and current target geometry; the
server independently revalidates every request. Ineligible targets (including
critical/dead actors outside their respective ranges) have no preview or request.
Where no client blind/blur projection is present, preflight uses the policy's
neutral `blind=false`, `blur=0` inputs; it does not infer an unsupported status.

## Parity and adaptation

The Shift + primary binding and transient popup follow SS14's `ExamineEntity`
interaction. SS14 uses mouse selection in its 2D world; camera-projected 3D rays,
explicitly selecting a window versus a target behind it, bounded candidate
cycling with Shift + scroll, and the practical 128-block visible-identification
cap are Minecraft adaptations, not claims of SS14 parity. The transparent tag is
explicit rather than inferred from `noOcclusion`, preserving tinted-window
occlusion and keeping vision independent from physical collision/use.

## Verification checklist

- A ray can select clear glass and a visible entity/block behind it; cycling is
  deterministic and returns to the window.
- Tinted glass and opaque solid shapes stop the ray.
- Displaced camera rays differ from controlled-body range origin; no chunks are
  force-loaded and candidate collection remains capped.
- World Shift-click consumes examination only; focused storage slots, screens,
  Ctrl pull, G-scroll construction, ordinary clicks, and Shift + E remain intact.
- Stale id/UUID, request, actor, or epoch responses do not replace current UI;
  detail visibility follows authoritative eligibility.
- Popup remains non-modal and coexists with HUD, chat, storage, hands, and
  movement; body/level lifecycle clears stale results.
- Scene examination is a deliberate Minecraft adaptation: Sun/Earth are stable
  scene identities, not SS14 celestial prototypes or distant world entities.
  Their selectable silhouettes must use the same transformed disc geometry as
  the sky renderer; opaque loaded world geometry blocks scene fallback. They
  have no world-space outline: public name and blurb are the scene presentation.
# Targeting defaults and cycling

The shared world query retains explicitly tagged transparent blocks as selectable candidates and continues its ray traversal behind them; the explicit `examine_transparent` classification does not make stained glass/ex-vision-tagged blocks transparent to ordinary opaque geometry. Client cycling uses the transparent hit as a logical anchor, defaults to the nearest eligible nontransparent target (falling back to the transparent candidate), and keeps a selected target by stable identity as hit geometry or candidate ordering changes. Shift+scroll can still select the transparent anchor itself. Airlock candidates identify the canonical lower/root block and expose the union of the shared geometry's frame and both leaves as candidate bounds; ray hits are still tested against the individual visible geometry pieces, so the bounds are for highlighting, not occlusion.
