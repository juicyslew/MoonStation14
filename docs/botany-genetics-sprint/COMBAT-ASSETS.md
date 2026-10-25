# Shared combat-mode UI assets

The combat-mode toggle uses the pinned SS14 `ActionCombatModeToggle` mapping:
`iconOff` = `Interface/Actions/harmOff.png` and `iconOn` = `Interface/Actions/harm.png`.
The original PNG bytes are packaged as `assets/moonstation14/textures/ui/actions/harm_off.png`
and `harm.png`; no conversion or visual adaptation was made. These are shared UI icons,
not plant assets.

Source: SS14 commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
`Resources/Textures/Interface/Actions/{harm.png,harmOff.png,meta.json}`.
Source Git blob IDs: `harm.png` `e4cd2fb8340a3f373ef1ec004848912ca474e8ea`,
`harmOff.png` `e68c585073f0355a6450e9cabd37a5cffcca10c6`, metadata
`886484c333b95743c8cdf6191ad3de4fccfa26c4`. Packaged SHA-256:
`harm.png` `8fb30aed2ebe08d8b3046278a7cee08b740d3278e1d1d740dd7de9fece2f4f0c`,
`harm_off.png` `6aafdfbf2e6058981d933cc7f5e3baff4afb9ca15a7adee51d9c6fb06cbc3b35`.

The complete source `meta.json` is retained beside the assets as `source-meta.json`.
It declares CC-BY-SA-3.0 and states: “Taken from
https://github.com/tgstation/tgstation/commit/3d049e69fe71a0be2133005e65ea469135d648c8.
Harm and disarm heavily modified. Web made by jackfrost (github) for SS14. Eyeclose and
Eyeopen by Bhijn and Myr (deathride58 on Github), under CC0.” Preserve this attribution
and the CC-BY-SA-3.0 license when redistributing; no relicensing is claimed.

`CombatModeIconResourceTest` checks packaged byte hashes and the expected source-to-toggle
on/off mapping. It is an asset/resource integrity test, not native UI visual acceptance;
native visual review remains unverified.

## Shared melee arc resources

The shared combat assets include the original `fist.png` and `spear.png` bytes from
the pinned SS14 checkout `Resources/Textures/Effects/arcs.rsi/{fist.png,spear.png}`.
Their source SHA-256 values (and packaged SHA-256 values, identical) are
`fist.png` `bf94a132e542bf7c31093ba80a7fc9aaf737a99acdae7ab288726b267a6a6a33` and
`spear.png` `7fd170b19ae26470b92ed7ebf09f8d664846c050a99290f4727506c39c11ae1c`.
Both PNGs declare 32x32 dimensions. The complete source metadata is retained as
`src/main/resources/assets/moonstation14/textures/effects/melee/source-meta.json` (SHA-256
`01c7fadb90bdf5c5c0544b8ee800d494ce45dea4f28694f46f4cb83b5669c084`). It records
CC-BY-SA-3.0 and attribution: “Taken from
https://github.com/tgstation/tgstation/raw/c545428822f1ee0d402b812221518632dbe198cb/icons/effects/effects.dmi
with offsets modified.” Preserve this provenance and license; no relicensing is claimed.

Upstream mapping boundary: `WeaponArcFist` inherits `WeaponArcStatic` and uses
the `fist` sprite state; `WeaponArcThrust` inherits `WeaponArcStatic` and uses
`spear` as its base sprite, while its arc visuals animate Thrust. Slash likewise
inherits the static base, so its base visual is spear; this is not a slash-state
sprite mapping. This import does not implement the renderer or 3D projection.
Copied weapon sprites are different assets; current hand art will be needed later.
`MeleeArcResourceTest` verifies packaged hashes and declared image dimensions only,
not rendering or visual acceptance.
