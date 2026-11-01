package com.juicyslew.moonstation14.ms14.combat.client;

/** Non-invasive focus requirements for human-owned diagnostic input. */
final class MeleeCaptureFocusPolicy {
    private MeleeCaptureFocusPolicy() { }
    static String reason(boolean active, boolean screenOpen, boolean grabbed) {
        if (!active) return "user-focus-unavailable";
        if (screenOpen) return "menu-open";
        if (!grabbed) return "user-focus-released";
        return null;
    }
    static boolean permits(boolean active, boolean screenOpen, boolean grabbed) {
        return reason(active, screenOpen, grabbed) == null;
    }
    static String reason(net.minecraft.client.Minecraft mc) {
        return reason(mc.isWindowActive(), mc.screen != null, mc.mouseHandler.isMouseGrabbed());
    }
    static void require(net.minecraft.client.Minecraft mc) {
        String reason = reason(mc);
        if (reason != null) throw new IllegalStateException("validation interference: " + reason);
    }
}
