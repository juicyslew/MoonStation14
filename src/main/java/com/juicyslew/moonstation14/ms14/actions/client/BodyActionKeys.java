package com.juicyslew.moonstation14.ms14.actions.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

public final class BodyActionKeys {
    private static final KeyMapping OPEN = new KeyMapping("key.moonstation14.actions", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.moonstation14");
    private static final KeyMapping[] SLOTS = java.util.stream.IntStream.rangeClosed(1, 9)
            .mapToObj(n -> slotKey(n, GLFW.GLFW_KEY_1 + n - 1)).toArray(KeyMapping[]::new);
    private static KeyMapping slotKey(int n, int key) { return new KeyMapping("key.moonstation14.action_slot_" + n, InputConstants.Type.KEYSYM, key, "key.categories.moonstation14"); }
    private BodyActionKeys() { }
    public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); for (var slot : SLOTS) event.register(slot); }
    public static boolean matches(int keyCode, int scanCode) { return OPEN.matches(keyCode, scanCode); }
    public static void beforeVanillaKeybinds() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        KeyMapping[] actions = SLOTS;
        var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForHud();
        long epoch = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
        boolean eligible = BodyActionKeyPolicy.eligible(mc.level != null, mc.screen == null, body != null,
                body != null && body.level() == mc.level, epoch);
        if (BodyActionKeyPolicy.consumeDebugChord(GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS, () -> drain(actions))) return;
        if (!eligible) { for (var action : actions) while (action.consumeClick()) { } return; }
        var snapshot = BodyActionClient.snapshot();
        if (snapshot == null || !BodyActionKeyPolicy.snapshotMatches(body.getUUID(), epoch, snapshot.bodyId(), snapshot.bodyEpoch())) { for (var action : actions) while (action.consumeClick()) { } return; }
        var hotbar = mc.options.keyHotbarSlots;
        for (int i = 0; i < actions.length; i++) {
            boolean pressed = false;
            while (actions[i].consumeClick()) pressed = true;
            int winner = i;
            if (!pressed) continue;
            boolean[] conflicts = new boolean[actions.length];
            for (int prior = 0; prior < i; prior++) conflicts[prior] = actions[prior].same(actions[i]);
            winner = BodyActionKeyPolicy.bindingWinner(conflicts, i);
            if (winner != i) continue;
            for (int j = i + 1; j < actions.length; j++) if (actions[j].same(actions[i])) while (actions[j].consumeClick()) { }
            for (var slot : hotbar) if (!slot.isUnbound() && slot.getKey().equals(actions[i].getKey()) && slot.getKeyModifier().equals(actions[i].getKeyModifier())) while (slot.consumeClick()) { }
            var entry = BodyActionSlots.at(snapshot, i + 1);
            if (entry != null) BodyActionClient.activate(entry);
        }
    }
    private static void drain(KeyMapping[] actions) { for (var action : actions) while (action.consumeClick()) { } }
    @EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class InputGuard {
        @SubscribeEvent public static void onKey(InputEvent.Key event) {
            if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) return;
            if (java.util.Arrays.stream(SLOTS).noneMatch(slot -> slot.matches(event.getKey(), event.getScanCode()))) return;
            BodyActionKeyPolicy.consumeDebugChord(GLFW.glfwGetKey(net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS, () -> drain(SLOTS));
        }
    }
    public static void tick() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForHud();
            if (body == null || mc.level == null || body.level() != mc.level
                    || com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch() <= 0
                    || mc.screen != null && !(mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen)) continue;
            BodyActionsScreen.toggle();
        }
    }
}
