package com.juicyslew.moonstation14;

import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public class CameraJitterHandler {
    static float jitterTimer = 0f;
    static float gameTimer = 0f;
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        // You would replace this check with your actual "tweaking" logic
        // e.g., if (player.getData(MS14DataAttachments.DRUG_EFFECTS).isTweaking())
        IStatusEffectTrait playerStatusEffects = (IStatusEffectTrait) Minecraft.getInstance().player;
        boolean isTweaking = MS14Provider.get(playerStatusEffects.toHandleSelf(), StatusEffectSystem.bridge).getMap().getOrDefault(ModStatusEffects.createKey("jitter"), 0f) > 0f;

        if (isTweaking) {
            float deltaTime = Minecraft.getInstance().getTimer().getGameTimeDeltaTicks() * 0.05f;
            gameTimer += deltaTime;
            float jitterVel = (float) Math.sin(5 * Math.sin(gameTimer * 2.5f))/2f + .7f;
            jitterTimer += deltaTime * jitterVel;
            // Increase intensity or frequency here
            float speed = 9.0f;
            float intensity = 2.0f;

            // Generate semi-random offsets
            // Using sin/cos makes the jitter feel more like a physical vibration
            float yawOffset = (float) Math.sin(jitterTimer * 0.47f * speed) * intensity;
            float pitchOffset = (float) Math.cos(jitterTimer * 0.33633f * speed) * intensity * 1.5f;

            // Apply the offsets to the current camera angles
            event.setYaw(event.getYaw() + yawOffset);
            event.setPitch(event.getPitch() + pitchOffset);
        }
    }
}
