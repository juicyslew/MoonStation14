package com.juicyslew.moonstation14.ms14.eye.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.eye.CriticalFogPolicy;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.mobs.MobStatePolicy;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import com.mojang.blaze3d.shaders.FogShape;

/** Client projection of a synchronized Critical medical state into camera fog. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
@net.neoforged.api.distmarker.OnlyIn(Dist.CLIENT)
public final class CriticalFogClient {
    private CriticalFogClient() { }

    /** Current synchronized presentation target only; no cached state survives target loss. */
    public static boolean isCriticalObservedBody() {
        LivingEntity body = com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.observedBody();
        if (body == null) return false;
        MobStateAttachment state = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        return state != null && state.data().state() == MobStateComponent.State.CRITICAL;
    }

    /** Critical sight adaptation: opaque black is required for both terrain and sky fog. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!isCriticalObservedBody()) return;
        event.setRed(0f);
        event.setGreen(0f);
        event.setBlue(0f);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        LivingEntity body = com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.observedBody();
        if (body == null) return;
        MobStateAttachment state = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        if (state == null || state.data().state() != MobStateComponent.State.CRITICAL) return;
        MobStatePolicy policy = CharacterIdentitySystem.projectForActor(body)
                .flatMap(MobStatePolicy::from).orElse(null);
        if (policy == null) return;
        double critical = policy.thresholds().thresholds().entrySet().stream()
                .filter(e -> e.getValue() == MobStateComponent.State.CRITICAL)
                .mapToDouble(java.util.Map.Entry::getKey).min().orElse(Double.NaN);
        double dead = policy.thresholds().thresholds().entrySet().stream()
                .filter(e -> e.getValue() == MobStateComponent.State.DEAD)
                .mapToDouble(java.util.Map.Entry::getKey).min().orElse(Double.NaN);
        DamageData damage = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        double total = damage == null ? 0 : damage.getMap().values().stream().mapToDouble(Float::doubleValue).sum();
        float radius = CriticalFogPolicy.radius(total, critical, dead);
        if (!Float.isFinite(radius)) return;
        double cameraBodyDistance = event.getCamera().getPosition().distanceTo(body.position());
        float criticalFar = CriticalFogPolicy.cameraFarPlane(radius, cameraBodyDistance);
        CriticalFogPolicy.FogPlanes planes = CriticalFogPolicy.combine(
                event.getNearPlaneDistance(), event.getFarPlaneDistance(), criticalFar);
        if (planes == null) return;
        // Camera-space adaptation is approximate (especially in third person), not an exact radial shader.
        event.setFogShape(FogShape.SPHERE);
        if (planes.far() < event.getFarPlaneDistance()) {
            event.setNearPlaneDistance(planes.near());
            event.setFarPlaneDistance(planes.far());
        }
        event.setCanceled(true);
    }
}

