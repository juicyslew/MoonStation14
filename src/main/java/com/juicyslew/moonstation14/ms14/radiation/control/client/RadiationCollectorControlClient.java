package com.juicyslew.moonstation14.ms14.radiation.control.client;

import com.juicyslew.moonstation14.ms14.radiation.control.RadiationCollectorControlSnapshot;

/** Client-side owner for the bounded context query state. */
public final class RadiationCollectorControlClient {
    public static final RadiationCollectorContextState STATE = new RadiationCollectorContextState();
    private RadiationCollectorControlClient() { }
    public static void receive(RadiationCollectorControlSnapshot snapshot) {
        STATE.receive(snapshot);
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (STATE.ready() != snapshot) return;
        var current = minecraft.screen;
        var decision = RadiationCollectorContextState.screenDecision(current != null,
                current instanceof RadiationCollectorContextScreen);
        if (decision != RadiationCollectorContextState.ScreenDecision.NO_OPEN)
            minecraft.setScreen(new RadiationCollectorContextScreen(snapshot));
    }
}
