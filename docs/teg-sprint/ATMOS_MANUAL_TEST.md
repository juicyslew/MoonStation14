# Atmos machinery — operator checks

R20 passed the complete technical gate; owner testing remains pending. Use a fresh disposable Overworld with a clear, loaded 64×64 area.

1. Run `/ms14 atmos_fixture build`. At a labelled bay, join its final two sign lines (`start` + literal) under `/ms14 atmos_fixture`, e.g. `/ms14 atmos_fixture start mixer`. Tab lists the nine choices. Bare `start` only shows help.
2. Watch that demonstration before starting another. Mixer/filter use visible plasma and water vapor. Heater/freezer/HX signs and indicator lamps update automatically from measured thermal changes, not merely enablement.
3. With an empty active hand, interact normally: type values and press Enter, search the filter picker, toggle Enabled, and drag windows. Inspect all three manifold pipe layers through the steel walls.
4. In the auxiliary bay follow the labelled flow: finite source room → scrubber → pipe → vent → recipient room. The scrubber captures its selected gas into finite pipe inventory; the vent must transfer that inventory into the recipient. Read status/receipt signs for observed capture, source decrease, moved moles and recipient increase. Start it with `/ms14 atmos_fixture start all` (or `/ms14 atmos_fixture start all <x y z>`); this explicitly starts remaining demonstrations plus auxiliary ventilation/miners, and never resets or refills used stock.
5. The manifold is one common mixture shared by all six 50 L ports (300 L total): three fixed Primary/Secondary/Tertiary lanes join it, not three isolated pairs. Its sign and `status` report actual Plasma/N₂ composition by connected port/lane; observe transfer before expecting a mixed result. Build initializes these ports as vacuum; finite rooms are initialized at selected start, so composition is not pre-mixed at build.
6. Repeat a selected start: it must refuse without refilling. Previously launched demonstrations may continue.

`/ms14 atmos_fixture status` is optional. Use a fresh lab for clean comparisons. Report client rendering/input/audio issues separately; TEG steps are in [MANUAL.md](MANUAL.md).
