package com.juicyslew.moonstation14.ms14.ghost_roles.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.GhostRoleComponent;
import com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleRoleAvailability;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.entities.AnimalMob;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/** Read-only index of loaded, prototype-mapped actors explicitly eligible for ghost roles. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class GhostRoleCatalog {
    private static final Map<MinecraftServer, Set<java.util.UUID>> CATALOGS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Boolean> BOOTSTRAPPED = new WeakHashMap<>();

    private GhostRoleCatalog() { }

    /** Snapshot eligible roles currently loaded on this server; first use performs the sole world scan. */
    public static List<GhostRolePayloads.Entry> snapshot(MinecraftServer server) {
        return snapshot(server, false);
    }

    /** Operator snapshot includes only resolved local animal hosts as development-only offers. */
    public static List<GhostRolePayloads.Entry> snapshot(MinecraftServer server, boolean operator) {
        if (server == null || !server.isSameThread()) return List.of();
        Set<java.util.UUID> index;
        synchronized (CATALOGS) {
            index = CATALOGS.computeIfAbsent(server, ignored -> new HashSet<>());
            if (!BOOTSTRAPPED.containsKey(server)) {
                BOOTSTRAPPED.put(server, Boolean.TRUE);
                for (ServerLevel level : server.getAllLevels())
                    for (Entity entity : level.getAllEntities()) refresh(server, entity);
            }
            List<GhostRolePayloads.Entry> available = new ArrayList<>();
            var iterator = index.iterator();
            while (iterator.hasNext()) {
                var candidate = iterator.next();
                Entity entity = server.overworld().getEntity(candidate);
                if (entity == null) {
                    for (ServerLevel level : server.getAllLevels()) {
                        entity = level.getEntity(candidate);
                        if (entity != null) break;
                    }
                }
                if (entity == null || entity.isRemoved()) { iterator.remove(); continue; }
                GhostRolePayloads.Entry entry = currentlyAvailable(server, entity, operator);
                if (entry != null) available.add(entry);
            }
            return boundSnapshot(available);
        }
    }

    static List<GhostRolePayloads.Entry> boundSnapshot(List<GhostRolePayloads.Entry> available) {
        available.sort(Comparator.comparing(GhostRolePayloads.Entry::development)
                    .thenComparing(GhostRolePayloads.Entry::roleId)
                    .thenComparing(entry -> entry.target().toString()));
        return List.copyOf(available.subList(0, Math.min(available.size(), GhostRolePayloads.MAX_ENTRIES)));
    }

    /** Resolve a requested target against current server state, independently of the bounded UI snapshot. */
    public static GhostRolePayloads.Entry entryForTarget(MinecraftServer server, Entity target, boolean operator) {
        if (server == null || target == null || !server.isSameThread()
                || !(target.level() instanceof ServerLevel level) || level.getServer() != server) return null;
        return currentlyAvailable(server, target, operator);
    }

    @SubscribeEvent
    public static void entityJoined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        synchronized (CATALOGS) {
            if (BOOTSTRAPPED.containsKey(server)) refresh(server, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void entityLeft(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        synchronized (CATALOGS) {
            Set<java.util.UUID> index = CATALOGS.get(level.getServer());
            if (index != null) index.remove(event.getEntity().getUUID());
        }
    }

    private static void refresh(MinecraftServer server, Entity entity) {
        Set<java.util.UUID> index = CATALOGS.get(server);
        if (index == null) return;
        if (!(entity instanceof Mob) || !(entity.level() instanceof ServerLevel level)
                || entity.isRemoved()) return;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (ModCharacters.characterForHost(level, id).isPresent()) index.add(entity.getUUID());
    }

    private static GhostRolePayloads.Entry currentlyAvailable(MinecraftServer server, Entity entity, boolean operator) {
        if (!(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel)
                || entity.isRemoved() || !entity.isAddedToLevel()
                || mob instanceof PlayerCharacterHarnessEntity
                || !MobStateSystem.isAlive(mob)
                || !LifecycleRoleAvailability.unclaimed(server, entity)) return null;
        var data = CharacterIdentitySystem.resolveForActor(mob).orElse(null);
        GhostRoleComponent role = data == null ? null : data.component(GhostRoleComponent.class).orElse(null);
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (role != null) return new GhostRolePayloads.Entry(entity.getUUID(), id.toString(), role.name(), true, role.takeover(), false);
        if (operator && mob instanceof AnimalMob && ModCharacters.characterForHost((ServerLevel) entity.level(), id).isPresent())
            return new GhostRolePayloads.Entry(entity.getUUID(), id.toString(), id.toString(), true, true, true);
        return null;
    }
}
