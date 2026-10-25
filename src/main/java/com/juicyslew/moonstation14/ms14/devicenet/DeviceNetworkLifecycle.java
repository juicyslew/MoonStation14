package com.juicyslew.moonstation14.ms14.devicenet;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One server-post-tick drain/counter decay; dimensions retain isolated endpoint runtimes. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DeviceNetworkLifecycle {
    private DeviceNetworkLifecycle() { }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        DeviceNetworkRuntime.updateServer(event.getServer());
    }
    @SubscribeEvent public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) DeviceNetworkRuntime.unloadLevel(level);
    }
    @SubscribeEvent public static void serverStopped(ServerStoppedEvent event) {
        DeviceNetworkRuntime.stopServer(event.getServer());
    }
}

