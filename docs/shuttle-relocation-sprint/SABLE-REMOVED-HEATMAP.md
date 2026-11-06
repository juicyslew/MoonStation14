# Sable removed-owner heat-map tick compatibility

Sable 2.0.6's `ServerSubLevel.tick()` invokes `SubLevelHeatMapManager.tick()` after its superclass tick and bounds update. `SubLevel.onRemove()` marks the sublevel removed, but the heat-map manager's `tick()` has no removed-owner check; its split path can use cached split state and assemble blocks through the removed plot. This allows stale owner work after plot removal.

The narrow `SableRemovedHeatMapTickMixin` cancels only the heat-map manager tick when its owning `ServerSubLevel.isRemoved()` is true. Active sublevels continue through Sable's original heat-map tick and physics/splitting behavior unchanged. This does not claim to resolve other Sable split/removal lifecycle paths.

Pinned source reference: [Sable `ServerSubLevel.java` at `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`](https://raw.githubusercontent.com/ryanhcode/sable/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/common/src/main/java/dev/ryanhcode/sable/sublevel/ServerSubLevel.java); companion implementation: `common/src/main/java/dev/ryanhcode/sable/sublevel/plot/heat/SubLevelHeatMapManager.java` at the same revision.
