# Portable networking item assets

Imported item icons are from the pinned SS14 checkout at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`:

- `Resources/Textures/Objects/Tools/network_configurator.rsi/icon.png` →
  `assets/moonstation14/textures/item/network_configurator.png`; RSI metadata
  attributes 20kdc, licensed CC-BY-SA-3.0. The dropped item model consumes a
  separately row-major-repacked vertical animation strip, with
  two Minecraft ticks per frame at 20 Hz (the upstream icon state is 32×32,
  32 frames, each delayed 0.1s). The source RSI's `mode-link`/`mode-list` states remain separate
  32×32 overlays; the generated item model selects the corresponding overlay
  from the stack's synchronized configurator mode property. The untouched import
  is retained as source/provenance.
- `Resources/Textures/Objects/Devices/signaller.rsi/signaller.png` and
  `advanced.png` → matching remote-signaller item textures; RSI metadata
  attributes the signaller state to /vg/station commit
  `2c980a1f423f26e990a578bae057d1eca19675ec`, with in-hands/advanced art by
  Flaregy for SS14; licensed CC-BY-SA-3.0.

No audio was imported. Upstream composition was verified against `tools.yml`
and `signaller.yml`: configurator is a BaseItem with Link mode as its default;
remote signaller inherits BaseItem and sends Pressed with use-delay composition,
BasicDevice/1280 wireless networking and range 15; advanced inherits it and
overrides range to 50. Those gameplay compositions remain core-registry data,
not hardcoded into these item definitions.
