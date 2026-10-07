package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.chat.client.LocalSpeechReviewOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Route chat-screen paging to the unified history instead of hidden vanilla history. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenReviewPagingMixin {
    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$pageUnifiedHistory(int keyCode, int scanCode, int modifiers,
                                                  CallbackInfoReturnable<Boolean> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null
                || !LocalSpeechReviewOverlay.replacesVanillaChat()) return;
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            LocalSpeechReviewOverlay.scroll(keyCode == GLFW.GLFW_KEY_PAGE_UP ? 1 : -1,
                    LocalSpeechReviewOverlay.historySize());
            callback.setReturnValue(true);
        }
    }
}
