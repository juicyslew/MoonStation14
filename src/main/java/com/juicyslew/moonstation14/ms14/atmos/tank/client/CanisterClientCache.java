package com.juicyslew.moonstation14.ms14.atmos.tank.client;

import com.juicyslew.moonstation14.ms14.atmos.tank.*;

import java.util.*;

/** Client-side authoritative latest view; snapshots never become client authority. */
public final class CanisterClientCache {
    private static final Map<UUID, CanisterSessionSnapshot> SNAPSHOTS = new HashMap<>();
    private static UUID pendingMachine;
    private CanisterClientCache() { }
    public static void handle(CanisterSessionSnapshot snapshot) {
        receive(snapshot);
        if (acceptsOpen(snapshot)) {
            CanisterWorkspace.INSTANCE.select(snapshot.sessionId(), new net.minecraft.core.BlockPos((int)snapshot.x(), (int)snapshot.y(), (int)snapshot.z()));
            finishOpen(snapshot.machineId());
        }
    }
    public static synchronized boolean matches(CanisterSessionSnapshot session, java.util.UUID body, long epoch) {
        return session != null && body != null && session.accepted() && body.equals(session.bodyId())
                && epoch == session.authorityEpoch();
    }
    public static synchronized void beginOpen(UUID machine) { pendingMachine = machine; }
    public static synchronized boolean acceptsOpen(CanisterSessionSnapshot snapshot) { return pendingMachine != null && pendingMachine.equals(snapshot.machineId()); }
    public static synchronized void finishOpen(UUID machine) { if (java.util.Objects.equals(pendingMachine, machine)) pendingMachine = null; }
    public static synchronized void receive(CanisterSessionSnapshot snapshot) { SNAPSHOTS.put(snapshot.sessionId(),snapshot); }
    public static synchronized Optional<CanisterSessionSnapshot> get(UUID session) { return Optional.ofNullable(SNAPSHOTS.get(session)); }
    public static synchronized void clear() { SNAPSHOTS.clear(); pendingMachine = null; CanisterWorkspace.INSTANCE.clear(); }
    public static synchronized void dimensionChanged(net.minecraft.resources.ResourceLocation dimension) { SNAPSHOTS.values().removeIf(s->!s.dimension().equals(dimension)); }
}

