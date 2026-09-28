package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.function.Supplier;

/** Read-only, loaded-world proof that an exact offline character body is actually dead. */
public final class MinecraftCharacterDeathEvidence {
    private MinecraftCharacterDeathEvidence() { }

    /**
     * Verifies actual death only. This does not mutate a lifecycle record, remove a body, or spawn a ghost.
     */
    public static boolean verify(MinecraftServer server, SavedLifecycleProfile offlineProfile,
                                 MobHarness characterId) {
        // Startup latches are intentionally consulted before server/thread/world state.
        boolean enabled = MindGhostStartupGate.enabledForServer();
        boolean movementConflict = MovementStartupGate.enabledForServer();
        if (!enabled || movementConflict) return false;
        if (server == null || offlineProfile == null || characterId == null
                || !server.isSameThread()) return false;
        if (!isMatchingOfflineIdentity(offlineProfile, characterId)) return false;

        return verifyObserved(offlineProfile, characterId, enabled, movementConflict, true,
                () -> observe(server, offlineProfile));
    }

    private static Observation observe(MinecraftServer server, SavedLifecycleProfile profile) {
        ResourceLocation dimension;
        try {
            dimension = ResourceLocation.parse(profile.dimension());
        } catch (RuntimeException invalidDimension) {
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null || !level.dimension().location().equals(dimension)) return null;
        // ServerLevel#getEntity is a loaded-entity lookup; never request or force-load a chunk.
        Entity entity = level.getEntity(profile.bodyId());
        if (!(entity instanceof PlayerCharacterHarnessEntity harness)
                || harness.getType() != PlayerCharacterHarnessRegistration.getEntityType()
                || harness.isRemoved() || harness.hasInvalidSavedBinding()) return null;

        PlayerCharacterBinding binding = harness.playerCharacterBinding();
        CharacterIdentityAttachment identity = harness.getExistingDataOrNull(
                ModDataAttachments.CHARACTER_IDENTITY.get());
        boolean humanPrototype = identity != null && identity.isBound()
                && ModCharacters.HUMAN_ID.equals(identity.characterId())
                && CharacterIdentitySystem.resolve(harness).isPresent();
        LivingEntity living = harness;
        return new Observation(harness.getUUID(), level.dimension().location().toString(),
                binding == null ? null : binding.accountId(), binding == null ? null : binding.profileKey(),
                binding == null ? null : binding.mindId(), true, !harness.hasInvalidSavedBinding(),
                humanPrototype, living.isDeadOrDying() || living.getHealth() <= 0.0F);
    }

    private static boolean isMatchingOfflineIdentity(SavedLifecycleProfile profile, MobHarness character) {
        return profile.state() == SavedLifecycleProfile.State.OFFLINE
                && character.kind() == MobHarnessKind.CHARACTER
                && profile.bodyId().equals(character.id().value());
    }

    /** Pure seam: verifies negative/positive evidence without constructing a Minecraft world. */
    static boolean verifyObserved(SavedLifecycleProfile profile, MobHarness character, boolean enabled,
                                  boolean movementConflict, boolean mainThread,
                                  Supplier<Observation> lookup) {
        if (!enabled || movementConflict) return false;
        if (profile == null || character == null || !mainThread
                || !isMatchingOfflineIdentity(profile, character) || lookup == null) return false;
        Observation observation = lookup.get();
        if (observation == null) return false;
        return profile.bodyId().equals(observation.entityId())
                && profile.dimension().equals(observation.dimension())
                && profile.accountId().equals(observation.accountId())
                && profile.profileKey().equals(observation.profileKey())
                && profile.mindId().equals(observation.mindId())
                && observation.registeredCustomEntity() && observation.validBinding()
                && observation.humanPrototype() && observation.dead();
    }

    static record Observation(UUID entityId, String dimension, UUID accountId, String profileKey, UUID mindId,
                              boolean registeredCustomEntity, boolean validBinding,
                              boolean humanPrototype, boolean dead) { }
}
