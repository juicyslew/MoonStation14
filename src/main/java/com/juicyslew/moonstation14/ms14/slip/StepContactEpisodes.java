package com.juicyslew.moonstation14.ms14.slip;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

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
        return containsPuddle(level, actor, null, source);
    }

    public static boolean containsPuddle(ServerLevel level, UUID actor, UUID frameId, BlockPos source) {
        synchronized (CONTACTS) {
            ActorContacts contacts = actor(level, actor, false);
            return contacts != null && contacts.puddles.containsKey(new PuddleKey(frameId, source));
        }
    }

    public static boolean containsItem(ServerLevel level, UUID actor, UUID source) {
        synchronized (CONTACTS) {
            ActorContacts contacts = actor(level, actor, false);
            return contacts != null && contacts.items.containsKey(source);
        }
    }

    public static boolean latchPuddle(ServerLevel level, UUID actorId, BlockPos source) {
        return latchPuddle(level, actorId, null, source);
    }

    public static boolean latchPuddle(ServerLevel level, UUID actorId, UUID frameId, BlockPos source) {
        synchronized (CONTACTS) {
            LevelContacts state = CONTACTS.get(level);
            ActorContacts contacts = actor(level, actorId, false);
            PuddleKey key = new PuddleKey(frameId, source);
            if (contacts != null && contacts.puddles.containsKey(key)) return false;
            if ((contacts != null && contacts.count() >= MAX_ACTOR_CONTACTS)
                    || (state != null && state.count >= MAX_LEVEL_CONTACTS)) return false;
            contacts = actor(level, actorId, true);
            contacts.puddles.put(key, level.getGameTime());
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

    public static void reconcile(ServerLevel level, Entity entity, AABB bounds,
                                  SharedPuddleContactQuery.Result observed) {
        synchronized (CONTACTS) {
            LevelContacts state = CONTACTS.get(level);
            if (state == null) return;
            ActorContacts contacts = state.actors.get(entity.getUUID());
            if (contacts == null) return;
            int before = contacts.count();
            if (observed.available()) contacts.puddles.keySet().removeIf(key ->
                    observed.contacts().stream().noneMatch(contact ->
                            java.util.Objects.equals(key.frameId(), contact.frameId())
                                    && key.position().equals(contact.sourcePosition())));
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
        private final Map<PuddleKey, Long> puddles = new HashMap<>();
        private final Map<UUID, Long> items = new HashMap<>();
        private int count() { return puddles.size() + items.size(); }
    }
    private static final class LevelContacts {
        private final Map<UUID, ActorContacts> actors = new HashMap<>();
        private int count;
    }

    /** Null frame identifies world support; non-null identifies a moving support frame. */
    private record PuddleKey(UUID frameId, BlockPos position) {
        private PuddleKey { position = position.immutable(); }
    }
}
