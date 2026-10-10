# Equipment integration validation

Validation evidence and boundaries. The owner closed the sprint on 2026-10-08;
see `HANDOFF.md`. Closure does not turn unperformed graphical checks or historical
native failures into passing evidence.

## Unit and compilation

### Final toggle/click/coat corrections

`./gradlew --no-parallel compileJava compileTestJava compileGameTestJava test jar`
passed: 2,858 unit tests. Log:
`build/validation-logs/equipment-toggle-click-coat.log`.
Final JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`D20EC7F1760B66E4102D94C8475BB426EE7AA5C2A37A1116F1A44A28970448A4`.
Source reviews passed. Simple coat resolves without suit-storage permission;
click-source proof uses the existing authoritative slot action; the hide image
is the original static toggle mirrored through UVs, not negative geometry.
No client/server was launched for these final corrections.

### Earlier authored wearable integration

Serial compile, full unit tests, and JAR packaging passed: 2,856 tests, no
failures/errors/skips. Log: `build/validation-logs/equipment-authored-wearables.log`.
That pass's JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`355E818DAB654EA8B639EAA7DDC1F5E860CEC4FA4C1E520C8CB8F8DC7B6AEC59`.
Tests cover all eleven imported meshes, prototype model inheritance, cache
snapshot replacement, and the production vertex/pose emitter. Client reload
registration uses the existing mod event bus. Source review passed; no client
or server was launched. In-game fit, animation, UVs and lighting remain pending.

### Earlier HUD/footwear corrections

`compileJava compileTestJava compileGameTestJava`, full `test`, and `jar`
passed serially with `--no-parallel`: 2,850 tests, 0 failures/errors/skips.
Log: `build/validation-logs/equipment-hud-footwear-corrections.log`.
That pass's `build/libs/moonstation14-0.0.1.jar` SHA-256:
`6880BE07C7116DFB4409BD6CB65EF534F58E6C59C838DAB307BB6CD89DD8AE7B`.
Archive inspection confirms both third-person appearance layers remain and the
removed first-person renderer/context are absent. Independent source review of
layout/hit testing and footwear geometry passed. No client/server was launched
for these corrections; shoe overlap and HUD appearance need visual retesting.

Hands retain their original centered pair and 3px gaps; all adjacent hotbar gaps
are 3px, with suit storage after both pockets. Clothing shares wearable hotbar
tile sizing and uses 1px gaps. Dependency regressions preserve suit storage's
qualifying outerwear requirement, independent from jumpsuit-only pocket/ID
enablement.

### Earlier passes

- Final validation pass: `./gradlew compileJava compileTestJava
  compileGameTestJava` and `./gradlew test jar` — passed; 2,851 unit tests
  completed, 0 failed. Logs: `build/validation-logs/equipment-final-compile.log`
  and `build/validation-logs/equipment-final-unit.log`. The packaged JAR
  `build/libs/moonstation14-0.0.1.jar` has SHA-256
  `94429642C3D503EA7F0529266ED10ACB20F247A94C628EFBC2BBFE4C7943859C`.

- `./gradlew compileJava compileTestJava compileGameTestJava` — passed after
  correcting the equipment-removal candidate accessor/result mapping. Log:
  `build/validation-logs/equipment-corrections-final-compile.log`.
- Focused equipment selectors (`equipment.*`, `equipment.client.*`,
  `EquipmentSlotsPrototypeTest`, `BodyHandStatePolicyTest`,
  `BodyHandTransferActionCodecTest`, `BodyAppearanceClientCacheTest`, and
  `BodyHandClientPolicyTest`) — 82 total, 82 completed, 82 passed, 0 failed,
  0 skipped. Log: `build/validation-logs/equipment-focused.log`.
- `./gradlew test` — 2,846 total, 2,846 passed, 0 failed, 0 skipped. Log:
  `build/validation-logs/equipment-corrections-final-unit.log`.
- `./gradlew jar` — passed. Artifact:
  `build/libs/moonstation14-0.0.1.jar` (SHA-256
  `2E1165736B7F68CF2D43C5D34E79D5F0C08464897E0A425459BE709841B6E07E`).

## Full native GameTest (before the final item-size metadata refinement)

Command: `./gradlew runGameTestServer
-Pms14GameTestDir=build/gametest-equipment-fixture`.
All 698 tests were selected and completed: 678 passed, 20 failed, 0 incomplete.
The Gradle task exited nonzero. Log: `build/validation-logs/equipment-full-gametest.log`;
failed-world/config artifacts retained under `build/gametest-equipment-fixture`.
No client was launched. `build/gametest-tags-fixture` was not reused because it
already contained configuration and artifacts.

Exact failed test names:

`builtfixtureinitializesandreportsresolvedruntime`,
`boundhumanownsorderedequipmentinsameattachment`,
`purebothdirectionswithoccupiedhands`,
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
`staggeredretryinitializesabsentneedswithoutrerollingsavedstate`,
`movebetweenwornhostsconserveschildrenandrejectsstaleandcollision`,
`legacyhandequipandremovefilledbeltaftersave`,
`transfersbothdirectionsandpreservesbothowners`,
`detacheddeadbodymovesfromliveactorsnativepull`,
`blockedfitandpublisherfailureconservechild`,
`freshlyequippedhostsreadasemptywithoutmutatingstacks`,
`directmoves`,
`blastknockslivingtargetsoutwardandskipsineligibletargets`,
`jumpingtautpullerfallsandlandsafterinputstops`,
`puregridinsertextractpreservestokenanchorandsnapshot`,
`farplayercannotdespawnboundunboundorcorpse`,
`disableddevicedoesnotmovegas`,
`poweredscrubbercapturesonlyselectedgas`,
`onlycompletedpaidsolvemovesconservedgas`.

