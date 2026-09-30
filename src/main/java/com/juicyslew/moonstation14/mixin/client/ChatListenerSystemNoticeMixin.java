package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.chat.client.LocalChatVisualPolicy;
import com.juicyslew.moonstation14.ms14.chat.client.LocalSpeechReviewOverlay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Capture only the accepted non-overlay system-chat branch after NeoForge's system-chat hook. */
@Mixin(ChatListener.class)
public abstract class ChatListenerSystemNoticeMixin {
    // Compose with other wrappers while preserving the accepted message's vanilla path first.
    @WrapOperation(method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;)V"))
    private void moonstation14$acceptedSystemNotice(ChatComponent chat, Component message, Operation<Void> original) {
        original.call(chat, message); // Preserve logging, storage, notifications, and vanilla fallback.
        Minecraft minecraft = Minecraft.getInstance();
        if (LocalChatVisualPolicy.shouldMirrorNotice(LocalChatVisualPolicy.Source.ACCEPTED_SYSTEM_CHAT,
                false, minecraft != null && minecraft.level != null && minecraft.player != null)) {
            LocalSpeechReviewOverlay.onSystemNotice(message);
        }
    }
}
