package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks perspective toggles while a committed body has an active contained view lease. */
@Mixin(Options.class)
public abstract class ContainedPerspectiveLockMixin {
    @Shadow private CameraType cameraType;
    @Inject(method = "setCameraType(Lnet/minecraft/client/CameraType;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void moonstation14$lockContainedPerspective(CameraType requested, CallbackInfo ci) {
        if (ContainedCharacterClientView.perspectiveLocked()) {
            cameraType = CameraType.THIRD_PERSON_BACK;
            ci.cancel();
        }
    }
}

