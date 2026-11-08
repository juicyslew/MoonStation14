package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.explosion.grenade.server.GrenadeDetonationService;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

/** Server-owned projectile for an already-armed grenade. */
public class ThrownGrenadeEntity extends ThrowableItemProjectile {
    private boolean invalidState;
    private Vec3 pendingImpactPosition;
    private Direction lastBlockHitFace;
    private boolean restingOnFloor;

    public ThrownGrenadeEntity(EntityType<? extends ThrownGrenadeEntity> type, Level level) {
        super(type, level);
    }

    public ThrownGrenadeEntity(Level level, LivingEntity shooter, ItemStack stack) {
        super(ModEntities.THROWN_GRENADE.get(), shooter, level);
        initializeArmedStack(stack);
    }

    /** Creates an armed projectile emitted by a device/world process, with no actor attribution. */
    public static ThrownGrenadeEntity fromWorld(Level level, ItemStack stack) {
        ThrownGrenadeEntity entity = new ThrownGrenadeEntity(ModEntities.THROWN_GRENADE.get(), level);
        entity.initializeArmedStack(stack);
        return entity;
    }

    private void initializeArmedStack(ItemStack stack) {
        if (stack == null || stack.getCount() != 1 || stack.get(ModDataComponents.GRENADE_FUSE.get()) == null
                || stack.get(ModDataComponents.GRENADE_FUSE.get()).phase() != GrenadeFuse.Phase.ARMED) {
            invalidState = true;
            return;
        }
        setItem(stack.copy());
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.GRENADE.get();
    }

    @Override
    public void tick() {
        boolean originallyNoGravity = isNoGravity();
        // A low-speed floor impact settles at the block's top face. Keep gravity enabled
        // generally, but avoid applying it while that loaded support still exists.
        if (restingOnFloor && hasFloorSupport()) {
            setDeltaMovement(getDeltaMovement().x, 0, getDeltaMovement().z);
            setNoGravity(true);
        } else {
            restingOnFloor = false;
            setNoGravity(originallyNoGravity);
        }
        super.tick();
        setNoGravity(originallyNoGravity);
        // ThrowableProjectile's tick performs its final setPos after onHit returns. Apply
        // face separation after that movement so the inherited position update can't undo it.
        if (pendingImpactPosition != null) {
            Vec3 position = pendingImpactPosition;
            pendingImpactPosition = null;
            if (level().hasChunkAt(net.minecraft.core.BlockPos.containing(position))) {
                setPos(position.x, position.y, position.z);
            }
        }
        if (restingOnFloor && hasFloorSupport()) dampFloorSlide();
        if (!(level() instanceof ServerLevel server)) return;
        if (invalidState || !validArmedStack()) {
            discard();
            return;
        }
        GrenadeFuseData data = getItem().get(ModDataComponents.GRENADE_FUSE.get());
        final GrenadeFuse fuse;
        try {
            fuse = data.toFuse();
        } catch (RuntimeException exception) {
            discard();
            return;
        }
        GrenadeFuse.Evaluation evaluation;
        try {
            evaluation = fuse.evaluate(server.getGameTime());
        } catch (RuntimeException exception) {
            discard();
            return;
        }
        if (evaluation.beepDue()) {
            server.playSound(null, getX(), getY(), getZ(), ModSounds.GRENADE_BEEP.get(), SoundSource.BLOCKS, 0.35f, 1.0f);
        }
        getItem().set(ModDataComponents.GRENADE_FUSE.get(), GrenadeFuseData.fromFuse(evaluation.state()));
        if (evaluation.expired()) {
            // Persist terminal state before the destructive bridge; this entity can never retry a blast.
            setDeltaMovement(0, 0, 0);
            GrenadeDetonationService.detonate(server, blockPosition());
            discard();
        }
    }

