package com.juicyslew.moonstation14.ms14.player_body_control.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Key mappings are registered on the mod event bus; runtime input remains on the game bus. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID,
        value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DevelopmentModeHotkeyRegistration {
    private DevelopmentModeHotkeyRegistration() { }

    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
        event.register(DevelopmentModeHotkeys.CREATIVE);
        event.register(DevelopmentModeHotkeys.RETURN);
    }
}
