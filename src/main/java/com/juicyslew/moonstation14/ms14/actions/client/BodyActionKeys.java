package com.juicyslew.moonstation14.ms14.actions.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class BodyActionKeys {
    private static final KeyMapping OPEN = new KeyMapping("key.moonstation14.actions",
            net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.moonstation14");
    private static boolean legacyBindingChecked;
    private static final KeyMapping[] SLOTS = createSlots();
    private static KeyMapping[] createSlots() {
        KeyMapping[] result = new KeyMapping[BodyActionHotkeyDefaults.MAX_SLOTS];
        for (int i = 0; i < result.length; i++) {
            var binding = BodyActionHotkeyDefaults.forSlot(i + 1);
            int key = switch (binding.digit()) {
                case 0 -> GLFW.GLFW_KEY_0; case 1 -> GLFW.GLFW_KEY_1; case 2 -> GLFW.GLFW_KEY_2; case 3 -> GLFW.GLFW_KEY_3;
                case 4 -> GLFW.GLFW_KEY_4; case 5 -> GLFW.GLFW_KEY_5; case 6 -> GLFW.GLFW_KEY_6; case 7 -> GLFW.GLFW_KEY_7;
                case 8 -> GLFW.GLFW_KEY_8; default -> GLFW.GLFW_KEY_9;
            };
            result[i] = new KeyMapping("key.moonstation14.action_slot_" + binding.slot(),
                    net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
                    binding.shift() ? KeyModifier.SHIFT : KeyModifier.NONE, InputConstants.Type.KEYSYM, key, "key.categories.moonstation14");
        }
        return result;
    }
    private BodyActionKeys() { }
    public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); for (var slot : SLOTS) event.register(slot); }
    /** Repairs the legacy plain-K binding saved before the Actions chord moved to Ctrl+K. */
    public static void normalizeLegacyOpenBinding() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (legacyBindingChecked || mc.options == null || !java.util.Arrays.asList(mc.options.keyMappings).contains(OPEN)) return;
        legacyBindingChecked = true;
        if (OPEN.getKey().getType() == InputConstants.Type.KEYSYM
                && OPEN.getKey().getValue() == GLFW.GLFW_KEY_K
                && OPEN.getKeyModifier() == KeyModifier.NONE) {
            OPEN.setKeyModifierAndCode(KeyModifier.CONTROL, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            KeyMapping.resetMapping();
            drain(new KeyMapping[]{OPEN});
            mc.options.save();
        }
    }
    public static boolean matches(int keyCode, int scanCode) { return OPEN.matches(keyCode, scanCode); }
    public static boolean matches(int keyCode, int scanCode, int modifiers) {
        return OPEN.matches(keyCode, scanCode) && BodyActionKeyPolicy.exactModifiers(modifiers, OPEN.getKeyModifier());
    }
    public static net.minecraft.network.chat.Component labelForSlot(int slot) {
        return slot < 1 || slot > SLOTS.length ? net.minecraft.network.chat.Component.empty() : SLOTS[slot - 1].getTranslatedKeyMessage();
    }
    public static String compactLabelForSlot(int slot) {
        if (slot < 1 || slot > SLOTS.length) return "";
        KeyMapping key = SLOTS[slot - 1];
        String name = key.getKey().getDisplayName().getString();
        return BodyActionHotkeyDefaults.compactLabel(name, key.getKeyModifier());
    }
    public static void beforeVanillaKeybinds() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        KeyMapping[] actions = SLOTS;
        var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForHud();
        long epoch = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
        boolean eligible = BodyActionKeyPolicy.eligible(mc.level != null, mc.screen == null, body != null, body != null && body.level() == mc.level, epoch);
        if (BodyActionKeyPolicy.consumeDebugChord(GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS, () -> drain(SLOTS))) return;
        if (!eligible) { drain(SLOTS); return; }
        var snapshot = BodyActionClient.snapshot();
        if (snapshot == null || !BodyActionKeyPolicy.snapshotMatches(body.getUUID(), epoch, snapshot.bodyId(), snapshot.bodyEpoch())) { drain(SLOTS); return; }
        var hotbar = mc.options.keyHotbarSlots;
        for (int i = 0; i < SLOTS.length; i++) {
            KeyMapping action = SLOTS[i];
            boolean pressed = false;
            while (action.consumeClick()) {
                if (action.getKeyModifier().isActive(null)) pressed = true;
            }
            if (!pressed) continue;
            int winner = BodyActionKeyPolicy.bindingWinner(java.util.Arrays.stream(SLOTS).map(BodyActionKeys::bindingIdentity).toArray(String[]::new), i);
            if (winner != i) continue;
            for (int j = i + 1; j < SLOTS.length; j++) if (bindingIdentity(SLOTS[j]).equals(bindingIdentity(action))) drain(new KeyMapping[]{SLOTS[j]});
            for (var slot : hotbar) if (!slot.isUnbound() && slot.getKey().equals(action.getKey()) && slot.getKeyModifier().equals(action.getKeyModifier())) drain(new KeyMapping[]{slot});
            BodyActionClient.activate(BodyActionClient.actionAt(i + 1));
        }
    }
    private static String bindingIdentity(KeyMapping key) { return key.getKey().getName() + ":" + key.getKeyModifier().name(); }
    private static void drain(KeyMapping[] actions) { for (var action : actions) while (action.consumeClick()) { } }
    @EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class InputGuard {
        @SubscribeEvent public static void onKey(InputEvent.Key event) {
            if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) return;
            boolean match = java.util.Arrays.stream(SLOTS).anyMatch(k -> k.matches(event.getKey(), event.getScanCode())
                    && BodyActionKeyPolicy.exactModifiers(event.getModifiers(), k.getKeyModifier()));
            var mc = net.minecraft.client.Minecraft.getInstance();
            boolean sameOpenKey = OPEN.matches(event.getKey(), event.getScanCode());
            if (BodyActionKeyPolicy.shouldDrainInvalidChord(sameOpenKey, event.getModifiers(), OPEN.getKeyModifier()))
                drain(new KeyMapping[]{OPEN});
            if (match) BodyActionKeyPolicy.consumeDebugChord(GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS, () -> drain(SLOTS));
        }
    }
    public static void tick() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        normalizeLegacyOpenBinding();
        while (OPEN.consumeClick()) {
            var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForHud();
            if (body == null || mc.level == null || body.level() != mc.level || com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch() <= 0 || mc.screen != null && !(mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen)) continue;
            BodyActionsScreen.toggle();
        }
    }
}
