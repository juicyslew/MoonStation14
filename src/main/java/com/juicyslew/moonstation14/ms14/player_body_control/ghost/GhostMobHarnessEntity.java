package com.juicyslew.moonstation14.ms14.player_body_control.ghost;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

/**
 * A minimal, non-player ghost body intended to be a future controllable harness.
 *
 * <p>Like SS14's observer, this has no gravity and ignores ordinary collision.
 * Minecraft's {@code noPhysics} is broader than SS14's ghost-impenetrable wall
 * rule: it passes through every block/entity collision, so this is only a
 * bounded vertical-slice approximation.</p>
 */
public final class GhostMobHarnessEntity extends Mob {
    public GhostMobHarnessEntity(EntityType<? extends GhostMobHarnessEntity> entityType, Level level) {
        super(entityType, level);
        applyGhostMovementPolicy();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0);
    }

    /** Observer bodies do not take ordinary or environmental damage. Session owners
     * still end them explicitly by discarding the transient entity. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void tick() {
        applyGhostMovementPolicy();
        super.tick();
    }

    private void applyGhostMovementPolicy() {
        setNoAi(true);
        setNoGravity(true);
        // Minecraft has no per-block ghost-impenetrable collision category.
        noPhysics = true;
    }
}
