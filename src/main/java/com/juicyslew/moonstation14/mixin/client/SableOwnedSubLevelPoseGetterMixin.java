package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-only getter bridge used by SDK and local query paths that call SubLevel pose getters directly. */
@Mixin(value=SubLevel.class, remap=false)
public abstract class SableOwnedSubLevelPoseGetterMixin {
    @Inject(method="logicalPose()Ldev/ryanhcode/sable/companion/math/Pose3d;",at=@At("HEAD"),cancellable=true,require=1)
    private void moonstation14$ownedLogicalPose(CallbackInfoReturnable<Pose3d> cir) {
        SubLevel self=(SubLevel)(Object)this;
        var scope=OwnedFrameSimulationScope.activeExact(self.getLevel(),self);
        if(scope!=null){cir.setReturnValue((Pose3d)scope.to(self));return;}
        var presentation=com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFramePresentationContext.active(self.getLevel(),self);
        if(presentation!=null){Pose3dc pose=presentation.to(self);if(pose!=null)cir.setReturnValue((Pose3d)pose);}
    }
    @Inject(method="lastPose()Ldev/ryanhcode/sable/companion/math/Pose3dc;",at=@At("HEAD"),cancellable=true,require=1)
    private void moonstation14$ownedLastPose(CallbackInfoReturnable<Pose3dc> cir) {
        SubLevel self=(SubLevel)(Object)this;
        var scope=OwnedFrameSimulationScope.activeExact(self.getLevel(),self);
        if(scope!=null){cir.setReturnValue(scope.from(self));return;}
        var presentation=com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFramePresentationContext.active(self.getLevel(),self);
        if(presentation!=null){Pose3dc pose=presentation.from(self);if(pose!=null)cir.setReturnValue(pose);}
    }
}
