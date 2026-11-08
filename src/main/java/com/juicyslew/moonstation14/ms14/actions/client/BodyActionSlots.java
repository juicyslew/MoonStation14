package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stateful allocation of action grants to the upstream-sized direct hotbar. */
public final class BodyActionSlots {
    public static final int MAX_SLOTS = 20;
    public record Slot(int number, BodyActionSnapshot.Entry entry) { }
    private record Identity(net.minecraft.resources.ResourceLocation actionId, String providerId) { }
    private final Map<Identity, Integer> assignments = new HashMap<>();
    private final LinkedHashMap<Identity, BodyActionSnapshot.Entry> known = new LinkedHashMap<>();
    private List<Slot> cachedView = List.of();

    /** Apply an accepted projection, retaining survivors and assigning new grants in grant order. */
    public void update(BodyActionSnapshot accepted) {
        if (accepted == null) { clear(); return; }
        LinkedHashMap<Identity, BodyActionSnapshot.Entry> incoming = new LinkedHashMap<>();
        for (var entry : accepted.actions()) incoming.putIfAbsent(new Identity(entry.actionId(), entry.providerId()), entry);
        assignments.keySet().retainAll(incoming.keySet());
        known.keySet().retainAll(incoming.keySet());
        for (var row : incoming.entrySet()) known.put(row.getKey(), row.getValue());
        for (var identity : known.keySet()) {
            if (assignments.containsKey(identity)) continue;
            int free = lowestFree();
            if (free == 0) break;
            assignments.put(identity, free);
        }
        rebuildView();
    }

    private int lowestFree() {
        Set<Integer> used = new HashSet<>(assignments.values());
        for (int i = 1; i <= MAX_SLOTS; i++) if (!used.contains(i)) return i;
        return 0;
    }
    public void clear() { assignments.clear(); known.clear(); cachedView = List.of(); }
    private void rebuildView() {
        List<Slot> result = new ArrayList<>();
        for (var row : known.entrySet()) {
            Integer number = assignments.get(row.getKey());
            if (number != null) result.add(new Slot(number, row.getValue()));
        }
        result.sort(java.util.Comparator.comparingInt(Slot::number));
        cachedView = List.copyOf(result);
    }
    public List<Slot> resolve() { return cachedView; }
    public BodyActionSnapshot.Entry at(int number) {
        if (number < 1 || number > MAX_SLOTS) return null;
        for (var row : known.entrySet()) if (java.util.Objects.equals(assignments.get(row.getKey()), number)) return row.getValue();
        return null;
    }
}
