# Owner feedback round 4

Typed projectiles' short appearance was caused by native `Entity.shouldRenderAtSqrDistance`: the vanilla threshold is `getBoundingBox().getSize() * 64 * viewScale`. With their 0.1-sized physical bounds, that culls them around 6.4 blocks. `TypedProjectileEntity` now derives presentation range from `EntityType.clientTrackingRange() * 16` and applies native view scale. This does not change physical collision, speed (40 units/s), or lifetime (10 seconds). A 400-unit path is not guaranteed: loaded chunks, tracking bounds, and frustum visibility still apply.
