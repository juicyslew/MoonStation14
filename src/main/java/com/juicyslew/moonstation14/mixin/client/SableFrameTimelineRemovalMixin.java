package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Releases captured per-frame history at the SDK's client frame-removal boundary. */
@Mixin(value = ClientSubLevel.class, remap = false)
public abstract class SableFrameTimelineRemovalMixin {
    @Inject(method = "onRemove()V", at = @At("HEAD"))
    private void moonstation$clearFrameTimeline(CallbackInfo ci) {
        ClientSubLevel frame = (ClientSubLevel)(Object)this;
        ClientFrameTimelineStore.remove(frame.getLevel(), frame.getUniqueId());
    }
}
