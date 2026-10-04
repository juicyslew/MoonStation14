package com.juicyslew.moonstation14.ms14.disposal.item;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.ThrownGrenadeEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stages disposal payloads in the world, preserving whole-stack metadata and armed grenade ticking. */
public final class DisposalWorldPayloadStage {
    private DisposalWorldPayloadStage() { }

    /** Adds the entity before returning its ledger location; failed insertion leaves no staged entity. */
    public static Optional<Staged> stage(ServerLevel level, ItemStack stack, Vec3 position, Vec3 velocity) {
        Objects.requireNonNull(level); Objects.requireNonNull(stack);
        Objects.requireNonNull(position); Objects.requireNonNull(velocity);
        if (stack.isEmpty()) return Optional.empty();
        Entity entity;
        if (armedGrenade(stack)) {
            if (stack.getCount() != 1) return Optional.empty();
            entity = ThrownGrenadeEntity.fromWorld(level, stack.copy());
        } else {
            ItemEntity item = new ItemEntity(level, position.x, position.y, position.z, stack.copy());
            item.setDeltaMovement(velocity);
            entity = item;
        }
        entity.setPos(position.x, position.y, position.z);
        entity.setDeltaMovement(velocity);
        if (!level.noCollision(entity, entity.getBoundingBox())) {
            entity.discard();
            return Optional.empty();
        }
        if (!level.addFreshEntity(entity) || !entity.isAddedToLevel()) {
            entity.discard();
            return Optional.empty();
        }
        UUID worldId = worldIdentity(level);
        return Optional.of(new Staged(entity, new ItemLocation.WorldEntity(worldId, entity.getUUID())));
    }

    public static UUID worldIdentity(ServerLevel level) {
        return UUID.nameUUIDFromBytes(level.dimension().location().toString()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static boolean armedGrenade(ItemStack stack) {
        var fuse = stack.get(ModDataComponents.GRENADE_FUSE.get());
        return stack.is(ModItems.GRENADE) && fuse != null && fuse.phase() == GrenadeFuse.Phase.ARMED;
    }

    public record Staged(Entity entity, ItemLocation.WorldEntity location) {
        public Staged { Objects.requireNonNull(entity); Objects.requireNonNull(location); }
        public void rollback() { entity.discard(); }
    }
}
