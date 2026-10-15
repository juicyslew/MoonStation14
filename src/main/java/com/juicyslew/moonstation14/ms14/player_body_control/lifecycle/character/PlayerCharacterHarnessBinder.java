package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.UUID;

/** Explicit server-only binding API; it never spawns an entity or changes the operator debug route. */
public final class PlayerCharacterHarnessBinder {
    private PlayerCharacterHarnessBinder() { }

    public static boolean bind(Mob body, UUID accountId, String profileKey,
                               UUID mindId, ResourceLocation characterPrototypeKey) {
        if (body == null || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return false;
        if (body.isRemoved() || !body.isAddedToLevel()
                || body.level().isClientSide || level.getEntity(body.getUUID()) != body) return false;
        boolean legacyHarness = body instanceof PlayerCharacterHarnessEntity;
        if (legacyHarness && (body.getType() != PlayerCharacterHarnessRegistration.getEntityType()
                || ((PlayerCharacterHarnessEntity) body).isInvalidUnbindable())) return false;

        PlayerCharacterBinding requested;
        try {
            requested = new PlayerCharacterBinding(Objects.requireNonNull(accountId),
                    Objects.requireNonNull(profileKey), Objects.requireNonNull(mindId));
        } catch (RuntimeException invalid) {
            return false;
        }
        ResourceLocation resolvedHost = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(body.getType());
        if (legacyHarness) {
            if (!ModCharacters.HUMAN_ID.equals(characterPrototypeKey)
                    || ModCharacters.characterForHost(level, PlayerCharacterHarnessRegistration.ID).isPresent()
                    || CharacterIdentitySystem.resolve(level, characterPrototypeKey).isEmpty()) return false;
        } else if (!ModCharacters.characterForHost(level, resolvedHost).filter(characterPrototypeKey::equals).isPresent()
                || CharacterIdentitySystem.resolveHost(body, level, characterPrototypeKey).isEmpty()) return false;

        PlayerCharacterBinding current = binding(body);
        if (!canReuseBinding(current, requested)) return false;
        CharacterIdentityAttachment identity = body.getExistingDataOrNull(
                com.juicyslew.moonstation14.component.ModDataAttachments.CHARACTER_IDENTITY.get());
        if (identity != null && (!identity.isBound() || !identity.characterId().equals(characterPrototypeKey))) {
            return false;
        }
        if (current != null && identity == null) return false;

        if (current != null && !current.equals(requested)) return false;
        if (!CharacterIdentitySystem.enroll(body, level, characterPrototypeKey)) return false;
        if (current == null) {
            if (legacyHarness) ((PlayerCharacterHarnessEntity) body).setPlayerCharacterBinding(requested);
            else body.setData(ModDataAttachments.MOB_LIFECYCLE_BINDING.get(), requested);
        }
        if (legacyHarness) return CharacterIdentitySystem.resolveForActor(body).isPresent();
        return CharacterIdentitySystem.resolve(body).filter(data -> data.hostEntityTypes().contains(resolvedHost)).isPresent();
    }

    /** Exact binding inspection; does not resolve or infer ownership from a prototype key. */
    public static PlayerCharacterBinding binding(Entity entity) {
        if (entity instanceof PlayerCharacterHarnessEntity harness) return harness.playerCharacterBinding();
        return entity == null ? null : entity.getExistingDataOrNull(ModDataAttachments.MOB_LIFECYCLE_BINDING.get());
    }

    /** Read-only allocation preflight for a mapped Mob; does not enroll identity or persist a binding. */
    public static boolean canBindMappedMob(Mob body, UUID accountId, String profileKey, UUID mindId,
                                           ResourceLocation characterPrototypeKey) {
        if (body == null || body instanceof PlayerCharacterHarnessEntity
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || body.isRemoved() || !body.isAddedToLevel()
                || body.isPassenger() || level.getEntity(body.getUUID()) != body
                || binding(body) != null || accountId == null || mindId == null
                || !isSupportedPrototypeKey(characterPrototypeKey)) return false;
        try { new PlayerCharacterBinding(accountId, profileKey, mindId); }
        catch (RuntimeException invalid) { return false; }
        var identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        return (identity == null || identity.isBound() && identity.characterId().equals(characterPrototypeKey))
                && CharacterIdentitySystem.resolveHost(body, level, characterPrototypeKey).isPresent()
                && ModCharacters.characterForHost(level,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()))
                .filter(characterPrototypeKey::equals).isPresent();
    }
    static boolean canReuseBinding(PlayerCharacterBinding current, PlayerCharacterBinding requested) {
        return current == null || current.equals(requested);
    }

    static boolean isSupportedPrototypeKey(ResourceLocation prototypeKey) {
        return prototypeKey != null;
    }
}

