package com.juicyslew.moonstation14.ms14.player_body_control.diagnostics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovementTraceFrameSamplerTest {
    @Test void frameIntervalIsBetweenMarkersAndCameraQuotaConsumesOnlySuccessfulOwnedSample() {
        var sampler=new MovementTraceFrameSampler();
        assertNull(sampler.current());
        assertFalse(sampler.canSampleOwnedBody()); // camera setup before a frame marker is skipped
        var first=sampler.onFrame(100);
        assertEquals(1,first.id()); assertFalse(first.intervalKnown()); assertEquals(0,first.intervalNanos());
        assertTrue(sampler.canSampleOwnedBody());
        sampler.sampleRecorded(false); // a failed/mismatched observation does not consume the frame
        assertTrue(sampler.canSampleOwnedBody());
        sampler.sampleRecorded(true);
        assertFalse(sampler.canSampleOwnedBody());
        assertEquals(2_000,sampler.onFrame(2_100).intervalNanos());
        assertEquals(2,sampler.current().id()); assertTrue(sampler.current().intervalKnown());
        assertTrue(sampler.canSampleOwnedBody());
        sampler.reset();
        assertNull(sampler.current());
        assertFalse(sampler.canSampleOwnedBody());
        assertFalse(sampler.onFrame(3_000).intervalKnown());
    }
}
