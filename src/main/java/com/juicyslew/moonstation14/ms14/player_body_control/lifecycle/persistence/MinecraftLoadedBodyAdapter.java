package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** Read-only, loaded-only Minecraft observation for the pure lifecycle body resolver. */
public final class MinecraftLoadedBodyAdapter {
    private static final double WORLD_COORDINATE_LIMIT = 30_000_000.0;

    private MinecraftLoadedBodyAdapter() { }

    public static LoadedBodyResolver.Resolution observe(MinecraftServer server, SavedLifecycleProfile profile) {
        // Both startup-latched decisions must be checked before even consulting thread/world state.
        boolean enabled = MindGhostStartupGate.enabledForServer();
        boolean movementConflict = MovementStartupGate.enabledForServer();
        if (!enabled || movementConflict) return disabled();
        if (server == null || profile == null) return recovery();
        return observe(profile, enabled, movementConflict, server.isSameThread(),
                id -> find(server, profile, id), MinecraftLoadedBodyAdapter::eligible);
    }

    static LoadedBodyResolver.Resolution observe(SavedLifecycleProfile profile, boolean masterEnabled,
                                                boolean movementConflict, boolean mainThread,
                                                LoadedBodyResolver.ReadOnlyLookup lookup,
                                                java.util.function.Predicate<LoadedBodyResolver.Candidate> eligible) {
        if (!masterEnabled || movementConflict)
            return disabled();
        if (profile == null) return recovery();
        if (!mainThread) return recovery();
        return LoadedBodyResolver.resolve(profile, true, lookup, eligible);
    }

    private static LoadedBodyResolver.Resolution disabled() {
        return new LoadedBodyResolver.Resolution(LoadedBodyResolver.Outcome.DISABLED, null);
    }

    private static LoadedBodyResolver.Resolution recovery() {
        return new LoadedBodyResolver.Resolution(LoadedBodyResolver.Outcome.RECOVERY_REQUIRED, null);
    }

    private static LoadedBodyResolver.LookupResult find(MinecraftServer server, SavedLifecycleProfile profile,
                                                        java.util.UUID bodyId) {
        List<Entity> found = new ArrayList<>(2);
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(bodyId); // exact UUID lookup is loaded-entity-only
            if (entity != null) found.add(entity);
        }
        if (found.size() > 1) return LoadedBodyResolver.LookupResult.ambiguous(candidates(found, profile));
        if (found.size() == 1) return LoadedBodyResolver.LookupResult.loaded(candidate(found.get(0), profile));

        ServerLevel recordedLevel = null;
        ResourceLocation dimension;
        try {
            dimension = ResourceLocation.parse(profile.dimension());
        } catch (RuntimeException invalidDimension) {
            return LoadedBodyResolver.LookupResult.missing();
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) {
                if (recordedLevel != null) return LoadedBodyResolver.LookupResult.ambiguous(List.of());
                recordedLevel = level;
            }
        }
        if (recordedLevel == null) return LoadedBodyResolver.LookupResult.missing();
        Integer chunkX = savedChunkCoordinate(profile.location().x());
        Integer chunkZ = savedChunkCoordinate(profile.location().z());
        if (chunkX == null || chunkZ == null || !withinWorld(profile.location().y()))
            return LoadedBodyResolver.LookupResult.missing();
        if (!recordedLevel.getChunkSource().hasChunk(chunkX, chunkZ))
            return LoadedBodyResolver.LookupResult.unloaded();
        // A loaded recorded chunk without the UUID is ambiguous recovery evidence, never absence.
        return LoadedBodyResolver.LookupResult.missing();
    }

    /** Returns null for saved coordinates outside the supported Minecraft world/int range. */
    public static Integer savedChunkCoordinate(double coordinate) {
        if (!withinWorld(coordinate)) return null;
        double block = Math.floor(coordinate);
        if (block < Integer.MIN_VALUE || block > Integer.MAX_VALUE) return null;
        return ((int) block) >> 4;
    }

    private static boolean withinWorld(double coordinate) {
        return Double.isFinite(coordinate) && coordinate >= -WORLD_COORDINATE_LIMIT
                && coordinate <= WORLD_COORDINATE_LIMIT;
    }

    private static List<LoadedBodyResolver.Candidate> candidates(List<Entity> entities,
                                                                  SavedLifecycleProfile profile) {
        return entities.stream().map(entity -> candidate(entity, profile)).toList();
    }

    private static LoadedBodyResolver.Candidate candidate(Entity entity, SavedLifecycleProfile profile) {
        PlayerCharacterBinding binding = entity instanceof PlayerCharacterHarnessEntity harness
                && !harness.hasInvalidSavedBinding() ? harness.playerCharacterBinding() : null;
        return new LoadedBodyResolver.Candidate(entity.getUUID(), binding == null ? null : binding.mindId(),
                entity.level().dimension().location().toString(), binding == null ? null : binding.accountId(),
                binding == null ? null : binding.profileKey(), entity instanceof LivingEntity living && living.isAlive()
                && !living.isDeadOrDying() && !entity.isRemoved(), entity);
    }

    private static boolean eligible(LoadedBodyResolver.Candidate candidate) {
        if (!(candidate.entity() instanceof PlayerCharacterHarnessEntity harness)
                || harness.getType() != PlayerCharacterHarnessRegistration.getEntityType()
                || harness.hasInvalidSavedBinding() || harness.playerCharacterBinding() == null
                || harness.isRemoved() || !harness.isAlive() || harness.isDeadOrDying()) return false;
        CharacterIdentityAttachment attachment = harness.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        return attachment != null && attachment.isBound()
                && ModCharacters.HUMAN_ID.equals(attachment.characterId())
                && CharacterIdentitySystem.resolve(harness).isPresent();
    }
}
