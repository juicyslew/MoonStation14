# Airlock crush-release cycle

Run the focused live-controller regression with:

```powershell
./gradlew runGameTestServer "-Pms14GameTestTags=airlock_crush_cycle" "-Pms14GameTestDir=build/gametest-tags-fixture"
```

The fixture starts an ordinarily paid opening, applies the safety and timing wire cuts, and samples the controller phase every server tick through the subsequent crush cycle. A delayed crush release must run `OPENING` for the configured first opening interval, then `OPENING_PASSABLE`, then reach fully `OPEN` before the shortened auto-close starts another close. The crushed body remains collision-exempt until the actual passability transition; the root's ordinary two-cell collision shape and client animation timestamp remain controlled by the existing block entity.
