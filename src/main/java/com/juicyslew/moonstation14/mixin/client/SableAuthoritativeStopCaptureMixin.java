package com.juicyslew.moonstation14.mixin.client;

import com.juicyslew.moonstation14.ms14.shuttle.sable.ClientFrameTimelineStore;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopMovingSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Mirrors the SDK's stop-moving signal into the captured frame timeline. */
@Mixin(value = ClientboundStopMovingSubLevelPacket.class, remap = false)
public abstract class SableAuthoritativeStopCaptureMixin {
    @Shadow public abstract long plotCoordinate();

    @WrapOperation(method = "handle(Lfoundry/veil/api/network/handler/PacketContext;)V",
            at = @At(value = "INVOKE", target = "Lfoundry/veil/api/network/handler/PacketContext;level()Lnet/minecraft/world/level/Level;"))
    private Level moonstation$captureStop(@Coerce Object context, Operation<Level> original,
                                          @Coerce Object packetContext) {
        Level level = original.call(context);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return level;
        var frame = container.getSubLevel(ChunkPos.getX(plotCoordinate()), ChunkPos.getZ(plotCoordinate()));
        if (frame instanceof ClientSubLevel clientFrame) ClientFrameTimelineStore.stop(level, clientFrame);
        return level;
    }
}
