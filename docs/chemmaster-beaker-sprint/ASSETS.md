# Beaker and ChemMaster assets

The standard and large beaker sprite sheets are copied from the pinned SS14
checkout (`c9df5ef5d675b0d1d226828bddf6b78c28502d91`),
`Resources/Textures/Objects/Specific/Chemistry/beaker.rsi` and
`beaker_large.rsi`. SS14 assets are distributed under the repository's
CC-BY-SA-3.0 license; retain attribution and that license when redistributing.
Minecraft item models use the original base sprite as untinted layer0 and the
original transparent fill states as layer1 (tint index 1), with six tier
overrides for each beaker size. Existing RSI metadata and attribution remain
unchanged.

ChemMaster uses a placeholder block model; final modeling remains owner-supplied.
Its click event reuses the existing APC `machine_switch.ogg` sample (identical to
the pinned upstream file). Slot insert/eject use the pinned revolver magazine
samples. Adjacent audio notices and `docs/audio-unknown-provenance.md` retain
unresolved per-file provenance; importing these files does not establish licensing
clearance. Existing attribution records are preserved.
