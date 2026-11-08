package com.juicyslew.moonstation14.ms14.hands.client;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NativeMovementKeySamplerTest {
    private static KeyMapping key(int code, String name) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM, code, "test");
    }

    @Test void samplesReboundPhysicalKeysOnlyWhenAuthorizedAndFocused() {
        var intent = new PopupMovementIntent();
        var held = Set.of(265, 32);
        NativeMovementKeySampler.KeyReader reader = held::contains;
        var state = NativeMovementKeySampler.sample(true, true, key(265,"up"), key(264,"down"),
                key(263,"left"), key(262,"right"), key(32,"jump"), key(340,"shift"), key(SPRINT,"sprint"), reader, intent);
        assertEquals(1, state.forward());
        assertTrue(state.jump());
        assertEquals(PopupMovementIntent.State.IDLE, NativeMovementKeySampler.sample(true, false,
                key(265,"up"), key(264,"down"), key(263,"left"), key(262,"right"), key(32,"jump"),
                key(340,"shift"), key(SPRINT,"sprint"), reader, intent));
    }
    private static final int SPRINT = 341;
}
