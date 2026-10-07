package com.juicyslew.moonstation14.ms14.doors.ui.client;

import com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireSnapshot;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/** Client lifecycle bridge called only after the server has authenticated panel access. */
public final class AirlockWireClient {
    private static AirlockWireScreen active;
    private static final AirlockWireLifecycle LIFECYCLE = new AirlockWireLifecycle();
    private AirlockWireClient() { }

    public static void open(int windowId, UUID session) {
        Minecraft.getInstance().execute(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null || session == null || windowId < 0) return;
            if (active != null) active.closeFromServer();
            active = new AirlockWireScreen(windowId, session);
            LIFECYCLE.register(active);
            active.startView();
            com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.openWireWorkspace();
        });
    }

    public static void accept(AirlockWireSnapshot snapshot) {
        var mc = Minecraft.getInstance();
        if (active != null) active.handleResponse(snapshot);
    }

    public static void close(int windowId, UUID session) {
        var mc = Minecraft.getInstance();
        if (active != null && active.windowId() == windowId && active.session().equals(session)) {
            var screen = active;
            active = null;
            LIFECYCLE.unregister(screen);
            screen.closeFromServer();
            com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.wireClosed();
        }
    }

    public static AirlockWireScreen active() { return active; }
    static void removed(AirlockWireScreen screen) {
        if (LIFECYCLE.unregister(screen) && active == screen) active = null;
    }
    static boolean isActive(AirlockWireScreen screen) { return LIFECYCLE.isActive(screen); }
    public static void authorityLost() {
        var old = active;
        active = null;
        if (old != null) LIFECYCLE.unregister(old);
        if (old != null) old.closeFromServer();
    }
}
