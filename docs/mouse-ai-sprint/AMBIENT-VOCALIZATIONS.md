# Ambient NPC vocalizations

The pinned SS14 checkout (`c9df5ef5d675b0d1d226828bddf6b78c28502d91`)
composes `MobMouse` from `SimpleMobBase` and `BaseMobAnimal`, and explicitly
adds `Speech` with `speechSounds: Squeak`, `speechVerb: SmallMob`, and the
`Squeak` emote (`Resources/Prototypes/Entities/Mobs/NPCs/animals.yml`,
`MobMouse`). Shared speech consumes the speech sound collection when speech is
actually emitted. It does not schedule periodic idle vocalizations; this feature
is a deliberate Minecraft ambience adaptation, not upstream parity.

The local resolved mouse composition adds `AmbientVocalization` alongside its
existing `Speech` cue. Its runtime consumer only acts on alive, movement-driven,
unclaimed NPC mobs, and broadcasts the existing `mouse_squeak` sound spatially
from the server. It does not fabricate a chat message or alter player speech.
The randomized 160–400 tick interval and 0.4 volume are local defaults; initial
timers are randomized to avoid synchronized sounds using the NPC's entity RNG.
`NpcAmbientVocalizationGameTests` observes real `PlayLevelSoundEvent`s for a
normally spawned mouse, checks source/volume/pitch and interval, verifies no
next-tick duplicate, checks movement-owned, Critical, Dead, and uncomposed mob
suppression, and exercises unload/rejoin timer cleanup using the normal entity
leave/join lifecycle with a stable UUID.
These native fixtures are added but were not run by the implementation worker.
No new audio asset is added.
