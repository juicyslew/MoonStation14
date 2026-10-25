package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnimalPopulationDiagnosticTest {
    @TempDir Path temp;

    @Test void gateAndSerializedFields() throws Exception {
        String prior = System.getProperty("ms14.animalPopulationDiagnostic");
        String posePrior = System.getProperty("ms14.animalFlyerPoseDiagnostic");
        try {
            System.setProperty("ms14.animalPopulationDiagnostic", "false");
            System.setProperty("ms14.animalFlyerPoseDiagnostic", "true");
            Path disabled = temp.resolve("disabled.jfr");
            try (Recording recording = recording()) {
                recording.start();
                AnimalPopulationDiagnostic.recordFlyerPose(UUID.randomUUID(), "minecraft:overworld", "minecraft:bat", 10, 2.5, true, "FLYING", true, 65);
                recording.stop(); recording.dump(disabled);
            }
            assertTrue(RecordingFile.readAllEvents(disabled).stream().noneMatch(e -> e.getEventType().getName().equals("ms14.ServerFlyerPose")));

            System.setProperty("ms14.animalPopulationDiagnostic", "true");
            System.setProperty("ms14.animalFlyerPoseDiagnostic", "false");
            Path poseDisabled = temp.resolve("pose-disabled.jfr");
            try (Recording recording = recording()) {
                recording.start(); AnimalPopulationDiagnostic.recordFlyerPose(UUID.randomUUID(), "minecraft:overworld", "minecraft:bat", 10, 2.5, true, "FLYING", true, 65);
                recording.stop(); recording.dump(poseDisabled);
            }
            assertTrue(RecordingFile.readAllEvents(poseDisabled).stream().noneMatch(e -> e.getEventType().getName().equals("ms14.ServerFlyerPose")));
            System.setProperty("ms14.animalFlyerPoseDiagnostic", "true");
            UUID id = UUID.randomUUID();
            Path enabled = temp.resolve("enabled.jfr");
            try (Recording recording = recording()) {
                recording.start();
                AnimalPopulationDiagnostic.recordFlyerPose(id, "minecraft:overworld", "minecraft:parrot", 25, 2.5, true, "FLYING", true, 65);
                recording.stop(); recording.dump(enabled);
            }
            var events = RecordingFile.readAllEvents(enabled).stream().filter(e -> e.getEventType().getName().equals("ms14.ServerFlyerPose")).toList();
            assertEquals(1, events.size());
            var event = events.get(0);
            assertEquals(id.toString(), event.getString("entityId"));
            assertEquals("minecraft:overworld", event.getString("dimension"));
            assertEquals("minecraft:parrot", event.getString("hostEntityType"));
            assertEquals(25, event.getLong("gameTick"));
            assertEquals(2.5, event.getDouble("entityY"));
            assertTrue(event.getBoolean("onGround"));
            assertEquals("FLYING", event.getString("flightState"));
            assertTrue(event.getBoolean("poweredFlight"));
            assertEquals(65, event.getInt("expectedFixtureFloorY"));
        } finally {
            if (prior == null) System.clearProperty("ms14.animalPopulationDiagnostic");
            else System.setProperty("ms14.animalPopulationDiagnostic", prior);
            if (posePrior == null) System.clearProperty("ms14.animalFlyerPoseDiagnostic");
            else System.setProperty("ms14.animalFlyerPoseDiagnostic", posePrior);
        }
    }

    @Test void samplePolicyHonorsIntervalAndCap() {
        assertFalse(AnimalPopulationDiagnostic.allowFlyerPoseSample(4, 0));
        assertTrue(AnimalPopulationDiagnostic.allowFlyerPoseSample(5, 0));
        assertTrue(AnimalPopulationDiagnostic.allowFlyerPoseSample(5, 15));
        assertFalse(AnimalPopulationDiagnostic.allowFlyerPoseSample(5, 16));
    }

    @Test void clientFlyerPoseGateAndSerializedFields() throws Exception {
        String prior = System.getProperty("ms14.animalPopulationDiagnostic");
        String posePrior = System.getProperty("ms14.animalFlyerPoseDiagnostic");
        try {
            UUID id = UUID.randomUUID();
            var pose = clientPose(id);
            System.setProperty("ms14.animalPopulationDiagnostic", "false");
            System.setProperty("ms14.animalFlyerPoseDiagnostic", "true");
            Path disabled = temp.resolve("client-disabled.jfr");
            try (Recording recording = clientRecording()) {
                recording.start();
                AnimalPopulationDiagnostic.commitClientFlyerPose(pose);
                recording.stop(); recording.dump(disabled);
            }
            assertTrue(RecordingFile.readAllEvents(disabled).stream().noneMatch(e -> e.getEventType().getName().equals("ms14.ClientFlyerPose")));

            System.setProperty("ms14.animalPopulationDiagnostic", "true");
            System.setProperty("ms14.animalFlyerPoseDiagnostic", "false");
            Path poseDisabled = temp.resolve("client-pose-disabled.jfr");
            try (Recording recording = clientRecording()) {
                recording.start(); AnimalPopulationDiagnostic.commitClientFlyerPose(clientPose(id));
                recording.stop(); recording.dump(poseDisabled);
            }
            assertTrue(RecordingFile.readAllEvents(poseDisabled).stream().noneMatch(e -> e.getEventType().getName().equals("ms14.ClientFlyerPose")));
            System.setProperty("ms14.animalFlyerPoseDiagnostic", "true");
            Path enabled = temp.resolve("client-enabled.jfr");
            try (Recording recording = clientRecording()) {
                recording.start();
                AnimalPopulationDiagnostic.commitClientFlyerPose(clientPose(id));
                recording.stop(); recording.dump(enabled);
            }
            var events = RecordingFile.readAllEvents(enabled).stream().filter(e -> e.getEventType().getName().equals("ms14.ClientFlyerPose")).toList();
            assertEquals(1, events.size());
            var event = events.get(0);
            assertEquals(id.toString(), event.getString("entityId"));
            assertEquals("minecraft:overworld", event.getString("dimension"));
            assertEquals("minecraft:parrot", event.getString("hostEntityType"));
            assertEquals(42, event.getLong("gameTick"));
            assertEquals(64.5, event.getDouble("entityY"));
            assertEquals(65.0, event.getDouble("renderY"));
            assertEquals("FLYING", event.getString("flightState"));
            assertTrue(event.getBoolean("poweredFlight"));
            assertTrue(event.getBoolean("resolvedFlightCapability"));
            assertFalse(event.getBoolean("onGround"));
            assertEquals("parrot_fly", event.getString("sourceClip"));
            assertEquals("parrot_idle", event.getString("destinationClip"));
            assertEquals(0.25f, event.getFloat("blendWeight"));
            assertTrue(event.getBoolean("hasVisibleGeometry"));
            assertEquals(24.0, event.getDouble("modelBottomPixels"));
            assertEquals(1.0f, event.getFloat("presentationScale"));
            assertEquals(65.0, event.getDouble("estimatedMeshBottomY"));
        } finally {
            if (prior == null) System.clearProperty("ms14.animalPopulationDiagnostic");
            else System.setProperty("ms14.animalPopulationDiagnostic", prior);
            if (posePrior == null) System.clearProperty("ms14.animalFlyerPoseDiagnostic");
            else System.setProperty("ms14.animalFlyerPoseDiagnostic", posePrior);
        }
    }

    @Test void meshBottomEstimateAccountsForGeometryAndScale() {
        assertEquals(65.0, AnimalPopulationDiagnostic.estimateMeshBottomY(65, 24, 1));
        assertEquals(64.59, AnimalPopulationDiagnostic.estimateMeshBottomY(65, 32, 0.82), 1e-10);
        assertEquals(65.5, AnimalPopulationDiagnostic.estimateMeshBottomY(65, 16, 1));
    }

    private static AnimalPopulationDiagnostic.ClientFlyerPose clientPose(UUID id) {
        var event = new AnimalPopulationDiagnostic.ClientFlyerPose();
        event.entityId = id.toString(); event.dimension = "minecraft:overworld"; event.hostEntityType = "minecraft:parrot";
        event.gameTick = 42; event.entityY = 64.5; event.renderY = 65; event.flightState = "FLYING";
        event.poweredFlight = true; event.resolvedFlightCapability = true; event.onGround = false;
        event.sourceClip = "parrot_fly"; event.destinationClip = "parrot_idle"; event.blendWeight = 0.25f;
        event.hasVisibleGeometry = true; event.modelBottomPixels = 24; event.presentationScale = 1;
        event.estimatedMeshBottomY = AnimalPopulationDiagnostic.estimateMeshBottomY(event.renderY, event.modelBottomPixels, event.presentationScale);
        return event;
    }

    private static Recording clientRecording() {
        Recording recording = new Recording();
        recording.enable(AnimalPopulationDiagnostic.ClientFlyerPose.class).withoutStackTrace();
        return recording;
    }

    private static Recording recording() {
        Recording recording = new Recording();
        recording.enable(AnimalPopulationDiagnostic.FlyerPose.class).withoutStackTrace();
        return recording;
    }
}
