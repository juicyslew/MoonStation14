package com.juicyslew.moonstation14.ms14.chat.server;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Full-replacement accent policies ported from the pinned SS14 mouse accent. */
public final class SpeechAccentPolicy {
    private static final List<String> MOUSE = List.of("Squeak!", "Piep!", "Chuu!", "Eeee!", "Pip!", "Fwiep!", "Heep!");
    private SpeechAccentPolicy() { }

    public static String replace(String accent, String text) {
        if (!"mouse".equals(accent) || text == null || text.isBlank()) return text;
        return MOUSE.get(ThreadLocalRandom.current().nextInt(MOUSE.size()));
    }

    public static String replace(String accent, String text, int selection) {
        if (!"mouse".equals(accent) || text == null || text.isBlank()) return text;
        return MOUSE.get(Math.floorMod(selection, MOUSE.size()));
    }

    /** Injectable selection makes the exact replacement mapping straightforward to test. */
    public static String replacement(String accent, int selection) {
        return "mouse".equals(accent) && selection >= 0 && selection < MOUSE.size() ? MOUSE.get(selection) : null;
    }
}
