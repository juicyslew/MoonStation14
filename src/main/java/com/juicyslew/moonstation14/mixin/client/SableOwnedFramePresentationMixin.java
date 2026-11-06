package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Presents the scene-wide frozen frame interval; owner-only endpoints remain isolated from this reader. */
@Mixin(value=ClientSubLevel.class, remap=false)
public abstract class SableOwnedFramePresentationMixin {
    @Inject(method="renderPose()Ldev/ryanhcode/sable/companion/math/Pose3dc;", at=@At("HEAD"), cancellable=true, require=1)
    private void moonstation14$ownedPresentationNoArg(CallbackInfoReturnable<Pose3dc> cir) {
        ClientSubLevel self=(ClientSubLevel)(Object)this;
        float partial=net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        Pose3dc pose=ClientFrameTimelineStore.presentationForFrame(self.getLevel(),self.getUniqueId(),partial);
        if(pose!=null)cir.setReturnValue(pose);
    }
    @Inject(method="renderPose(F)Ldev/ryanhcode/sable/companion/math/Pose3dc;", at=@At("HEAD"), cancellable=true, require=1)
    private void moonstation14$ownedPresentation(float partialTick, CallbackInfoReturnable<Pose3dc> cir) {
        ClientSubLevel self=(ClientSubLevel)(Object)this;
        Pose3dc pose=ClientFrameTimelineStore.presentationForFrame(self.getLevel(),self.getUniqueId(),partialTick);
        if(pose!=null)cir.setReturnValue(pose);
    }
}
