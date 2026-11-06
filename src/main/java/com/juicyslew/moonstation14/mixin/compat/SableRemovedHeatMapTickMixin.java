package com.juicyslew.moonstation14.mixin.compat;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stop Sable's cached plot-split work from ticking after its owning sublevel is removed. */
@Mixin(value = SubLevelHeatMapManager.class, remap = false)
public abstract class SableRemovedHeatMapTickMixin {
    @Shadow @Final private ServerSubLevel subLevel;

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void moonstation14$skipRemovedOwnerHeatMap(CallbackInfo callback) {
        if (subLevel.isRemoved()) callback.cancel();
    }
}
