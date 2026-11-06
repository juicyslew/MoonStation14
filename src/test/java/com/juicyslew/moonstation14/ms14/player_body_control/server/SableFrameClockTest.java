package com.juicyslew.moonstation14.ms14.player_body_control.server;

import org.junit.jupiter.api.Test;
import java.util.OptionalLong;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SableFrameClockTest {
    @Test void keysCompletedSendPassRatherThanNextCounterOrWorldTime() {
        assertEquals(OptionalLong.empty(), SableFrameClock.completedStep(0));
        assertEquals(OptionalLong.empty(), SableFrameClock.completedStep(1));
        assertEquals(OptionalLong.of(1), SableFrameClock.completedStep(2));
        assertEquals(OptionalLong.of(37), SableFrameClock.completedStep(38));
        // A level's Sable counter restarts independently of Minecraft game time.
        assertEquals(OptionalLong.empty(), SableFrameClock.completedStep(1));
    }
}
