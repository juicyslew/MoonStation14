# Airlock asset preparation

Reference: SS14 checkout `SS14-dev/space-station-14` at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Standard Airlock declares base
`closed`, `closed_unlit`, `welded`, `bolted_unlit`, `emergency_unlit`, and
`panel_open` visual layers. DoorSystem consumes open/close/deny sounds and
animated opening/closing appearances; bolt visual uses `bolted_unlit` and the
maintenance panel visual uses `panel_open`. Its Sprite base door is a 32x32 SS14
image, not a Minecraft two-block door. The Minecraft footprint is one block wide
by two blocks tall: the Minecraft renderer adapts the source art to a simple 3D
frame and moving leaves (not a two-block-wide doorway),
retain the full-color sprite as base and layer the powered unlit emissive sprite
over closed/transitioning appearances (hidden while unpowered and for standard
open). Layer bolt/panel images only when active. These assets do not supply
Minecraft block registration, authority, or rendering behavior.

## Prepared files

* `textures/block/airlock/neutral_metal.png` is an intentionally uniform 1x1
  RGBA backing texture for the Minecraft-only frame/leaf side, top, and bottom
  faces. Its exact opaque RGB is `#545454`, sampled from pixel `(2,0)` of the
  unchanged upstream-derived `closed.png` (the opaque neutral metal pixel).
  This is a derived convenience texture, not a replacement or edit to source art;
  it inherits that door art's attribution/license above.

* `assets/moonstation14/textures/block/airlock/{closed,closed_unlit,open,opening,closing,opening_unlit,closing_unlit,panel_open,bolted_unlit}.png`:
  from `Resources/Textures/Structures/Doors/Airlocks/Standard/basic.rsi`.
  Source RSI metadata gives CC-BY-SA-3.0 and attributes the artwork to
  tgstation (commit `c6e3401f2e7e1e55c57060cdf956a98ef1fefc24`). Static states
   and overlays are copied unchanged (32x32); `closed_unlit` is a powered light
   overlay, not the powerless door image. Source has no `open_unlit` state. Opening,
  closing and their unlit variants are 96x64, six 32x32 frames in row-major
  order. Original transformed vertical sheets remain at their root paths, but
  do not use Minecraft animation metadata: atlas animation is global, not per
  controller. Independent renderer frames are at
  `textures/block/airlock/frames/{opening,closing,opening_unlit,closing_unlit}_{0..5}.png`,
  each 32x32, crops preserving source pixels. Source per-frame delays are
  `.1,.1,.07,.07,.07,.2` seconds for all four sequences; select elapsed frame
  independently per door/controller, not via global looping animation. Source
  frame delays sum to .61 seconds and do not specify authoritative door
  transition duration.
* `assets/moonstation14/textures/item/airlock_screwdriver.png` is a 32x32
  source-order alpha-over composite: `screwdriver.rsi/screwdriver.png` followed
  by `screwdriver-screwybits.png`. The base and overlay are retained unchanged in
  `textures/item/source/` for audit/recomposition. Both derive from the same RSI;
  retained `screwdriver-meta.json` records CC-BY-SA-3.0 and vgstation commit
  `1cdfb0230cc96d0ba751fa002d04f8aa2f25ad7d`. Static local composite uses the
  standard non-random base color; upstream RandomSprite rainbow variants are not
  reproduced because the local runtime has no corresponding layer randomizer.
* `assets/moonstation14/textures/item/airlock_multitool.png`:
  `Resources/Textures/Objects/Tools/multitool.rsi/icon.png`, 32x32 unchanged;
  RSI metadata: CC-BY-SA-3.0, tgstation commit
  `94f27c7b25bbde39c1412e24fb32bf6470fcc394`.
* `assets/moonstation14/textures/gui/airlock/{light_off_base,light_on_base,wire_1,wire_1_cut,wire_2,wire_2_cut}.svg` copied from `Resources/Textures/Interface/WireHacking`, with matching `.png` raster outputs alongside.
  UI should use the existing machine visual kit controls and these upstream
  wire/light art assets; assign actual runtime layout colors rather than
  hardcoding a wire-function/color association. SS14 layout-color appearance is
  data-driven. SVG artwork is unmodified; no separate license/author metadata
  was present beside these SVGs in the pinned checkout. The PNGs are transparent
  Chromium/Edge headless renders of the original SVGs at intrinsic source canvas
  dimensions and device scale 1: the light bases are 10x10 and 20x20; wire/cut
  artwork is 16x50. A 64x64 transparent-viewport screenshot was cropped to the
  source canvas, preserving source artwork placement; alpha=0 pixels were
  verified outside each drawing. Original SVGs are retained beside the PNGs.
  No runtime SVG dependency is needed for these GUI textures. SVG author/license
  provenance remains unresolved: resolve before public distribution if the
  upstream-wide license is not sufficient for desired redistribution.
