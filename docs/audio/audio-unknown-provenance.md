# Audio provenance requiring verification

`assets/moonstation14/sounds/ss14/weapons/guns/hits/bullet_hit.ogg` is copied from Space Station 14 `Resources/Audio/Weapons/Guns/Hits/bullet_hit.ogg` at pinned revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. The directory's `attributions.yml` does not list `bullet_hit.ogg`; its author/license/source are therefore unknown here and must not be inferred from neighboring samples.

Audio imported for singularity/emitter playback from the same pinned revision: `Effects/singularity.ogg`, `Effects/singularity_form.ogg`, `Effects/singularity_collapse.ogg`, and `Weapons/emitter.ogg`. Neither containing `Effects/attributions.yml` nor `Weapons/attributions.yml` lists these filenames. Their author, license, and original source are unknown; no attribution or license is inferred. Upstream `SingularityComponent.DissipationSound` defaults to `singularity_collapse.ogg`; see `Content.Shared/Singularity/Components/SingularityComponent.cs`.

The gas valve squeak collection uses imported `Effects/squeak1.ogg`, `Effects/squeak2.ogg`, and `Effects/squeak3.ogg` from the same pinned revision. `Effects/attributions.yml` does not list these samples; author, license, and original source remain unknown and are not inferred. Revolver magazine audio reuses the existing Chemistry Master resources rather than the duplicate copies; their existing file-specific license notes state provenance/license unresolved.

Gas pressure release and internals-connect event mappings are deferred because the corresponding local capabilities/assets were removed/not implemented; no sound event or feature is claimed for them.

## Additional unresolved audio

`voice/reptilian/reptilian_scream.ogg` is from pinned SS14 `Resources/Audio/Voice/Reptilian/reptilian_scream.ogg`; upstream `attritbutions.yml` gives copyright “scream_lizard.ogg” by Skyrat-SS13 and source `https://github.com/Skyrat-SS13/Skyrat-tg/pull/892`, but leaves license blank. License remains unknown. `animals/wawa_protest.ogg` is from `Resources/Audio/Animals/wawa_protest.ogg`, credited to FairlySadPanda, CC-BY-4.0, source `https://github.com/space-wizards/space-station-14/pull/37936`. Hashes and exact source paths are recorded in `audio-interaction-provenance-notes.md`.

- `src/main/resources/assets/moonstation14/sounds/mouse_squeak.ogg` — imported from pinned SS14 checkout `Resources/Audio/Animals/mouse_squeak.ogg`; upstream source attribution/license was not present in the inspected source metadata. Preserve this entry until provenance is confirmed.
- `src/main/resources/assets/moonstation14/sounds/ss14/items/eating_{1,2,3}.ogg` — imported from pinned SS14 checkout `Resources/Audio/Items/eating_{1,2,3}.ogg` (collection `Resources/Prototypes/SoundCollections/food.yml`, id `eating`); no attribution/license metadata for these files was found in the inspected upstream audio tree. Preserve until provenance is confirmed.

These unresolved files are also listed in the [media reuse and attribution policy](../project-reference/media-use-and-attribution.md), which requires recording every imported audio file with unresolved file-specific terms and its adjacent notice.