    private boolean hasFloorSupport() {
        var bounds = getBoundingBox();
        double underside = bounds.minY;
        var below = net.minecraft.core.BlockPos.containing(getX(), underside - 0.02, getZ());
        if (!level().hasChunkAt(below)) return false;
        var shape = level().getBlockState(below).getCollisionShape(level(), below);
        for (var box : shape.toAabbs()) {
            double top = below.getY() + box.maxY;
            if (Math.abs(top - underside) < 0.03
                    && bounds.maxX > below.getX() + box.minX && bounds.minX < below.getX() + box.maxX
                    && bounds.maxZ > below.getZ() + box.minZ && bounds.minZ < below.getZ() + box.maxZ) return true;
        }
        return false;
    }

    private void dampFloorSlide() {
        var motion = getDeltaMovement();
        double x = motion.x * 0.68, z = motion.z * 0.68;
        if (Math.abs(x) < 0.015) x = 0;
        if (Math.abs(z) < 0.015) z = 0;
        setDeltaMovement(x, 0, z);
    }

    private boolean validArmedStack() {
        ItemStack stack = getItem();
        if (stack.isEmpty() || stack.getItem() != ModItems.GRENADE.get() || stack.getCount() != 1) return false;
        GrenadeFuseData data = stack.get(ModDataComponents.GRENADE_FUSE.get());
        return data != null && data.phase() == GrenadeFuse.Phase.ARMED;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        // Reflect and damp the impact velocity; position correction is deferred until after
        // the inherited projectile tick completes its movement and final setPos.
        var velocity = getDeltaMovement();
        Direction face = result instanceof BlockHitResult blockHit ? blockHit.getDirection() : null;
        if (result instanceof BlockHitResult blockHit) {
            lastBlockHitFace = face;
            Vec3 contact = blockHit.getLocation();
            double radius = getBbWidth() * 0.5 + 0.001;
            double px = contact.x + face.getStepX() * radius;
            double py = contact.y + face.getStepY() * radius;
            double pz = contact.z + face.getStepZ() * radius;
            if (face == Direction.UP) py = contact.y + 0.001;
            else if (face == Direction.DOWN) py = contact.y - getBbHeight() - 0.001;
            // Collision queries only return hits in loaded space. Do not move the entity into
            // a potentially unloaded neighboring chunk just to separate it from the face.
            pendingImpactPosition = new Vec3(px, py, pz);
        }
        double nx, ny, nz;
        if (face != null) {
            nx = face.getStepX(); ny = face.getStepY(); nz = face.getStepZ();
        } else {
            // No entity damage: treat an entity contact as a soft bounce opposite travel,
            // so the projectile doesn't remain embedded and retrigger the same hit each tick.
            double length = velocity.length();
            if (length < 1.0e-6 || !Double.isFinite(length)) return;
            nx = -velocity.x / length; ny = -velocity.y / length; nz = -velocity.z / length;
        }
        double into = velocity.x * nx + velocity.y * ny + velocity.z * nz;
        if (into >= 0) return;
        double restitution = face == Direction.UP ? (velocity.y < -0.22 ? 0.42 : 0.0) : 0.35;
        double x = velocity.x - (1.0 + restitution) * into * nx;
        double y = velocity.y - (1.0 + restitution) * into * ny;
        double z = velocity.z - (1.0 + restitution) * into * nz;
        if (face == Direction.UP) { x *= 0.62; z *= 0.62; }
        if (face == Direction.UP && velocity.y >= -0.22) {
            y = 0;
            restingOnFloor = true;
        } else if (face == Direction.UP) {
            restingOnFloor = false;
        }
        double speed = Math.sqrt(x * x + y * y + z * z);
        if (!Double.isFinite(speed)) {
            setDeltaMovement(0, 0, 0);
            return;
        }
        if (speed > 1.5) { x *= 1.5 / speed; y *= 1.5 / speed; z *= 1.5 / speed; }
        setDeltaMovement(x, y, z);
    }

    public Direction lastBlockHitFace() {
        return lastBlockHitFace;
    }

}

