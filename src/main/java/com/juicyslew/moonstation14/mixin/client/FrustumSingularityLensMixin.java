package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.singularity.client.SingularityWorldLensClient;
import com.juicyslew.moonstation14.ms14.singularity.client.SingularityWorldLensCulling;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public abstract class FrustumSingularityLensMixin {
    @Inject(method = "isVisible(Lnet/minecraft/world/phys/AABB;)Z", at = @At("RETURN"), cancellable = true)
    private void moonstation14$includeLensAffectedBounds(AABB bounds, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !SingularityWorldLensClient.isActive()) return;
        var camera = SingularityWorldLensClient.cameraPosition();
        for (var source : SingularityWorldLensClient.worldSources()) {
            if (SingularityWorldLensCulling.sourceConeIntersects(bounds, source, camera)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
