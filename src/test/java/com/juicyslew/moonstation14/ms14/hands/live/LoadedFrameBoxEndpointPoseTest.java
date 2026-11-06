package com.juicyslew.moonstation14.ms14.hands.live;

import dev.ryanhcode.sable.companion.math.Pose3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoadedFrameBoxEndpointPoseTest {
    @Test void productionSelectorLazilyChoosesAndCopiesEndpointOrOrdinaryPose() {
        Pose3d logical = new Pose3d();
        logical.position().set(3, 4, 5);
        Pose3d render = new Pose3d();
        render.position().set(7, 8, 9);
        Pose3d endpoint = (Pose3d) LoadedFrameRayQuery.captureFrameBoxPose(true,
                () -> logical, () -> { throw new AssertionError("endpoint must not evaluate render/provider pose"); });

        assertEquals(3, endpoint.position().x, 0);
        logical.position().x = 99;
        assertEquals(3, endpoint.position().x, 0, "endpoint capture must not alias mutable logical pose");

        Pose3d ordinaryRender = (Pose3d) LoadedFrameRayQuery.captureFrameBoxPose(false,
                () -> { throw new AssertionError("ordinary query must not evaluate endpoint pose"); }, () -> render);
        assertEquals(7, ordinaryRender.position().x, 0, "ordinary frame-box render pose remains selected");
        render.position().x = 42;
        assertEquals(7, ordinaryRender.position().x, 0, "ordinary pose is captured without mutable aliasing");
        assertNull(LoadedFrameRayQuery.captureFrameBoxPose(true, () -> null, () -> render));
    }
}
