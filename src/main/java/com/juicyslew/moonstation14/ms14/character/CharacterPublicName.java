package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;

/** Shared public-name policy: native custom identity, resolved character metadata, then native type name. */
public final class CharacterPublicName {
    private CharacterPublicName() { }

    public static Component component(Entity entity) {
        if (entity == null) return Component.empty();
        if (entity.hasCustomName()) return entity.getCustomName();
        if (entity instanceof LivingEntity living) {
            CharacterData data = (living.level().isClientSide()
                    ? CharacterIdentitySystem.projectForActor(living)
                    : CharacterIdentitySystem.resolveForActor(living)).orElse(null);
            if (data != null) return fromMetadata(data.metadata(), entity.getDisplayName());
        }
        return entity.getDisplayName();
    }

    /** Resolved prototype names are already public human-readable values; preserve native fallback components. */
    public static Component fromMetadata(ExamineMetadata metadata, Component nativeFallback) {
        return metadata != null && metadata.hasName() ? Component.literal(metadata.name()) : nativeFallback;
    }
}
