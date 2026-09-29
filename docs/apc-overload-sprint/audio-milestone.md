# APC breaker sound

Server-owned `PowerDeviceBlockEntity` emits `apc/switch` once per accepted manual
breaker change and once per timed actual-output overload trip. Playback is at
the APC position with `SoundSource.BLOCKS`, volume 0.7943282 (about -2 dB),
pitch 1, with no excluded player. Rejected actions, NBT loading, energy-only
solves, and repeated observations of an already-open breaker do not emit it.
The sound event has an English subtitle; clients do not predict playback or
play a duplicate on the opening button. The same `machine_switch.ogg` clip for
manual switch and timed trip follows the SS14 breaker-switch precedent; this
does not implement low-power flicker or a separate low-power sound.
In the local SS14 checkout, `Content.Server/Power/Components/ApcComponent.cs`
defaults `OnReceiveMessageSound` to `/Audio/Machines/machine_switch.ogg`, and
`Content.Server/Power/EntitySystems/ApcSystem.cs` calls `ApcToggleBreaker` both
for a manual action and on overload trip; that method plays the switch sound.

The unchanged 7,307-byte source is SS14
`Resources/Audio/Machines/machine_switch.ogg`, pinned at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`, copied to
`src/main/resources/assets/moonstation14/sounds/apc/machine_switch.ogg`.
Source and destination SHA-256:
`e39e881d52afc399f50ac4ed9b79084736a3025cf10cde51460f106a2bace55d`.
**File-specific license unresolved**: neither the upstream README's general
"most assets CC-BY-SA" guidance nor the missing entry in
`Resources/Audio/Machines/attributions.yml` establishes this file's license.
It is not marked MIT. See the [adjacent notice](../../src/main/resources/assets/moonstation14/sounds/apc/machine_switch.ogg.license.txt)
and [unresolved-audio register](../audio-unknown-provenance.md); seek rights
evidence and remove/replace the asset if distribution terms cannot be resolved.

Coordinator-reported independent verification passed:

```powershell
.\gradlew.bat compileJava compileGameTestJava test --no-daemon
.\gradlew.bat runGameTestServer -Pms14GameTestDir=build/apc-audio-verified-world --no-daemon
```

The isolated server run passed **168/168 required GameTests**. Prior full runs
failed varying unrelated cable/atmosphere/wired GameTests; do not infer a
deterministically passing suite. No manual client playback/listening test has
occurred, and owner acceptance remains pending.
