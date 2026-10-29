# Tank-slot and valve audio mapping

Upstream checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

* Canister valve default is `valveSqueak`, inherited from `GasMaxPressureHolderComponent` (`Content.Shared/Atmos/Components/GasMaxPressureHolderComponent.cs`). `Resources/Prototypes/SoundCollections/atmospherics.yml` declares three members: `/Audio/Effects/squeak1.ogg`, `squeak2.ogg`, and `squeak3.ogg`. These have been copied unchanged to `assets/moonstation14/sounds/ss14/atmos/`. No verified author/licensing metadata was located in the pinned checkout; attribution remains unknown.
* Local tank internals connect/disconnect and a separately controllable gas-tank valve are absent. No such capability was added. `spray.ogg` (the atmospheric canister valve release default) is not used for the unrelated local inserted-tank-to-canister refill.

## Vocal event resources

* `vocal_human_scream` reuses the six existing `emote/scream/malescream_{1..6}.ogg` files for the upstream MaleHuman fallback; no recordings duplicated.
* `vocal_reptilian_scream` uses `Resources/Audio/Voice/Reptilian/reptilian_scream.ogg` → `sounds/voice/reptilian/reptilian_scream.ogg` (SHA-256 `106e94acb5c76cbadd70cc2e0ffc795cde5af95be9b8825b86f9280143014383`); upstream metadata names Skyrat-SS13 and its PR source but omits license.
* `vocal_scurret_scream` uses `Resources/Audio/Animals/wawa_protest.ogg` → `sounds/animals/wawa_protest.ogg` (SHA-256 `f9e7cf3973775453a6cac27675beba5f829b1341753beaa8e9f9cb1f1fea0f42`); FairlySadPanda, CC-BY-4.0, SS14 PR 37936.
* `vocal_wilhelm` uses `Resources/Audio/Voice/Human/wilhelm_scream.ogg` → `sounds/voice/human/wilhelm_scream.ogg` (SHA-256 `20baa803554c0dfb8b895f4b6f8963529e9400c208917b0af954925098656f6a`); upstream attributions identify /tg/station, CC-BY-SA-3.0, source commit `3d049e69fe71a0be2133005e65ea469135d648c8`.
* Item-slot sounds upstream default to `/Audio/Weapons/Guns/MagIn/revolver_magin.ogg` and `/Audio/Weapons/Guns/MagOut/revolver_magout.ogg` (`Content.Shared/Containers/ItemSlot/ItemSlotsSystem.cs`). Local events reuse the byte-identical existing `sounds/chemistry/chemmaster/` samples and retain their file-specific license notes; duplicate imports were removed. Source provenance is `Resources/Audio/Weapons/Guns/sources.json`, which attributes these to CEV-Eris commit `01f7518e0f8177734a6579aba2bbf76024aa96c4` (`rev_magin.ogg`, `rev_magout.ogg`). A verified license is not inferred from that source record. Unresolved provenance is indexed in `audio-unknown-provenance.md`.
