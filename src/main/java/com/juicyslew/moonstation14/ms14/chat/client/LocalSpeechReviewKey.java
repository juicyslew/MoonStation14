package com.juicyslew.moonstation14.ms14.chat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Configurable client-only shortcut; never intercepts chat or other screens. */
public final class LocalSpeechReviewKey {
    private static final KeyMapping REVIEW = new KeyMapping("key.moonstation14.speech_review",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.moonstation14");
    private static final KeyMapping OLDER = new KeyMapping("key.moonstation14.speech_review_older",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, "key.categories.moonstation14");
    private static final KeyMapping NEWER = new KeyMapping("key.moonstation14.speech_review_newer",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, "key.categories.moonstation14");

    private LocalSpeechReviewKey() { }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(REVIEW);
        event.register(OLDER);
        event.register(NEWER);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        // Drain queued presses even when a menu is open; never replay them into gameplay.
        boolean allowed = minecraft.level != null && minecraft.player != null && minecraft.screen == null;
        while (REVIEW.consumeClick()) if (allowed) LocalSpeechReviewOverlay.toggle();
        // Jump by a page of entries (at least five), without capturing normal gameplay input.
        while (OLDER.consumeClick()) if (allowed) LocalSpeechReviewOverlay.scroll(1, LocalSpeechReviewOverlay.historySize());
        while (NEWER.consumeClick()) if (allowed) LocalSpeechReviewOverlay.scroll(-1, LocalSpeechReviewOverlay.historySize());
    }

    public static String boundKeyName() {
        return REVIEW.getTranslatedKeyMessage().getString();
    }
}
