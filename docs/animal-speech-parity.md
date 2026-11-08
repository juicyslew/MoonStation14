# Animal speech mapping

Source mapping is pinned to SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`Content.Server/Chat/Systems/ChatSystem.cs`'s IC path permits speech without a `Speech`
component (`Content.Shared/Chat/SharedChatSystem.cs` supplies a default verb);
`Content.Shared/Speech/SpeechComponent.cs`
documents that the component is optional. `Content.Server/Speech/AccentSystem.cs`
and `ReplacementAccentSystem.cs` process accents independently of that component.
Local speech is a typed aggregate capability used by the existing local IC
admission/transform/audio route. `supported_nonhostile_animal.json` is therefore
the narrow aggregate fallback for the new supported-animal family; it does not
alter legacy `supported_npc`, `supported_animal_core`, mouse, sheep, or native
pig compositions. `snail` is non-breathing and receives the empty Speech
aggregate directly without inheriting respiratory composition.

Explicit source voice collections are preserved: Squeak (bat, hamster,
mothroach descendants), Cluck (chicken), Goat (goat), Monkey (monkey), Parrot
(parrot), Lizard (kobold), Arachnid (crab and giant spider), Alto (Diona nymph),
and Wawa (scurret). Local accent-only profiles intentionally have no speech
cue: duck and kangaroo retain text replacement only. The supported-animal
fallback grants neither a voice nor accent. Replacement accents for these
compositions are limited to source IDs and source replacement strings; lizard
regex behavior follows pinned `LizardAccentSystem.cs`.

Speech emotes, Vocal gender collections, BodyEmotes, and SS14 speech verbs are
not represented by the local aggregate and are not claimed as ported. Existing
emote support remains limited to the registered local emote allowlist.

Audio samples in `assets/moonstation14/sounds/animals/` are copied unmodified;
their source filenames, licenses, and credits are in that directory's
`ATTRIBUTION.md`. Goonstation-derived voice files retain the non-commercial
CC-BY-NC-SA-3.0 restriction.
