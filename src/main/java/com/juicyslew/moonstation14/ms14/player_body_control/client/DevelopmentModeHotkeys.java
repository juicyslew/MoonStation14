package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Convenience chords for the existing permission-checked development commands. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class DevelopmentModeHotkeys {
    static final KeyMapping CREATIVE = new KeyMapping("key.moonstation14.dev_creative", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.moonstation14");
    static final KeyMapping RETURN = new KeyMapping("key.moonstation14.dev_return", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.moonstation14");
    private static boolean wasCreativeDown;
    private static boolean wasReturnDown;

    private DevelopmentModeHotkeys() { }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || mc.screen != null) {
            while (CREATIVE.consumeClick()) { }
            while (RETURN.consumeClick()) { }
            wasCreativeDown = CREATIVE.isDown();
            wasReturnDown = RETURN.isDown();
            return;
        }
        long window = mc.getWindow().getWindow();
        boolean modifiers = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        modifiers &= InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean creativeDown = CREATIVE.isDown(), returnDown = RETURN.isDown();
        while (CREATIVE.consumeClick()) { }
        while (RETURN.consumeClick()) { }
        boolean creative = pressedThisTick(creativeDown, wasCreativeDown);
        boolean returning = pressedThisTick(returnDown, wasReturnDown);
        wasCreativeDown = creativeDown;
        wasReturnDown = returnDown;
        var command = commandForChord(modifiers, mc.screen != null, creative, returning);
        if (command != null) mc.getConnection().sendUnsignedCommand(command);
    }

    static String commandForChord(boolean modifiers, boolean screenOpen, boolean creativePressed, boolean returnPressed) {
        if (!modifiers || screenOpen) return null;
        if (creativePressed) return "ms14dev creative";
        if (returnPressed) return "ms14dev return";
        return null;
    }

    static boolean pressedThisTick(boolean down, boolean wasDown) { return down && !wasDown; }
}
