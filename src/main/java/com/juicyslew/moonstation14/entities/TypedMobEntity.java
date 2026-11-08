package com.juicyslew.moonstation14.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** Native living host for prototype-bound mobs; behavior is supplied by typed consumers. */
public class TypedMobEntity extends Mob {
    public TypedMobEntity(EntityType<? extends TypedMobEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 20.0);
    }
}
