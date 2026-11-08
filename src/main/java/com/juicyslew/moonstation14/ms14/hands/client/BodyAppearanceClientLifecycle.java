package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandNetworking;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.world.entity.Entity;

/** Client-only lifecycle hook; deliberately separated from common/server networking classes. */
@EventBusSubscriber(modid="moonstation14", value=Dist.CLIENT)
public final class BodyAppearanceClientLifecycle {
    private BodyAppearanceClientLifecycle() { }
    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) { BodyHandNetworking.clientCache().clear(); }
    @SubscribeEvent public static void entityLeft(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) BodyHandNetworking.clientCache().remove(event.getLevel().dimension().location(),event.getEntity().getUUID(),event.getEntity().getId());
    }
    @SubscribeEvent public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level && level.isClientSide)
            BodyHandNetworking.clientCache().clearDimension(level.dimension().location());
    }
}

