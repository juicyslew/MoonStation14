package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Shared bounded episode registry for actor contact with puddles and loose items. */
public final class StepContactEpisodes {
    private static final int MAX_ACTOR_CONTACTS = 32;
    private static final int MAX_LEVEL_CONTACTS = 8192;
    private static final Map<ServerLevel, LevelContacts> CONTACTS = new WeakHashMap<>();

    private StepContactEpisodes() { }

    public static boolean containsPuddle(ServerLevel level, UUID actor, BlockPos source) {
        synchronized (CONTACTS) {
            ActorContacts contacts = actor(level, actor, false);
            return contacts != null && contacts.puddles.containsKey(source);
        }
    }

    public static boolean containsItem(ServerLevel level, UUID actor, UUID source) {
        synchronized (CONTACTS) {
            ActorContacts contacts = actor(level, actor, false);
            return contacts != null && contacts.items.containsKey(source);
        }
    }

    public static boolean latchPuddle(ServerLevel level, UUID actorId, BlockPos source) {
        synchronized (CONTACTS) {
            LevelContacts state = CONTACTS.get(level);
            ActorContacts contacts = actor(level, actorId, false);
            if (contacts != null && contacts.puddles.containsKey(source)) return false;
            if ((contacts != null && contacts.count() >= MAX_ACTOR_CONTACTS)
                    || (state != null && state.count >= MAX_LEVEL_CONTACTS)) return false;
            contacts = actor(level, actorId, true);
            contacts.puddles.put(source, level.getGameTime());
            CONTACTS.get(level).count++;
            return true;
        }
    }

    public static boolean latchItem(ServerLevel level, UUID actorId, UUID source) {
        synchronized (CONTACTS) {
            LevelContacts state = CONTACTS.get(level);
            ActorContacts contacts = actor(level, actorId, false);
            if (contacts != null && contacts.items.containsKey(source)) return false;
            if ((contacts != null && contacts.count() >= MAX_ACTOR_CONTACTS)
                    || (state != null && state.count >= MAX_LEVEL_CONTACTS)) return false;
            contacts = actor(level, actorId, true);
            contacts.items.put(source, level.getGameTime());
            CONTACTS.get(level).count++;
            return true;
        }
    }

    public static void reconcile(ServerLevel level, Entity entity, AABB bounds) {
        synchronized (CONTACTS) {
            LevelContacts state = CONTACTS.get(level);
            if (state == null) return;
            ActorContacts contacts = state.actors.get(entity.getUUID());
            if (contacts == null) return;
            int before = contacts.count();
            contacts.puddles.entrySet().removeIf(entry -> {
                BlockPos pos = entry.getKey();
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(ModBlocks.PUDDLE.get())) return true;
                AABB shape = level.getBlockState(pos).getShape(level, pos, CollisionContext.empty()).bounds().move(pos);
                return !bounds.intersects(shape);
            });
            AABB feet = entity instanceof LivingEntity ? ItemStepContactGeometry.feetBand(bounds) : bounds;
            contacts.items.keySet().removeIf(id -> !(level.getEntity(id) instanceof com.juicyslew.moonstation14.entities.LooseItemEntity item)
                    || item.isRemoved() || !feet.intersects(item.getBoundingBox()));
            state.count -= before - contacts.count();
            if (contacts.count() == 0) state.actors.remove(entity.getUUID());
            if (state.actors.isEmpty()) CONTACTS.remove(level);
        }
    }

    public static void clearActor(ServerLevel level, UUID id) {
        synchronized (CONTACTS) {
            LevelContacts state = CONTACTS.get(level);
            if (state == null) return;
            ActorContacts removed = state.actors.remove(id);
            if (removed != null) state.count -= removed.count();
            if (state.actors.isEmpty()) CONTACTS.remove(level);
        }
    }

    private static ActorContacts actor(ServerLevel level, UUID id, boolean create) {
        LevelContacts state = CONTACTS.get(level);
        if (state == null && create) { state = new LevelContacts(); CONTACTS.put(level, state); }
        if (state == null) return null;
        ActorContacts result = state.actors.get(id);
        if (result == null && create) { result = new ActorContacts(); state.actors.put(id, result); }
        return result;
    }

    private static final class ActorContacts {
        private final Map<BlockPos, Long> puddles = new HashMap<>();
        private final Map<UUID, Long> items = new HashMap<>();
        private int count() { return puddles.size() + items.size(); }
    }
    private static final class LevelContacts {
        private final Map<UUID, ActorContacts> actors = new HashMap<>();
        private int count;
    }
}
