package com.juicyslew.moonstation14.ms14.construction.tool;

import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BodyDisposalTubeAnchorUseTest {
    @Test
    void resolvedToolSpeedScalesOneSecondDelayAndAlwaysUsesWholeServerTicks() {
        assertEquals(20, BodyDisposalTubeAnchorUse.delayTicks(1.0));
        assertEquals(10, BodyDisposalTubeAnchorUse.delayTicks(2.0));
        assertEquals(40, BodyDisposalTubeAnchorUse.delayTicks(0.5));
        assertEquals(1, BodyDisposalTubeAnchorUse.delayTicks(100.0));
        assertThrows(IllegalArgumentException.class, () -> BodyDisposalTubeAnchorUse.delayTicks(0));
    }

    @Test
    void targetRevalidationRequiresExactTubeIdentityRevisionAndAnchorState() {
        UUID id = UUID.randomUUID();
        var captured = new DisposalTubeRecord(id, new BlockPos(1, 2, 3), "disposal_pipe", Direction.NORTH,
                false, "", "", 7);
        assertEquals(true, BodyDisposalTubeAnchorUse.sameTube(captured, id, 7, false));
        assertEquals(false, BodyDisposalTubeAnchorUse.sameTube(new DisposalTubeRecord(UUID.randomUUID(), captured.pos(),
                captured.prototypeId(), captured.orientation(), false, "", "", 7), id, 7, false));
        assertEquals(false, BodyDisposalTubeAnchorUse.sameTube(new DisposalTubeRecord(id, captured.pos(),
                captured.prototypeId(), captured.orientation(), false, "", "", 8), id, 7, false));
        assertEquals(false, BodyDisposalTubeAnchorUse.sameTube(captured, id, 7, true));
    }
}
