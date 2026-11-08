# Medical treatment sound registry correction

Medical sound keys in `assets/moonstation14/sounds.json` map registered native event IDs to audio samples; defining those keys alone does not register `SoundEvent`s. Treatment's server playback admission checks `BuiltInRegistries.SOUND_EVENT` and previously returned before delivery because the medical events were absent from `ModSounds`. Registering the canonical events fixes that registry gate. The focused GameTest checks sound IDs from the resolved medical item catalog, including inherited/default compositions, rather than only checking resource JSON.

Existing SS14-derived audio assets are reused; no new audio or provenance claim is introduced. Gauze intentionally shares the brutepack begin/end sounds as specified by its resolved policy. This server-side registry test proves admission, not audible client playback/delivery.

## Clinical effects and diagnosis

Gauze's production treatment-effects consumer applies its resolved slash/piercing damage deltas and bleed-rate modifier independently. It does not heal blunt damage or the `DamageKeys.BLOODLOSS` ledger; the latter is distinct from `BloodComponent.bleedRate` and may be changed later by bloodstream physiology. `/ms14damagecheck` reports the controlled body's initialized bleed rate and blood-loss ledger separately. These snapshots help distinguish active bleeding from accumulated blood-loss damage; they do not establish why bleeding began or prove a completed authenticated dose occurred.
