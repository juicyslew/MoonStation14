package com.juicyslew.moonstation14.ms14.station.debug;

import net.neoforged.bus.api.IEventBus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class StarterStationRegistrationTest {
    private static final String PROPERTY = "ms14.starterStation.enabled";

    @Test void registrationIsEnabledByDefaultAndOnlyExplicitFalseDisablesBothListeners() {
        String previous = System.getProperty(PROPERTY);
        try {
            System.clearProperty(PROPERTY);
            assertEquals(2, registerCount());
            System.setProperty(PROPERTY, "true");
            assertEquals(2, registerCount());
            System.setProperty(PROPERTY, "false");
            assertEquals(0, registerCount());
            System.setProperty(PROPERTY, "unexpected");
            assertEquals(2, registerCount());
        } finally {
            if (previous == null) System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY, previous);
        }
    }

    private static int registerCount() {
        AtomicInteger listeners = new AtomicInteger();
        IEventBus bus = (IEventBus) Proxy.newProxyInstance(IEventBus.class.getClassLoader(),
                new Class<?>[]{IEventBus.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addListener")) listeners.incrementAndGet();
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    return null;
                });
        StarterStationService.register(bus);
        return listeners.get();
    }
}
