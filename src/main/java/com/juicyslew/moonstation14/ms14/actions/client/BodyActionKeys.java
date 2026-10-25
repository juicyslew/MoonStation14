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
    private static final KeyMapping SLOT_1 = slotKey(1, GLFW.GLFW_KEY_1);
    private static final KeyMapping SLOT_2 = slotKey(2, GLFW.GLFW_KEY_2);
    private static final KeyMapping SLOT_3 = slotKey(3, GLFW.GLFW_KEY_3);
    private static KeyMapping slotKey(int n, int key) { return new KeyMapping("key.moonstation14.action_slot_" + n, InputConstants.Type.KEYSYM, key, "key.categories.moonstation14"); }
    private BodyActionKeys() { }
    public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); event.register(SLOT_1); event.register(SLOT_2); event.register(SLOT_3); }
    public static boolean matches(int keyCode, int scanCode) { return OPEN.matches(keyCode, scanCode); }
    public static void beforeVanillaKeybinds() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        KeyMapping[] actions = { SLOT_1, SLOT_2, SLOT_3 };
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
            winner = BodyActionKeyPolicy.bindingWinner(new String[]{actions[0].getName(), actions[1].getName(), actions[2].getName()}, i);
            if (winner != i) continue;
            for (int j = i + 1; j < actions.length; j++) if (actions[j].same(actions[i])) while (actions[j].consumeClick()) { }
            for (var slot : hotbar) if (!slot.isUnbound() && slot.getKey().equals(actions[i].getKey()) && slot.getKeyModifier().equals(actions[i].getKeyModifier())) while (slot.consumeClick()) { }
            BodyActionClient.activate(BodyActionSlots.at(snapshot, i + 1));
        }
    }
    private static void drain(KeyMapping[] actions) { for (var action : actions) while (action.consumeClick()) { } }
    @EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class InputGuard {
        @SubscribeEvent public static void onKey(InputEvent.Key event) {
            if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) return;
            if (! (SLOT_1.matches(event.getKey(), event.getScanCode()) || SLOT_2.matches(event.getKey(), event.getScanCode()) || SLOT_3.matches(event.getKey(), event.getScanCode()))) return;
            BodyActionKeyPolicy.consumeDebugChord(GLFW.glfwGetKey(net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS, () -> drain(new KeyMapping[]{SLOT_1, SLOT_2, SLOT_3}));
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
