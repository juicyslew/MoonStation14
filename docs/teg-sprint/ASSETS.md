# TEG and atmospherics resource import

Owner-feedback asset status: `power-teg-circulator-finish-20261026-r1` is delivered for art review, **not accepted or imported**. The local ten-profile/state correspondence and remaining boundaries are in [ASSET_RUNTIME_CONTRACT.md](ASSET_RUNTIME_CONTRACT.md). Gas-miner art is separately requested as `atmos-station-gas-miner-family-20261026-r1`; its runtime model remains a placeholder. Historical deferred-model notes below are not a claim that the new delivery is installed.

The native SS14 media below is copied byte-for-byte from the pinned
`space-station-14` checkout at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Every imported RSI retains its original `meta.json`; per-file source and
runtime SHA-256 values are recorded in
`src/main/resources/assets/moonstation14/power-reactor-provenance/teg/resource-import-manifest.json`.

## Imported RSI families

* `textures/ss14/structures/power/generation/teg.rsi` — `TegCenter` and
  `TegCirculator` (`Resources/Prototypes/Entities/Structures/Power/Generation/teg.yml`),
  states for center output/powered indicators and circulator movement.
  RSI metadata attributes it to Baystation12 `power.dmi`, modified to split
  light layers; CC-BY-SA-3.0.
* `textures/ss14/structures/piping/atmospherics/pump.rsi` and
  `pump_alt{1,2}.rsi` — pressure/volume pumps and layered-pipe variants;
  `pipe.rsi` supplies their connector layers; `binary.yml` and `alt_layers.yml`.
  Original metadata (including its individual tgstation/SS14 source credits) is
  retained; CC-BY-SA-3.0.
* `gasfilter.rsi` and `gasmixer.rsi` — filter/mixer and flipped variants;
  `trinary.yml`. Original metadata retained; CC-BY-SA-3.0.
* `pneumaticvalve.rsi` — valve layers in `trinary.yml`; original metadata
  retained; CC-BY-SA-4.0, Kevin Zheng 2022.
* `thermomachine.rsi` and `thermomachine_alt{1,2}.rsi` — heater/freezer
  layers and pipe-layer variations; `unary.yml` and `alt_layers.yml`.
  Original metadata retained; CC-BY-SA-3.0.
* `heatexchanger.rsi` — exchanger layers in `binary.yml`; original metadata
  retained; CC-BY-SA-3.0.

The two audio files are unchanged upstream files under
`sounds/ss14/atmospherics/`: `gas_pump.ogg` (CC0-1.0, “Taken from source”,
https://freesound.org/people/karinalarasart/sounds/441419/) and `gas_hiss.ogg`
(CC-BY-4.0, “Taken from source”,
https://freesound.org/people/geodylabs/sounds/122803/), as recorded in the
pinned `Resources/Audio/Ambience/Objects/attributions.yml`.
`vending_machine_hum.ogg` is imported unchanged for the TEG center; no per-file
author/license attribution was found in the pinned upstream metadata and its
unknown status is recorded in `docs/audio-unknown-provenance.md` and the import
manifest. The three canonical `valveSqueak` clips were already present and reused.

Native sprite consumption is now wired through derived server-owned `display`
and `pipe_layer` blockstates and native cutout models for all ten machines.
`tools/convert_atmos_rsi.py` produces 212 direction/state textures with original
pixels and timing; `tools/generate_atmos_models.py` binds the resolved visual
identities to block/item models. Derivative hashes and source references are in
`power-reactor-provenance/teg/atmos-derived-manifest.json` under the asset root.
Independent source/resource review and diagnostic `run_7c0a7662ac13473714788371`
passed: 207 focused JVM tests, 625 readiness tests and all 15 selected native
tests (15/1,350 discovered). Native checks exercise finite-state preservation,
early circulator motion, later powered-still state and LV-loss clearing. The
steady heater check admits three full allocation intervals in a separate batch;
it does not assume parallel fixture topology changes leave the solver cadence
steady. Connected-client/GPU presentation remains unverified. Resolved server
ambient profiles and authoritative eligibility payloads are wired for pumps,
mixer/filter, open valve and TEG output display. Client loops consume those
payloads using exact loaded-source identity and camera range. Diagnostic
`run_6c6bc56a2358c66e717e8246` passed 217 focused JVM tests, interaction readiness
(538 tests) and all 15 selected native checks, including real filter-flow payload
and accepted valve sound packets. Loop lifecycle is covered through the actual
playback owner. Cue-range attenuation is a Minecraft adaptation: native
attenuation is disabled and resolved-range linear falloff is applied once.
Connected-client rendering and heard audio remain unverified. The accepted R6 TEG 3D
model family is deferred and is not included here; its separate import remains
pending acceptance/dependency verification. No model or audio is archived in
this documentation directory.

## Verified consumer requirements and remaining boundaries

Pinned binary/trinary/unary prototypes and their appearance systems distinguish enabled settings from powered/flowing visuals. Pump ambience is transfer-gated; mixer/filter hiss is transfer-gated; manual-valve hiss follows open state. Heater/freezer, passive exchanger and circulator do not have an established device-specific ambient clip.

The manual valve's activation sound is **`valveSqueak`**, defined in upstream `Resources/Prototypes/SoundCollections/atmospherics.yml` (three squeak clips, pitch variation 0.25), not either imported ambience clip. TEG center ambience is **`vending_machine_hum.ogg`**, gated by generation display level. Both now have canonical resource references and live consumers; unresolved per-file attribution remains explicitly recorded.

Manual valve sprites use `pump.rsi:pumpManualValve{,On}`; the imported pneumatic-valve RSI is not evidence of that runtime mapping. TEG center uses `teg`, powered `teg_mid`, and generation overlays `teg-op1` through `teg-op11`; circulators use `circ-0/1/2` and matching light layers. Conversion follows verified Robust RSI frame packing and direction order South/North/East/West. The interim native casing and sprite panels preserve the SS14 artwork; they are not the deferred custom 3D model. Native baked-model/PNG animation conventions are indexed in `docs/machine-model-integration/HANDOFF.md`.

Vent and scrubber ambience uses `sounds/ss14/atmospherics/gas_vent.ogg`, copied unchanged from pinned upstream `Resources/Audio/Ambience/Objects/gas_vent.ogg`. Upstream `attributions.yml` records CC0-1.0, “Taken from source”, https://freesound.org/people/kyles/sounds/453642/.
