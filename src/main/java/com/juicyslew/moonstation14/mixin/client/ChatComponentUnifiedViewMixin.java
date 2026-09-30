package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.chat.client.LocalChatVisualPolicy;
import com.juicyslew.moonstation14.ms14.chat.client.LocalSpeechReviewOverlay;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide vanilla history visuals and hit targets, never its storage or chat input. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentUnifiedViewMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$hideVanillaHistory(GuiGraphics graphics, int tick, int mouseX, int mouseY,
                                                   boolean focused, CallbackInfo callback) {
        if (moonstation14$hideVanillaHistory()) callback.cancel();
    }

    @Inject(method = "getClickedComponentStyleAt(DD)Lnet/minecraft/network/chat/Style;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$hideVanillaClickStyle(double mouseX, double mouseY,
                                                     CallbackInfoReturnable<Style> callback) {
        if (moonstation14$hideVanillaHistory()) callback.setReturnValue(null);
    }

    @Inject(method = "handleChatQueueClicked(DD)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$hideVanillaQueueClick(double mouseX, double mouseY,
                                                     CallbackInfoReturnable<Boolean> callback) {
        if (moonstation14$hideVanillaHistory()) callback.setReturnValue(false);
    }

    @Inject(method = "getMessageTagAt(DD)Lnet/minecraft/client/GuiMessageTag;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$hideVanillaTagHover(double mouseX, double mouseY,
                                                   CallbackInfoReturnable<GuiMessageTag> callback) {
        if (moonstation14$hideVanillaHistory()) callback.setReturnValue(null);
    }

    @Inject(method = "scrollChat(I)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$scrollUnifiedHistory(int amount, CallbackInfo callback) {
        if (!moonstation14$hideVanillaHistory()) return;
        LocalSpeechReviewOverlay.scroll(LocalChatVisualPolicy.scrollDirection(amount), LocalSpeechReviewOverlay.historySize());
        callback.cancel();
    }

    private static boolean moonstation14$hideVanillaHistory() {
        Minecraft minecraft = Minecraft.getInstance();
        return LocalChatVisualPolicy.shouldHideVanillaHistory(LocalSpeechReviewOverlay.replacesVanillaChat(),
                minecraft != null && minecraft.level != null && minecraft.player != null);
    }
}
