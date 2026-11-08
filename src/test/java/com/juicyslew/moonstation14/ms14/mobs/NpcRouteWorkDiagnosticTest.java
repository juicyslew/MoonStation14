package com.juicyslew.moonstation14.ms14.mobs;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class NpcRouteWorkDiagnosticTest {
    @Test void optInRecordsSingleCallFieldsAndDisabledDoesNotCreateMeasurement() throws Exception {
        String previous = System.getProperty("ms14.animalPopulationDiagnostic");
        try {
            System.clearProperty("ms14.animalPopulationDiagnostic");
            assertNull(NpcRouteWorkDiagnostic.begin(EntityType.PARROT));
            System.setProperty("ms14.animalPopulationDiagnostic", "true");
            try (var recording = new Recording()) {
                recording.enable(NpcRouteWorkDiagnostic.NpcRouteWork.class);
                recording.start();
                var measurement = NpcRouteWorkDiagnostic.begin(EntityType.PARROT);
                measurement.expandedNode();
                measurement.surfaceQuery();
                measurement.surfaceCacheHit();
                measurement.surfaceComputation();
                measurement.standableCall();
                measurement.edgeClearCall();
                measurement.noCollisionProbe();
                measurement.finish("ROUTE");
                recording.stop();
                var file = Files.createTempFile("npc-route-work", ".jfr");
                try {
                    recording.dump(file);
                    var events = RecordingFile.readAllEvents(file);
                    assertEquals(1, events.size());
                    var event = events.getFirst();
                    assertEquals("ms14.NpcRouteWork", event.getEventType().getName());
                    var values = new HashMap<String, Object>();
                    event.getFields().forEach(field -> values.put(field.getName(), event.getValue(field.getName())));
                    assertEquals(EntityType.getKey(EntityType.PARROT).toString(), values.get("entityType"));
                    assertEquals("ROUTE", values.get("outcome"));
                    for (String field : new String[]{"expandedNodes", "surfaceQueries", "surfaceCacheHits", "surfaceComputations", "standableCalls", "edgeClearCalls", "noCollisionProbes"})
                        assertEquals(1L, values.get(field));
                    assertTrue(event.getDuration().toNanos() >= 0);
                } finally { Files.deleteIfExists(file); }
            }
        } finally {
            if (previous == null) System.clearProperty("ms14.animalPopulationDiagnostic");
            else System.setProperty("ms14.animalPopulationDiagnostic", previous);
        }
    }
}
