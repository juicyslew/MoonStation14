# Guns audio resource mapping

Assets copied byte-for-byte from `docs/guns-sprint/reference/upstream-assets.zip`, pinned SS14 revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. SoundEvent IDs below use `moonstation14:` namespace. Source clips are under `Resources/Audio/`; local files mirror the relative paths under `assets/moonstation14/sounds/ss14/` (except Effects clips use `ss14/effects/`).

## Gunshots

| Registered event path | Local clip | Source cue and selected consumer mapping |
|---|---|---|
| `ss14/weapons/guns/gunshots/pistol` | `.../gunshots/pistol.ogg` | `Weapons/Guns/Gunshots/pistol.ogg`; pistol profile cue. |
| `ss14/weapons/guns/gunshots/rifle` | `.../gunshots/ltrifle.ogg` | `Weapons/Guns/Gunshots/ltrifle.ogg`; WeaponRifleLecter `soundGunshot.path` (leaf override, not parent `batrifle`). |
| `ss14/weapons/guns/gunshots/revolver` | existing `.../gunshots/revolver.ogg` | `Weapons/Guns/Gunshots/revolver.ogg`; reused event/resource. |
| `ss14/weapons/guns/gunshots/shotgun` | `.../gunshots/shotgun.ogg` | `Weapons/Guns/Gunshots/shotgun.ogg`. |
| `ss14/weapons/guns/gunshots/sniper` | `.../gunshots/sniper.ogg` | `Weapons/Guns/Gunshots/sniper.ogg`. |
| `ss14/weapons/guns/gunshots/laser` | `.../gunshots/laser.ogg` | `Weapons/Guns/Gunshots/laser.ogg`; laser profiles. |
| `ss14/weapons/guns/gunshots/laser_cannon` | `.../gunshots/laser_cannon.ogg` | `Weapons/Guns/Gunshots/laser_cannon.ogg`; Energy Shotgun base cue. |
| `ss14/weapons/guns/gunshots/taser2` | `.../gunshots/taser2.ogg` | `Weapons/Guns/Gunshots/taser2.ogg`; disabler cue (no selected local profile consumer yet). |

Gunshot cue parameters are `volume_db: 0`, `pitch_variation: 0`. Upstream source for Lecter is `Rifles/rifles.yml` leaf `WeaponRifleLecter`, overriding its `BaseWeaponRifle`'s `batrifle.ogg`. Battery gunshot parameters inherit `GunComponent.SoundGunshot` default `/Audio/Weapons/Guns/Gunshots/smg.ogg` unless overridden by selected prototype; Energy Shotgun explicitly overrides with `laser_cannon.ogg`. Its battery fire modes select projectile/cost/pacifism and do not alter sound. Cue availability does not imply playback wiring beyond profile fields.

## Cocking, bolt, and magazine cues

| Event ID path | Source OGG / local OGG stem | Intended upstream consumer field |
|---|---|---|
| `ss14/weapons/guns/cock/pistol_cock` | `Cock/pistol_cock.ogg` | pistol `soundRack`/cock action |
| `ss14/weapons/guns/cock/sf_rifle_cock` | `Cock/sf_rifle_cock.ogg` | rifle cock/rack action |
| `ss14/weapons/guns/cock/ltrifle_cock` | `Cock/ltrifle_cock.ogg` | typed magazine/chamber provider default rack cue (`GunSoundDefaults.cue("cock/ltrifle_cock")`) |
| `ss14/weapons/guns/cock/smg_cock` | `Cock/smg_cock.ogg` | SMG cock/rack action |
| `ss14/weapons/guns/bolt/rifle_bolt_closed` | `Bolt/rifle_bolt_closed.ogg` | bolt close/rack action |
| `ss14/weapons/guns/bolt/rifle_bolt_open` | `Bolt/rifle_bolt_open.ogg` | bolt open/rack action |
| `ss14/weapons/guns/magin/pistol_magin` | `MagIn/pistol_magin.ogg` | pistol magazine insert |
| `ss14/weapons/guns/magout/pistol_magout` | `MagOut/pistol_magout.ogg` | pistol magazine eject |
| `ss14/weapons/guns/magin/ltrifle_magin` | `MagIn/ltrifle_magin.ogg` | Lecter magazine insert |
| `ss14/weapons/guns/magout/ltrifle_magout` | `MagOut/ltrifle_magout.ogg` | Lecter magazine eject |
| `ss14/weapons/guns/magin/bullet_insert` | `MagIn/bullet_insert.ogg` | magazine/round insertion |
| `ss14/weapons/guns/magin/shotgun_insert` | `MagIn/shotgun_insert.ogg` | Kammerer shell insert |
| `ss14/weapons/guns/magin/batrifle_magin` | `MagIn/batrifle_magin.ogg` | power-cell slot insertion default |
| `ss14/weapons/guns/magout/batrifle_magout` | `MagOut/batrifle_magout.ogg` | power-cell slot ejection default |
| `ss14/weapons/guns/misc/selector` | `Misc/selector.ogg` | fire-mode selection |

The gun interaction event keys, OGG dependencies, and `ModSounds` registrations are present. These events are resource primitives for typed gun fields implemented separately; this document does not claim interaction consumers are wired yet. Existing revolver magazine-in, magazine-out, and spin events are reused instead of duplicated. Existing `ss14/effects/thudswoosh` event and resource are also reused.

## Attribution

The upstream `Weapons/Guns/sources.json` records CEV-Eris source URLs for the pistol/rifle interaction OGGs, but does not establish a license. For files absent from file-specific `attributions.yml`, source/author/license remain unverified; do not infer a license. `taser2.ogg` is attributed to tgstation, CC-BY-SA-3.0, at source `https://github.com/tgstation/tgstation/tree/88d7dbfc105fbf40284d7b7c4587f8d23c0ac3ac`. Per-file unresolved terms are indexed in `docs/audio-unknown-provenance.md`.
