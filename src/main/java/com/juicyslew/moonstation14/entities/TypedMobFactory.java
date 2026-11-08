package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

import java.util.Objects;
import java.util.Optional;

/** Creates detached, correctly prototype-bound mobs; callers own world publication. */
public final class TypedMobFactory {
    private TypedMobFactory() { }

    public static Optional<TypedMobEntity> create(ServerLevel level,
            EntityType<? extends TypedMobEntity> type, ResourceLocation characterId, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(type, "entity type");
        Objects.requireNonNull(characterId, "character id");
        Objects.requireNonNull(position, "position");
        if (level.getServer() == null || !level.getServer().isSameThread()) return Optional.empty();
        ResourceLocation host = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (CharacterIdentitySystem.resolveForHost(ModCharacters.catalog(level), characterId, host).isEmpty())
            return Optional.empty();
        TypedMobEntity entity = type.create(level);
        if (entity == null) return Optional.empty();
        entity.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        if (!CharacterIdentitySystem.enroll(entity, level, characterId)) {
            entity.discard();
            return Optional.empty();
        }
        return Optional.of(entity);
    }
}
