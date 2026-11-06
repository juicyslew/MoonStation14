package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Adds the supported-frame render interpolation offset without changing logical look state. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class SupportedBodyCameraClient {
    private SupportedBodyCameraClient() { }

    /** Narrow entity-yaw access used to transform and restore the real renderer endpoints. */
    public interface ModelYawAccess {
        float yaw(); void yaw(float value); float oldYaw(); void oldYaw(float value);
        float bodyYaw(); void bodyYaw(float value); float oldBodyYaw(); void oldBodyYaw(float value);
        float headYaw(); void headYaw(float value); float oldHeadYaw(); void oldHeadYaw(float value);
    }

    /** Applies one shared committed render delta to all interpolated yaw endpoints, restoring even on render failure. */
    public static void withModelYaw(ModelYawAccess model,float offset,Runnable render) {
        float yaw=model.yaw(),oldYaw=model.oldYaw(),body=model.bodyYaw(),oldBody=model.oldBodyYaw(),
                head=model.headYaw(),oldHead=model.oldHeadYaw();
        try {
            if(Float.isFinite(offset)&&offset!=0f) {
                model.yaw(composePresentationYaw(yaw,offset));model.oldYaw(composePresentationYaw(oldYaw,offset));
                model.bodyYaw(composePresentationYaw(body,offset));model.oldBodyYaw(composePresentationYaw(oldBody,offset));
                model.headYaw(composePresentationYaw(head,offset));model.oldHeadYaw(composePresentationYaw(oldHead,offset));
            }
            render.run();
        } finally {
            model.yaw(yaw);model.oldYaw(oldYaw);model.bodyYaw(body);model.oldBodyYaw(oldBody);
            model.headYaw(head);model.oldHeadYaw(oldHead);
        }
    }

    public static float modelRootRotationDegrees(float bodyYaw) { return 180.0F-bodyYaw; }
    /** Posture movement-facing yaw is independent of transformed entity yaw; apply shared offset once. */
    public static float modelRootRotationDegrees(float entityBodyYaw,float postureFacingYaw,
                                                  boolean hasPosture,float presentationOffset) {
        float rootYaw=hasPosture?composePresentationYaw(postureFacingYaw,presentationOffset):entityBodyYaw;
        return modelRootRotationDegrees(rootYaw);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = (float) event.getPartialTick();
        float offset = GhostControlClient.cameraRenderOffset(minecraft, minecraft.getCameraEntity(), partialTick);
        if (Float.isFinite(offset)) event.setYaw(composePresentationYaw(event.getYaw(), offset));
        if (minecraft.getCameraEntity() instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body) {
            if (minecraft.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK) {
                var comfort = com.juicyslew.moonstation14.ms14.mobs.client.MedicalPerspectiveClient.angularRecoveryViewAngles();
                if (comfort.isPresent()) {
                    event.setYaw(comfort.get().yaw());
                    event.setPitch(comfort.get().pitch());
                    event.setRoll(0.0F);
                    return;
                }
            }
            if (body.angularRecoveryState().isPresent()
                    || AngularRecoveryPresentation.hasCommittedPresentation(body)) {
                var composed = PureAngularRecoveryCameraMath.compose(
                        AngularRecoveryPresentation.orientation(body, partialTick),
                        event.getYaw(), event.getPitch(), event.getRoll());
                event.setYaw(composed.yaw());
                event.setPitch(composed.pitch());
                event.setRoll(composed.roll());
            }
        }
    }

    /** Same additive render basis is used for camera and all model yaw endpoints. */
    public static float composePresentationYaw(float baseYaw, float offset) {
        return ClientSupportedHeading.normalize(baseYaw + offset);
    }
}
