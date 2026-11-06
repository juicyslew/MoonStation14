package com.juicyslew.moonstation14.ms14.player_body_control.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientSnapshotAuthorityTest {
    @Test void correctionWarningUsesPredictedTimeDeltaOrBarrierOnly() {
        Vec3 before = new Vec3(0, 0, 0), authority = new Vec3(0, 0, 1), pendingFinal = before;
        assertEquals(false, ClientSnapshotAuthority.shouldReportCorrection(before, pendingFinal, false));
        assertEquals(true, ClientSnapshotAuthority.shouldReportCorrection(before, new Vec3(0, 0, .35), false));
        assertEquals(true, ClientSnapshotAuthority.shouldReportCorrection(before, pendingFinal, true));
    }
}
