package com.juicyslew.moonstation14.ms14.atmos.tank.client;

import com.juicyslew.moonstation14.ms14.atmos.tank.*;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid="moonstation14", value=Dist.CLIENT)
public final class CanisterClientEvents {
    private CanisterClientEvents() { }
    @net.neoforged.bus.api.SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { CanisterClientCache.clear(); }
    @net.neoforged.bus.api.SubscribeEvent public static void levelUnload(LevelEvent.Unload event) {
        if(event.getLevel()==Minecraft.getInstance().level) CanisterClientCache.clear();
    }
}


