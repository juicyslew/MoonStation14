package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;

/** Bounded diagnostics: recent cell events plus the first ignition root for each recent cycle. */
final class IgnitionDiagnosticHistory {
    static final int CAPACITY = 64;
    private final ArrayDeque<AtmosphereService.IgnitionDiagnostic> recent = new ArrayDeque<>(CAPACITY);
    private final LinkedHashMap<Long, AtmosphereService.IgnitionDiagnostic> firstByCycle = new LinkedHashMap<>();

    void append(AtmosphereService.IgnitionDiagnostic event) {
        if (recent.size() == CAPACITY) recent.removeFirst();
        recent.addLast(event);
        // Explicit/direct exposure outside an active atmosphere cycle is keyed by game-time step.
        long cycleKey = event.cycleIdentity() == 0 ? event.gameTime() : event.cycleIdentity();
        firstByCycle.putIfAbsent(cycleKey, event);
        if (firstByCycle.size() > CAPACITY) firstByCycle.remove(firstByCycle.keySet().iterator().next());
    }

    List<AtmosphereService.IgnitionDiagnostic> recent(BlockPos position) {
        return recent.stream().filter(event -> position == null || event.position().equals(position)).toList();
    }

    List<AtmosphereService.IgnitionDiagnostic> firstByCycle() { return List.copyOf(firstByCycle.values()); }
}
