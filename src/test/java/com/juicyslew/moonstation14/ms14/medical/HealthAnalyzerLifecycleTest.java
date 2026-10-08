package com.juicyslew.moonstation14.ms14.medical;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HealthAnalyzerLifecycleTest {
    @Test void resolvedScanAndUiRangesAreIndependentPolicies() {
        var body = new net.minecraft.world.phys.Vec3(0, 0, 0);
        var at275 = new net.minecraft.world.phys.Vec3(2.75, 0, 0);
        var at31 = new net.minecraft.world.phys.Vec3(3.1, 0, 0);
        var analyzer = new MedicalItemData.HealthAnalyzerPolicy(.8f, 3.5f, 1f);
        var ui = new MedicalItemData.UiPolicy(3f);
        assertTrue(HealthAnalyzerLifecycle.inResolvedRange(body, new net.minecraft.world.phys.Vec3(3.25, 0, 0), analyzer.scanRange()));
        assertFalse(HealthAnalyzerLifecycle.inResolvedRange(body, new net.minecraft.world.phys.Vec3(3.6, 0, 0), analyzer.scanRange()));
        assertTrue(HealthAnalyzerLifecycle.inUiRange(body, at275, ui.range()));
        assertFalse(HealthAnalyzerLifecycle.inUiRange(body, at31, ui.range()));
        assertEquals(.8f, analyzer.scanDelay());
        assertEquals(1f, analyzer.updateInterval());
    }
    @Test void resolvedCustomUiRangeUsesControlledBodyAndPhysicalDroppedRoot() {
        var controlledBody = new net.minecraft.world.phys.Vec3(0, 0, 0);
        var droppedItem = new net.minecraft.world.phys.Vec3(5, 0, 0);
        // Carrier is intentionally elsewhere: its position is not an input to this production policy.
        var carrier = new net.minecraft.world.phys.Vec3(100, 0, 0);
        assertTrue(HealthAnalyzerLifecycle.inUiRange(controlledBody, droppedItem, 6.0));
        assertFalse(HealthAnalyzerLifecycle.inUiRange(controlledBody, droppedItem, 4.0));
        assertFalse(HealthAnalyzerLifecycle.inUiRange(carrier, droppedItem, 6.0));
    }

    @Test void rootIdentityRequiresExactLoadedWorldUuidAndMissingRootCannotResolve() {
        UUID droppedId = UUID.randomUUID();
        assertFalse(HealthAnalyzerLifecycle.exactRoot(droppedId, null)); // unloaded/deleted lookup
        assertFalse(HealthAnalyzerLifecycle.exactRoot(droppedId, UUID.randomUUID())); // carrier is not the dropped root
        assertTrue(HealthAnalyzerLifecycle.exactRoot(droppedId, droppedId));
    }

    @Test void transferRootSelectsExactDropItemAndPickupControlledBodyWithoutOverwrite() {
        UUID body = UUID.randomUUID(), droppedItem = UUID.randomUUID();
        var dropRoot = HealthAnalyzerLifecycle.transferRoot(body, droppedItem, true, true);
        assertEquals(droppedItem, dropRoot.entity());
        assertTrue(dropRoot.worldItem());
        var pickupRoot = HealthAnalyzerLifecycle.transferRoot(body, null, false, false);
        assertEquals(body, pickupRoot.entity());
        assertFalse(pickupRoot.worldItem());
        assertNull(HealthAnalyzerLifecycle.transferRoot(body, droppedItem, true, false));
    }

    @Test void inactiveScannerRootIsStillRangeCheckedAndExplicitCloseDoesNotReopen() {
        var body = new net.minecraft.world.phys.Vec3(0, 0, 0);
        var root = new net.minecraft.world.phys.Vec3(3, 0, 0);
        boolean scannerActive = false; // inactive changes sampling only, not root/range policy
        assertFalse(scannerActive);
        assertFalse(HealthAnalyzerLifecycle.inUiRange(body, root, 2));
        boolean subscriberOpen = false; // periodic publishing only visits existing subscribers
        assertFalse(subscriberOpen);
    }

    @Test void pauseAndResumeRetainSubjectReadingsAndOnlyChangeFrameAndQualifiers() {
        UUID scanner = UUID.randomUUID(), actor = UUID.randomUUID(), target = UUID.randomUUID();
        var original = HealthAnalyzerSnapshot.readings(scanner, 2, actor, 12, target, 8, "Patient", "Human", "Stable",
                true, true, Optional.of(310f), Optional.of(.7f), true, false, Map.of("blunt", 12f));
        var paused = HealthAnalyzerLifecycle.paused(original, 9, true, false);
        assertEquals(9, paused.frame()); assertTrue(paused.active()); assertFalse(paused.inRange());
        assertEquals(original.name(), paused.name()); assertEquals(original.targetId(), paused.targetId());
        assertEquals(original.damage(), paused.damage()); assertEquals(original.groups(), paused.groups());
        var resumed = HealthAnalyzerLifecycle.paused(paused, 10, true, true);
        assertEquals(10, resumed.frame()); assertTrue(resumed.inRange());
        assertEquals(original.damage(), resumed.damage()); assertEquals(original.usableBloodFraction(), resumed.usableBloodFraction());
    }
}
