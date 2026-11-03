package com.juicyslew.moonstation14.ms14.actions.client;

/** Pure SS14 direct-action defaults; slot number is stable and 1-based. */
public final class BodyActionHotkeyDefaults {
    public static final int MAX_SLOTS = 20;
    public record Binding(int slot, int digit, boolean shift, String compactLabel) { }
    private BodyActionHotkeyDefaults() { }
    public static Binding forSlot(int slot) {
        if (slot < 1 || slot > BodyActionSlots.MAX_SLOTS) return null;
        int index = (slot - 1) % 10;
        int digit = index == 9 ? 0 : index + 1;
        return new Binding(slot, digit, slot > 10, (slot > 10 ? "S+" : "") + digit);
    }
    public static String compactLabel(String keyName, net.neoforged.neoforge.client.settings.KeyModifier modifier) {
        String prefix = modifier == net.neoforged.neoforge.client.settings.KeyModifier.SHIFT ? "S+"
                : modifier == net.neoforged.neoforge.client.settings.KeyModifier.CONTROL ? "C+"
                : modifier == net.neoforged.neoforge.client.settings.KeyModifier.ALT ? "A+" : "";
        return prefix + keyName;
    }
    public static String compactLabel(String keyName, boolean shift) { return compactLabel(keyName,
            shift ? net.neoforged.neoforge.client.settings.KeyModifier.SHIFT : net.neoforged.neoforge.client.settings.KeyModifier.NONE); }
}
