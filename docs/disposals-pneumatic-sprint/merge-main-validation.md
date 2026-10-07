# Main merge integration — October 7, 2026

Merged inputs: disposal branch `78d47a9`, incoming main `9aa0004`. All 23 conflicted files are resolved and staged; no merge commit was created.

## Preserved contracts

- Both prototype families, registration/reload paths, services, networking, localization, sounds and provenance are retained. Main's item-only jug remains item-only; disposal pipes remain catalog-only/non-vanilla-placeable, and disposal units retain authoritative body-hand placement. Functional ventilation devices remain listed once in Items.
- Main's current mapped world/in-hand controls, exact loose selection, pulling and timed interactions coexist with disposal construction, self-entry and containment. Welder activation uses the live `USE_IN_HAND` dispatcher. Contained body-local operations remain available; external pickups, throws, world-target interactions and puddle ingestion are denied at packet admission.
- Damage is applied once, with both main's applied-damage notification and disposal's typed diagnostic notification retained. Movement combines shared blocking, pull/knockdown speed and existing external-flight handling.
- World-cell gas remains 1 m³; unit/holder capacities remain resolved 2500 L/70 L. Main's float-based gas contract is retained. Two unit expectations were adapted to float-representable quantities/ULP bounds, not changed gameplay volumes or gas rules.
- Fixture registration uses tags and explicit supported template namespaces, including the camera-tour alias. Batch setup/teardown callbacks are explicitly registered only for batches containing selected tests; they do not bypass tag selection or alter ordinary selected/total counts. No tests were disabled to obtain a pass.

## Validation

- Full JUnit: **2,467 passed**, zero failures/errors. Java/GameTest compilation and jar generation passed. `build/merge-main-unit-build.log`.
- Independent source reviews passed. No conflict markers or unmerged index entries remain; staged whitespace check passed.
- One isolated, **unfiltered full GameTest run**, **586/586 selected/discovered**, zero untagged tests: **577 passed, 9 required failures**, in 49.70 server seconds. `build/merge-main-full-gametest.log`; failure artifacts retained under `build/gametest-merge-main-01`. Owner run configuration was not overwritten.
- Both camera-tour tests and the new live finite-cell intake/stale-endpoint tests completed without failure. This also exercises the explicitly registered camera-tour batch lifecycle.
- Artifact: `build/libs/moonstation14-0.0.1.jar`, 5,678,038 bytes, UTC mtime `2026-10-07T07:25:28Z`.

Subsequent semantic tag audit corrected cross-system selectors: live intake and gas-conservation cases include `atmosphere`, power-sensitive flush cases include `power`, and contained hand-drop includes `hands`. Generic APC/movement cases no longer claim disposal coverage; external-flight methods carry disposal/containment tags only where exercised. Fixture bodies, assertions, namespaces and batches are unchanged. All **7 tag-selector/metadata unit tests passed**, and fixture compilation passed (`build/merge-main-semantic-tags.log`); no additional server run was made.

## Full-run failures and follow-up

The two newly authored ray/landing regressions were subsequently corrected and independently reviewed. A focused isolated run selected **2/586** tests via `disposal-regression`; **both passed**, zero failures (`build/disposal-regressions-gametest.log`). The ray fixture had placed the zombie's feet above the tube, putting its eye ray above the outline; it now uses actual eye height to aim through the centerline. Landing is now followed by one explicit world tick before asserting grounded friction and tracking cleanup. Wrong-face/occlusion rejection, strict velocity, passive gravity, expiry and identity checks remain; initial binding/hands snapshots strengthen preservation coverage. No production gameplay code changed. Only the successful follow-up fixture world was removed; full-run failure artifacts remain.

| Test | Observed assertion |
| --- | --- |
| `builtfixtureinitializesandreportsresolvedruntime` | Ventilation fixture device meters unavailable/UNKNOWN. |
| `livetubeshapeisusedforrayandwrongfaceandocclusionstillreject` | **Resolved in focused follow-up:** corrected eye-ray fixture geometry. |
| `airborneflightexpirycontinuespassivegravitytolanding` | **Resolved in focused follow-up:** verifies settlement after the ordinary grounded step. |
| `connectedcablepathlamploadtripsapcandmanualrecloserestoresit` | Expected live connected-fixture solve/trip not observed. |
| `localextensionreceiverrespectsradiusandcablecuts` | Expected known APC cable at radius four not observed. |
| `leasesonlyexplicitlyconfiguredhumanvillagers` | Expected post-lease vanilla displacement not observed. |
| `heldbicaridinedosemetabolizesandhealsbrutedamage` | Expected scheduled oral healing not observed. |
| `farplayercannotdespawnboundunboundorcorpse` | Another fixture's `soap-slip` FakePlayer was nearest, violating fixture isolation. |
| `poweredscrubbercapturesonlyselectedgas` | No two positive paid solves within deadline; meter UNKNOWN. |

Source/log investigation found no demonstrated new semantic regression caused by the merge. Power/ventilation failures overlap historical records; lease displacement and FakePlayer proximity suggest fixture assumptions/interference. Oral-healing cause remains unresolved. The other seven failures are **not** established as harmless or pre-existing through parent-branch reproduction. The full suite has not been rerun after the two fixture corrections, and unrelated gameplay was not changed to force a pass. Runtime regression-free certification remains limited by those seven failures and the absence of a connected-client retest.
