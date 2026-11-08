package com.juicyslew.moonstation14.ms14.combat;

/** Client receipt queue for authoritative committed-disarm flight events. */
public final class DisarmFlightClientState {
    private static volatile java.util.function.Consumer<TransientDisarmFlightPayload> receiver = payload -> { };
    private DisarmFlightClientState() { }
    public static void install() {
        receiver = payload -> net.minecraft.client.Minecraft.getInstance().execute(() ->
                com.juicyslew.moonstation14.ms14.combat.client.DisarmFlightVisuals.receive(payload));
    }
    public static void receive(TransientDisarmFlightPayload payload) { receiver.accept(payload); }
}
