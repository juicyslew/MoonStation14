package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.MoonStation14;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Keeps operational Sable frames simulated independently of player presence. */
public final class ShuttleSimulationResidency {
    private static final SubLevelLoadingTicketType<String> TICKET_TYPE =
            SubLevelLoadingTicketType.create(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "operational_frame"),
                    com.mojang.serialization.Codec.STRING);
    private static final Map<ServerLevel, ShuttleSimulationResidency> LEVELS = new WeakHashMap<>();

    private final java.lang.ref.WeakReference<ServerLevel> levelRef;
    private final Set<ServerSubLevel> admitted = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ServerSubLevel> released = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean active = true;
    private final SubLevelObserver observer = new SubLevelObserver() {
        @Override public void onSubLevelAdded(SubLevel sub) {
            if (sub instanceof ServerSubLevel serverSub) acquire(serverSub);
        }

        @Override public void onSubLevelRemoved(SubLevel sub, SubLevelRemovalReason reason) {
            if (!active) return;
            if (sub instanceof ServerSubLevel serverSub) {
                // UNLOADED is not ordinary retirement: our ticket should prevent it. A
                // deliberate removal must not leave this owner's persisted ticket behind.
                if (reason != SubLevelRemovalReason.UNLOADED) {
                    ServerLevel level = levelRef.get();
                    ServerSubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
                    if (container != null) container.removeForceLoadTicket(serverSub, TICKET_TYPE,
                            serverSub.getUniqueId().toString());
                }
                admitted.remove(serverSub);
                released.remove(serverSub);
            }
        }
    };

    private ShuttleSimulationResidency(ServerLevel level) { levelRef = new java.lang.ref.WeakReference<>(level); }

    /** Call during mod construction, before Sable can load saved sublevel ticket metadata. */
    public static void registerTicketType() { /* class initialization performs registration */ }

    public static synchronized ShuttleSimulationResidency install(ServerLevel level) {
        if (level == null) return null;
        ShuttleSimulationResidency existing = LEVELS.get(level);
        if (existing != null && existing.active) return existing;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        var service = new ShuttleSimulationResidency(level);
        container.addObserver(service.observer);
        LEVELS.put(level, service);
        // Container readiness may precede LevelEvent.Load; register observer first, then reconcile.
        for (ServerSubLevel sub : container.getAllSubLevels()) service.acquire(sub);
        return service;
    }

    public static synchronized ShuttleSimulationResidency forLevel(ServerLevel level) {
        ShuttleSimulationResidency service = LEVELS.get(level);
        return service != null && service.active ? service : install(level);
    }

    /** Explicitly relinquish residency for a deliberate frame retirement/unload test. */
    public synchronized boolean release(ServerSubLevel owner) {
        if (!active || owner == null || !admitted.contains(owner)) return false;
        ServerLevel level = levelRef.get();
        ServerSubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        if (container == null || container.getSubLevel(owner.getUniqueId()) != owner) return false;
        boolean removed = container.removeForceLoadTicket(owner, TICKET_TYPE, owner.getUniqueId().toString());
        admitted.remove(owner);
        released.add(owner);
        return removed;
    }

    private synchronized void acquire(ServerSubLevel owner) {
        if (!active || owner == null || released.contains(owner) || admitted.contains(owner)) return;
        ServerLevel level = levelRef.get();
        ServerSubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        if (container == null || owner.getLevel() != level || container.getSubLevel(owner.getUniqueId()) != owner) return;
        String stableKey = owner.getUniqueId().toString();
        // A persisted identical ticket is already doing its job; add is idempotent.
        container.addForceLoadTicket(owner, TICKET_TYPE, stableKey);
        admitted.add(owner);
    }

    /** Stop runtime observation only; intentionally leave ticket metadata for world save/restart. */
    public static synchronized void close(ServerLevel level) {
        ShuttleSimulationResidency service = LEVELS.remove(level);
        if (service == null) return;
        service.active = false;
        service.admitted.clear();
        service.released.clear();
        service.levelRef.clear();
    }
}
