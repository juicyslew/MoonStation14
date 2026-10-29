package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;

/** Shared public-name policy: custom name, bound saved personal identity, resolved metadata, then native name. */
public final class CharacterPublicName {
    private CharacterPublicName() { }

    public static Component component(Entity entity) {
        if (entity == null) return Component.empty();
        if (entity.hasCustomName()) return entity.getCustomName();
        if (entity instanceof LivingEntity living) {
            Component personalName = boundPersonalName(living);
            if (personalName != null) return personalName;
            CharacterData data = (living.level().isClientSide()
                    ? CharacterIdentitySystem.projectForActor(living)
                    : CharacterIdentitySystem.resolveForActor(living)).orElse(null);
            if (data != null) return fromMetadata(data.metadata(), entity.getDisplayName());
        }
        return entity.getDisplayName();
    }

    /** A body's saved identity follows its durable account/profile binding, not its current controller. */
    private static Component boundPersonalName(LivingEntity entity) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return null;
        var binding = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                .PlayerCharacterHarnessBinder.binding(entity);
        if (binding == null || level.getServer() == null) return null;
        return com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData
                .existing(level.getServer().overworld())
                .flatMap(data -> data.character(new com.juicyslew.moonstation14.ms14.chat.identity
                        .ChatIdentityRegistry.CharacterKey(binding.accountId(), binding.profileKey())))
                .map(identity -> Component.literal(identity.name())).orElse(null);
    }

    /** Resolved prototype names are already public human-readable values; preserve native fallback components. */
    public static Component fromMetadata(ExamineMetadata metadata, Component nativeFallback) {
        return metadata != null && metadata.hasName() ? Component.literal(metadata.name()) : nativeFallback;
    }
}
