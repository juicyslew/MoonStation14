package com.juicyslew.moonstation14.ms14.timed_interaction;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** Registration for the presentation-only timed interaction snapshot. */
public final class TimedInteractionNetworking {
    private TimedInteractionNetworking() { }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToClient(TimedInteractionSnapshot.TYPE, TimedInteractionSnapshot.STREAM_CODEC,
                        (payload, context) -> TimedInteractionSnapshot.deliverClient(payload));
    }
}

