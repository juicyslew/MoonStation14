package com.juicyslew.moonstation14.ms14.devicenet;

import com.juicyslew.moonstation14.ms14.devicenet.item.RemoteSignallerServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoteSignallerTickOrderingTest {
    @Test
    void endpointCleanupRunsAfterDefaultPriorityNetworkDrain() throws Exception {
        SubscribeEvent subscription = RemoteSignallerServer.class
                .getMethod("endTick", net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class)
                .getAnnotation(SubscribeEvent.class);

        assertEquals(EventPriority.LOWEST, subscription.priority());
    }
}
