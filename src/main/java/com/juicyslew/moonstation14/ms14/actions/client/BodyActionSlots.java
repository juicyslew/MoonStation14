package com.juicyslew.moonstation14.ms14.actions.client;

import com.juicyslew.moonstation14.ms14.actions.BodyActionSnapshot;

import java.util.List;

/** Stable numbered action slots; absent providers leave their assigned slot empty. */
public final class BodyActionSlots {
    public record Slot(int number, BodyActionSnapshot.Entry entry) { }
    private BodyActionSlots() { }

    public static List<Slot> resolve(BodyActionSnapshot snapshot) {
        var result = new java.util.ArrayList<Slot>();
        put(result, 1, snapshot, "combat_mode_toggle");
        put(result, 2, snapshot, "scream");
        put(result, 3, snapshot, "debug_pulse");
        return List.copyOf(result);
    }

    private static void put(List<Slot> slots, int number, BodyActionSnapshot snapshot, String action) {
        BodyActionSnapshot.Entry match = snapshot.actions().stream()
                .filter(entry -> entry.actionId().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", action)))
                .findFirst().orElse(null);
        slots.add(new Slot(number, match));
    }

    public static BodyActionSnapshot.Entry at(BodyActionSnapshot snapshot, int number) {
        return number < 1 || number > 3 ? null : resolve(snapshot).get(number - 1).entry();
    }
}
