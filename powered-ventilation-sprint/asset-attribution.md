# Ventilation presentation asset attribution

The following single-frame SS14 sprites are derived from the pinned checkout at `c9df5ef5d675b0d1d226828bddf6b78c28502d91` (`SS14-dev/space-station-14`):

| Local resource | SS14 source | RSI metadata attribution | License |
| --- | --- | --- | --- |
| `assets/moonstation14/textures/block/air_alarm.png` | `Resources/Textures/Structures/Wallmounts/air_monitors.rsi/alarmp.png`, top-left 32x32 direction frame | Taken from tgstation, modified for SS14 | CC-BY-SA-3.0 |
| `assets/moonstation14/textures/block/gas_vent.png` | `Resources/Textures/Structures/Piping/Atmospherics/vent.rsi/vent_off.png`, top-left 32x32 direction frame | BeeStation/BeeStation-Hornet, commit `4ccd79de285e79e504308bcd6fa5908d6b7685f7` | CC-BY-SA-3.0 |
| `assets/moonstation14/textures/block/gas_scrubber.png` | `Resources/Textures/Structures/Piping/Atmospherics/scrubber.rsi/scrub_off.png`, top-left 32x32 direction frame | tgstation/tgstation, commit `57cd1d59ca019dd0e7811ac451f295f818e573da` | CC-BY-SA-3.0 |

The pinned upstream revision is `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Each RSI's `meta.json` explicitly declares `CC-BY-SA-3.0`, frame size 32x32, and the respective copyright attribution above. `vent_off`, `scrub_off`, and `alarmp` each have `directions: 4`; each local PNG is the top-left (first/default direction) 32x32 cell, not the complete 64x64 direction sheet. The alarm's selected top-left `alarmp` cell depicts its display text upright and horizontal (the intended front-facing frame), not a rotated side direction. SS14's `Resources/Textures` assets are licensed CC-BY-SA-3.0; see upstream `Resources/Textures/Structures/Wallmounts/air_monitors.rsi/meta.json`, `Resources/Textures/Structures/Piping/Atmospherics/vent.rsi/meta.json`, and `Resources/Textures/Structures/Piping/Atmospherics/scrubber.rsi/meta.json`. These derived crops remain subject to that license and attribution/share-alike requirements; they are not relicensed under this project's code license.

Vent and scrubber use their static `vent_off` / `scrub_off` art. Models do not represent live powered state; no dynamic status rendering is wired. Alarm uses the `alarmp` static sprite. The body sides remain a simple vanilla iron backing, while the visible front texture is the attributed source sprite.
