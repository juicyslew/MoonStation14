# Machine models and APC lighting

## STATUS

Owner explicitly closed the sprint on 2026-10-10 after the final APC brightness
increase. Existing ChemMaster, HV source, HV→MV substation and APC now use the
supplied models. APC is physically wall-mounted. Final resolved ambient light
is level **5**. No commits/staging were performed. Aggregate native failures
remain open; completion is not a full-suite-green or observed GPU claim.

## ARCHITECTURE

Models and animated PNG/`.mcmeta` textures use Minecraft/NeoForge's native
resource pipeline; no new rendering dependency or custom screen renderer.
ChemMaster projects POWERED from the existing full-fresh receiver predicate and
LOADED from its committed input slot, independent of viewers. Its combined
loaded/powered model retains the loaded mesh and animated display face.

Typed inherited `PowerAppearanceData` profiles, loaded by `PrototypeRuntime`,
are consumed by `PowerRuntime` and APC shape/light projection. Display, GLOW
and emission are derived views, not electrical authority. Updates preserve
facing, device identity, battery energy and graph ports.

`ApcPowerDeviceBlock` extends the shared power block and alone adds integer
EMISSION. Runtime projects the resolved profile level into this state; normal
`Properties.lightLevel` consumes it. Never return emission only by consulting a
block entity: native chunk light-source discovery uses `EmptyBlockGetter`.
Base profiles emit 0; lamps retain 14. Charged battery or actual input enables
APC ambient light; opening the breaker does not extinguish a charged display.

Charging/Full screen overlays select by DISPLAY/FACING without a GLOW prerequisite;
Lack retains GLOW gating. Only screen faces are fullbright/unshaded, using
face-level `neoforge_data` with block/sky light 15 and AO false. Native baking
and the default renderer preserve packed light; no experimental lighting option
is required. Screen fullbright and nearby block light are separate mechanisms.

## IMPORTANT FILES

- `ms14/power/device/{PowerAppearanceData,ModPowerAppearances,PowerDeviceFixturePlacement}.java`
  under `src/main/java/com/juicyslew/moonstation14/`.
- `block/custom/{PowerDeviceBlock,ApcPowerDeviceBlock}.java`, `block/ModBlocks.java`,
  `ms14/power/runtime/PowerRuntime.java`, and `ms14/prototype/PrototypeRuntime.java`.
- `src/main/resources/data/moonstation14/moonstation14/power_appearance/`:
  inherited profiles; `apc.json` owns light level 5 and attachment shape.
- Native blockstates/models/textures under `assets/moonstation14/`; mixer
  resources are under `models/block/machines/mixer/` and corresponding textures.
- `ms14/chemistry/chemmaster/{ChemMasterBlock,ChemMasterBlockEntity,ChemMasterSessionService}.java`.
- `StarterStationPowerPlan` / `StarterStationService`: plan, preflight and
  verification of supported APC placement. Independent power, ventilation,
  ChemMaster, disposal, air-alarm and airlock fixtures use the shared mount helper.
- `PowerDeviceGameTests`, `PowerAppearanceGameTests`, `ApcScreenGlowResourceTest`,
  `PowerAppearancePrototypeTest`, `ChemMasterMixerResourceTest`,
  `ChemMasterSessionAckTest` and ChemMaster native acceptance tests.
- `VALIDATION.md`, `MANUAL.md`, `src/main/resources/assets/moonstation14/mixer-attribution.txt`
  and `assets/moonstation14/power-provenance/`.

## SS14 CORRESPONDENCE

Pinned checkout: `c9df5ef5d675b0d1d226828bddf6b78c28502d91` at
`C:/Users/William/Documents/SS14-dev/space-station-14`.
Prototypes: `Structures/Machines/chem_master.yml` and
`Structures/Power/{smes,substation,apc}.yml` under `Resources/Prototypes/Entities/`.
APC charge semantics: strictly >90% Full, otherwise net receiving greater than
supply Charging, otherwise Lack. Breaker state does not directly choose a screen.
Upstream charge screens are unshaded; PointLight radius 1.5/energy 3/color by
charge state is adapted to small monochrome Minecraft block light, now level 5.
Local fixed HV generation and stateless substation conversion remain deliberate
adaptations; importing SMES/substation art did not add battery mechanics.
Unsupported maintenance/damage/remote states were not invented to activate art.

## DECISIONS / WHY

APC FACING is the outward wall normal. Backing support, rotated thin collision/
outline and support-loss removal agree; electrical ports retain their existing
meaning. Fixtures create explicit supports without overwriting occupied backing
or cable records. Gameplay does not automatically spawn backing walls.

ChemMaster unsolicited snapshots now preserve each session's LAST_REQUEST ACK
watermark. Publishing -1 after a completed action restored pending after
reinsertion; do not undo this or mix viewer ACKs. No retry timeout or guard bypass
was introduced. Output functionality remains deferred; owner accepted leaving
the currently non-opening Output tab unchanged.

## KNOWN GAPS

GPU presentation is not established by headless checks. The isolated native
lighting test proves source scanning of an already-on APC, not actual chunk
unload/reload. Existing world APCs were not migrated or given auto-generated walls.
This handoff does not close the separate ChemMaster dump-capture investigation.

## VALIDATION

Final build/unit/GameTest compilation passed: **2,979 unit tests**, no failures,
errors or skips. Final filtered native **10/737 selected, 10 passed**. Isolated
opaque-room checks establish zero baseline, APC-only propagation, charged/open-
breaker light, native source discovery and extinction after drain; earlier lamp-
adjacent tests were insufficient. Native face baking verifies Full/Charging UV2.

Latest full run before the final cosmetic level change: **734/737 passed**.
Unresolved failures: `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`obstructionusesexactlytwodoorwaycellsandsafetydeniescloseactor`, and
`crushreleasecompletesnormalopenbeforefastautoclose`. Do not call them unrelated
or baseline without evidence. Reused fixture records can exhaust the 16-entry
ventilation fixture limit; clean only disposable test state when appropriate.

Logs: `capturebuild/validation-logs/apc-light-brightness-*.log` and
`apc-light-source-fix-*.log`. Final JAR `build/libs/moonstation14-0.0.1.jar`:
SHA-256 `0295543CB6B3D15AF07CD0456A0D755B7F67002CB4AED77E8601F05ED5C55622`.

## NEXT WORK

Classify aggregate native failures separately. Use `MANUAL.md` for any further
visual retest. For future appearance changes, start from resolved profiles,
native resource selection and isolated lighting tests—not nearby powered lamps
or resource-metadata assertions alone.
