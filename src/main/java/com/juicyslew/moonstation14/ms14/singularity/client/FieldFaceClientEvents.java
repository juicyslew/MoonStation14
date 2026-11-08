package com.juicyslew.moonstation14.ms14.singularity.client;

import com.juicyslew.moonstation14.ms14.singularity.containment.FieldFaceNetworking;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client-only lifecycle subscriber for the visual field-face snapshot cache. */
@EventBusSubscriber(modid="moonstation14", value=Dist.CLIENT)
public final class FieldFaceClientEvents {
    static { FieldFaceNetworking.installClientHandler(FieldFaceVisualCache::accept); }
    private FieldFaceClientEvents() { }
    @net.neoforged.bus.api.SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { FieldFaceVisualCache.clear(); }
    @net.neoforged.bus.api.SubscribeEvent public static void levelUnload(LevelEvent.Unload e) {
        if(e.getLevel()==Minecraft.getInstance().level) FieldFaceVisualCache.clear();
    }
}

