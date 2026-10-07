package com.juicyslew.moonstation14.ms14.medical_observer.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Prevents vanilla death UI from obscuring the controlled corpse/ghost experience. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ObserverDeathScreenPolicy {
    private ObserverDeathScreenPolicy() { }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof DeathScreen)) return;
        if (shouldSuppress(ObserverClient.current(), Minecraft.getInstance().player))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DeathScreen && shouldSuppress(ObserverClient.current(), mc.player))
            mc.setScreen(null);
    }

    static boolean shouldSuppress(ObserverPayloads.Snapshot snapshot, LivingEntity player) {
        if (snapshot != null && (snapshot.mode() == ObserverPayloads.Mode.CORPSE
                || snapshot.mode() == ObserverPayloads.Mode.GHOST)) return true;
        if (player == null || MobStateSystem.policy(player) == null) return false;
        return MobStateSystem.isDead(player) || player.getHealth() <= 0;
    }
}
