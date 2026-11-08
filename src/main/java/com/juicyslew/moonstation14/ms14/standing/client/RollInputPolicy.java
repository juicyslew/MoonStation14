package com.juicyslew.moonstation14.ms14.standing.client;

import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Classifies the press-time roll chord, independent of later modifier state. */
final class RollInputPolicy {
    enum Intent { NONE, SELF, OTHER }
    private final java.util.ArrayDeque<Intent> pending = new java.util.ArrayDeque<>();
    private Object levelContext;
    private RollInputPolicy() { }
    static RollInputPolicy pending() { return new RollInputPolicy(); }
    void keyEvent(int action, int modifiers, KeyModifier configured, boolean menuOwnsChord) {
        if (action != GLFW.GLFW_PRESS || menuOwnsChord) return;
        Intent intent = classify(modifiers, configured);
        if (intent != Intent.NONE) pending.addLast(intent);
    }
    Intent poll() { return pending.isEmpty() ? Intent.NONE : pending.removeFirst(); }
    void clear() { pending.clear(); }
    void syncContext(Object level) {
        if (levelContext != level) pending.clear();
        levelContext = level;
    }
    void invalidateContext() { pending.clear(); levelContext = null; }
    static Intent classify(int modifiers, KeyModifier configured) {
        int actual = modifiers & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT);
        int expected = configured == KeyModifier.SHIFT ? GLFW.GLFW_MOD_SHIFT
                : configured == KeyModifier.CONTROL ? GLFW.GLFW_MOD_CONTROL
                : configured == KeyModifier.ALT ? GLFW.GLFW_MOD_ALT : 0;
        if ((actual & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) != (expected & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT))) return Intent.NONE;
        if (expected == GLFW.GLFW_MOD_SHIFT) return actual == expected ? Intent.SELF : Intent.NONE;
        if ((actual & ~GLFW.GLFW_MOD_SHIFT) != expected) return Intent.NONE;
        return (actual & GLFW.GLFW_MOD_SHIFT) != 0 ? Intent.OTHER : Intent.SELF;
    }
}
