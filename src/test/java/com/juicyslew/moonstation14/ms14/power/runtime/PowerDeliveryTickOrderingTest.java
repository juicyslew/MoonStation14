package com.juicyslew.moonstation14.ms14.power.runtime;

import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkLifecycle;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins graph refresh -> published power sample -> device-network deferred delivery order. */
class PowerDeliveryTickOrderingTest {
    @Test void postTickPhasesPublishPowerBeforeDevicePacketDrain() throws Exception {
        assertPriority(PowerGraphService.class.getMethod("onPostServerTick", ServerTickEvent.Post.class),
                EventPriority.HIGH);
        assertPriority(PowerRuntime.class.getMethod("onTick", ServerTickEvent.Post.class), EventPriority.NORMAL);
        assertPriority(DeviceNetworkLifecycle.class.getMethod("serverTick", ServerTickEvent.Post.class),
                EventPriority.LOWEST);
    }

    private static void assertPriority(java.lang.reflect.Method method, EventPriority expected) {
        assertEquals(expected, method.getAnnotation(SubscribeEvent.class).priority(), method.getName());
    }
}
