package com.juicyslew.moonstation14;

import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectInstance;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectPayload;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
@net.neoforged.api.distmarker.OnlyIn(Dist.CLIENT)
public class CameraJitterHandler {
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player instanceof IStatusEffectTrait playerStatusEffects)) {
            return;
        }

        StatusEffectInstance jitter = MS14Provider.get(playerStatusEffects.toHandleSelf(), StatusEffectSystem.bridge)
                .get(ModStatusEffects.createKey("jitter"))
                .filter(StatusEffectInstance::isActive)
                .orElse(null);
        if (jitter == null) {
            return;
        }

        float amplitude = 10f;
        float frequency = 4f;
        if (jitter.payload() instanceof StatusEffectPayload.Jitter payload) {
            amplitude = payload.amplitude();
            frequency = payload.frequency();
        }

        // Use the synchronized world clock and this frame's partial tick as
        // the phase. No client-global timer can drift or leak between players.
        double phase = (minecraft.player.level().getGameTime()
                + minecraft.getTimer().getGameTimeDeltaPartialTick(true)) / 20.0d;
        double angularPhase = phase * frequency * Math.PI * 2.0d;
        float intensity = Math.min(4f, amplitude / 100f + 1f) * 0.5f;
        float yawOffset = (float) Math.sin(angularPhase * 0.47d) * intensity;
        float pitchOffset = (float) Math.cos(angularPhase * 0.33633d) * intensity * 1.5f;
        event.setYaw(event.getYaw() + yawOffset);
        event.setPitch(event.getPitch() + pitchOffset);
    }
}
