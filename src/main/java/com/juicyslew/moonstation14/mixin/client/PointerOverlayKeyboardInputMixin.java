package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes focused nonmodal workspace editors while gameplay has no vanilla Screen. */
@Mixin(KeyboardHandler.class)
abstract class PointerOverlayKeyboardInputMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void ms14$routePointerOverlayKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || !PointerOverlayRegistry.textFocused()) return;
        if ((action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)
                && PointerOverlayRegistry.key(key, scanCode, modifiers)) ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void ms14$routePointerOverlayCharacter(long window, int codePoint, int modifiers, CallbackInfo ci) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || !PointerOverlayRegistry.textFocused()) return;
        if (Character.isBmpCodePoint(codePoint)) {
            if (PointerOverlayRegistry.character((char) codePoint, modifiers)) ci.cancel();
            return;
        }
        char[] pair = Character.toChars(codePoint);
        boolean first = PointerOverlayRegistry.character(pair[0], modifiers);
        boolean second = PointerOverlayRegistry.character(pair[1], modifiers);
        if (first || second) ci.cancel();
    }
}
