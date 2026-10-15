# Mouse passage parity

Reference checkout is pinned to SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Upstream mouse composition maps its small-mob collision mask/layer into the
registered local `CollisionProfile` on the inherited `mouse` character prototype.
The local runtime resolves that component for the actual actor; unprofiled actors
retain native Minecraft collision. `base_airlock` supplies inherited fixture mask
and layer data consumed by `AirlockBlockEntity.prototype()` and the shared
`AirlockPassagePolicy` two-way cross-intersection predicate.

The registered bit mapping preserves upstream SS14 `CollisionGroup` bit values
(`Content.Shared/Physics/CollisionGroup.cs`): `Opaque=1`, `Impassable=2`,
`Mid=4`, `High=8`, `Low=16`, `Bullet=64`, `Interact=128`. Mouse SmallMobMask/
Layer are `18/65`; airlock FullTileMask/AirlockLayer are `158/205`. The focused
resolved unit checks their zero cross-intersections. Local inherited human profile
defaults are not asserted.

`BlockCollisions` changes only its collision-shape stream for a resolved actor and
registered airlock fixture. The block's own shape, `ClipContext.COLLIDER` ray
behavior, atmosphere sealing, and unsupported vanilla fixtures remain unchanged.
Current local airlocks have no welding state; therefore welded layer-change
behavior is deliberately not represented or claimed. Normal/bolted/unpowered
airlocks share the same fixture pair policy. Native wall geometry remains blocking.

Normal actor collision defaults are not yet composed into inherited local
character prototypes. Missing profiles fail closed to native Minecraft behavior;
human blocking is not claimed as resolved SS14 profile parity.
