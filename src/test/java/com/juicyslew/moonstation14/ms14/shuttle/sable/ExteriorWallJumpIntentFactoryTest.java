package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExteriorWallJumpIntentFactoryTest {
    @Test void registeredExteriorDriverEncodesJumpInButtonsNotVerticalWish() {
        var jump=ExteriorWallSustainedJumpGameTests.intentFor(19,23,1000,250,
                GhostControlPayloads.BUTTON_JUMP,42.5f);
        assertEquals(19,jump.epoch());
        assertEquals(23,jump.sequence());
        assertEquals((short)1000,jump.wishX());
        assertEquals((short)250,jump.wishZ());
        assertEquals((byte)0,jump.verticalWish());
        assertEquals(GhostControlPayloads.BUTTON_JUMP,jump.buttons());
        assertEquals(42.5f,jump.yaw());

        var heldForward=ExteriorWallSustainedJumpGameTests.intentFor(19,24,1000,250,0,42.5f);
        assertEquals((byte)0,heldForward.verticalWish());
        assertEquals(0,heldForward.buttons(),"released jump leaves both button and vertical wish neutral");
    }
}
