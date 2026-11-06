package com.juicyslew.moonstation14.gametest.support.mixin;

import com.juicyslew.moonstation14.gametest.support.GameTestProgressObserver;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Non-invasive observation of tests admitted by the public ticker API. */
@Mixin(GameTestTicker.class)
public abstract class GameTestTickerProgressMixin {
    @Inject(method = "add", at = @At("TAIL"))
    private void moonstation14$observeAdded(GameTestInfo info, CallbackInfo ci) {
        GameTestProgressObserver.admitted(info);
    }
}
