package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Objects;

/** Projects the authoritative movement payload onto one deterministic modifier. */
public final class MovementSpeedProjection {
    private static final String MODIFIER_PATH = "status_effect/movement_speed/";

    private MovementSpeedProjection() {
    }

    /** Returns the stable modifier ID for a status prototype key. */
    public static ResourceLocation modifierId(ResourceKey<StatusEffectData> key) {
        Objects.requireNonNull(key, "status effect key");
        ResourceLocation status = key.location();
        return ResourceLocation.fromNamespaceAndPath(status.getNamespace(), MODIFIER_PATH + status.getPath());
    }

    /** Removes the projection if present, without disturbing other modifiers. */
    public static void remove(LivingEntity entity, ResourceKey<StatusEffectData> key) {
        Objects.requireNonNull(entity, "entity");
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.removeModifier(modifierId(key));
        }
    }

    /**
     * Reconciles one projection.  A null payload means that the status is not
     * currently projecting movement (pending, removed, or behavior-less).
     */
    public static void reconcile(LivingEntity entity, ResourceKey<StatusEffectData> key,
                                 StatusEffectPayload.MovementSpeedModifier payload) {
        Objects.requireNonNull(entity, "entity");
        ResourceLocation id = modifierId(key);
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null || payload == null) {
            if (attribute != null) {
                attribute.removeModifier(id);
            }
            return;
        }

        double amount = (double) payload.multiplier() - 1.0d;
        AttributeModifier current = attribute.getModifier(id);
        if (current != null && Double.compare(current.amount(), amount) == 0
                && current.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            return;
        }
        if (current != null) {
            attribute.removeModifier(id);
        }
        attribute.addTransientModifier(new AttributeModifier(
                id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
