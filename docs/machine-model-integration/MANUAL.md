# Model client retest

Use the rebuilt JAR and a newly built starter station/fixtures.

1. Check all four machine models and rotations for missing textures or bad
   lighting. Place APCs on each wall direction; floor placement must fail.
   Open the APC UI by aiming at the panel. Removing its backing must remove it.
2. Verify the station/fixture power chain still powers its consumers. Toggle
   the APC breaker and cut/reconnect upstream power; check battery backup and
   Full/Charging/Lack display changes. These displays do not directly mirror
   breaker on/off. SMES remains a fixed HV source, not a battery.
   In darkness, verify the powered screen glows without lighting the whole
   casing and gives a small nearby light. Empty battery/no input must go dark.
3. Power a ChemMaster, insert/eject a beaker, and remove/restore power. Check
   both loaded and empty models; powered screens must animate in either case,
   and unpowered screens must stop showing the powered appearance.
4. Leave the ChemMaster UI open, eject and reinsert a beaker, then transfer a
   reagent or change Sort. The UI must remain usable, not stuck pending.

Report missing models/textures, incorrect facing, failed targeting or frozen
animations with a screenshot and client log. Aggregate native failures are
listed in `VALIDATION.md`; visual approval is still pending.
