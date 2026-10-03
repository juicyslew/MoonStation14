# Body inventory interaction audit

Status: **Control routing repairs built and independently reviewed; not blanket item usability acceptance.**

## Controls and authority

The pinned SS14 reference is `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`Resources/keybinds.yml` distinguishes left-click Use, E world activation,
Z held activation, Q drop, Ctrl+Q throw, and X hand selection. The accepted
local inventory/cursor gestures remain unchanged. Shift+B equips/stores to
the backpack; it is not the retired B installation shortcut.

Grenades inherit `TriggerOnUse` through `TimerGrenadeBase`: primary Use
primes them, not Z. Held activation and throwing are separate intents.
Six-face structure installation is an existing Minecraft adaptation.

Body actions nominate intent and targets. Server admission verifies the
controlled body, epoch, active hand, revision/token, and body-eye hit.
Tool stack mutations preserve the exact body owner; no carrier inventory
insertion, synthetic player, or carrier-camera interaction is substituted.
APC menus retain exact-menu authority and fresh body target access.

## Routed existing behavior

| Family | Intended body route |
| --- | --- |
| Three functional pipe lanes; vent, scrubber, alarm supplies | Left-click installation, existing geometry/consumption rules |
| Air alarm and APC menus | E activation; never install the held supply on E |
| Grenade | Left-click prime; Ctrl+Q specialized armed-grenade throw |
| HV/MV/APC cable spools and cutter | Left-click place/cut; cut spool returned to the world |
| Steel and crowbar | Left-click existing girder/wall transitions |
| Both station floor tiles and pry tool | Left-click existing finish placement/removal |
| Ordinary registered project BlockItems | Left-click body-oriented placement and exact-hand consumption |
| Atmosphere analyzer | Primary Use samples body eye or validated world hit |
| Bag, belt, materials | Existing body inventory/storage/equipment/drop routes |

The live ventilation dispatch is in `BodyHandActionNetworking`, **before**
`BodyWorldItemActions`; inspecting only the latter misses the installers.
Ordinary block placement does not imply every block callback is supported.

## Known unsupported behavior

- Generic ordinary-item throwing and throwing unarmed grenades. SS14 holder
  defaults are speed 11, range 8, cooldown 0.5 seconds; these are not the
  local grenade projectile settings. Do not substitute those projectile
  settings or invent a per-item Throwable marker. SS14 stacks throw whole
  unless `ThrowIndividually` applies.
- Pickaxe body mining/attack; waffle body eating.
- Bottle/jug body reagent use, drinking, and jug placement. The withdrawn
  bare placed-jug sip is not an approved action; see the character capability
  audit. Ingestion needs an actual item, timing, and body capability contract.
- T-ray scanner activation/reveal.
- Magic-block activation sound and atmosphere-test-block activation
  diagnostics. Placement and automatic block behavior are distinct.
- Cosmetic gas-pipe visual items remain Creative appearance tools, not
  functional finite gas lanes.

Generators, substations, and lamps have no existing activation UI to forward.
Unsupported Z activation remains denied rather than inventing an action.

## Validation

Independent source reviews cover body stack ownership, input routing,
analyzer sampling, APC authority, and construction failure paths. Follow-up
repairs address dispatch omissions, exact rollback classification, and
air-alarm/grenade primary routing. `build compileGameTestJava` passed with
2,060 JUnit tests, zero failures/errors/skips; evidence:
`build/item-controls-and-template-final-build.log`. Final source review passed for
the repaired activation/priming paths and compensation classification.
`git diff --check` passed; no commit was made.

A combined headless suite initially stalled. The reproduced cause was two
malformed power-test templates: integer-array size tags were loaded as zero
dimensions, producing a chunk-readiness wait before test timeouts could
start. See `docs/gametest-template-stall.md`. The corrected template encoding
is independently reviewed and guarded by an actual-resource regression test.

An ordinary fresh-world run passed all 479 GameTests in 16.65 seconds
(`build/gametest-template-size-fixed-standard.log`). The old origin/order
replay completed all 479 in 16.92 seconds, but reported one
`poweredscrubbercapturesonlyselectedgas` ventilation-solve assertion failure
(`build/gametest-template-size-fixed-replay.log`). It is not a suite pass;
that ordering/origin-sensitive assertion remains unresolved. Temporary
diagnostic/replay mixins were removed. The final fresh run without them
passed all 479 GameTests in 16.98 seconds (40 seconds overall):
`build/gametest-template-final-clean.log`.
Dedicated spawn/callback failure
injection and connected client usability remain unverified; policy tests and
source review do not establish end-to-end mouse/menu behavior.

### Short client check

1. Control a character and put each tested supply/tool in its active body hand.
2. Left-click to install/use; E opens alarm/APC menus even while holding a
   supply. B/N/M must not perform their old feature actions.
3. Left-click a grenade while aiming at a block and at empty space; Ctrl+Q
   throws the armed grenade without also dropping another item. Z must not
   prime it.
4. Click/drag in inventory and storage windows: no world placement or attack.
5. Check hand costs, returned world materials, and body-collision rejection.
