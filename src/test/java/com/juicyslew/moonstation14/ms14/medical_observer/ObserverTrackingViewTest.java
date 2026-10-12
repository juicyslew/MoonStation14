package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObserverTrackingViewTest {
    @Test
    void reconciliationRepairsStaleNativeViewAndResyncsChangedOwnerAtSameCenter() {
        Object level = new Object();
        var a = new net.minecraft.world.level.ChunkPos(2, 3);
        var b = new net.minecraft.world.level.ChunkPos(8, 3);
        var stale = ObserverTracking.reconcile(a, 8, level, true, level, a, 8, b, 8);
        assertTrue(stale.applyView(), "actual native view must win over private cache");
        assertFalse(stale.resyncClientCenter());

        var changedOwner = ObserverTracking.reconcile(a, 8, level, false, level, a, 8, a, 8);
        assertTrue(changedOwner.applyView());
        assertTrue(changedOwner.resyncClientCenter());
    }

    @Test
    void reconciliationSuppressesOnlyFullyUnchangedViewAndReappliesRadiusOrLevelChanges() {
        Object level = new Object();
        var center = new net.minecraft.world.level.ChunkPos(2, 3);
        var unchanged = ObserverTracking.reconcile(center, 8, level, true, level, center, 8, center, 8);
        assertFalse(unchanged.applyView());
        assertFalse(unchanged.resyncClientCenter());
        assertTrue(ObserverTracking.reconcile(center, 8, level, true, level, center, 7, center, 8).applyView());
        assertTrue(ObserverTracking.reconcile(center, 8, level, true, new Object(), center, 8, center, 8).applyView());
        assertFalse(ObserverTracking.reconcile(center, 8, level, true, level,
                new net.minecraft.world.level.ChunkPos(3, 3), 8, center, 8).resyncClientCenter());
    }

    @Test
    void productionViewPositionConsumesTheSharedResolvedTarget() {
        ItemEntity ghost = new ItemEntity(EntityType.ITEM, null);
        ghost.setPos(80, 64, 3);
        var target = new ControlledViewTarget.Target(ghost, ControlledViewTarget.Family.LIFECYCLE_GHOST, 7);
        assertEquals(ghost.position(), ObserverTracking.resolveViewPosition(new Vec3(2, 64, 3), target));
        assertEquals(new net.minecraft.world.level.ChunkPos(5, 0), chunk(ObserverTracking.resolveViewPosition(Vec3.ZERO, target)));
    }

    @Test
    void authenticatedCandidateSelectionPreservesFamilyPriorityAndRejectsAbsentCandidates() {
        var ghost = target(ControlledViewTarget.Family.LIFECYCLE_GHOST, 1);
        var medical = target(ControlledViewTarget.Family.MEDICAL_OBSERVER, 2);
        var character = target(ControlledViewTarget.Family.LIFECYCLE_CHARACTER, 3);
        var harness = target(ControlledViewTarget.Family.HARNESS, -1);

        assertSame(ghost, ControlledViewTarget.select(ghost, medical, character, harness));
        assertSame(medical, ControlledViewTarget.select(null, medical, character, harness));
        assertSame(character, ControlledViewTarget.select(null, null, character, harness));
        assertSame(harness, ControlledViewTarget.select(null, null, null, harness));
        assertNull(ControlledViewTarget.select(null, null, null, null));
        // Stale, wrong-level, removed, or dead authorities must be filtered by production valid() before selection.
    }

    @Test
    void bothTrackingConsumersUseSharedAuthenticatedResolver() throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("user.dir"));
        while (root != null && !java.nio.file.Files.isDirectory(root.resolve("src/main/java"))) root = root.getParent();
        assertNotNull(root, "Could not locate project source root");
        for (Class<?> consumer : new Class<?>[]{ObserverTracking.class, ObserverChunkTickets.class}) {
            String source = java.nio.file.Files.readString(root.resolve(
                    "src/main/java/com/juicyslew/moonstation14/ms14/medical_observer/" + consumer.getSimpleName() + ".java"));
            assertTrue(source.contains("ControlledViewTarget.resolve(player)"), consumer.getSimpleName());
        }
        String resolver = java.nio.file.Files.readString(root.resolve(
                "src/main/java/com/juicyslew/moonstation14/ms14/medical_observer/ControlledViewTarget.java"));
        assertTrue(resolver.contains("LifecycleGhostSessionControl.canonicalGhost(player)"));
        assertTrue(resolver.contains("medical.boundAuthority() == null"));
        assertTrue(resolver.contains("lifecycleGhost == null ? null : lifecycleGhost.entity()"));
    }

    private static ControlledViewTarget.Target target(ControlledViewTarget.Family family, long epoch) {
        return new ControlledViewTarget.Target(new ItemEntity(EntityType.ITEM, null), family, epoch);
    }

    private static net.minecraft.world.level.ChunkPos chunk(Vec3 position) {
        return new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(position));
    }
}
