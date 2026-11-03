package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentityRegistry;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData;
import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;

/** Shared public-name policy: custom name, bound saved personal identity, resolved metadata, then native name. */
public final class CharacterPublicName {
    private CharacterPublicName() { }

    public static Component component(Entity entity) {
        if (entity == null) return Component.empty();
        if (entity.hasCustomName()) return entity.getCustomName();
        if (entity instanceof LivingEntity living) {
            Component personalName = savedIdentity(living).map(identity -> Component.literal(identity.name())).orElse(null);
            if (personalName != null) return personalName;
            CharacterData data = (living.level().isClientSide()
                    ? CharacterIdentitySystem.projectForActor(living)
                    : CharacterIdentitySystem.resolveForActor(living)).orElse(null);
            if (data != null) return fromMetadata(data.metadata(), entity.getDisplayName());
        }
        return entity.getDisplayName();
    }

    /** Exact current body identity, read-only; never falls back through historical profile or allocates. */
    public static java.util.Optional<ChatIdentityRegistry.Entry> savedIdentity(LivingEntity entity) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return java.util.Optional.empty();
        var binding = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                .PlayerCharacterHarnessBinder.binding(entity);
        if (level.getServer() == null) return java.util.Optional.empty();
        ChatIdentitySavedData data = ChatIdentitySavedData.existing(level.getServer().overworld()).orElse(null);
        if (data == null) return java.util.Optional.empty();
        if (binding != null) {
            var current = data.character(new ChatIdentityRegistry.CharacterKey(binding.accountId(), binding.profileKey()));
            if (current.isPresent()) return current;
        }
        var npc = data.npc(entity.getUUID());
        return npc.map(entry -> new ChatIdentityRegistry.Entry(entry.name(), entry.rgb()));
    }

    /** Resolved prototype names are already public human-readable values; preserve native fallback components. */
    public static Component fromMetadata(ExamineMetadata metadata, Component nativeFallback) {
        return metadata != null && metadata.hasName() ? Component.literal(metadata.name()) : nativeFallback;
    }
}
