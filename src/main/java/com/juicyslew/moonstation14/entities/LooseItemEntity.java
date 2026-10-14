package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.explosion.grenade.server.GrenadeDetonationService;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

/** Persistent loose object with independent motion; it deliberately has no ItemEntity pickup/merge/lifespan behavior. */
public class LooseItemEntity extends ItemEntity {
    private static final EntityDataAccessor<Float> ROTATION = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SETTLED = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.BOOLEAN);
    private float angularVelocity;
    private float previousRotationAngle;
    private float currentRotationAngle;
    private long nextSupportCheck;
    private boolean motionBlockedByUnloaded;
    private int creativePickupDelay;
    private final LooseClientPositionInterpolation clientPosition = new LooseClientPositionInterpolation();

    public LooseItemEntity(EntityType<? extends LooseItemEntity> type, Level level) { super(type, level); }

    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        ItemStack stack = getItem();
        if (stack == null || stack.isEmpty()) return super.getDimensions(pose);
        try {
            LooseObjectProfile profile = LooseObjectProfile.resolveForStack(stack, level().isClientSide);
            return net.minecraft.world.entity.EntityDimensions.scalable(profile.physicalWidth(), profile.physicalHeight());
        } catch (RuntimeException unavailableDuringInitialization) {
            return super.getDimensions(pose);
        }
    }

    @Override public void setItem(ItemStack stack) {
        super.setItem(stack);
        refreshDimensions();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROTATION, 0f);
        builder.define(SETTLED, false);
    }

    public static LooseItemEntity create(Level level, Vec3 position, ItemStack stack) {
        LooseItemEntity entity = new LooseItemEntity(ModEntities.LOOSE_ITEM.get(), level);
        entity.setPos(position);
        entity.setItem(stack.copy());
        entity.setUnlimitedLifetime();
        entity.setDeltaMovement(Vec3.ZERO);
        return entity;
    }

    /**
     * Keep copies on the registered loose-item type. ItemPickupParticle copies its
     * source before rendering it, and the entity type selects the renderer.
     */
    @Override public LooseItemEntity copy() {
        LooseItemEntity copy = new LooseItemEntity(ModEntities.LOOSE_ITEM.get(), level());
        copy.copyPosition(this);
        copy.setItem(getItem().copy());
        copy.setDeltaMovement(getDeltaMovement());
        copy.setRotationAngle(getRotationAngle());
        copy.setSettled(settled());
        copy.angularVelocity = angularVelocity;
        copy.previousRotationAngle = previousRotationAngle;
        copy.currentRotationAngle = currentRotationAngle;
        copy.nextSupportCheck = nextSupportCheck;
        copy.motionBlockedByUnloaded = motionBlockedByUnloaded;
        copy.creativePickupDelay = creativePickupDelay;
        return copy;
    }

    public LooseObjectProfile profile() {
        return LooseObjectProfile.resolveForStack(getItem(), level().isClientSide);
    }

    public void launch(Vec3 velocity, float angularVelocity) {
        if (velocity == null || !finite(velocity)) return;
        setDeltaMovement(clamp(velocity, 2.5));
        this.angularVelocity = Float.isFinite(angularVelocity) ? Math.max(-1.0f, Math.min(1.0f, angularVelocity)) : 0;
        setSettled(false);
    }

    public void applyImpulse(Vec3 impulse) {
        if (impulse == null || !finite(impulse)) return;
        double mass = profile().mass();
        if (!Double.isFinite(mass) || mass <= 0) return;
        launch(clamp(getDeltaMovement().add(impulse.scale(1.0 / Math.max(.01, mass))), 2.5), angularVelocity);
    }

    /** Applies the shared normalized-mass tether delta in native blocks/tick and wakes a settled item. */
    public void applyPullVelocityChange(Vec3 deltaPerTick) {
        if (level().isClientSide || deltaPerTick == null || !finite(deltaPerTick)) return;
        Vec3 next = getDeltaMovement().add(deltaPerTick);
        if (!finite(next)) return;
        setDeltaMovement(next);
        setSettled(false);
    }

    /** Collision-resolved rope correction consumed immediately rather than queued as a weak impulse. */
    public void moveByPull(Vec3 correction) {
        if (level().isClientSide || correction == null || !finite(correction)) return;
        var sweep = getBoundingBox().expandTowards(correction);
        if (!chunksLoaded(sweep)) return;
        Vec3 before = position();
        move(MoverType.SELF, correction);
        Vec3 actual = position().subtract(before);
        if (actual.horizontalDistanceSqr() > 1.0e-10) {
            Vec3 velocity = getDeltaMovement();
            setDeltaMovement(0, velocity.y, 0);
            setSettled(false);
        }
    }

    public boolean settled() { return entityData.get(SETTLED); }
    public float getRotationAngle() { return entityData.get(ROTATION); }
    public float getRotationAngle(float partialTick) {
        float delta = Mth.wrapDegrees((float) Math.toDegrees(currentRotationAngle - previousRotationAngle));
        return previousRotationAngle + (float) Math.toRadians(delta) * Mth.clamp(partialTick, 0, 1);
    }
    public void setRotationAngle(float angle) { if (Float.isFinite(angle)) entityData.set(ROTATION, angle); }
    private void setSettled(boolean value) { entityData.set(SETTLED, value); }

    public void setCreativePickupDelay(int ticks) { creativePickupDelay = Math.max(2, Math.min(40, ticks)); }
    /** Admission policy for hand pickup; specialized world fixtures may deny hand ownership. */
    public boolean admitsBodyHandPickup() { return true; }

    @Override public void playerTouch(net.minecraft.world.entity.player.Player player) {
        if (!admitsBodyHandPickup() || creativePickupDelay > 0 || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || !serverPlayer.isCreative()
                || com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(serverPlayer).isPresent()) return;
        super.playerTouch(player);
    }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (!ROTATION.equals(accessor) && !SETTLED.equals(accessor)) refreshDimensions();
        if (!level().isClientSide && ROTATION.equals(accessor)) {
            previousRotationAngle = currentRotationAngle;
            currentRotationAngle = getRotationAngle();
        }
    }

    @Override public void tick() {
        if (isRemoved() || getItem().isEmpty()) return;
        // Preserve base entity interpolation bookkeeping without running ItemEntity's pickup, merge, or expiry loop.
        xo = getX(); yo = getY(); zo = getZ(); xOld = getX(); yOld = getY(); zOld = getZ();
        tickCount++;
        if (!level().isClientSide && creativePickupDelay > 0) creativePickupDelay--;
        if (level().isClientSide) {
            advanceClientPosition();
            refreshClientProfileDimensions();
            previousRotationAngle = currentRotationAngle;
            currentRotationAngle = getRotationAngle();
            return;
        }
        evaluateGrenadeFuse();
        if (isRemoved()) return;
        Vec3 tether = com.juicyslew.moonstation14.ms14.loose_objects.pulling.TetherMotion
                .deltaVelocityPerSecond(this).scale(.05);
        if (tether.lengthSqr() > 0) applyPullVelocityChange(tether);
        // Only the exact live server UUID is a physical world-item target; do not wake
        // detached/preview instances that happen to execute a presentation tick.
        if (level().getEntity(getId()) == this) {
            Vec3 gravity = com.juicyslew.moonstation14.ms14.singularity.SingularityGravityPulse
                    .deltaVelocity(level(), position(), level().getGameTime()).scale(.05);
            if (gravity.lengthSqr() > 0) applyPullVelocityChange(gravity);
        }
        if (settled()) {
            if (tickCount < nextSupportCheck) return;
            nextSupportCheck = tickCount + 10;
            if (hasFloorSupport()) return;
            setSettled(false);
        }
        Vec3 velocity = getDeltaMovement();
        if (!finite(velocity)) velocity = Vec3.ZERO;
        motionBlockedByUnloaded = false;
        Vec3 before = position();
        Vec3 travel = velocity.add(0, -0.04, 0);
        // Avoid collision scans which could touch unloaded chunks; bound the swept search to a small neighborhood.
        // Entity.move's block collision query is the swept box (plus numerical tolerance),
        // not a one-block halo. A halo would freeze valid motion near unloaded neighbors.
        // Account for vanilla step-up collision probing when enabled on this entity.
        double stepHeight = maxUpStep();
        var sweep = getBoundingBox().expandTowards(travel);
        if (stepHeight > 0) sweep = sweep.expandTowards(0, stepHeight, 0);
        if (!chunksLoaded(sweep)) {
            motionBlockedByUnloaded = true;
            // Retain velocity (including gravity accumulated on earlier ticks) and retry soon.
            nextSupportCheck = tickCount + 5;
            return;
        }
        move(MoverType.SELF, travel);
        Vec3 actual = position().subtract(before);
        boolean hitX = Math.abs(actual.x - travel.x) > 1.0e-6;
        boolean hitY = Math.abs(actual.y - travel.y) > 1.0e-6;
        boolean hitZ = Math.abs(actual.z - travel.z) > 1.0e-6;
        double approachSpeed = travel.length();
        double restitution = profile().restitution();
        double vx = hitX ? -travel.x * restitution : travel.x;
        double vy = hitY ? -travel.y * restitution : travel.y;
        double vz = hitZ ? -travel.z * restitution : travel.z;
        if (onGround() && Math.abs(vy) < .12) vy = 0;
        double friction = Math.max(0, Math.min(.25, profile().friction()));
        double horizontalDamp = onGround() || hitX || hitZ ? .86 : .995;
        double contactFriction = onGround() || hitX || hitZ ? 1 - friction : 1;
        vx *= contactFriction * horizontalDamp;
        vz *= contactFriction * horizontalDamp;
        vy *= .995;
        Vec3 next = new Vec3(vx, vy, vz);
        float angle = getRotationAngle() + angularVelocity;
        setRotationAngle(Float.isFinite(angle) ? angle : 0);
        if (next.lengthSqr() < .0004 && onGround() && hasFloorSupport()) {
            next = Vec3.ZERO;
            angularVelocity = 0;
            setSettled(true);
            nextSupportCheck = tickCount + 10;
        }
        setDeltaMovement(next);
        if ((hitX || hitY || hitZ) && profile().breakOnImpact()
                && approachSpeed >= profile().breakThreshold()) breakJug();
    }

    /** Stage vanilla entity-position packets and move only the client presentation toward them. */
    @Override public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (!level().isClientSide) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
            return;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        // Entity.lerpTo teleports immediately in 1.21.1. Keep its rotation handling but
        // stage position locally so tick() can preserve the previous render position.
        setRot(yRot, xRot);
        if (clientPosition.stage(getX(), getY(), getZ(), x, y, z)) {
            // Keep both vanilla renderer histories continuous at correction/teleport snaps.
            setPos(x, y, z);
            xo = xOld = x; yo = yOld = y; zo = zOld = z;
        }
    }

    @Override public double lerpTargetX() { return clientPosition.targetX(getX()); }
    @Override public double lerpTargetY() { return clientPosition.targetY(getY()); }
    @Override public double lerpTargetZ() { return clientPosition.targetZ(getZ()); }

    private void advanceClientPosition() {
        if (!clientPosition.pending()) return;
        double[] position = clientPosition.advance(getX(), getY(), getZ());
        setPos(position[0], position[1], position[2]);
    }

    /** Client catalogs can arrive after the tracked entity and its initial fallback-sized box. */
    private void refreshClientProfileDimensions() {
        try {
            var catalog = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientLooseObjectProfiles();
            if (catalog.size() == 0 || getItem().isEmpty()) return;
            LooseObjectProfile profile = LooseObjectProfile.resolveForStack(getItem(), true);
            if (Float.compare(getBbWidth(), profile.physicalWidth()) != 0
                    || Float.compare(getBbHeight(), profile.physicalHeight()) != 0) refreshDimensions();
        } catch (RuntimeException unavailableOrUnknownProfile) {
            // Keep the current dimensions until a valid client profile is available; never consult server data.
        }
    }

    private void evaluateGrenadeFuse() {
        if (!(level() instanceof ServerLevel server)) return;
        GrenadeFuseData data = getItem().get(ModDataComponents.GRENADE_FUSE.get());
        if (data == null || data.phase() != GrenadeFuse.Phase.ARMED) return;
        try {
            GrenadeFuse.Evaluation result = data.toFuse().evaluate(server.getGameTime());
            if (result.beepDue()) server.playSound(null, getX(), getY(), getZ(), ModSounds.GRENADE_BEEP.get(), SoundSource.BLOCKS, .35f, 1f);
            getItem().set(ModDataComponents.GRENADE_FUSE.get(), GrenadeFuseData.fromFuse(result.state()));
            if (result.expired()) {
                BlockPos at = blockPosition();
                discard();
                GrenadeDetonationService.detonate(server, at);
            }
        } catch (RuntimeException invalid) {
            discard();
        }
    }

    private void breakJug() {
        if (!(level() instanceof ServerLevel server)) return;
        ReagentAttachment contents = null;
        var component = getItem().get(ModDataComponents.REAGENT.get());
        if (component != null) contents = new ReagentAttachment(component);
        if (contents == null || contents.isEmpty()) {
            playJugBreakFeedback(server);
            discard();
            return;
        }
        ReagentAttachment spill = new ReagentAttachment(contents.getMap());
        BlockPos landing = findSpillSurface(server);
        if (landing == null) return;
        ReagentSystem.handleSpillSolution(spill, server, landing);
        // The spill bridge removes only admitted quantity from its argument; keep any remainder authoritative.
        if (spill.isEmpty()) {
            getItem().remove(ModDataComponents.REAGENT.get());
            playJugBreakFeedback(server);
            discard();
        } else if (!spill.equals(contents)) getItem().set(ModDataComponents.REAGENT.get(), spill.toComponent());
    }

    /** Find the first loaded puddle surface or solid support below this contact, without loading chunks. */
    private BlockPos findSpillSurface(ServerLevel server) {
        int x = BlockPos.containing(getBoundingBox().getCenter()).getX();
        int z = BlockPos.containing(getBoundingBox().getCenter()).getZ();
        int startY = Mth.floor(getBoundingBox().minY - .001);
        int minY = Math.max(server.getMinBuildHeight(), startY - 512);
        for (int y = startY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!server.hasChunkAt(pos)) return null;
            var state = server.getBlockState(pos);
            if (state.is(com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get())) return pos;
            var shape = state.getCollisionShape(server, pos);
            if (!shape.isEmpty()) {
                for (var box : shape.toAabbs()) {
                    if (box.maxY > 0 && box.minY < 1) return pos;
                }
                return null;
            }
        }
        return null;
    }

    private void playJugBreakFeedback(ServerLevel server) {
        server.playSound(null, getX(), getBoundingBox().minY, getZ(), SoundEvents.GLASS_BREAK,
                SoundSource.BLOCKS, 1f, 1f);
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), getX(),
                getBoundingBox().minY + .03, getZ(), 6, .08, .02, .08, .04);
    }

    private boolean hasFloorSupport() {
        var bounds = getBoundingBox();
        BlockPos below = BlockPos.containing(getX(), bounds.minY - .02, getZ());
        // Unknown support is not treated as permanent support: settled entities periodically retry this
        // check, while the swept-chunk guard below prevents motion/collision queries into unloaded chunks.
        if (!level().hasChunkAt(below)) return false;
        var shape = level().getBlockState(below).getCollisionShape(level(), below);
        for (var box : shape.toAabbs()) if (Math.abs(below.getY() + box.maxY - bounds.minY) < .04
                && bounds.maxX > below.getX() + box.minX && bounds.minX < below.getX() + box.maxX
                && bounds.maxZ > below.getZ() + box.minZ && bounds.minZ < below.getZ() + box.maxZ) return true;
        return false;
    }

    private boolean chunksLoaded(net.minecraft.world.phys.AABB box) {
        LooseChunkBounds bounds = LooseChunkBounds.from(box);
        int minX = bounds.minX(), maxX = bounds.maxX();
        int minZ = bounds.minZ(), maxZ = bounds.maxZ();
        if (maxX - minX > 4 || maxZ - minZ > 4) return false;
        for (int x=minX; x<=maxX; x++) for (int z=minZ; z<=maxZ; z++) if (!level().hasChunk(x,z)) return false;
        return true;
    }
    public boolean motionBlockedByUnloaded() { return motionBlockedByUnloaded; }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static Vec3 clamp(Vec3 v, double max) { double l=v.length(); return !Double.isFinite(l) ? Vec3.ZERO : l > max ? v.scale(max/l) : v; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("LooseRotation", getRotationAngle()); tag.putFloat("LooseAngularVelocity", angularVelocity);
        tag.putBoolean("LooseSettled", settled());
        if (creativePickupDelay > 0) tag.putInt("LooseCreativePickupDelay", creativePickupDelay);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setRotationAngle(tag.getFloat("LooseRotation"));
        angularVelocity = tag.contains("LooseAngularVelocity") && Float.isFinite(tag.getFloat("LooseAngularVelocity")) ? tag.getFloat("LooseAngularVelocity") : 0;
        setSettled(tag.getBoolean("LooseSettled"));
        creativePickupDelay = Math.max(0, Math.min(40, tag.getInt("LooseCreativePickupDelay")));
    }
}