Compared by exact name with `docs/basic-medical-treatment-sprint/MERGE-MAIN.md`,
9 names match its prior 14-failure run. The other 11 current failures were not
listed there; they are not automatically classified as regressions or inherited.
Several equipment-named fixtures failed with `No value present`; the
`blockedfitandpublisherfailureconservechild` fixture also reports `No value
present`. These require parent review before any further native run or fixture
changes. Unrelated physics/power/ventilation failures were not repaired here.

The final physical item-size mapping change occurred after this full native run;
the run does not validate that metadata refinement. Post-refinement compile,
82 focused units, and 2,841 full unit/JAR tests passed; no second native run was
launched.

## Corrections pass and remaining evidence

## Final focused native validation

Command: `./gradlew runGameTestServer
"-Pms14GameTestTags=hands,storage,equipment-regression,equipment-quick-equip,equipment-drag-world"
-Pms14GameTestDir=build/gametest-equipment-regression-fixture`. The selector
preflight selected 195 of 701 fixtures. All 195 completed: 193 passed, 2
failed, 0 incomplete. The runner exited nonzero. Log:
`build/validation-logs/equipment-final-native-focused.log`; failed fixture
artifacts are retained under `build/gametest-equipment-regression-fixture`.

Failures: `pullsdeadhumantargetandreleaseswhensourcebecomescritical` (unrelated
pulling/physics assertion: “dead human target can be pulled”) and
`transfersworldgridchildtoemptyequipmentslot` (“world item transfers to head
equipment”). The round-trip/compensation fixture was selected and did not
appear in the failure list. The earlier focused attempt was aborted by missing
fixture-template metadata; this completed run supersedes its incomplete native
evidence. The world-transfer failure requires investigation; no second native
run was launched. Post-run source review corrected the compensation fixture's
trusted publisher to compare against its current state during rollback; this
source-only correction is compile-validated but not covered by another native
run. No client/pixel check was run.

No client was launched. World-grid gear drag/drop is wired in both directions,
including a native round-trip/failed-publication fixture. Broad destination
preview coverage and native pixel/render checks remain outstanding. Manual
The removed first-person arms/gloves/coat appearance is not a pixel-acceptance
target; the updated manual explicitly checks their absence and retained F5/observer
layers. CPU validation makes no claim about pixel output. Focused results do not
establish full GameTest-suite success.

## Final world-equipment regression

After fixing the fixture's obstructed reach ray and rollback publisher, the
`equipment-drag-world` run selected 1/701 fixtures and completed 1: 1 passed,
0 failed. `build/world-final.log` records `All 1 required tests passed` and
`BUILD SUCCESSFUL`. It exercises world-grid→head, head→world-grid with both
hands full, and failed-publication compensation. The fixture uses the shared
transfer and commit paths; production reach/authority rules were not changed.
Independent final source review passed. The broader completed focused run was
193/195; its remaining world-equipment failure is resolved by this narrow run,
but the pulling-physics failure and historical full-suite failures are not
reclassified as passing.

## Boundaries

Footprint geometry remains storage geometry. Pocket size fallback uses only
explicit physical size metadata on verified leaf prototypes; unknown profiles
remain unadmitted. Connected graphical/pixel acceptance and end-to-end client
rendering are not established by these CPU/server checks.

## First-person scope reduction and artifact check

The custom first-person appearance renderer/context and first-person arm helper
were removed. The locally controlled body's normal world pass remains suppressed
only when its first-person camera is bound to it; clothing and held-item layers
remain registered for F5 third person and observers. The existing CHARACTER
control binding requires `LocalPlayer.isSpectator()`; vanilla
`ItemInHandRenderer.renderHandsWithItems` does not draw spectator-carrier hands,
so no global native-hand cancellation hook is needed.

`compileJava compileTestJava compileGameTestJava` passed, and `test` passed all
2,847 tests. The `jar` task then passed. The archive
`build/libs/moonstation14-0.0.1.jar` contains
`BodyAppearanceItemLayer.class`, `BodyAppearanceClothingLayer.class`, and
`PlayerCharacterHarnessRenderer.class`; it contains neither
`FirstPersonBodyAppearanceRenderer.class` nor
`FirstPersonBodyRenderContext.class`. No client was launched; manual checking is
limited to confirming no first-person arms/items and retained F5/observer layers.
# Authored wearable integration check

The focused headless authored-wearable tests parse all eleven installed mesh
resources, verify immutable reload-snapshot replacement, and exercise the same
bone-transform/vertex-emission helper used by the live renderer. The full serial
validation run is `gradlew --no-parallel compileJava compileTestJava
compileGameTestJava test jar`; it does not launch a client/server. It verifies
compilation and packaging, not visual appearance. See `MANUAL.md` for the required
in-game mesh/UV/articulation review.
