# Machine model integration validation

## Implemented state

Existing ChemMaster, HV source, HV→MV substation and APC IDs now use supplied
native model/texture assets with attribution retained. ChemMaster projects fresh
full power and committed input occupancy independently; its powered-loaded model
preserves the loaded mesh and uses the animated screen atlas on its display face.
All 16 facing/power/occupancy combinations have resource coverage.

Power appearance uses typed inherited profiles (`PowerAppearanceData`,
`ModPowerAppearances`, `PrototypeRuntime`) consumed by `PowerRuntime` and APC
shape/attachment logic in `PowerDeviceBlock`. Graphics do not grant power or
change device facing, identity, battery ownership or ports. APC charge visuals
use the strict >90% Full rule and actual charging; breaker state does not directly
choose an off texture. Source/substation art remains an explicitly documented
adaptation of existing fixed generation/stateless conversion, not new storage.

APC is a thin, directional wall-mounted block with support checks/removal.
`PowerDeviceFixturePlacement` centralizes explicit fixture mounting; starter
station plans/preflight/verification and independent power, ventilation,
ChemMaster, disposal, air-alarm and airlock placements were audited/updated.
Body-origin targeting fixtures aim at the actual panel; spoof guards remain.
Live power tests await bounded known topology and still test battery-backed
operation after upstream loss without granting replacement power.

Independent source reviews passed for combined mixer appearance, power model
bindings, wall mounting/fixture coverage and the targeted regression corrections.

## Current evidence

### Final owner brightness adjustment

The resolved APC profile now emits **level 5**, raised from 3. Native state
emission consumes that value; base profiles remain 0 and lamps remain 14.
Screen fullbright and power/breaker/display predicates are unchanged.

- Clean unit/build/GameTest compilation: **2,979 passed**, zero failures/errors/skips.
- Filtered native: **10/737 selected, 10 passed**.
- Full native was not rerun for this cosmetic profile-value adjustment. The
  latest full evidence remains **734/737**, with the three unresolved failures
  listed under the source correction below.
- Logs: `capturebuild/validation-logs/apc-light-brightness-unit-build.log` and
  `apc-light-brightness-native-focused.log` in the same directory.
- Final JAR SHA-256:
  `0295543CB6B3D15AF07CD0456A0D755B7F67002CB4AED77E8601F05ED5C55622`.

Owner accepted this feature slice subject to this brightness increase. Automated
checks do not constitute a GPU visual claim; aggregate native failures remain open.

### APC glow / ChemMaster acknowledgement follow-up (latest build)

APC powered-screen overlays use the verified NeoForge 21.1.224 face-level
`neoforge_data` lightmap/AO fields, only on the screen. A separate derived GLOW
state follows actual battery energy or incoming power. The resolved profile
provides minor monochrome block light (APC level 5, inherited base default 0).
Screen/casing resource separation and ambient-light projection passed source
review. Native coverage verifies nearby block light, charged/open-breaker glow,
and extinction after zero energy/no input through actual solves/light updates.

ChemMaster snapshot ACKs preserve each session's existing LAST_REQUEST watermark.
Unsolicited refresh/reinsertion no longer publishes -1 over a completed ACK,
preventing the permanent-pending regression. Tests exercise production ACK policy and
workspace pending/stale-frame/next-request behavior without weakening guards.

- Clean unit/build/GameTest compilation: **2,976 passed, zero failures/errors/skips**
  (unit XML: `build/test-results/test/`).
- Filtered APC/ChemMaster: **10/736 selected, 10 passed**. After strengthening
  the native darkness/open-breaker checks, GameTest compilation and this same
  focused run passed again; production code/JAR were unchanged.
