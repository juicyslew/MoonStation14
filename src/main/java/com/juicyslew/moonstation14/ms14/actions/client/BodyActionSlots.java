package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;

import java.util.List;

/** Numbered action slots; fixed actions lead when present, remaining actions pack contiguously. */
public final class BodyActionSlots {
    public record Slot(int number, BodyActionSnapshot.Entry entry) { }
    private BodyActionSlots() { }

    public static List<Slot> resolve(BodyActionSnapshot snapshot) {
        var result = new java.util.ArrayList<Slot>();
        put(result, snapshot, "combat_mode_toggle");
        put(result, snapshot, "scream");
        var assigned = new java.util.HashSet<BodyActionSnapshot.Entry>();
        result.stream().map(Slot::entry).filter(java.util.Objects::nonNull).forEach(assigned::add);
        int number = result.size() + 1;
        for (var entry : snapshot.actions()) if (assigned.add(entry)) result.add(new Slot(number++, entry));
        return List.copyOf(result);
    }

    private static void put(List<Slot> slots, BodyActionSnapshot snapshot, String action) {
        BodyActionSnapshot.Entry match = snapshot.actions().stream()
                .filter(entry -> entry.actionId().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", action)))
                .findFirst().orElse(null);
        if (match != null) slots.add(new Slot(slots.size() + 1, match));
    }

    public static BodyActionSnapshot.Entry at(BodyActionSnapshot snapshot, int number) {
        if (number < 1) return null;
        var slots = resolve(snapshot);
        return number > slots.size() ? null : slots.get(number - 1).entry();
    }
}
