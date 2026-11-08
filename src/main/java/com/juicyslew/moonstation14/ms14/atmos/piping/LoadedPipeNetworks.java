package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/** Topology metadata cache. Unloading is deliberately not a topology event. */
public final class LoadedPipeNetworks {
    private static final Map<ServerLevel, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final class State {
        final Set<PipeTopology.NodeKey> dirty = new HashSet<>();
        final Set<PipeTopology.NodeKey> blocked = new HashSet<>();
        final Map<UUID, Set<PipeTopology.NodeKey>> capturedGroups = new LinkedHashMap<>();
        boolean initial = true;
    }
    private LoadedPipeNetworks() { }

    /** Seeds only the position and immediate physical candidates; old-network members are added at reconcile. */
    public static void invalidateTopology(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.getServer().isSameThread()) return;
        State state = STATES.computeIfAbsent(level, ignored -> new State());
        Set<BlockPos> nearby = new HashSet<>(); nearby.add(pos);
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) nearby.add(pos.relative(d));
        PipeGasInventory inventory = PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);
        for (BlockPos candidate : nearby) {
            for (PipeTopology.Layer layer : PipeTopology.Layer.values())
                state.dirty.add(new PipeTopology.NodeKey(candidate, layer));
            if (inventory == null) continue;
            for (PipeTopology.NodeKey key : inventory.snapshot().keySet()) {
                if (!key.position().equals(candidate)) continue;
                state.dirty.add(key);
                inventory.network(key).ifPresent(network -> state.capturedGroups.put(network.id(), network.members()));
            }
        }
    }

    /** Wakes only blocked frontiers whose node or immediate seam neighbor is in this FULL chunk. */
    public static void onChunkLoad(ServerLevel level, ChunkPos chunk) {
        if (level == null || chunk == null || !level.getServer().isSameThread()) return;
        State state = STATES.get(level);
        if (state == null || state.blocked.isEmpty()) return;
        Set<BlockPos> frontiers = new HashSet<>();
        for (PipeTopology.NodeKey key : state.blocked) {
            BlockPos pos = key.position();
            if (new ChunkPos(pos).equals(chunk)) {
                frontiers.add(pos);
                continue;
            }
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                if (new ChunkPos(pos.relative(direction)).equals(chunk)) {
                    frontiers.add(pos);
                    break;
                }
            }
        }
        for (BlockPos frontier : frontiers) invalidateTopology(level, frontier);
    }

    /** Drops per-level topology cache state when its owning atmosphere service is cleared. */
    public static void clear(ServerLevel level) {
        if (level == null) return;
        STATES.remove(level);
    }

    /** Attempts each affected existing network at most once per invalidation event. */
    public static boolean reconcile(ServerLevel level) {
        if (level == null || !level.getServer().isSameThread()) return false;
        State state = STATES.computeIfAbsent(level, ignored -> new State());
        PipeGasInventorySavedData saved = PipeGasInventorySavedData.existing(level).orElse(null);
        if (saved == null) { state.initial = false; state.dirty.clear(); return true; }
        PipeGasInventory before = saved.snapshot();
        if (state.initial) {
            state.dirty.addAll(before.snapshot().keySet());
            state.initial = false;
        }
        if (state.dirty.isEmpty()) return true;
        // Prune stale blocked owners only at a topology reconciliation boundary, never on stable reads.
        state.blocked.removeIf(key -> before.get(key).isEmpty());

        Set<PipeTopology.NodeKey> seeds = new HashSet<>(state.dirty);
        state.dirty.clear();
        Set<PipeTopology.NodeKey> affected = new HashSet<>();
        for (PipeTopology.NodeKey seed : seeds)
            before.network(seed).ifPresent(n -> affected.addAll(n.members()));
        for (Set<PipeTopology.NodeKey> members : state.capturedGroups.values()) affected.addAll(members);
        affected.addAll(seeds);
        state.blocked.removeAll(affected);

        // Preserve every untouched group, including dormant/unloaded members and its exact shared mixture.
        List<Set<PipeTopology.NodeKey>> groups = new ArrayList<>();
        Set<PipeTopology.NodeKey> covered = new HashSet<>();
        Set<UUID> affectedNetworkIds = new HashSet<>(state.capturedGroups.keySet());
        for (PipeTopology.NodeKey key : affected)
            before.network(key).ifPresent(n -> affectedNetworkIds.add(n.id()));
        for (PipeGasInventory.Network n : networks(before)) {
            if (!affectedNetworkIds.contains(n.id())) { groups.add(n.members()); covered.addAll(n.members()); }
        }
        Set<PipeTopology.NodeKey> attempted = new HashSet<>();
        Set<PipeTopology.NodeKey> resolved = new HashSet<>();
        Set<UUID> failedNetworks = new HashSet<>();
        List<Set<PipeTopology.NodeKey>> discovered = new ArrayList<>();
        for (PipeTopology.NodeKey key : affected) {
            PipeGasInventory.Entry entry = before.get(key).orElse(null);
            if (entry == null || !entry.active() || !attempted.add(key)) continue;
            Optional<LoadedPipeComponent.Snapshot> found = LoadedPipeComponent.discover(level, key);
            if (found.isEmpty()) {
                before.network(key).ifPresent(n -> {
                    failedNetworks.add(n.id());
                });
                state.blocked.add(key);
                continue;
            }
            Set<PipeTopology.NodeKey> members = new LinkedHashSet<>();
            for (LoadedPipeComponent.Node node : found.get().nodes()) members.add(node.key());
            attempted.addAll(members);
            discovered.add(Set.copyOf(members));
        }
        // If any discovery in a connected old region is incomplete, retain that whole old
        // region; do not publish a partial discovered component beside its stale remainder.
        Set<PipeTopology.NodeKey> failedMembers = new HashSet<>();
        boolean expanded;
        do {
            expanded = false;
            for (PipeGasInventory.Network n : networks(before))
                if (failedNetworks.contains(n.id()) && failedMembers.addAll(n.members())) expanded = true;
            for (Set<PipeTopology.NodeKey> candidate : discovered) {
                if (!Collections.disjoint(candidate, failedMembers)) {
                    for (PipeTopology.NodeKey member : candidate) {
                        PipeGasInventory.Network old = before.network(member).orElse(null);
                        if (old != null && failedNetworks.add(old.id())) expanded = true;
                    }
                }
            }
        } while (expanded);
        if (!failedMembers.isEmpty()) {
            state.blocked.addAll(failedMembers);
            for (PipeGasInventory.Network n : networks(before)) {
                if (failedNetworks.contains(n.id())) {
                    groups.add(n.members());
                    covered.addAll(n.members());
                }
            }
        }
        for (Set<PipeTopology.NodeKey> candidate : discovered) {
            if (!Collections.disjoint(candidate, failedMembers) || !Collections.disjoint(candidate, covered)) {
                state.blocked.addAll(candidate);
                continue;
            }
            groups.add(candidate); covered.addAll(candidate); state.blocked.removeAll(candidate); resolved.addAll(candidate);
        }
        // Dormant nodes remain in their persisted group. Never append a partially overlapping
        // old group: that would submit a duplicate/overlapping partition to remapNetworks.
        for (PipeGasInventory.Network n : networks(before)) {
            if (covered.containsAll(n.members())) continue;
            if (!Collections.disjoint(n.members(), covered)) {
                state.blocked.addAll(affected);
                state.dirty.addAll(seeds);
                return false;
            }
            groups.add(n.members());
            covered.addAll(n.members());
        }
        try {
            PipeGasInventory after = before.remapNetworks(groups);
            if (after != before && !saved.replace(before.revision(), before, after)) {
                state.blocked.addAll(affected);
                state.dirty.addAll(seeds);
                return false;
            }
            // A successful remap resolves still-present affected identities; stale keys are
            // cleared independently above and cannot withhold a later incarnation.
            state.blocked.removeIf(key -> resolved.contains(key) && after.network(key).isPresent());
            state.capturedGroups.clear();
        } catch (RuntimeException ignored) {
            state.blocked.addAll(affected);
            // Preserve pre-reconcile membership and keep unresolved identities inaccessible.
        }
        return true;
    }

    private static Collection<PipeGasInventory.Network> networks(PipeGasInventory inventory) {
        Map<UUID, PipeGasInventory.Network> result = new LinkedHashMap<>();
        for (PipeGasInventory.Entry entry : inventory.snapshot().values())
            inventory.network(entry.node()).ifPresent(n -> result.put(n.id(), n));
        return result.values();
    }

    public static boolean ready(ServerLevel level, PipeTopology.NodeKey key) {
        if (level == null || key == null || !level.getServer().isSameThread()) return false;
        State state = STATES.get(level);
        if (state == null) state = STATES.computeIfAbsent(level, ignored -> new State());
        if (state.initial || !state.dirty.isEmpty()) reconcile(level);
        return !state.blocked.contains(key);
    }
    public static Optional<PipeGasInventory.Network> network(ServerLevel level, PipeTopology.NodeKey key) {
        if (!ready(level, key)) return Optional.empty();
        return PipeGasInventorySavedData.existing(level).flatMap(data -> data.snapshot().network(key));
    }
}