- Full native before the test-only extension: **736 selected, 733 passed**.
  Failures: `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `liveapcmeterdrivesscheduledtwophasedoorandautoclose`,
  `logpulsedisableslampandrestoresatresolveddeadline`. They remain unresolved;
  no unrelated/baseline claim is made. An earlier broader filter also reported
  a beaker-drinking failure; it is not proven unrelated.
- Logs: `capturebuild/validation-logs/apc-glow-chemmaster-ack-native-focused.log`
  and `apc-glow-chemmaster-ack-native-full.log` in that directory.
- Previous JAR SHA-256:
  `3102175113E299A586980A996916BBEF7A03846DB95CE47EE74D0369FE229D5B`.

### APC native block-light source correction

The earlier nearby-light GameTest was masked by an unrelated level-14 lamp and
did not prove APC-only illumination. Native chunk source discovery queries state
emission with `EmptyBlockGetter`; the former APC override needed a block entity
and catalog lookup and returned zero during that scan. Emission is now projected
into an APC-only integer blockstate property from the resolved profile's
`lightLevel`, consumed by normal `Properties.lightLevel`. PowerRuntime projects
it with GLOW from actual energy/input and updates only on change. Lamp level 14
and non-APC state composition remain unchanged.

The isolated native fixture builds an opaque room away from the GameTest beacon,
establishes a zero-light baseline, then verifies charged/open-breaker emission,
context-free state emission, native `ChunkAccess.findBlockLightSources`
discovery without a GLOW transition, adjacent propagation, and extinction after
drain. It does not unload/reload the GameTest chunk; the source scan validates
the discovery prerequisite without claiming a reload test. Charging/Full
overlays depend only on DISPLAY/FACING; Lack retains GLOW gating. Native
face-baking/full-bright coverage remains. GPU behavior remains unverified.

- Clean unit/build/GameTest compilation: **2,979 passed, zero failures**.
- Focused APC/model native run: **10/737 selected, 10 passed**.
- Full native: **737/737 discovered, 734 passed, 3 failed**:
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `obstructionusesexactlytwodoorwaycellsandsafetydeniescloseactor`,
  `crushreleasecompletesnormalopenbeforefastautoclose`. No baseline or
  unrelatedness claim is made.
- Logs: `capturebuild/validation-logs/apc-light-source-fix-unit-build.log`,
  `apc-light-source-fix-native-focused.log`, and `apc-light-source-fix-native-full.log`.
- Final JAR SHA-256: `D09D6F70F3EF8953EB3C2D91ED1BF452C73A0565B4D2214FBBD6069761459B29`.

### Original model integration snapshot

- Clean unit/build/GameTest compilation: **2,974 unit tests passed**.
- Targeted machine-model regression run: **4 selected, 4 passed** (body-origin
  APC targeting, live source→substation→APC→lamp, disposal backup, overload).
- Fresh-world broad filter: **176/736 selected, 173 passed, 3 failed**:
  `venttransfersfinitepipegastoroomconservatively`,
  `verticalbendcarriesbodywholeitemandzeroair`,
  `restoredholderwithchangedtubeidentitysafelyreleasesownedpayload`.
- Fresh-world full native: **736/736 selected, 733 passed, 3 failed**:
  `liveapcmeterdrivesscheduledtwophasedoorandautoclose`,
  `logpulsedisableslampandrestoresatresolveddeadline`,
  `crushreleasecompletesnormalopenbeforefastautoclose`.
  Corresponding airlock isolated runs passed; this does not prove aggregate
  failures unrelated. Both aggregate runs remain non-green and unresolved.
- Earlier ventilation fixture initialization failure exhausted the 16-entry
  saved fixture limit in reused disposable state. Its saved-data artifact was
  preserved; the failure did not recur on fresh state. No owner world/config
  was reset, and no production capacity limit was increased.

Logs: `build/validation-logs/machine-model-integration-*.log`, especially
`unit-build`, `native-focused-clean`, `native-full-clean` and `power-only`.

JAR: `build/libs/moonstation14-0.0.1.jar`.
Verified SHA-256:
`3CDA232F50CB8712743A3517F3CC02D844106D0224B792A8874060A147915BD0`.

## Remaining acceptance

Client GPU loading, model appearance, animation, lighting and practical panel
placement/interactions are not established by headless tests. Aggregate native
failures remain open; no full-suite-green or baseline claim is made. Existing
world APCs were not migrated or given automatically generated backing walls.
