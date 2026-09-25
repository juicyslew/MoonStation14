# Slip audio provenance and temporary feedback

## Upstream behavior

The SS14 comparison is pinned to
[`c9df5ef5d675b0d1d226828bddf6b78c28502d91`](https://github.com/space-wizards/space-station-14/tree/c9df5ef5d675b0d1d226828bddf6b78c28502d91).
At that revision, `Content.Shared/Slippery/SlipperyComponent.cs` defaults
`SlipSound` to `/Audio/Effects/slip.ogg`, and
`Content.Shared/Slippery/SlipperySystem.cs` plays that sound for an admitted
slip. The pinned `Resources/Audio/Effects/attributions.yml` does not identify
`slip.ogg`. Its introduction in commit
[`626c8c51`](https://github.com/space-wizards/space-station-14/commit/626c8c51)
does not provide file-specific author, source, or license information.

Accordingly, this project does **not** copy/import `slip.ogg` and makes no
license claim about it. The source audio is not the canonical sound here.

## Current substitute

`SlipSystem` temporarily plays the existing vanilla Minecraft
`SoundEvents.SLIME_BLOCK_FALL` at volume `0.35` and pitch `1.0`. This is
explicitly **temporary, noncanonical feedback**, not an exact equivalent of
SS14's `slip.ogg`. It uses the game's existing sound event; no sound asset or
sound definition is added.

The sound is emitted by the authoritative server once after a `SlipEvent` has
been admitted and listeners have been notified. Failed contact checks, rejected
attempt gates, stun-only paths, and repeated ticks without a newly admitted
event do not play it. Distinct admitted slips (including multiple sources for
a super-slippery contact) each produce one sound emission. The server broadcast
avoids a separate client-side playback path.

## Authentic-audio follow-up

An authentic upstream sound may only be considered after obtaining verifiable
file-specific provenance and permission to use it, then recording its source,
author, exact license, modifications, and any other required attribution in the
[`third-party-emote-audio.md`](../../third-party-emote-audio.md)-style
third-party audio documentation. Until that evidence exists, retain the vanilla
substitute and do not import `slip.ogg`.
