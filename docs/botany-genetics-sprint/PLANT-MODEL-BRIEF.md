# Full botany grown-plant model request

## Scope and reuse

Art-only extension for the full pinned SS14 seed catalog: **71 seed identities / 70 grown-plant RSI families**. Preserve existing wheat, tomato, potato, poppy, aloe models, their complete source/license dependencies, and the corrected tray/status slots. **65 new visual families** are commissioned below. Fake/real capfruit intentionally share `capfruit` art; their gameplay identities remain distinct. Do not copy or regenerate the five existing families.

This request covers tray-attached grown plants only. Seed packets and loose/held produce currently use SS14 sprites; their additional 3D requirements and special-output reuse are being audited separately. No mobs, firearm mechanics, tools, machines, vines, collisions, or gameplay are commissioned by this art request. Include grown killer-tomato art; that does not imply its mob product is implemented or commissioned here.

## Exact source and state inventory

Reference checkout: `C:/Users/William/Documents/SS14-dev/space-station-14`; pin `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- Seeds: `Resources/Prototypes/Hydroponics/seeds.yml`; default stage count is 6 in `Content.Server/Botany/SeedPrototype.cs`.
- Each RSI below is under `Resources/Textures/Objects/Specific/Hydroponics/`; its `meta.json` and PNGs are the exact state/frame/direction/delay/attribution sources.
- For **each family**, deliver `stage-1` through the specified stage count, `harvest`, and `dead`. No empty plant model. All referenced required states were verified present. Do not treat item `produce`, `seed`, in-hand, slice, or other states as growth stages.

| Stable visual key / seed | RSI basename | Growth stages |
|---|---|---:|
| meatwheat | meatwheat.rsi | 6 |
| oat | oat.rsi | 6 |
| banana | banana.rsi | 6 |
| mimana | mimana.rsi | 6 |
| carrots | carrot.rsi | 3 |
| laughinPea | laughin_pea.rsi | 3 |
| lemon | lemon.rsi | 6 |
| lemoon | lemoon.rsi | 6 |
| lime | lime.rsi | 6 |
| orange | orange.rsi | 6 |
| extradimensionalOrange | extradimensional_orange.rsi | 6 |
| pineapple | pineapple.rsi | 3 |
| sugarcane | sugarcane.rsi | 3 |
| teaPlant | tea_plant.rsi | 5 |
| papercane | papercane.rsi | 3 |
| towercap | towercap.rsi | 3 |
| steelcap | steelcap.rsi | 3 |
| blueTomato | blue_tomato.rsi | 6 |
| bloodTomato | blood_tomato.rsi | 6 |
| killerTomato | tomatokiller.rsi | 2 |
| eggplant | eggplant.rsi | 3 |
| cabbage | cabbage.rsi | 1 |
| garlic | garlic.rsi | 3 |
| apple | apple.rsi | 6 |
| goldenApple | golden_apple.rsi | 6 |
| corn | corn.rsi | 3 |
| onion | onion.rsi | 3 |
| onionred | onion_red.rsi | 3 |
| chanterelle | chanterelle.rsi | 3 |
| eggy | eggy.rsi | 6 |
| cannabis | cannabis.rsi | 3 |
| rainbowCannabis | rainbow_cannabis.rsi | 3 |
| tobacco | tobacco.rsi | 3 |
| nettle | nettle.rsi | 5 |
| deathNettle | death_nettle.rsi | 5 |
| chili | chili.rsi | 6 |
| chilly | chilly.rsi | 6 |
| lily | lily.rsi | 3 |
| lingzhi | lingzhi.rsi | 3 |
| ambrosiaVulgaris | ambrosia_vulgaris.rsi | 6 |
| ambrosiaDeus | ambrosia_deus.rsi | 6 |
| galaxythistle | galaxythistle.rsi | 3 |
| glasstle | glasstle.rsi | 3 |
| flyAmanita | fly_amanita.rsi | 2 |
| gatfruit | gatfruit.rsi | 2 |
| capfruit (fakeCapfruit, realCapfruit aliases) | capfruit.rsi | 2 |
| rice | rice.rsi | 4 |
| soybeans | soybeans.rsi | 4 |
| spacemansTrumpet | spacemans_trumpet.rsi | 4 |
| koibean | koibean.rsi | 4 |
| grape | grape.rsi | 2 |
| watermelon | watermelon.rsi | 6 |
| holymelon | holymelon.rsi | 6 |
| cocoa | cocoa.rsi | 6 |
| berries | berries.rsi | 6 |
| bungo | bungo.rsi | 4 |
| pea | pea.rsi | 3 |
| worldPea | world_pea.rsi | 3 |
| pumpkin | pumpkin.rsi | 3 |
| bluePumpkin | blue_pumpkin.rsi | 3 |
| cotton | cotton.rsi | 3 |
| pyrotton | pyrotton.rsi | 3 |
| cherry | cherry.rsi | 6 |
| anomalyBerry | anomaly_berry.rsi | 2 |
| bloonion | bloonion.rsi | 4 |

Preserve native optical animation where a commissioned plant state has it: rainbow cannabis stages/harvest (22 metadata delay entries) and ambrosia Deus harvest (four frames). Read and retain exact metadata delays; do not invent growth timers or mechanical movement. Extradimensional orange/holymelon item-produce animation is not a tray-growth state, so record it as item-scope rather than adding invented plant motion.

## Binding and export

Reuse the original botany native Java contract: 16 units per block; root `(8,5,8)` already block-local; renderer translation `(0,0,0)` and scale 1. Draw one preserved tray plus zero/one whole plant. Precedence: dead → harvestable → authoritative stage. Normal world lighting, cutout, no `cullface` on plant faces, legal Java element rotations. Prefer existing X/Z `1..15`, Y `5..32` envelope; surface materially necessary size exceptions at the early design gate, not silently clipped plants. No new runtime library, shader, model timer, or collider.

Use lowercase Minecraft resource paths with an explicit source seed-ID→stable visual key→resource mapping; preserve upstream identities/case in metadata. Runtime destination family: `src/main/resources/assets/moonstation14/models/block/botany/plants/<resource-key>/<state>.json` and matching textures. Supply a versioned plant-state map compatible with existing `formatVersion: 1`, explicit aliases, complete flat file/destination manifest, all source/license dependencies, and no overwrites of accepted five-family/tray assets. Texture animation may use native PNG strips/`.mcmeta` with source-authentic cadence.

## Source fidelity and deliverables

Before batch production, deliver original-first gameplay-scale contacts for representative tree/cap, mushroom, flower, vine/fruit, and animated families alongside the preserved five. Preserve source silhouette, palette, distinctive anatomy, growth progression, and recognizable harvest/dead states. Do not substitute generic bushes, boxes, recolours, or pixel-voxel extrusion merely to cover IDs. Record conservative 3D depth/shape adaptations and any uncertain interpretation.

Deliver editable embedded-texture `.bbmodel` masters; native JSON/PNG/.mcmeta exports; precise logical-state/alias map; source snapshots and hashes; per-RSI attribution/license; original-first multi-view/state/animation previews; saved-file/state/bounds/UV/native-frame validation and read-only verifier. All listed RSI art is CC-BY-SA-3.0 except bloonion (CC0-1.0); retain each `meta.json` copyright verbatim and do not relicense derived art through tool licenses. Keep frozen originals, prior packages, guards, and registries untouched.

Delivery and automated asset checks do not establish visual acceptance or runtime installation. Independent visual review and explicit owner acceptance precede import; live renderer state/lighting/pointer/performance validation remains the gameplay integration owner's responsibility.
