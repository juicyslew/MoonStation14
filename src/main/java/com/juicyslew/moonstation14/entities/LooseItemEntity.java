package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.explosion.grenade.server.GrenadeDetonationService;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions;
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
import org.joml.Vector3f;
import net.minecraft.util.Mth;
import com.juicyslew.moonstation14.ms14.damage.ItemDamageState;
import com.juicyslew.moonstation14.ms14.damage.ItemDamageKernel;
import com.juicyslew.moonstation14.ms14.damage.DamageReducer;

/** Persistent loose object with independent motion; it deliberately has no ItemEntity pickup/merge/lifespan behavior. */
public class LooseItemEntity extends ItemEntity implements com.juicyslew.moonstation14.ms14.singularity.SingularityConsumable {
    private static final EntityDataAccessor<Float> ROTATION = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SETTLED = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> THROWN_FLIGHT = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> ATTACH_TARGET = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> ATTACH_TARGET_ID = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> ATTACH_CONTACT = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> ATTACH_DIRECTION = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> ATTACH_KIND = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ATTACH_TICK = SynchedEntityData.defineId(LooseItemEntity.class, EntityDataSerializers.LONG);
    private static Vector3f toVector(Vec3 v) { return new Vector3f((float)v.x, (float)v.y, (float)v.z); }
    private static Vec3 fromVector(Vector3f v) { return new Vec3(v.x, v.y, v.z); }
    private float angularVelocity;
    private float previousRotationAngle;
    private float currentRotationAngle;
    private long nextSupportCheck;
    private boolean motionBlockedByUnloaded;
    private int creativePickupDelay;
    private boolean pendingThrownLandingDamage;
    private boolean damageDestructionStarted;
    private boolean activeThrownCombat;
    private boolean thrownLandingSoundConsumed;
    private java.util.UUID thrownByBody;
    private final ThrowerClearance throwerClearance = new ThrowerClearance();
    private final java.util.Set<java.util.UUID> previousThrownContacts = new java.util.HashSet<>();
    private final com.juicyslew.moonstation14.ms14.combat.throwing.LooseItemCollisionSoundPolicy collisionSoundPolicy = new com.juicyslew.moonstation14.ms14.combat.throwing.LooseItemCollisionSoundPolicy();
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
        builder.define(THROWN_FLIGHT, false);
        builder.define(ATTACH_TARGET, java.util.Optional.empty()); builder.define(ATTACH_TARGET_ID, -1);
        builder.define(ATTACH_CONTACT, new Vector3f()); builder.define(ATTACH_DIRECTION, new Vector3f()); builder.define(ATTACH_KIND, -1); builder.define(ATTACH_TICK, 0L);
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
        copy.entityData.set(THROWN_FLIGHT, thrownFlightPresentation());
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
        collisionSoundPolicy.resetTerrain();
    }

    /** Called only after BodyLooseThrow has successfully published the world-owned entity. */
    public void markExplicitThrowForLanding() {
        if (!level().isClientSide && !isRemoved() && isAddedToLevel()) pendingThrownLandingDamage = true;
    }

    /** Arms target-contact behavior only after authenticated world publication. */
    public void armThrownCombat(java.util.UUID throwerBody) {
        if (!level().isClientSide && throwerBody != null && !isRemoved() && isAddedToLevel()
                && level().getEntity(getId()) == this) { previousThrownContacts.clear(); throwerClearance.reset(); activeThrownCombat = true; entityData.set(THROWN_FLIGHT, true); thrownLandingSoundConsumed = false; thrownByBody = throwerBody;
            com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.resolveForStack(getItem(), false)
                    .flatMap(com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile::throwSound)
                    .ifPresent(sound -> com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(
                            (ServerLevel) level(), sound, getX(), getY(), getZ(), 1f, 1f, SoundSource.NEUTRAL)); }
    }
    public boolean activeThrownCombat() { return activeThrownCombat; }
    /** Public synchronized presentation projection; combat authority remains server-private. */
    public boolean thrownFlightPresentation() { return entityData.get(THROWN_FLIGHT); }
    public java.util.Optional<java.util.UUID> thrownByBody() { return java.util.Optional.ofNullable(thrownByBody); }
    public void stopThrownCombat() { activeThrownCombat = false; entityData.set(THROWN_FLIGHT, false); thrownByBody = null; throwerClearance.reset(); previousThrownContacts.clear(); }

    public java.util.Optional<ItemAttachment> attachment() {
        var target=entityData.get(ATTACH_TARGET); int kind=entityData.get(ATTACH_KIND);
        if (target.isEmpty() || kind < 0 || kind >= ItemAttachment.AttachmentKind.values().length) return java.util.Optional.empty();
         return java.util.Optional.of(new ItemAttachment(target.get(), entityData.get(ATTACH_TARGET_ID), fromVector(entityData.get(ATTACH_CONTACT)), fromVector(entityData.get(ATTACH_DIRECTION)), ItemAttachment.AttachmentKind.values()[kind], entityData.get(ATTACH_TICK)));
    }
    public boolean attachTo(net.minecraft.world.entity.LivingEntity target, Vec3 worldContact, Vec3 incoming, ItemAttachment.AttachmentKind kind) {
        if (level().isClientSide || target == null || target.level()!=level() || target.isRemoved() || attachment().isPresent() || worldContact==null || incoming==null || kind==null || !finite(worldContact) || !finite(incoming)) return false;
        var local=AttachmentPoseGeometry.captureLocalPoint(target, worldContact);
        var direction=AttachmentPoseGeometry.captureLocalDirection(target, incoming);
        var thrownProfile = com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.resolveForStack(getItem(), false).orElse(null);
        entityData.set(ATTACH_TARGET, java.util.Optional.of(target.getUUID())); entityData.set(ATTACH_TARGET_ID,target.getId());
        entityData.set(ATTACH_CONTACT,toVector(local)); entityData.set(ATTACH_DIRECTION,toVector(direction)); entityData.set(ATTACH_KIND,kind.ordinal());
        entityData.set(ATTACH_TICK, level().getGameTime());
        stopThrownCombat(); setDeltaMovement(Vec3.ZERO); setNoGravity(true); setPos(worldContact);
        if (kind == ItemAttachment.AttachmentKind.EMBEDDED && thrownProfile != null)
            thrownProfile.embedding().flatMap(com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.EmbeddingData::hitSound)
                    .ifPresent(sound -> com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(
                            (ServerLevel) level(), sound, getX(), getY(), getZ(), 1f, 1f, SoundSource.NEUTRAL));
        return true;
    }
    /** Detach in place, preserving the sole ItemStack owner. */
    public boolean detachAttachment() {
        if(level().isClientSide || attachment().isEmpty()) return false;
        ItemAttachment old = attachment().orElseThrow();
        var profile = com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.resolveForStack(getItem(), false).orElse(null);
        entityData.set(ATTACH_TARGET,java.util.Optional.empty()); entityData.set(ATTACH_TARGET_ID,-1); entityData.set(ATTACH_KIND,-1); entityData.set(ATTACH_TICK,0L);
        setNoGravity(false); setDeltaMovement(Vec3.ZERO);
        if (old.kind() == ItemAttachment.AttachmentKind.EMBEDDED && profile != null
                && profile.embedding().map(com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.EmbeddingData::deleteOnRemove).orElse(false)) discard();
        return true;
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
    public boolean admitsBodyHandPickup() { return attachment().isEmpty(); }

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
        if (attachment().isPresent()) {
            ItemAttachment a=attachment().orElseThrow();
            var target=level().getEntity(a.targetEntityId());
            if (!(target instanceof net.minecraft.world.entity.LivingEntity living) || !living.getUUID().equals(a.targetUUID()) || living.isRemoved()) {
                target = level() instanceof ServerLevel server ? server.getEntity(a.targetUUID()) : null;
            }
            if(target instanceof net.minecraft.world.entity.LivingEntity living && living.getUUID().equals(a.targetUUID()) && !living.isRemoved()) {
                if (living.getId() != a.targetEntityId()) entityData.set(ATTACH_TARGET_ID, living.getId());
                setPos(AttachmentPoseGeometry.bodyToWorld(living, a.targetLocalContact())); setDeltaMovement(Vec3.ZERO); return;
            }
            detachAttachment();
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
                    .deltaVelocity(level(), position(), level().getGameTime())
                    .scale(com.juicyslew.moonstation14.ms14.loose_objects.pulling.TetherMotion.SECONDS_PER_TICK);
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
        if (dispatchThrownContacts(before, position(), travel)) return;
        boolean hitX = Math.abs(actual.x - travel.x) > 1.0e-6;
        boolean hitY = Math.abs(actual.y - travel.y) > 1.0e-6;
        boolean hitZ = Math.abs(actual.z - travel.z) > 1.0e-6;
        var terrainProfile = com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.resolveForStack(getItem(), false).orElse(null);
        emitCollisionCue(collisionSoundPolicy.terrain(terrainProfile, (hitX ? 1 : 0) | (hitY ? 2 : 0) | (hitZ ? 4 : 0), travel, level().getGameTime()), getX(), getY(), getZ());
        if (hitY && travel.y < 0) {
            boolean armedThrownLanding = activeThrownCombat;
            boolean cueLanding = shouldCueThrownLandingSound(armedThrownLanding, thrownLandingSoundConsumed);
            if (cueLanding) thrownLandingSoundConsumed = true;
            var thrownSoundProfile = com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.resolveForStack(getItem(), false).orElse(null);
            if (cueLanding && level() instanceof ServerLevel server && thrownSoundProfile != null) thrownSoundProfile.landSound().ifPresent(sound ->
                    com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(server, sound, getX(), getY(), getZ(), new com.juicyslew.moonstation14.ms14.combat.PositionalSoundCue(sound, thrownSoundProfile.landSoundVolumeDb(), 0f).volume(), 1f, SoundSource.NEUTRAL));
            stopThrownCombat();
        }
        if ((hitX || hitZ) && applyHighSpeedImpact(velocity.horizontalDistance(), true)) return;
        if (hitY && travel.y < 0 && pendingThrownLandingDamage) {
            pendingThrownLandingDamage = false;
            if (applyThrowLandDamage()) return;
        }
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
            stopThrownCombat();
            nextSupportCheck = tickCount + 10;
        }
        setDeltaMovement(next);
        if ((hitX || hitY || hitZ) && profile().breakOnImpact()
                && approachSpeed >= profile().breakThreshold()) breakJug();
    }

    static boolean shouldCueThrownLandingSound(boolean armed, boolean alreadyConsumed) {
        return armed && !alreadyConsumed;
    }

    private boolean dispatchThrownContacts(Vec3 from, Vec3 to, Vec3 incoming) {
        if (!activeThrownCombat || !(level() instanceof ServerLevel server)) { previousThrownContacts.clear(); return false; }
        var hitBox = getBoundingBox();
        // The entity has already moved; reconstruct its starting box and union both ends.
        var endBox = hitBox.move(from.subtract(to));
        var search = hitBox.minmax(endBox).inflate(2.0);
        java.util.Set<java.util.UUID> contacts = new java.util.HashSet<>();
        var profile = com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemProfile.resolveForStack(getItem(), false).orElse(null);
        if (profile == null) { previousThrownContacts.clear(); return false; }
        var thrower = thrownByBody == null ? null : server.getEntity(thrownByBody);
        boolean suppressThrowerThisSweep = false;
        if (thrower instanceof net.minecraft.world.entity.LivingEntity livingThrower) {
            var bounds = com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(livingThrower);
            suppressThrowerThisSweep = throwerClearance.suppress(bounds.intersects(getBoundingBox()));
        }
        var hits = new java.util.ArrayList<ThrownBodyContact>();
        for (var candidate : server.getEntities(this, search, e -> e instanceof net.minecraft.world.entity.LivingEntity
                && com.juicyslew.moonstation14.ms14.combat.CommonMeleeTargetEligibility.damageable(e)
                && !e.noPhysics)) {
            var living = (net.minecraft.world.entity.LivingEntity) candidate;
            var bounds = com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(living);
            var intersection = ThrownContactPhysics.firstContact(bounds, getBbWidth(), getBbHeight(), from, to);
            if (living.getUUID().equals(thrownByBody) && suppressThrowerThisSweep) continue;
            if (intersection.isEmpty()) continue;
            contacts.add(living.getUUID());
            if (!previousThrownContacts.contains(living.getUUID()))
                hits.add(new ThrownBodyContact(living, bounds, intersection.get(), from.distanceToSqr(intersection.get())));
        }
        hits.sort(java.util.Comparator.comparingDouble(ThrownBodyContact::distanceSqr));
        for (var hit : hits) {
            var living = hit.body();
            Vec3 point = contactOnSurface(hit.intersection(), hit.bounds());
            emitCollisionCue(collisionSoundPolicy.body(profile, incoming, level().getGameTime()), point.x, point.y, point.z);
            if (!profile.damageOnHit().isEmpty()) com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChangeDetailed(
                    living, profile.damageOnHit(), 1f, profile.ignoreResistances(),
                    com.juicyslew.moonstation14.ms14.damage.DamageSystem.Cause.COMBAT,
                    java.util.Optional.ofNullable(thrownByBody).map(server::getEntity).orElse(this));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.juicyslew.moonstation14.ms14.combat.throwing.ThrownItemHitEvent(
                    this, living, point, incoming, thrownByBody, profile));
            if (!activeThrownCombat || isRemoved()) return true;
            // A contact event that did not attach still blocks flight through bodies behind it.
            // Stop at the first hard contact; terrain response is handled by the normal movement path next tick.
            Vec3 normal = ThrownContactPhysics.normal(hit.bounds(), point, incoming);
            setPos(ThrownContactPhysics.outside(hit.intersection(), normal, getBbWidth(), getBbHeight()));
            setDeltaMovement(ThrownContactPhysics.reflect(incoming, normal, profile().restitution()));
            if (normal.y > 0 && getDeltaMovement().y > 0) setOnGround(false);
            return true;
        }
        previousThrownContacts.clear(); previousThrownContacts.addAll(contacts);
        return false;
    }

    private record ThrownBodyContact(net.minecraft.world.entity.LivingEntity body,
                                     net.minecraft.world.phys.AABB bounds, Vec3 intersection, double distanceSqr) { }

    private void emitCollisionCue(java.util.Optional<com.juicyslew.moonstation14.ms14.combat.PositionalSoundCue> cue, double x, double y, double z) {
        if (cue.isEmpty() || !(level() instanceof ServerLevel server)) return;
        var sound = cue.get();
        com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(server, sound.event(), x, y, z, sound.volume(), 1f, SoundSource.NEUTRAL);
    }

    static Vec3 contactOnSurface(Vec3 point, net.minecraft.world.phys.AABB bounds) {
        return new Vec3(net.minecraft.util.Mth.clamp(point.x, bounds.minX, bounds.maxX),
                net.minecraft.util.Mth.clamp(point.y, bounds.minY, bounds.maxY),
                net.minecraft.util.Mth.clamp(point.z, bounds.minZ, bounds.maxZ));
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
        BlockPos landing = com.juicyslew.moonstation14.ms14.damage.CommonItemDestructionEffects.findSpillSurface(server, getBoundingBox());
        if (landing == null) return;
        spillAt(server, landing, spill);
        // The spill bridge removes only admitted quantity from its argument; keep any remainder authoritative.
        if (spill.isEmpty()) {
            getItem().remove(ModDataComponents.REAGENT.get());
            playJugBreakFeedback(server);
            discard();
        } else if (!spill.equals(contents)) getItem().set(ModDataComponents.REAGENT.get(), spill.toComponent());
    }

    /** Find the first loaded puddle surface or solid support below this contact, without loading chunks. */
    /** Shared loaded-only spill attempt; ReagentSystem retains its normal admitted-quantity policy. */
    private static void spillAt(ServerLevel server, BlockPos landing, ReagentAttachment spill) {
        ReagentSystem.handleSpillSolution(spill, server, landing);
    }

    private boolean applyHighSpeedImpact(double horizontalSpeedPerTick, boolean hardHorizontalContact) {
        if (!(level() instanceof ServerLevel server)) return false;
        var profile = profile().itemDamage();
        if (profile == null || damageDestructionStarted) return false;
        ItemStack ownedStack = getItem();
        ItemDamageState old = ownedStack.getOrDefault(ModDataComponents.ITEM_DAMAGE_STATE.get(), ItemDamageState.EMPTY);
        var impact = ItemDamageKernel.highSpeedImpact(old, profile.highSpeedImpact(), profile.allowedGroups(), profile.allowedTypes(),
                profile.minimumImpactSpeed(), profile.impactFactor(), profile.impactCooldownTicks(),
                server.getGameTime(), horizontalSpeedPerTick, hardHorizontalContact, profile.destructionThreshold(), profile.damageModifiers());
        if (!impact.applied()) return false;
        if (isRemoved() || getItem() != ownedStack) return false;
        ownedStack.set(ModDataComponents.ITEM_DAMAGE_STATE.get(), impact.state());
        destroyAtAccumulatedThreshold(server, profile, ownedStack, DamageReducer.total(impact.state().damage()));
        return isRemoved();
    }

    private boolean applyThrowLandDamage() {
        if (!(level() instanceof ServerLevel server)) return false;
        var profile = profile().itemDamage();
        if (profile == null || damageDestructionStarted || profile.throwLandDamage().isEmpty()) return false;
        ItemStack ownedStack = getItem();
        ItemDamageState old = ownedStack.getOrDefault(ModDataComponents.ITEM_DAMAGE_STATE.get(), ItemDamageState.EMPTY);
        ItemDamageState next = ItemDamageKernel.apply(old, profile.throwLandDamage(), profile.allowedGroups(), profile.allowedTypes(), profile.damageModifiers());
        if (isRemoved() || getItem() != ownedStack) return false;
        ownedStack.set(ModDataComponents.ITEM_DAMAGE_STATE.get(), next);
        destroyAtAccumulatedThreshold(server, profile, ownedStack, DamageReducer.total(next.damage()));
        return isRemoved();
    }

    /** Positive typed damage from an external source; resistance bypass skips self modifiers, not damage gates. */
    public boolean applyTypedItemDamage(ServerLevel server, java.util.Map<String, Float> raw, boolean ignoreResistances) {
        if (server == null || server != level() || server.isClientSide || server.getServer() == null
                || !server.getServer().isSameThread() || server.getEntity(getUUID()) != this
                || isRemoved() || damageDestructionStarted || !isAddedToLevel() || raw == null) return false;
        ItemStack ownedStack = getItem();
        if (ownedStack == null || ownedStack.isEmpty()) return false;
        LooseObjectProfile.ItemDamageComposition profile;
        try { profile = profile().itemDamage(); } catch (RuntimeException unavailable) { return false; }
        if (profile == null) return false;
        ItemDamageState old = ownedStack.getOrDefault(ModDataComponents.ITEM_DAMAGE_STATE.get(), ItemDamageState.EMPTY);
        ItemDamageState next;
        try {
            if (!raw.values().stream().anyMatch(value -> value != null && Float.isFinite(value) && value > 0)) return false;
            var modifiers = ignoreResistances ? com.juicyslew.moonstation14.ms14.damage.DamageModifierData.EMPTY : profile.damageModifiers();
            if (!ItemDamageKernel.hasApplicableDamage(raw, profile.allowedGroups(), profile.allowedTypes(), modifiers)) return false;
            next = ItemDamageKernel.apply(old, raw, profile.allowedGroups(), profile.allowedTypes(), modifiers);
        }
        catch (RuntimeException invalidDamage) { return false; }
        if (isRemoved() || getItem() != ownedStack || damageDestructionStarted) return false;
        ownedStack.set(ModDataComponents.ITEM_DAMAGE_STATE.get(), next);
        destroyAtAccumulatedThreshold(server, profile, ownedStack, DamageReducer.total(next.damage()));
        return true;
    }

    private void destroyAtAccumulatedThreshold(ServerLevel server, LooseObjectProfile.ItemDamageComposition profile,
                                                ItemStack ownedStack, float accumulated) {
        switch (ItemDamageKernel.classifyDestruction(accumulated, profile.destructionThreshold(), profile.overkillDestructionThreshold())) {
            case OVERKILL -> destroyByItemDamage(server, profile, ownedStack, true);
            case NORMAL -> destroyByItemDamage(server, profile, ownedStack);
            case NONE -> { }
        }
    }

    private void destroyByItemDamage(ServerLevel server, LooseObjectProfile.ItemDamageComposition profile, ItemStack ownedStack) {
        destroyByItemDamage(server, profile, ownedStack, false);
    }

    private void destroyByItemDamage(ServerLevel server, LooseObjectProfile.ItemDamageComposition profile, ItemStack ownedStack, boolean overkill) {
        if (damageDestructionStarted || isRemoved() || getItem() != ownedStack) return;
        damageDestructionStarted = true;
        if (overkill) { discard(); return; }
        Vec3 origin = new Vec3(getX(), getBoundingBox().minY, getZ());
        BlockPos spillSurface = profile.spillCurrentReagents() ? com.juicyslew.moonstation14.ms14.damage.CommonItemDestructionEffects.findSpillSurface(server, getBoundingBox()) : null;
        var itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ownedStack.getItem());
        com.juicyslew.moonstation14.ms14.damage.CommonItemDestructionEffects.publish(server, origin, spillSurface,
                ownedStack, profile, () -> !isRemoved() && getItem() == ownedStack
                        && itemId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(getItem().getItem())),
                () -> { discard(); return com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS; });
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
        if (pendingThrownLandingDamage) tag.putBoolean("LoosePendingThrownLandingDamage", true);
        attachment().ifPresent(a -> a.writeTo(tag));
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setRotationAngle(tag.getFloat("LooseRotation"));
        angularVelocity = tag.contains("LooseAngularVelocity") && Float.isFinite(tag.getFloat("LooseAngularVelocity")) ? tag.getFloat("LooseAngularVelocity") : 0;
        setSettled(tag.getBoolean("LooseSettled"));
        creativePickupDelay = Math.max(0, Math.min(40, tag.getInt("LooseCreativePickupDelay")));
        pendingThrownLandingDamage = tag.getBoolean("LoosePendingThrownLandingDamage");
        ItemAttachment.readFrom(tag).ifPresent(a -> {
            entityData.set(ATTACH_TARGET, java.util.Optional.of(a.targetUUID()));
            entityData.set(ATTACH_TARGET_ID, a.targetEntityId());
            entityData.set(ATTACH_KIND, a.kind().ordinal());
            entityData.set(ATTACH_TICK, a.attachedAtTick());
            entityData.set(ATTACH_CONTACT, toVector(a.targetLocalContact()));
            entityData.set(ATTACH_DIRECTION, toVector(a.targetLocalDirection()));
            setNoGravity(true);
        });
    }
}
