# Recharger source art

Source: pinned SS14 checkout `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
`Resources/Textures/Structures/Power/recharger.rsi`. Original 32×32 source PNGs
are copied unchanged to `assets/moonstation14/textures/ss14/power/recharger.rsi/`.
The copied RSI metadata retains its attribution and CC-BY-SA-3.0 declaration:
`https://github.com/discordia-space/CEV-Eris/raw/9ea3eccbe22e18d24653949067f3d7dd12194ea9/icons/obj/stationobjs.dmi`,
in-hands by spanky-spanky (GitHub). Minecraft model geometry is a deliberate small
tabletop adaptation; sprite pixels are unmodified. Only empty/full/light-off/
light-empty/light-charging/light-charged sprites are imported (not in-hand states).
Minecraft `.png.mcmeta` frame timing reproduces the RSI 32×32 animations without
modifying or repacking upstream PNG bytes.

SHA-256 (runtime copies):

| File | SHA-256 |
|---|---|
| empty.png | `869316efc6948345733cc93fdd80b5ab8fe36130a9af3cd50d4242dce8bbd33f` |
| full.png | `869316efc6948345733cc93fdd80b5ab8fe36130a9af3cd50d4242dce8bbd33f` |
| light-charged.png | `b84250e802a2d90eb9920e02cca11dddbf1b725ac31e2c9a67df0e24ba483fa3` |
| light-charging.png | `ebab39717eb1f15fdbfc2898368665a2efb4f8b01f765852eec881d2485daaab` |
| light-empty.png | `24a8076c22c139e402a64c5603922ad5a2c65512b0bb039152c0015d56fd72e4` |
| light-off.png | `ba9b58054872944cdc32a387fc18ab5e00eca9957fd38de721d14960afd8d9d5` |
| meta.json (six-state runtime subset) | `20d66d2f6203c48bf2185f646982785cf25cbc1046be22bed686427e17921ef7` |
