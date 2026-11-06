package com.juicyslew.stockcontrol;

import net.minecraft.gametest.framework.GameTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;

@EventBusSubscriber(modid = "sable_control", bus = EventBusSubscriber.Bus.MOD)
public final class StockControlTestRegistration {
    private StockControlTestRegistration() { }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        StockControlIsolation.verify();
        try {
            Class<?> fixture = Class.forName("com.juicyslew.stockcontrol.StockHullImpactGameTests", false,
                    StockControlTestRegistration.class.getClassLoader());
            Method[] methods = Arrays.stream(fixture.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(GameTest.class)).toArray(Method[]::new);
            if (methods.length != 2) throw new IllegalStateException("Expected exactly two discovered stock fixtures, found " + methods.length);
            for (Method method : methods) event.register(method);
            LoggerFactory.getLogger("StockSableControl").info("GameTest tag preflight: {}/{} fixtures selected", methods.length, methods.length);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Stock impact fixture class is unavailable", e);
        }
    }
}
