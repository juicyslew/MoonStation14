package com.juicyslew.moonstation14.ms14.mobs;

import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import net.minecraft.world.entity.EntityType;

/** Per-call opt-in route work measurement; instances never outlive a route attempt. */
final class NpcRouteWorkDiagnostic {
    private NpcRouteWorkDiagnostic() { }

    static Measurement begin(EntityType<?> type) {
        if (!Boolean.getBoolean("ms14.animalPopulationDiagnostic")) return null;
        var event = new NpcRouteWork();
        event.entityType = EntityType.getKey(type).toString();
        event.begin();
        return new Measurement(event);
    }
    static Measurement testMeasurement() { return new Measurement(null); }

    static final class Measurement {
        private final NpcRouteWork event;
        private long expandedNodes, surfaceQueries, surfaceCacheHits, surfaceComputations, standableCalls, edgeClearCalls, noCollisionProbes;
        private Measurement(NpcRouteWork event) { this.event = event; }
        void expandedNode() { expandedNodes++; }
        void surfaceQuery() { surfaceQueries++; }
        void surfaceCacheHit() { surfaceCacheHits++; }
        void surfaceComputation() { surfaceComputations++; }
        void standableCall() { standableCalls++; }
        void edgeClearCall() { edgeClearCalls++; }
        void noCollisionProbe() { noCollisionProbes++; }
        long standableCalls() { return standableCalls; }
        long noCollisionProbes() { return noCollisionProbes; }
        void finish(String outcome) {
            if (event == null) return;
            event.outcome = outcome;
            event.expandedNodes = expandedNodes;
            event.surfaceQueries = surfaceQueries;
            event.surfaceCacheHits = surfaceCacheHits;
            event.surfaceComputations = surfaceComputations;
            event.standableCalls = standableCalls;
            event.edgeClearCalls = edgeClearCalls;
            event.noCollisionProbes = noCollisionProbes;
            event.end();
            event.commit();
        }
    }

    @Name("ms14.NpcRouteWork") @Label("MS14 NPC route work") @StackTrace(false)
    static final class NpcRouteWork extends Event {
        @Label("Entity type") String entityType;
        @Label("Outcome") String outcome;
        @Label("Expanded nodes") long expandedNodes;
        @Label("Surface queries") long surfaceQueries;
        @Label("Surface cache hits") long surfaceCacheHits;
        @Label("Surface computations") long surfaceComputations;
        @Label("Standable calls") long standableCalls;
        @Label("Edge clear calls") long edgeClearCalls;
        @Label("No-collision probes") long noCollisionProbes;
    }
}
