# Airlock crush-release cycle

Run the focused live-controller regression with:

```powershell
./gradlew runGameTestServer "-Pms14GameTestTags=airlock_crush_cycle" "-Pms14GameTestDir=build/gametest-tags-fixture"
```

The fixture starts an ordinarily paid opening, applies the safety and timing wire cuts, and samples the controller phase every server tick through the subsequent crush cycle. A delayed crush release must run `OPENING` for the configured first opening interval, then `OPENING_PASSABLE`, then reach fully `OPEN` before the shortened auto-close starts another close. The crushed body remains collision-exempt until the actual passability transition; the root's ordinary two-cell collision shape and client animation timestamp remain controlled by the existing block entity.

Stationary contact coverage is selected with `airlock_stationary_contact`. It asserts an actual living Critical enrolled body is shocked at the partial-close solidification edge, receives the longer five-second shock paralysis after crush status, is not shocked again by scheduled callbacks, and can be shocked again on a later solidification cycle. A face-tangent Critical body also receives one passive contact shock and triggers quiet normal opening at the closed edge despite its paralysis; recovery itself causes no duplicate shock. A loaded medical-DEAD human with its center outside the left doorway face and bbox tangent to the root quietly reopens normal completed closes on repeated episodes; a 0.01-block actual gap is excluded. Safe abort, loaded-dead safety obstruction, power-loss, nearby-body, and inactive-electrification negatives use the same powered live fixture. Root closed-state checks use existing resolved `DoorBumpOpener` and normal power/bolt admission; they do not poll continuously and are suppressed throughout a retained crush/reopen delay.
