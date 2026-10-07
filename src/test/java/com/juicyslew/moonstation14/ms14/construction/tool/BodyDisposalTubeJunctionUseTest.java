package com.juicyslew.moonstation14.ms14.construction.tool;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyDisposalTubeJunctionUseTest {
    @Test void screwingSpeedDeterminesAtLeastOneTickAndRoundsUp() {
        assertEquals(20, BodyDisposalTubeRotationUse.requiredTicks(1.0));
        assertEquals(10, BodyDisposalTubeRotationUse.requiredTicks(2.0));
        assertEquals(4, BodyDisposalTubeRotationUse.requiredTicks(6.0));
        assertEquals(1, BodyDisposalTubeRotationUse.requiredTicks(100.0));
        assertThrows(IllegalArgumentException.class, () -> BodyDisposalTubeRotationUse.requiredTicks(0));
    }

    @Test void exactRecordIdentityAndPrototypeArePartOfRotationAdmission() {
        UUID id = UUID.randomUUID();
        var record = new DisposalTubeRecord(id, new BlockPos(1, 2, 3), "moonstation14:disposal_pipe",
                new DisposalOrientation(Direction.NORTH, Direction.UP), false, "", "", 4,
                DisposalTubeRecord.HostKind.EXPOSED, "", null);
        assertTrue(BodyDisposalTubeRotationUse.same(record, record));
        assertFalse(BodyDisposalTubeRotationUse.same(new DisposalTubeRecord(UUID.randomUUID(), record.pos(),
                record.prototypeId(), record.frame(), false, "", "", 4, DisposalTubeRecord.HostKind.EXPOSED, "", null), record));
        assertFalse(BodyDisposalTubeRotationUse.same(new DisposalTubeRecord(id, record.pos(),
                "moonstation14:disposal_router", record.frame(), false, "", "", 4,
                DisposalTubeRecord.HostKind.EXPOSED, "", null), record));
    }
}
