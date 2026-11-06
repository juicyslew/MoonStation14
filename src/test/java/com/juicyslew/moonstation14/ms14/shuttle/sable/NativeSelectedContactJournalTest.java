package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeSelectedContactJournalTest {
    @Test void validatesDeduplicatesBoundsAndSnapshots() {
        var journal = new NativeSelectedContactJournal();
        UUID frame = UUID.randomUUID();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        journal.record(frame, mutable, new Vec3(Double.NaN, 0, 0), false, Vec3.ZERO, Vec3.ZERO, 1, 10, true);
        journal.record(null, mutable, Vec3.ZERO, false, Vec3.ZERO, Vec3.ZERO, 1, 10, true);
        journal.record(frame, mutable, new Vec3(2, 0, 0), false, new Vec3(1,2,3), new Vec3(4,5,6), 42, 420, true);
        mutable.set(9, 9, 9);
        journal.record(frame, new BlockPos(1, 2, 3), new Vec3(1, .01, 0), true, Vec3.ZERO, Vec3.ZERO, 43, 430, false);
        assertEquals(1, journal.snapshot().size());
        var snapshot = journal.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertEquals(new BlockPos(1, 2, 3), snapshot.getFirst().pos());
        assertEquals(1.0, snapshot.getFirst().normal().x, 1.0e-12);
        assertEquals(42, snapshot.getFirst().serverTick());
        assertEquals(420, snapshot.getFirst().levelGameTime());
        assertEquals(new Vec3(1,2,3), snapshot.getFirst().entityPosition());

        for (int i = 0; i < 8; i++) journal.record(frame, new BlockPos(i, 0, 0), new Vec3(0, 1, 0), false, Vec3.ZERO, Vec3.ZERO, 1, 10, false);
        assertEquals(8, journal.snapshot().size());
        assertTrue(journal.overflowed());
    }
}
