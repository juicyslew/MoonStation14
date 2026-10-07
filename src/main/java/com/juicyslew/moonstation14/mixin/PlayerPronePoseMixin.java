package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerPronePoseMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void moonstation14$keepPronePose(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (PostureSystem.isDownPosture(self)) {
            self.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }
}
