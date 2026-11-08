package com.juicyslew.moonstation14.ms14.hands.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Samples rebound keyboard movement bindings from native physical key state. */
public final class NativeMovementKeySampler {
    @FunctionalInterface public interface KeyReader { boolean pressed(int key); }
    private NativeMovementKeySampler() { }

    public static PopupMovementIntent.State sample(boolean authorized, boolean focused, KeyMapping up,
            KeyMapping down, KeyMapping left, KeyMapping right, KeyMapping jump, KeyMapping shift,
            KeyMapping sprint, KeyReader reader, PopupMovementIntent intent) {
        boolean eligible = authorized && focused;
        return intent.sample(eligible, eligible && held(up, reader), eligible && held(down, reader),
                eligible && held(left, reader), eligible && held(right, reader), eligible && held(jump, reader),
                eligible && held(shift, reader), eligible && held(sprint, reader));
    }

    private static boolean held(KeyMapping mapping, KeyReader reader) {
        var key = mapping.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) return false;
        int code = key.getType() == InputConstants.Type.SCANCODE ? scanKey(key.getValue()) : key.getValue();
        return code >= 0 && code <= GLFW.GLFW_KEY_LAST && reader.pressed(code);
    }

    private static int scanKey(int scan) {
        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++)
            if (GLFW.glfwGetKeyScancode(key) == scan) return key;
        return -1;
    }
}