* `assets/moonstation14/sounds/airlock/{airlock_open,airlock_close,airlock_deny,boltsup,boltsdown}.ogg`:
  original SS14 sound files, copied unchanged. Open/close/deny are the door
  component sounds; bolt up/down are the verified `DoorBoltComponent` defaults.
  Keep sound event/collection registration with the core worker; no Java,
  ModSounds, or registrations were changed here.

* `textures/item/crowbar.png` is the standard (grey) `icon` state from
  `Resources/Textures/Objects/Tools/crowbar.rsi`; the standard entity is
  `Crowbar` (not the edited cover/alternate variants). Source is 32x32,
  CC-BY-SA-3.0, tgstation commit `eea0599511b088fdab9d43e562210cdbd51c6a98`.
* `textures/item/wirecutter.png` (the existing item model's layer0) and its
  matching `airlock_wirecutter.png` are 32x32 composites of the unchanged
  `wirecutters.rsi/cutters.png` base then `cutters-cutty-thingy.png` grey working
  parts. Both source layers and `wirecutters-meta.json` are retained in
  `textures/item/source/`; RSI metadata gives CC-BY-SA-3.0 and vgstation commit
  `1cdfb0230cc96d0ba751fa002d04f8aa2f25ad7d`. Wirecutter upstream's RandomSprite
  rainbow base variation is not emulated; composite uses the static source base.
  Item model binds the complete composite; existing item ID is unchanged.

The item-image composition tests compare each baked 32x32 frame against the
source base-then-overlay alpha composite and assert an opaque gray working-part
pixel exists in the overlay.

The timed screwdriver panel operation plays the resolved `WiresPanel` open/close
sound only after a successful timed completion and state change; cancellation or
revalidation failure is silent. Pinned `WiresPanelComponent` defaults are
`/Audio/Machines/screwdriveropen.ogg` and `screwdriverclose.ogg`, and
`SharedWiresSystem.OnPanelDoAfter` selects the corresponding sound after
`TogglePanel` succeeds. Local supported profile defaults use the unchanged source
files at `sounds/airlock/panel_{open,close}.ogg`. This is panel feedback, not a
screwdriver-item response sound. Upstream has no attribution entries for these
two files; their provenance remains unresolved and is recorded in
`docs/audio-unknown-provenance.md`.

For wire tools, pinned `Content.Server/Wires/WiresSystem.cs` `UpdateWires` calls
`Tool.PlayToolSound` for cut and mend (but not pulse), and
`Content.Shared/Tools/Systems/SharedToolSystem.cs` plays resolved
`Tool.UseSound` only when present. Local optional `ToolProfile.useSound` resolves
through inheritance and plays after an accepted local cut/mend mutation;
unsupported/missing sound metadata, rejected/stale commands, and pulse stay silent.
The source sound attribution status is recorded in `docs/audio-unknown-provenance.md`.

Airlock attempt feedback is silent for an unknown power meter and for known
unpowered supply. This matches pinned `SharedDoorSystem.CanOpen`'s power gate
before its denial path and `SharedAirlockSystem.OnBeforeDoorOpened` / `CanChangeState`
power-and-bolt cancellation. Existing powered denial/obstruction feedback remains
unchanged; unknown power does not become a fake access-denied event.

## Door appearance/sound mapping for renderer integration

Controller visual API maps logical appearance to `closed`, `open`, `opening`,
or `closing`; retain that full-color image as base and overlay
`closed_unlit`, `opening_unlit`, or `closing_unlit` when powered (except for
the standard open appearance). Independently overlay `bolted_unlit` for the bolt indicator and `panel_open`
while the maintenance panel is open. Do not treat the
bolt light as the door state itself. The full-size Minecraft doorway is an
adaptation, not an SS14 1x2 sprite: source is one 32x32 pixel plane and requires
renderer adaptation across the one-wide, two-tall Minecraft footprint. Avoid
stretching source assets on disk. UI opens the panel / wires feature; no decorative unsupported wire
functions are included.

## Audio attribution note

The copied door sounds have explicit attribution in pinned upstream
`Resources/Audio/Machines/attributions.yml`: open and deny are CC-BY-SA-3.0
attributed to /tg/station; close is CC-BY-SA-3.0, from CEV-Eris at commit
`789ed19064ac42376d2355192cd1069a45a310f1`, modified by mirrorcult. This is
recorded in the dedicated unknown-provenance ledger as well, per repository
asset policy. No vanilla Minecraft door sounds are substituted.

`boltsup.ogg` and `boltsdown.ogg` are consumed by the pinned
`Content.Shared/Doors/Components/DoorBoltComponent.cs` defaults but have no
matching entries in the pinned `Resources/Audio/Machines/attributions.yml`.
Their provenance/license is therefore unresolved; see
`docs/audio-unknown-provenance.md` and obtain upstream provenance before
distribution.
